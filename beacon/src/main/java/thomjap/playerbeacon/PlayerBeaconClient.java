package thomjap.playerbeacon;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import thomjap.playerbeacon.command.BeaconCommands;
import thomjap.playerbeacon.config.BeaconConfig;
import thomjap.playerbeacon.input.BeaconKeybind;
import thomjap.playerbeacon.net.BeaconRelay;

/**
 * Player Beacon entry point: EMITTER-ONLY variant of Player Tracker.
 * Periodically broadcasts the player position to the relay, but neither shows nor
 * receives other players' positions.
 */
public class PlayerBeaconClient implements ClientModInitializer {
	public static final String MOD_ID = "playerbeacon";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static BeaconConfig config;
	public static BeaconRelay relay;

	private int tickCounter = 0;

	/** Call after any waypoint change to re-share them. */
	public static void syncWaypoints() {
		if (relay != null) {
			relay.sendWaypoints();
		}
	}

	public static String selfName() {
		if (config != null && config.playerName != null && !config.playerName.isBlank()) {
			return config.playerName;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.getSession() != null ? mc.getSession().getUsername() : "Player";
	}

	@Override
	public void onInitializeClient() {
		config = BeaconConfig.load();
		relay = new BeaconRelay(config);
		if (config.enabled) {
			relay.start();
		}

		BeaconKeybind.register();
		BeaconCommands.register();
		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

		LOGGER.info("Player Beacon (emitter-only) initialized. Relay: {}", config.serverUrl);
	}

	private void onClientTick(MinecraftClient mc) {
		BeaconKeybind.handle(mc);

		if (!config.enabled || mc.player == null || mc.world == null) {
			return;
		}
		tickCounter++;
		if (tickCounter < Math.max(1, config.sendIntervalTicks)) {
			return;
		}
		tickCounter = 0;

		String dim = mc.world.getRegistryKey().getValue().toString();
		relay.sendPosition(selfName(),
				mc.player.getX(), mc.player.getY(), mc.player.getZ(), dim);
	}
}
