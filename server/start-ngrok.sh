#!/usr/bin/env bash
#
# Lance le serveur relais + le tunnel ngrok sur ton domaine FIXE.
# L'URL à coller dans le mod ne change jamais : wss://<domaine>/ws
#
# Prérequis (une seule fois) :
#   - installer ngrok
#   - ngrok config add-authtoken TON_TOKEN
#
# Usage :  ./start-ngrok.sh
# Arrêt  :  Ctrl+C
#
set -euo pipefail
cd "$(dirname "$0")"

# Config locale non versionnée (voir .env.example)
if [ -f .env ]; then
	set -a
	# shellcheck disable=SC1091
	. ./.env
	set +a
fi

PORT="${PORT:-8000}"
DOMAIN="${NGROK_DOMAIN:-}"
if [ -z "$DOMAIN" ]; then
	echo "NGROK_DOMAIN non défini. Copie .env.example en .env et mets ton domaine ngrok."
	exit 1
fi

# --- venv + dépendances ---
if [ ! -d .venv ]; then
	python3 -m venv .venv
fi
# shellcheck disable=SC1091
source .venv/bin/activate
pip install -q -r requirements.txt

command -v ngrok >/dev/null 2>&1 || {
	echo "ngrok introuvable. Installe-le puis : ngrok config add-authtoken TON_TOKEN"
	exit 1
}

# --- serveur en arrière-plan ---
uvicorn main:app --host 0.0.0.0 --port "$PORT" >"$(mktemp)" 2>&1 &
SRV_PID=$!
trap 'kill "$SRV_PID" 2>/dev/null || true' EXIT INT TERM

echo "=================================================================="
echo "  URL à coller dans le mod (toi + tes potes) :"
echo
echo "      wss://$DOMAIN/ws"
echo
echo "  Fixe (ne change plus).  En jeu : M -> Serveur & salon..."
echo "=================================================================="
echo "Laisse cette fenetre ouverte pendant que vous jouez. Ctrl+C pour arreter."
echo

# --- tunnel ngrok au premier plan (ses logs/erreurs s'affichent) ---
ngrok http --url="https://$DOMAIN" "$PORT"
