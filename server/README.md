# Relay server — Player Tracker

Real-time WebSocket relay. Receives player positions and rebroadcasts them to
members of the same `room`.

## Run locally

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
```

- `GET /` → health check (`{"status":"ok","rooms":N,"players":M}`).
- `WS /r/{token}` → single entry point; the **opaque token** picks the role:
  - `TRACKER_KEY` → full duplex (send position + receive others).
  - `BEACON_KEY` → **emitter-only, enforced server-side**: feeds the same rooms
    (visible to trackers) but never receives the roster — so even the Tracker mod
    on this token sees nobody. Any other token is refused.
  The two tokens are independent random strings, generated once and persisted to
  `.relaykeys` (or set via `TRACKER_KEY` / `BEACON_KEY` env vars). The **Beacon**
  link handed out is `beacon:<encrypted>` — the real URL is encrypted (see
  `linkcrypto.py` / the mod's `LinkCrypto`), so recipients can't read it.
- `GET /map?room=<room>&k=<TRACKER_KEY>` → **live web map** (requires the tracker
  token; it shows everyone's position).

> ⚠️ Uvicorn is **not** started with `--reload`: after any change to `main.py`,
> stop it (`Ctrl+C`) and restart it to apply the changes.

## All-in-one: `./start.sh` or `./start-ngrok.sh`

Easiest for each session: these scripts start the server **and** the tunnel, and
print the `wss://…/ws` URL to paste into the mod directly.

```bash
./start.sh          # cloudflared tunnel (URL changes on every launch)
./start-ngrok.sh    # ngrok tunnel (FIXED URL, see the ngrok section below)
```

Copy the printed URL, paste it into the mod (M → Server & room…), share it with
your friends using the same room. Keep the window open, `Ctrl+C` to stop
everything. (Manual steps detailed below.)

## Expose to your friends with cloudflared (fast, no network config)

```bash
# once: download the binary
curl -L -o ~/cloudflared https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64
chmod +x ~/cloudflared

# terminal A: the server (see above, must be running)
# terminal B: the tunnel to the local server
~/cloudflared tunnel --url http://localhost:8000
```

cloudflared prints a box with a URL like `https://xxxx-yyyy.trycloudflare.com`.
In the mod, everyone uses **`wss://xxxx-yyyy.trycloudflare.com/ws`** (the `https`
becomes `wss`, and you add `/ws`).

- Keep **both terminals open** while you play.
- If you restart uvicorn, **no need to restart cloudflared** (the URL does not
  change). But if you restart **cloudflared**, the URL changes → you must update
  it everywhere.
- Test the full chain: `curl https://xxxx-yyyy.trycloudflare.com/` should return
  `{"status":"ok",...}`.

## Alternative: ngrok (fixed URL)

ngrok also exposes `localhost:8000`, but offers a **free fixed domain** (1 per
account) — no need to reshare the URL every session.

```bash
# once:
#  - create an account on ngrok.com and grab your authtoken
ngrok config add-authtoken YOUR_TOKEN
#  - claim your free domain at https://dashboard.ngrok.com/domains
#    (auto-generated name, e.g. plucky-otter-1234.ngrok-free.dev)

# each session (uvicorn running alongside):
ngrok http --url=https://plucky-otter-1234.ngrok-free.dev 8000
```

In the mod: `wss://plucky-otter-1234.ngrok-free.dev/ws`. The `./start-ngrok.sh`
script does it all at once — set your domain with
`NGROK_DOMAIN=your-domain.ngrok-free.dev ./start-ngrok.sh` (or edit the variable
at the top of the script).

> ⚠️ Use **only one** tunnel at a time (cloudflared **or** ngrok, not both).
> Choosing a custom subdomain name is paid; the free auto-generated domain is
> more than enough.

## Protocol

Client → server:

```json
// Publish your position (the mod sends ~4x/second while sharing)
{ "type": "update", "room": "my-room", "name": "Steve",
  "x": 12.5, "y": 64.0, "z": -30.2, "dim": "minecraft:overworld" }

// Stealth mode: receive others WITHOUT publishing your position.
// Also immediately removes the player from the roster if present (instant vanish).
{ "type": "subscribe", "room": "my-room" }

// SHARED waypoints (for the map + other players). Replaces the owner's list.
{ "type": "waypoints", "room": "my-room", "owner": "Steve", "waypoints": [
  { "name": "Base", "x": 100, "y": 64, "z": 200, "dim": "minecraft:overworld", "color": -1381654 }
] }
```

Server → clients (rebroadcast on every change in the room):

```json
{ "type": "players",
  "players":   [ { "name": "Alex", "x": 100.0, "y": 70.0, "z": 5.0, "dim": "minecraft:overworld" } ],
  "waypoints": [ { "name": "Base", "x": 100, "y": 64, "z": 200, "dim": "minecraft:overworld",
                   "color": -1381654, "owner": "Steve" } ] }
```

A player's waypoints are removed when they disconnect. Only **shared** waypoints
are sent (private ones never leave the client).

On connect, the mod sends a `subscribe` (so it receives others even in stealth
mode). A player with no update for `STALE_SECONDS` (20s) is removed
automatically.

## Deployment

On a VPS behind a reverse proxy with TLS (strongly recommended to get `wss://`).
Example **Caddy** (`Caddyfile`):

```
tracker.mydomain.com {
    reverse_proxy 127.0.0.1:8000
}
```

Caddy handles HTTPS automatically; the mod then uses
`wss://tracker.mydomain.com/ws`. Platforms like Railway / Render / Fly.io also
provide turnkey HTTPS/WSS — just point `serverUrl` at the provided URL
(`wss://...`).

To run in production:

```bash
uvicorn main:app --host 0.0.0.0 --port 8000 --workers 1
```

> ⚠️ Keep **1 worker**: room state is in memory and shared across connections. To
> scale over multiple workers/instances, you would need a shared backend (e.g.
> Redis pub/sub) — not needed for use among friends.

## Security

- The `room` acts as a shared secret: pick a code that can't be guessed.
- Use **wss://** (TLS) in production so positions aren't exposed in clear text.
- Optionally add an authentication token if the relay is public.
