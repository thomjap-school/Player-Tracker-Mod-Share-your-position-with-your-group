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
 * Mod configuration, saved to {@code .minecraft/config/playertracker.json}.
 */
public class TrackerConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH =
			FabricLoader.getInstance().getConfigDir().resolve("playertracker.json");

	/** Relay server URL. Use wss:// in production (TLS). */
	public String serverUrl = "ws://localhost:8000/ws";
	/** "Room" code: only players with the same code see each other. */
	public String room = "mon-salon";
	/** Displayed username. Empty = Minecraft username. */
	public String playerName = "";

	/** Mod enabled (connection + HUD + sharing). Turns everything off. */
	public boolean enabled = true;
	/** Stealth mode: if false, you see others but do not share your position. */
	public boolean sharePosition = true;
	/** HUD display enabled. */
	public boolean hudEnabled = true;
	/** Show the compass at the top of the screen. */
	public boolean showCompass = true;
	/** Show the players list on the left. */
	public boolean showList = true;
	/** Show the small server connection indicator (top-left). */
	public boolean showStatus = true;
	/** Show exact coordinates (X Y Z) next to the username in the list. */
	public boolean showCoords = false;

	/** HUD position (movable via the editor, M key). -1 = auto. */
	public int compassX = -1;   // -1 = compass centered horizontally
	public int compassY = 6;
	public int listX = 4;
	public int listY = 24;

	/** Position of the connection indicator (movable via the M editor). */
	public int statusX = 4;
	public int statusY = 4;

	/** Personal waypoints (created via /tracker or on death). */
	public List<Waypoint> waypoints = new ArrayList<>();
	/** Keys (Waypoint.sharedKey) of others' SHARED waypoints hidden from my view. */
	public List<String> hiddenShared = new ArrayList<>();
	/** Automatically create a waypoint for non-common event crates. */
	public boolean crateWaypoints = true;
	/** GLFW key code that clears nearby crate waypoints. -1 = unbound. Set via the hidden menu. */
	public int crateClearKey = -1;
	/** Show a 3D beam (beacon-style) at each waypoint (experimental). */
	public boolean showBeams = true;

	/** Position send interval (in ticks; 5 = ~4x/second). */
	public int sendIntervalTicks = 5;
	/** Hide a player who hasn't moved for this delay (ms). */
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
