# Player Tracker

Minecraft **Fabric 1.21.11** mod (client) + **Python relay server** that lets a
group of players see, in real time, **the direction and distance** of the other
players **who have the mod**.

It is an **opt-in** system: only players who install the mod *and* use the same
room code (`room`) show up. The mod never reveals the position of players who do
not take part, and **sends nothing to the Minecraft server** (100% client-side,
no packets). It works on any game server (even vanilla) because sharing goes
through the relay.

```
┌────────────┐   position (WebSocket)   ┌──────────────┐
│  Mod A     │ ───────────────────────► │              │
│ (Fabric)   │ ◄─────────────────────── │  Relay       │
└────────────┘   other players' pos     │  server      │
┌────────────┐                          │  (FastAPI)   │
│  Mod B     │ ◄──────────────────────► │              │
└────────────┘                          └──────────────┘
```

## Features

- **Compass HUD** (top of the screen): a colored marker per player, positioned
  by their direction.
- **"Tracked players" list** (left side): arrow + distance + cardinal direction,
  with an option for exact coordinates (X Y Z).
- **Other dimension**: the player appears in the list with a colored dot and the
  name of their dimension (🟩 Surface / 🟥 Nether / 🟪 End), and is **excluded
  from the compass**.
- **Stealth mode (N)**: you still see the others, but you **instantly** disappear
  from their radar (you stop sharing your position).
- **16 distinct colors** + a **connection indicator** (green/red dot).
- **Waypoints**: create markers (via `/tracker` commands or the GUI), colored,
  **private by default** or **shared** with the room. Includes:
  - automatic **death markers** (private),
  - **3D beacon-style beam** at the waypoint's base (optional, experimental).
- **Live web map** (`/map`): real-time 2D map of the room in the browser, with a
  **world selector** (Overworld / Nether / End / custom dimensions).
- **In-game HUD editor** (M key): **drag** the compass / list / indicator with
  the mouse, show/hide them, manage waypoints, set server / room / username —
  without editing any file.
- **Automatic language**: follows the game language (French, otherwise
  **English**).

## Structure

- `mod/` — the Fabric mod (Java 21, client-side only).
- `server/` — the WebSocket relay server (FastAPI + Uvicorn) + web map (`map.html`).

The mod code is organized by responsibility:

```
thomjap/playertracker/
├── PlayerTrackerClient   entry point: wiring + tick + death marker
├── config/               TrackerConfig (loaded/saved as JSON)
├── model/                TrackedPlayer, Waypoint
├── net/                  RelayClient (WebSocket + reconnection)
├── hud/                  TrackerHud (compass/list), WaypointBeamRenderer (beams)
├── screen/               GUI screens (HUD editor, server config, waypoints)
├── input/                TrackerKeybinds (H / M / N)
├── command/              TrackerCommands (/tracker …)
└── util/                 Dimensions, Waypoints (shared helpers)
```

## In-game shortcuts

| Key | Effect |
|--------|-------|
| **H** | Show / hide the HUD (and the beams) |
| **M** | Editor: **drag** to move the compass/list/indicator; ON/OFF buttons; "Server & room…" and "Waypoints" buttons |
| **N** | Stealth mode (see others without being seen), instant |
| *(unbound)* | Turn **everything** off (disconnects: you no longer see anyone) |

Keys can be changed in **Options → Controls → *Player Tracker* category**.

## Waypoints & commands

Personal markers, **private by default**. Manage them via the **GUI**
(M → **Waypoints**: All / Private / Shared tabs; per row: share, hide, color,
delete; the Shared tab also shows other players', with a hide option) or via the
**commands**:

| Command | Effect |
|----------|-------|
| `/tracker add waypoint <x> <y> <z> [color] <name>` | create a waypoint (private) |
| `/tracker add sharewaypoint <x> <y> <z> [color] <name>` | create a **shared** waypoint |
| `/tracker add calcwaypoint <x> <y> <z> [color] <name>` | create the point **in both worlds** (Overworld ↔ Nether, ÷8/×8), shared |
| `/tracker share waypoint <name>` | toggle private ↔ shared |
| `/tracker change waypoint color <name> <color>` | change the color |
| `/tracker remove waypoint <name>` · `/tracker list` | delete · list |
| `/tracker beams on\|off` | 3D beams |

- **Shared** = visible to the room (in-game for others as `(username)`, and on the
  web map). **Death markers stay private**.
- **Hide** = hide a waypoint from **your** view (HUD + beam) without deleting it;
  also works on other players' shared waypoints.
- **Colors** = Minecraft's (`red`, `gold`, `aqua`, `light_purple`…).

## Web map

The relay serves a `/map` page: a real-time 2D map of the room's players and
**shared waypoints**.

```
http://localhost:8000/map?room=my-room
https://<your-tunnel>/map?room=my-room
```

**Room** + **World** menus (filter by dimension) at the top. Viewable even
without the mod. Nothing to install for the viewer.

## 1. Start the relay server

```bash
cd server
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
```

Check that it is running: http://localhost:8000/ → `{"status":"ok",...}`.

## 2. Build the mod

Requires **JDK 21** (the Gradle wrapper is already included and uses Gradle
9.6.1 — required by Fabric Loom 1.17.17).

```bash
cd mod
JAVA_HOME=/path/to/jdk-21 ./gradlew build
```

Output jar: `mod/build/libs/playertracker-1.0.0.jar`. To test in dev without
installing: `./gradlew runClient`.

## 3. Install the mod

Copy `playertracker-1.0.0.jar` into your instance's `mods/` folder, together with
**Fabric Loader** and **Fabric API** (1.21.11). Depending on your launcher:

- **Official launcher**: `.minecraft/mods/`
- **Modrinth App**: `…/ModrinthApp/profiles/<profile name>/mods/`
- **Prism / MultiMC**: `…/instances/<instance>/minecraft/mods/`

> ⚠️ Install it in the `mods/` folder **of the instance actually launched** by
> your launcher (not necessarily `.minecraft`).

## 4. Configure (easiest: in-game)

In game: **M** → **"Server & room…"**, then fill in:

- **Server URL**: the address of **your** relay (e.g. `wss://xxx.trycloudflare.com/ws`).
- **Room**: a secret code shared by the whole group.
- **Username**: empty = your Minecraft username.

Then **Save & reconnect**. All players on the same room (and same URL) see each
other.

<details>
<summary>Alternative: config file</summary>

On first launch, the mod creates `<game folder>/config/playertracker.json`:

```json
{
  "serverUrl": "ws://localhost:8000/ws",
  "room": "my-room",
  "playerName": "",
  "enabled": true,
  "sharePosition": true,
  "hudEnabled": true,
  "showCompass": true,
  "showList": true,
  "showStatus": true,
  "showCoords": false,
  "compassX": -1, "compassY": 6,
  "listX": 4, "listY": 24,
  "statusX": 4, "statusY": 4,
  "waypoints": [],
  "showBeams": true,
  "sendIntervalTicks": 5,
  "staleTimeoutMs": 15000
}
```
</details>

## 5. Play with friends (outside localhost)

The server must be reachable from the Internet through a **tunnel**. Two
all-in-one scripts in `server/` start the server **and** the tunnel, then print
the `wss://…/ws` URL to paste into the mod. You and your friends use the **same
URL + the same room**; they need neither a server nor a tunnel.

| Script | Tunnel | Account | URL |
|--------|--------|--------|-----|
| `./start.sh` | cloudflared | none | **changes** on every launch |
| `./start-ngrok.sh` | ngrok | free (authtoken) | **fixed** (static domain) |

**cloudflared** (fastest, no account):
```bash
# once: download cloudflared
curl -L -o ~/cloudflared https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64
chmod +x ~/cloudflared
./start.sh
```

**ngrok** (fixed URL, free account):
```bash
# once: ngrok config add-authtoken YOUR_TOKEN
# + claim your free domain at https://dashboard.ngrok.com/domains
cd server && cp .env.example .env      # then put your domain in .env
./start-ngrok.sh
```

> `server/.env` (your domain, your local settings) is **not** versioned — the
> `.env.example` serves as the template.

In both cases, the printed URL is already `wss://…/ws`, ready to paste into the
mod (**M → Server & room…**). Keep the window open during the game.

> For a fully persistent setup (without keeping your PC on), host `server/` on a
> VPS or Railway/Render over `wss://` — see [server/README.md](server/README.md).

## Versions

| Component | Version |
|-----------|---------|
| Minecraft | 1.21.11 |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.141.6+1.21.11 |
| Yarn mappings | 1.21.11+build.6 |
| Fabric Loom | 1.17.17 |
| Gradle | 9.6.1 |
| Java | 21 |
