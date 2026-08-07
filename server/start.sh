#!/usr/bin/env bash
#
# Lance le serveur relais + le tunnel cloudflared, et affiche l'URL wss://
# prête à coller dans le mod (à repartager à chaque session, l'URL change).
#
# Usage :  ./start.sh
# Arrêt  :  Ctrl+C (coupe le serveur ET le tunnel)
#
set -euo pipefail
cd "$(dirname "$0")"

PORT="${PORT:-8000}"
CF="${CLOUDFLARED:-$HOME/cloudflared}"
SRV_LOG="$(mktemp)"
CF_LOG="$(mktemp)"

# --- venv + dépendances ---
if [ ! -d .venv ]; then
	python3 -m venv .venv
fi
# shellcheck disable=SC1091
source .venv/bin/activate
pip install -q -r requirements.txt

# --- cloudflared présent ? ---
if [ ! -x "$CF" ]; then
	echo "cloudflared introuvable à '$CF'."
	echo "Télécharge-le une fois :"
	echo "  curl -L -o ~/cloudflared https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64"
	echo "  chmod +x ~/cloudflared"
	exit 1
fi

# --- démarrage ---
uvicorn main:app --host 0.0.0.0 --port "$PORT" >"$SRV_LOG" 2>&1 &
SRV_PID=$!
"$CF" tunnel --url "http://localhost:$PORT" >"$CF_LOG" 2>&1 &
CF_PID=$!

cleanup() { kill "$SRV_PID" "$CF_PID" 2>/dev/null || true; }
trap cleanup EXIT INT TERM

echo "Serveur (pid $SRV_PID) + tunnel (pid $CF_PID) démarrés. Recherche de l'URL..."

# --- récupérer l'URL trycloudflare ---
URL=""
for _ in $(seq 1 40); do
	URL="$(grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' "$CF_LOG" | head -1 || true)"
	[ -n "$URL" ] && break
	sleep 1
done

echo
if [ -z "$URL" ]; then
	echo "!! URL pas trouvée. Regarde le log du tunnel : $CF_LOG"
else
	WSS="wss://${URL#https://}/ws"
	echo "=================================================================="
	echo "  URL à coller dans le mod (toi + tes potes) :"
	echo
	echo "      $WSS"
	echo
	echo "  (M -> Serveur & salon... -> colle-la, choisis un salon commun)"
	echo "=================================================================="
fi
echo
echo "Logs : serveur=$SRV_LOG  tunnel=$CF_LOG"
echo "Laisse cette fenêtre ouverte pendant que vous jouez. Ctrl+C pour arrêter."

# garde le script vivant tant que le serveur ou le tunnel tourne
wait
