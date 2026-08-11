package thomjap.playertracker.input;

import thomjap.playertracker.PlayerTrackerClient;
import thomjap.playertracker.config.TrackerConfig;
import thomjap.playertracker.crate.CrateWatcher;
import thomjap.playertracker.model.Waypoint;
import thomjap.playertracker.screen.HudEditScreen;
import thomjap.playertracker.util.Dimensions;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Key bindings: toggle the HUD display and position sharing.
 */
public class TrackerKeybinds {
	// Since MC 1.21.9, key categories are identified by an Identifier.
	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(PlayerTrackerClient.MOD_ID, "main"));

	/** Crate waypoints within this distance (blocks) can be cleared with the keybind. */
	private static final double CRATE_CLEAR_RANGE = 8.0;

	private static KeyMapping toggleHud;
	private static KeyMapping toggleTracking;
	private static KeyMapping toggleSharing;
	private static KeyMapping openEditor;

	/** Edge-detection state for the custom (hidden) crate-clear key. */
	private static boolean crateKeyWasDown = false;

	public static void register() {
		toggleHud = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.playertracker.toggle_hud",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_H,
				CATEGORY));

		openEditor = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.playertracker.open_editor",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_M,
				CATEGORY));

		toggleSharing = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.playertracker.toggle_sharing",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_N,
				CATEGORY));

		// Unbound by default: to be set in the controls options.
		toggleTracking = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.playertracker.toggle_tracking",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN,
				CATEGORY));
		// Note: the crate-clear key is intentionally NOT a vanilla KeyMapping.
		// It is a custom key polled below, configurable only from the hidden menu.
	}

	public static void handle(Minecraft mc) {
		TrackerConfig cfg = PlayerTrackerClient.config;

		while (openEditor.consumeClick()) {
			mc.setScreen(new HudEditScreen());
		}
		while (toggleSharing.consumeClick()) {
			cfg.sharePosition = !cfg.sharePosition;
			cfg.save();
			if (!cfg.sharePosition) {
				// Remove ourselves from others' radar right away.
				PlayerTrackerClient.relay.sendSubscribe();
			}
			actionBar(mc, Component.translatable(cfg.sharePosition
					? "playertracker.msg.sharing_on"
					: "playertracker.msg.stealth"));
		}
		while (toggleHud.consumeClick()) {
			cfg.hudEnabled = !cfg.hudEnabled;
			cfg.save();
			actionBar(mc, Component.translatable(cfg.hudEnabled
					? "playertracker.msg.hud_on"
					: "playertracker.msg.hud_off"));
		}
		while (toggleTracking.consumeClick()) {
			cfg.enabled = !cfg.enabled;
			cfg.save();
			if (cfg.enabled) {
				PlayerTrackerClient.relay.start();
			} else {
				PlayerTrackerClient.relay.stop();
			}
			actionBar(mc, Component.translatable(cfg.enabled
					? "playertracker.msg.tracking_on"
					: "playertracker.msg.tracking_off"));
		}
		// Custom (hidden) crate-clear key: raw polling with edge detection.
		// Only when no screen is open, so it never fires while typing/rebinding.
		int key = cfg.crateClearKey;
		boolean down = key >= 0 && mc.screen == null
				&& InputConstants.isKeyDown(mc.getWindow(), key);
		if (down && !crateKeyWasDown) {
			removeNearbyCrates(mc);
		}
		crateKeyWasDown = down;
	}

	/** Removes crate waypoints within {@link #CRATE_CLEAR_RANGE} blocks (3D) of the player. */
	private static void removeNearbyCrates(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			return;
		}
		TrackerConfig cfg = PlayerTrackerClient.config;
		double px = mc.player.getX();
		double py = mc.player.getY();
		double pz = mc.player.getZ();
		String dim = Dimensions.current();
		double maxSq = CRATE_CLEAR_RANGE * CRATE_CLEAR_RANGE;

		List<Waypoint> toRemove = new ArrayList<>();
		for (Waypoint w : cfg.waypoints) {
			if (!CrateWatcher.TAG.equals(w.tag) || !w.dim.equals(dim)) {
				continue;
			}
			double dx = w.x - px;
			double dy = w.y - py;
			double dz = w.z - pz;
			if (dx * dx + dy * dy + dz * dz <= maxSq) {
				toRemove.add(w);
			}
		}

		if (toRemove.isEmpty()) {
			actionBar(mc, Component.translatable("playertracker.crate.none_nearby"));
			return;
		}
		cfg.waypoints.removeAll(toRemove);
		cfg.save();
		PlayerTrackerClient.syncWaypoints();
		actionBar(mc, Component.translatable("playertracker.crate.removed", toRemove.size()));
	}

	private static void actionBar(Minecraft mc, Component text) {
		if (mc.player != null) {
			mc.player.sendOverlayMessage(Component.literal("[Tracker] ").append(text));
		}
	}
}
