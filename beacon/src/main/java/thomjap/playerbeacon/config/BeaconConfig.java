package thomjap.playerbeacon.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import thomjap.playerbeacon.PlayerBeaconClient;
import thomjap.playerbeacon.model.Waypoint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Emitter config, in {@code config/playerbeacon.json}. */
public class BeaconConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH =
			FabricLoader.getInstance().getConfigDir().resolve("playerbeacon.json");

	/** Relay server URL (the same as Player Tracker). */
	public String serverUrl = "ws://localhost:8000/ws";
	/** Shared room (must match the group tracking you). */
	public String room = "mon-salon";
	/** Displayed username. Empty = Minecraft username. */
	public String playerName = "";
	/** Broadcasting enabled. */
	public boolean enabled = true;
	/** Intervalle d'envoi (ticks ; 5 = ~4x/seconde). */
	public int sendIntervalTicks = 5;
	/** Waypoints broadcast to the room (created via /beacon). */
	public List<Waypoint> waypoints = new ArrayList<>();

	public static BeaconConfig load() {
		try {
			if (Files.exists(PATH)) {
				BeaconConfig cfg = GSON.fromJson(Files.readString(PATH), BeaconConfig.class);
				if (cfg != null) {
					cfg.save();
					return cfg;
				}
			}
		} catch (Exception e) {
			PlayerBeaconClient.LOGGER.error("Failed to load the config", e);
		}
		BeaconConfig cfg = new BeaconConfig();
		cfg.save();
		return cfg;
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			Files.writeString(PATH, GSON.toJson(this));
		} catch (IOException e) {
			PlayerBeaconClient.LOGGER.error("Failed to save the config", e);
		}
	}
}
