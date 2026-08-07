package thomjap.playertracker.config;

import thomjap.playertracker.PlayerTrackerClient;
import thomjap.playertracker.model.Waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration du mod, sauvegardée dans
 * {@code .minecraft/config/playertracker.json}.
 */
public class TrackerConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH =
			FabricLoader.getInstance().getConfigDir().resolve("playertracker.json");

	/** URL du serveur relais. Utilise wss:// en production (TLS). */
	public String serverUrl = "ws://localhost:8000/ws";
	/** Code de "salon" : seuls les joueurs avec le même code se voient. */
	public String room = "mon-salon";
	/** Pseudo affiché. Vide = pseudo Minecraft. */
	public String playerName = "";

	/** Mod activé (connexion + HUD + partage). Coupe tout. */
	public boolean enabled = true;
	/** Mode furtif : si false, tu vois les autres mais tu ne partages pas ta position. */
	public boolean sharePosition = true;
	/** Affichage du HUD activé. */
	public boolean hudEnabled = true;
	/** Afficher la boussole en haut de l'écran. */
	public boolean showCompass = true;
	/** Afficher la liste des joueurs à gauche. */
	public boolean showList = true;
	/** Afficher le petit indicateur de connexion au serveur (haut-gauche). */
	public boolean showStatus = true;
	/** Afficher les coordonnées exactes (X Y Z) à côté du pseudo dans la liste. */
	public boolean showCoords = false;

	/** Position du HUD (déplaçable via l'éditeur, touche M). -1 = auto. */
	public int compassX = -1;   // -1 = boussole centrée horizontalement
	public int compassY = 6;
	public int listX = 4;
	public int listY = 24;

	/** Position de l'indicateur de connexion (déplaçable via l'éditeur M). */
	public int statusX = 4;
	public int statusY = 4;

	/** Waypoints personnels (créés via /tracker ou à la mort). */
	public List<Waypoint> waypoints = new ArrayList<>();
	/** Clés (Waypoint.sharedKey) des waypoints PARTAGÉS des autres masqués de ma vue. */
	public List<String> hiddenShared = new ArrayList<>();
	/** Créer automatiquement un waypoint pour les caisses d'event non communes. */
	public boolean crateWaypoints = true;
	/** Afficher un faisceau 3D (type beacon) à chaque waypoint (expérimental). */
	public boolean showBeams = true;

	/** Intervalle d'envoi de la position (en ticks ; 5 = ~4x/seconde). */
	public int sendIntervalTicks = 5;
	/** On masque un joueur qui n'a pas bougé depuis ce délai (ms). */
	public long staleTimeoutMs = 15000;

	public static TrackerConfig load() {
		try {
			if (Files.exists(PATH)) {
				TrackerConfig cfg = GSON.fromJson(Files.readString(PATH), TrackerConfig.class);
				if (cfg != null) {
					cfg.save(); // réécrit avec les éventuels nouveaux champs par défaut
					return cfg;
				}
			}
		} catch (Exception e) {
			PlayerTrackerClient.LOGGER.error("Échec du chargement de la config", e);
		}
		TrackerConfig cfg = new TrackerConfig();
		cfg.save();
		return cfg;
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			Files.writeString(PATH, GSON.toJson(this));
		} catch (IOException e) {
			PlayerTrackerClient.LOGGER.error("Échec de la sauvegarde de la config", e);
		}
	}
}
