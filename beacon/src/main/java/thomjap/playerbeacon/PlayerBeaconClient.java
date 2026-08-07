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
 * Point d'entrée de Player Beacon : variante ÉMETTEUR SEUL de Player Tracker.
 * Diffuse périodiquement la position du joueur au relais, mais n'affiche ni ne
 * reçoit la position des autres.
 */
public class PlayerBeaconClient implements ClientModInitializer {
	public static final String MOD_ID = "playerbeacon";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static BeaconConfig config;
	public static BeaconRelay relay;

	private int tickCounter = 0;

	/** À appeler après toute modification des waypoints pour les repartager. */
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

		LOGGER.info("Player Beacon (émetteur seul) initialisé. Relais : {}", config.serverUrl);
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
