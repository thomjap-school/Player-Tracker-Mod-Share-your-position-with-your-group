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
# Génère/charge les jetons de rôle AVANT tout le reste (évite toute course).
python3 -c "import relaykeys; relaykeys.load()" >/dev/null 2>&1 || true
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
	URL="$(grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' "$CF_LOG" | grep -vE '://api\.' | head -1 || true)"
	[ -n "$URL" ] && break
	sleep 1
done

echo
if [ -z "$URL" ]; then
	echo "!! Impossible d'obtenir l'URL du tunnel cloudflared."
	if grep -qiE "failed to request quick Tunnel|certificate is valid for|x509" "$CF_LOG"; then
		echo "   Ton reseau bloque/intercepte trycloudflare.com (frequent en ecole/entreprise)."
		echo "   -> Utilise ngrok a la place :  ./start-ngrok.sh"
	fi
	echo "   Detail du log ($CF_LOG) :"
	grep -iE "failed|error|certificate|x509" "$CF_LOG" | tail -3
else
	HOST="${URL#https://}"
	LINKS="$(python3 links.py "$HOST" wss)"
	TRACKER="$(printf '%s\n' "$LINKS" | sed -n 's/^TRACKER //p')"
	BEACON="$(printf '%s\n' "$LINKS" | sed -n 's/^BEACON //p')"
	MAP="$(printf '%s\n' "$LINKS" | sed -n 's/^MAP //p')"
	echo "=================================================================="
	echo "  Liens (meme salon) — coller dans M -> Serveur & salon... :"
	echo
	echo "   * Player Tracker (voir + etre vu) — GARDE-le dans ton groupe :"
	echo "       $TRACKER"
	echo
	echo "   * Player Beacon (emetteur seul) — a donner a ceux que tu veux"
	echo "     suivre SANS qu'ils voient les autres. Le lien est CHIFFRE :"
	echo "     seul le mod Beacon le decode (impossible de lire le host ni"
	echo "     de le transformer en lien Tracker) :"
	echo "       $BEACON"
	echo
	echo "   * Carte web (montre tout le monde, protegee par le jeton) :"
	echo "       $MAP"
	echo
	echo "=================================================================="
fi
echo
echo "Logs : serveur=$SRV_LOG  tunnel=$CF_LOG"
echo "Laisse cette fenêtre ouverte pendant que vous jouez. Ctrl+C pour arrêter."

# garde le script vivant tant que le serveur ou le tunnel tourne
wait
