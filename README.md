# Player Tracker

Mod Minecraft **Fabric 1.21.11** (client) + **serveur relais Python** qui permet
à un groupe de joueurs de voir en temps réel **la direction et la distance** des
autres joueurs **qui ont le mod**.

C'est un système **opt-in** : seuls les joueurs qui installent le mod *et*
utilisent le même code de salon (`room`) apparaissent. Le mod ne révèle jamais
la position de joueurs qui ne participent pas, et **n'envoie rien au serveur
Minecraft** (100 % client-side, aucun paquet). Il fonctionne sur n'importe quel
serveur de jeu (même vanilla) car le partage passe par le relais.

```
┌────────────┐   position (WebSocket)   ┌──────────────┐
│  Mod A     │ ───────────────────────► │              │
│ (Fabric)   │ ◄─────────────────────── │  Serveur     │
└────────────┘   positions des autres   │  relais      │
┌────────────┐                          │  (FastAPI)   │
│  Mod B     │ ◄──────────────────────► │              │
└────────────┘                          └──────────────┘
```

## Fonctionnalités

- **HUD boussole** (haut de l'écran) : un repère coloré par joueur, positionné
  selon sa direction.
- **Liste « Joueurs suivis »** (à gauche) : flèche + distance + direction
  cardinale, option coords exactes (X Y Z).
- **Autre dimension** : le joueur apparaît dans la liste avec une pastille de
  couleur et le nom de sa dimension (🟩 Surface / 🟥 Nether / 🟪 End), et il est
  **exclu de la boussole**.
- **Mode furtif (N)** : tu vois les autres, mais tu disparais **instantanément**
  de leur radar (tu ne partages plus ta position).
- **16 couleurs distinctes** + **indicateur de connexion** (point vert/rouge).
- **Waypoints** : crée des points de repère (commandes `/tracker` ou GUI), colorés,
  **privés par défaut** ou **partagés** au salon. Inclut :
  - **Marqueurs de mort** automatiques (privés),
  - **Faisceau 3D type beacon** au sol du waypoint (option, expérimental).
- **Carte web live** (`/map`) : carte 2D temps réel du salon dans le navigateur,
  avec **sélecteur de monde** (Overworld / Nether / End / dimensions custom).
- **Éditeur de HUD en jeu** (touche M) : **déplacer** boussole / liste / indicateur
  à la souris, les afficher/masquer, gérer les waypoints, régler serveur / salon /
  pseudo — sans éditer de fichier.
- **Langue automatique** : suit la langue du jeu (français, sinon **anglais**).

## Structure

- `mod/` — le mod Fabric (Java 21, client-side uniquement).
- `server/` — le serveur relais WebSocket (FastAPI + Uvicorn) + carte web (`map.html`).

Le code du mod est rangé par responsabilité :

```
thomjap/playertracker/
├── PlayerTrackerClient   entrée : câblage + tick + marqueur de mort
├── config/               TrackerConfig (chargée/sauvée en JSON)
├── model/                TrackedPlayer, Waypoint
├── net/                  RelayClient (WebSocket + reconnexion)
├── hud/                  TrackerHud (boussole/liste), WaypointBeamRenderer (faisceaux)
├── screen/               écrans GUI (éditeur HUD, config serveur, waypoints)
├── input/                TrackerKeybinds (H / M / N)
├── command/              TrackerCommands (/tracker …)
└── util/                 Dimensions, Waypoints (helpers partagés)
```

## Raccourcis en jeu

| Touche | Effet |
|--------|-------|
| **H** | Afficher / masquer le HUD (et les faisceaux) |
| **M** | Éditeur : **glisser** pour déplacer boussole/liste/indicateur ; boutons ON/OFF ; boutons « Serveur & salon… » et « Waypoints » |
| **N** | Mode furtif (voir les autres sans être vu), instantané |
| *(non liée)* | Couper **tout** (déconnecte : tu ne vois plus personne) |

Les touches sont modifiables dans **Options → Commandes → catégorie *Player
Tracker***.

## Waypoints & commandes

Points de repère personnels, **privés par défaut**. Gérables par le **GUI**
(M → **Waypoints** : onglets Tous / Privés / Partagés ; par ligne :
partager, masquer, colorer, supprimer ; l'onglet Partagés montre aussi ceux des
autres, avec option masquer) ou par les **commandes** :

| Commande | Effet |
|----------|-------|
| `/tracker add waypoint <x> <y> <z> [couleur] <nom>` | crée un waypoint (privé) |
| `/tracker add sharewaypoint <x> <y> <z> [couleur] <nom>` | crée un waypoint **partagé** |
| `/tracker add calcwaypoint <x> <y> <z> [couleur] <nom>` | crée le point **dans les 2 mondes** (Overworld ↔ Nether, ÷8/×8), partagé |
| `/tracker share waypoint <nom>` | bascule privé ↔ partagé |
| `/tracker change waypoint color <nom> <couleur>` | change la couleur |
| `/tracker remove waypoint <nom>` · `/tracker list` | supprime · liste |
| `/tracker beams on\|off` | faisceaux 3D |

- **Partagé** = visible par le salon (en jeu chez les autres avec `(pseudo)`, et sur
  la carte web). Les **marqueurs de mort restent privés**.
- **Masquer** = cacher un waypoint de **ta** vue (HUD + faisceau) sans le supprimer ;
  fonctionne aussi sur les waypoints partagés des autres.
- **Couleurs** = celles de Minecraft (`red`, `gold`, `aqua`, `light_purple`…).

## Carte web

Le relais sert une page `/map` : carte 2D temps réel des joueurs et **waypoints
partagés** du salon.

```
http://localhost:8000/map?room=mon-salon
https://<ton-tunnel>/map?room=mon-salon
```

Menu **Salon** + **Monde** (filtre par dimension) en haut. Consultable même sans le
mod. Rien à installer côté visiteur.

## 1. Lancer le serveur relais

```bash
cd server
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
```

Vérifie qu'il tourne : http://localhost:8000/ → `{"status":"ok",...}`.

## 2. Compiler le mod

Nécessite le **JDK 21** (le wrapper Gradle est déjà inclus et utilise
Gradle 9.6.1 — requis par Fabric Loom 1.17.17).

```bash
cd mod
JAVA_HOME=/chemin/vers/jdk-21 ./gradlew build
```

Jar de sortie : `mod/build/libs/playertracker-1.0.0.jar`. Pour tester en dev sans
installer : `./gradlew runClient`.

## 3. Installer le mod

Copie `playertracker-1.0.0.jar` dans le dossier `mods/` de ton instance, avec
**Fabric Loader** et **Fabric API** (1.21.11). Selon ton launcher :

- **Launcher officiel** : `.minecraft/mods/`
- **Modrinth App** : `…/ModrinthApp/profiles/<nom du profil>/mods/`
- **Prism / MultiMC** : `…/instances/<instance>/minecraft/mods/`

> ⚠️ Installe-le dans le dossier `mods/` **de l'instance réellement lancée** par
> ton launcher (pas forcément `.minecraft`).

## 4. Configurer (le plus simple : en jeu)

En jeu : **M** → **« Serveur & salon… »**, puis renseigne :

- **URL du serveur** : l'adresse de **ton** relais (ex. `wss://xxx.trycloudflare.com/ws`).
- **Salon** : un code secret commun à tout le groupe.
- **Pseudo** : vide = ton pseudo Minecraft.

Puis **Enregistrer & se reconnecter**. Tous les joueurs du même salon (et même
URL) se voient.

<details>
<summary>Alternative : fichier de config</summary>

Au premier lancement, le mod crée `<dossier de jeu>/config/playertracker.json` :

```json
{
  "serverUrl": "ws://localhost:8000/ws",
  "room": "mon-salon",
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

## 5. Jouer avec des amis (hors localhost)

Le serveur doit être joignable depuis Internet via un **tunnel**. Deux scripts
tout-en-un dans `server/` lancent le serveur **et** le tunnel, puis affichent
l'URL `wss://…/ws` à coller dans le mod. Toi et tes potes mettez la **même URL +
le même salon** ; eux n'ont besoin ni de serveur ni de tunnel.

| Script | Tunnel | Compte | URL |
|--------|--------|--------|-----|
| `./start.sh` | cloudflared | aucun | **change** à chaque lancement |
| `./start-ngrok.sh` | ngrok | gratuit (authtoken) | **fixe** (domaine statique) |

**cloudflared** (le plus rapide, aucun compte) :
```bash
# une seule fois : télécharger cloudflared
curl -L -o ~/cloudflared https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64
chmod +x ~/cloudflared
./start.sh
```

**ngrok** (URL fixe, compte gratuit) :
```bash
# une seule fois : ngrok config add-authtoken TON_TOKEN
# + réclame ton domaine gratuit sur https://dashboard.ngrok.com/domains
cd server && cp .env.example .env      # puis mets ton domaine dans .env
./start-ngrok.sh
```

> `server/.env` (ton domaine, tes réglages locaux) n'est **pas** versionné — c'est
> le `.env.example` qui sert de modèle.

Dans les deux cas, l'URL affichée est déjà en `wss://…/ws`, prête à coller dans
le mod (**M → Serveur & salon…**). Garde la fenêtre ouverte pendant la partie.

> Pour du 100 % durable (sans garder ton PC allumé), héberge `server/` sur un VPS
> ou Railway/Render en `wss://` — voir [server/README.md](server/README.md).

## Versions

| Composant | Version |
|-----------|---------|
| Minecraft | 1.21.11 |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.141.6+1.21.11 |
| Yarn mappings | 1.21.11+build.6 |
| Fabric Loom | 1.17.17 |
| Gradle | 9.6.1 |
| Java | 21 |
