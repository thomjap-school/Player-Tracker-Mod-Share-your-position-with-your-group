"""
Player Tracker — serveur relais temps réel.

Reçoit les positions des joueurs (mod Fabric) via WebSocket et les rediffuse
à tous les autres joueurs de la même "room". Système opt-in : seuls les
joueurs qui ont le mod et partagent la même room se voient.

Lancer :
    pip install -r requirements.txt
    uvicorn main:app --host 0.0.0.0 --port 8000
"""

import json
import os
import time
from typing import Dict, Tuple

from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.responses import FileResponse, JSONResponse

MAP_HTML = os.path.join(os.path.dirname(__file__), "map.html")

app = FastAPI(title="Player Tracker Relay")

# On considère un joueur "hors ligne" s'il n'a pas envoyé de position depuis ce délai.
STALE_SECONDS = 20

# room -> { name -> {x, y, z, dim, ts} }
rooms: Dict[str, Dict[str, dict]] = {}
# websocket -> (room, name)
clients: Dict[WebSocket, Tuple[str, str]] = {}
# room -> { owner -> [ {name, x, y, z, dim, color, owner}, ... ] }
room_waypoints: Dict[str, Dict[str, list]] = {}
# websocket -> (room, owner) : pour retirer ses waypoints à la déconnexion
client_waypoints: Dict[WebSocket, Tuple[str, str]] = {}


def build_roster(room: str) -> str:
    """Construit la liste des joueurs actifs d'une room (et purge les inactifs)."""
    now = time.time()
    players = []
    room_players = rooms.get(room, {})
    for name, st in list(room_players.items()):
        if now - st["ts"] > STALE_SECONDS:
            room_players.pop(name, None)
            continue
        players.append(
            {"name": name, "x": st["x"], "y": st["y"], "z": st["z"], "dim": st["dim"]}
        )
    wps = []
    for owner_wps in room_waypoints.get(room, {}).values():
        wps.extend(owner_wps)
    return json.dumps({"type": "players", "players": players, "waypoints": wps})


def _remove_from_roster(room: str, name: str) -> bool:
    """Retire un joueur du roster de sa room et purge la room si elle devient vide.
    Renvoie True si le joueur y figurait (donc s'il faut rediffuser)."""
    if not name or room not in rooms:
        return False
    existed = rooms[room].pop(name, None) is not None
    if not rooms[room]:
        rooms.pop(room, None)
    return existed


def _drop_waypoints(room: str, owner: str) -> None:
    """Retire les waypoints d'un propriétaire dans une room."""
    if room in room_waypoints:
        room_waypoints[room].pop(owner, None)
        if not room_waypoints[room]:
            room_waypoints.pop(room, None)


async def broadcast(room: str) -> None:
    """Envoie la liste des joueurs à tous les clients connectés à cette room."""
    message = build_roster(room)
    dead = []
    for ws, (r, _name) in list(clients.items()):
        if r != room:
            continue
        try:
            await ws.send_text(message)
        except Exception:
            dead.append(ws)
    for ws in dead:
        await cleanup(ws)


async def cleanup(ws: WebSocket) -> None:
    """Retire un client déconnecté (position + waypoints) et prévient sa/ses room(s)."""
    info = clients.pop(ws, None)
    wp = client_waypoints.pop(ws, None)
    to_update = set()
    if info and _remove_from_roster(info[0], info[1]):
        to_update.add(info[0])
    if wp:
        _drop_waypoints(wp[0], wp[1])
        to_update.add(wp[0])
    for room in to_update:
        try:
            await broadcast(room)
        except Exception:
            pass


@app.get("/")
async def health():
    total = sum(len(v) for v in rooms.values())
    return JSONResponse({"status": "ok", "rooms": len(rooms), "players": total})


@app.get("/map")
async def live_map():
    """Carte web live : abonnée en lecture seule à un salon (?room=...)."""
    return FileResponse(MAP_HTML, media_type="text/html")


@app.websocket("/ws")
async def ws_endpoint(ws: WebSocket):
    await ws.accept()
    try:
        while True:
            raw = await ws.receive_text()
            try:
                data = json.loads(raw)
            except (ValueError, TypeError):
                continue

            msg_type = data.get("type")

            # Mode furtif : s'abonner à un salon pour RECEVOIR les autres, sans
            # publier sa propre position (on n'apparaît pas dans le roster).
            if msg_type == "subscribe":
                room = str(data.get("room", "default"))[:64]
                prev = clients.get(ws)
                # Retire toute identité publiée précédente (même salon inclus) pour
                # disparaître IMMÉDIATEMENT du radar des autres (mode furtif).
                if prev and _remove_from_roster(prev[0], prev[1]) and prev[0] != room:
                    await broadcast(prev[0])
                clients[ws] = (room, None)  # None = abonné sans position
                await broadcast(room)  # prévient le salon (tu as disparu) + te renvoie le roster
                continue

            # Waypoints partagés (affichés sur la carte web).
            if msg_type == "waypoints":
                room = str(data.get("room", "default"))[:64]
                owner = str(data.get("owner", "?"))[:32]
                clean = []
                for w in (data.get("waypoints") or [])[:200]:
                    try:
                        clean.append({
                            "name": str(w.get("name", ""))[:64],
                            "x": float(w["x"]),
                            "y": float(w["y"]),
                            "z": float(w["z"]),
                            "dim": str(w.get("dim", "minecraft:overworld"))[:64],
                            "color": int(w.get("color", 0)),
                            "owner": owner,
                        })
                    except (KeyError, ValueError, TypeError):
                        continue
                # nettoie d'éventuels waypoints précédents de ce client (autre room/owner)
                prev_wp = client_waypoints.get(ws)
                if prev_wp and prev_wp != (room, owner):
                    _drop_waypoints(prev_wp[0], prev_wp[1])
                    if prev_wp[0] != room:
                        await broadcast(prev_wp[0])
                if clean:
                    room_waypoints.setdefault(room, {})[owner] = clean
                    client_waypoints[ws] = (room, owner)
                else:
                    _drop_waypoints(room, owner)
                    client_waypoints.pop(ws, None)
                await broadcast(room)
                continue

            if msg_type != "update":
                continue

            room = str(data.get("room", "default"))[:64]
            name = str(data.get("name", "player"))[:32]
            try:
                x = float(data["x"])
                y = float(data["y"])
                z = float(data["z"])
            except (KeyError, ValueError, TypeError):
                continue
            dim = str(data.get("dim", "minecraft:overworld"))[:64]

            # Si le joueur a changé de room ou de pseudo, on nettoie l'ancienne entrée.
            prev = clients.get(ws)
            if prev and prev != (room, name) and _remove_from_roster(prev[0], prev[1]):
                await broadcast(prev[0])

            clients[ws] = (room, name)
            rooms.setdefault(room, {})[name] = {
                "x": x,
                "y": y,
                "z": z,
                "dim": dim,
                "ts": time.time(),
            }
            await broadcast(room)
    except WebSocketDisconnect:
        pass
    except Exception:
        pass
    finally:
        await cleanup(ws)
