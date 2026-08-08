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
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Client entry point of the Player Tracker mod.
 *
 * <p>Periodically sends the local player position to the relay server and
 * registers the HUD element that shows the direction of other players.
 */
public class PlayerTrackerClient implements ClientModInitializer {
	public static final String MOD_ID = "playertracker";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static TrackerConfig config;
	public static RelayClient relay;

	private int tickCounter = 0;
	private boolean wasDead = false;

	/** Name used to identify this player to the relay. */
	public static String selfName() {
		if (config != null && config.playerName != null && !config.playerName.isBlank()) {
			return config.playerName;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.getUser() != null) {
			return mc.getUser().getName();
		}
		return "Player";
	}

	/** Call after any waypoint change to re-share them (web map). */
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
				Identifier.fromNamespaceAndPath(MOD_ID, "tracker"),
				new TrackerHud());

		LOGGER.info("Player Tracker initialized. Relay: {}", config.serverUrl);
	}

	private void onClientTick(Minecraft mc) {
		TrackerKeybinds.handle(mc);

		// Death marker: the moment the player dies, drop a waypoint.
		if (mc.player != null && mc.level != null) {
			boolean dead = mc.player.isDeadOrDying();
			if (dead && !wasDead) {
				createDeathWaypoint(mc);
			}
			wasDead = dead;
		}

		if (!config.enabled || mc.player == null || mc.level == null) {
			return;
		}

		tickCounter++;
		if (tickCounter < Math.max(1, config.sendIntervalTicks)) {
			return;
		}
		tickCounter = 0;

		// Stealth mode: we stay connected (so we still see others) but do not
		// send our position, so others no longer see us.
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

	private void createDeathWaypoint(Minecraft mc) {
		String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
		String name = selfName() + " died at " + time;
		config.waypoints.add(new Waypoint(
				name, mc.player.getX(), mc.player.getY(), mc.player.getZ(), Dimensions.current(), 0xFFFF5555));
		config.save();
		syncWaypoints();
		mc.player.sendSystemMessage(Component.translatable("playertracker.death.saved"));
	}
}
