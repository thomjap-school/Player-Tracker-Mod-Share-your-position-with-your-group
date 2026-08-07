# Serveur relais — Player Tracker

Relais WebSocket temps réel. Reçoit les positions des joueurs et les rediffuse
aux membres de la même `room`.

## Lancer en local

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
```

- `GET /` → état de santé (`{"status":"ok","rooms":N,"players":M}`).
- `GET /map?room=<salon>` → **carte web live** (page HTML autonome, `map.html`).
- `WS /ws` → point d'entrée temps réel.

> ⚠️ Uvicorn n'est **pas** lancé en `--reload` : après toute modification de
> `main.py`, arrête (`Ctrl+C`) et relance-le pour appliquer les changements.

## Tout-en-un : `./start.sh` ou `./start-ngrok.sh`

Le plus simple à chaque session : ces scripts lancent le serveur **et** le tunnel,
et affichent directement l'URL `wss://…/ws` à coller dans le mod.

```bash
./start.sh          # tunnel cloudflared (URL qui change à chaque lancement)
./start-ngrok.sh    # tunnel ngrok (URL FIXE, voir la section ngrok plus bas)
```

Copie l'URL affichée, colle-la dans le mod (M → Serveur & salon…), partage-la à
tes potes avec le même salon. Laisse la fenêtre ouverte, `Ctrl+C` pour tout
arrêter. (Détail des étapes manuelles ci-dessous.)

## Exposer à tes amis avec cloudflared (rapide, sans config réseau)

```bash
# une seule fois : télécharger le binaire
curl -L -o ~/cloudflared https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64
chmod +x ~/cloudflared

# terminal A : le serveur (voir ci-dessus, doit tourner)
# terminal B : le tunnel vers le serveur local
~/cloudflared tunnel --url http://localhost:8000
```

cloudflared affiche un encadré avec une URL du type
`https://xxxx-yyyy.trycloudflare.com`. Dans le mod, chacun met
**`wss://xxxx-yyyy.trycloudflare.com/ws`** (le `https` devient `wss`, et on ajoute
`/ws`).

- Garde **les deux terminaux ouverts** pendant que vous jouez.
- Si tu redémarres uvicorn, **inutile de relancer cloudflared** (l'URL ne change
  pas). Mais si tu relances **cloudflared**, l'URL change → il faut la remettre
  partout.
- Tester la chaîne complète : `curl https://xxxx-yyyy.trycloudflare.com/` doit
  renvoyer `{"status":"ok",...}`.

## Alternative : ngrok (URL fixe)

ngrok expose aussi `localhost:8000`, mais offre un **domaine fixe gratuit** (1 par
compte) — plus besoin de repartager l'URL à chaque session.

```bash
# une seule fois :
#  - crée un compte sur ngrok.com et récupère ton authtoken
ngrok config add-authtoken TON_TOKEN
#  - réclame ton domaine gratuit sur https://dashboard.ngrok.com/domains
#    (nom auto-généré, ex. plucky-otter-1234.ngrok-free.dev)

# à chaque session (uvicorn tourne à côté) :
ngrok http --url=https://plucky-otter-1234.ngrok-free.dev 8000
```

Dans le mod : `wss://plucky-otter-1234.ngrok-free.dev/ws`. Le script
`./start-ngrok.sh` fait tout d'un coup — mets ton domaine via
`NGROK_DOMAIN=ton-domaine.ngrok-free.dev ./start-ngrok.sh` (ou édite la variable
en haut du script).

> ⚠️ On n'utilise **qu'un seul** tunnel à la fois (cloudflared **ou** ngrok), pas
> les deux. Choisir un nom de sous-domaine personnalisé est payant ; le domaine
> auto-généré gratuit suffit largement.

## Protocole

Client → serveur :

```json
// Publier sa position (le mod envoie ~4x/seconde tant qu'on partage)
{ "type": "update", "room": "mon-salon", "name": "Steve",
  "x": 12.5, "y": 64.0, "z": -30.2, "dim": "minecraft:overworld" }

// Mode furtif : recevoir les autres SANS publier sa position.
// Retire aussi immédiatement le joueur du roster s'il y était (disparition instantanée).
{ "type": "subscribe", "room": "mon-salon" }

// Waypoints PARTAGÉS (pour la carte + les autres joueurs). Remplace la liste du proprio.
{ "type": "waypoints", "room": "mon-salon", "owner": "Steve", "waypoints": [
  { "name": "Base", "x": 100, "y": 64, "z": 200, "dim": "minecraft:overworld", "color": -1381654 }
] }
```

Serveur → clients (rediffusé à chaque changement de la room) :

```json
{ "type": "players",
  "players":   [ { "name": "Alex", "x": 100.0, "y": 70.0, "z": 5.0, "dim": "minecraft:overworld" } ],
  "waypoints": [ { "name": "Base", "x": 100, "y": 64, "z": 200, "dim": "minecraft:overworld",
                   "color": -1381654, "owner": "Steve" } ] }
```

Les waypoints d'un propriétaire sont retirés à sa déconnexion. Seuls les waypoints
**partagés** sont envoyés (les privés ne quittent jamais le client).

À la connexion, le mod envoie un `subscribe` (il reçoit donc les autres même en
furtif). Un joueur sans mise à jour depuis `STALE_SECONDS` (20 s) est retiré
automatiquement.

## Déploiement

Sur un VPS derrière un reverse proxy avec TLS (fortement recommandé pour avoir
du `wss://`). Exemple **Caddy** (`Caddyfile`) :

```
tracker.mondomaine.fr {
    reverse_proxy 127.0.0.1:8000
}
```

Caddy gère le HTTPS automatiquement ; le mod utilise alors
`wss://tracker.mondomaine.fr/ws`. Les plateformes type Railway / Render / Fly.io
fournissent aussi du HTTPS/WSS clé en main — pointe simplement `serverUrl` sur
l'URL fournie (`wss://...`).

Pour lancer en production :

```bash
uvicorn main:app --host 0.0.0.0 --port 8000 --workers 1
```

> ⚠️ Garde **1 worker** : l'état des rooms est en mémoire et partagé entre les
> connexions. Pour scaler sur plusieurs workers/instances, il faudrait un backend
> partagé (ex. Redis pub/sub) — non nécessaire pour un usage entre amis.

## Sécurité

- Le `room` sert de secret partagé : choisis un code non devinable.
- Utilise **wss://** (TLS) en production pour ne pas exposer les positions en clair.
- Ajoute éventuellement un jeton d'authentification si le relais est public.
