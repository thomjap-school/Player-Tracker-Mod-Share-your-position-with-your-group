package thomjap.playertracker;

import thomjap.playertracker.command.TrackerCommands;
import thomjap.playertracker.config.TrackerConfig;
import thomjap.playertracker.crate.CrateWatcher;
import thomjap.playertracker.hud.TrackerHud;
import thomjap.playertracker.hud.WaypointBeamRenderer;
import thomjap.playertracker.input.TrackerKeybinds;
import thomjap.playertracker.model.Waypoint;
import thomjap.playertracker.net.RelayClient;
import thomjap.playertracker.util.Dimensions;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Point d'entrée client du mod Player Tracker.
 *
 * <p>Envoie périodiquement la position du joueur local au serveur relais et
 * enregistre l'élément de HUD qui affiche la direction des autres joueurs.
 */
public class PlayerTrackerClient implements ClientModInitializer {
	public static final String MOD_ID = "playertracker";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static TrackerConfig config;
	public static RelayClient relay;

	private int tickCounter = 0;
	private boolean wasDead = false;

	/** Pseudo utilisé pour identifier ce joueur auprès du relais. */
	public static String selfName() {
		if (config != null && config.playerName != null && !config.playerName.isBlank()) {
			return config.playerName;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.getSession() != null) {
			return mc.getSession().getUsername();
		}
		return "Player";
	}

	/** À appeler après toute modification des waypoints pour les repartager (carte web). */
	public static void syncWaypoints() {
		if (relay != null) {
			relay.sendWaypoints();
		}
	}

	@Override
	public void onInitializeClient() {
		config = TrackerConfig.load();
		relay = new RelayClient(config);
		if (config.enabled) {
			relay.start();
		}

		TrackerKeybinds.register();
		TrackerCommands.register();
		CrateWatcher.register();
		WaypointBeamRenderer.register();
		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

		HudElementRegistry.addLast(
				Identifier.of(MOD_ID, "tracker"),
				new TrackerHud());

		LOGGER.info("Player Tracker initialisé. Relais : {}", config.serverUrl);
	}

	private void onClientTick(MinecraftClient mc) {
		TrackerKeybinds.handle(mc);

		// Marqueur de mort : au moment où le joueur meurt, on pose un waypoint.
		if (mc.player != null && mc.world != null) {
			boolean dead = mc.player.isDead();
			if (dead && !wasDead) {
				createDeathWaypoint(mc);
			}
			wasDead = dead;
		}

		if (!config.enabled || mc.player == null || mc.world == null) {
			return;
		}

		tickCounter++;
		if (tickCounter < Math.max(1, config.sendIntervalTicks)) {
			return;
		}
		tickCounter = 0;

		// Mode furtif : on reste connecté (donc on voit les autres) mais on
		// n'envoie pas sa position, donc les autres ne nous voient plus.
		if (!config.sharePosition) {
			return;
		}

		relay.sendPosition(
				selfName(),
				mc.player.getX(),
				mc.player.getY(),
				mc.player.getZ(),
				Dimensions.current());
	}

	private void createDeathWaypoint(MinecraftClient mc) {
		String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
		String name = selfName() + " mort à " + time;
		config.waypoints.add(new Waypoint(
				name, mc.player.getX(), mc.player.getY(), mc.player.getZ(), Dimensions.current(), 0xFFFF5555));
		config.save();
		syncWaypoints();
		mc.player.sendMessage(Text.translatable("playertracker.death.saved"), false);
	}
}
