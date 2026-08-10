package thomjap.playertracker.input;

import thomjap.playertracker.PlayerTrackerClient;
import thomjap.playertracker.config.TrackerConfig;
import thomjap.playertracker.crate.CrateWatcher;
import thomjap.playertracker.model.Waypoint;
import thomjap.playertracker.screen.HudEditScreen;
import thomjap.playertracker.util.Dimensions;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Key bindings: toggle the HUD display and position sharing.
 */
public class TrackerKeybinds {
	// Since MC 1.21.9, key categories are identified by an Identifier.
	private static final KeyBinding.Category CATEGORY =
			KeyBinding.Category.create(Identifier.of(PlayerTrackerClient.MOD_ID, "main"));

	/** Crate waypoints within this distance (blocks) can be cleared with the keybind. */
	private static final double CRATE_CLEAR_RANGE = 8.0;

	private static KeyBinding toggleHud;
	private static KeyBinding toggleTracking;
	private static KeyBinding toggleSharing;
	private static KeyBinding openEditor;
	private static KeyBinding removeCrate;

	public static void register() {
		toggleHud = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.playertracker.toggle_hud",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_H,
				CATEGORY));

		openEditor = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.playertracker.open_editor",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_M,
				CATEGORY));

		toggleSharing = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.playertracker.toggle_sharing",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_N,
				CATEGORY));

		// Unbound by default: to be set in the controls options.
		toggleTracking = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.playertracker.toggle_tracking",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN,
				CATEGORY));

		// Unbound by default: clears crate waypoints within 8 blocks.
		removeCrate = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.playertracker.remove_crate",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN,
				CATEGORY));
	}

	public static void handle(MinecraftClient mc) {
		TrackerConfig cfg = PlayerTrackerClient.config;

		while (openEditor.wasPressed()) {
			mc.setScreen(new HudEditScreen());
		}
		while (toggleSharing.wasPressed()) {
			cfg.sharePosition = !cfg.sharePosition;
			cfg.save();
			if (!cfg.sharePosition) {
				// Remove ourselves from others' radar right away.
				PlayerTrackerClient.relay.sendSubscribe();
			}
			actionBar(mc, Text.translatable(cfg.sharePosition
					? "playertracker.msg.sharing_on"
					: "playertracker.msg.stealth"));
		}
		while (toggleHud.wasPressed()) {
			cfg.hudEnabled = !cfg.hudEnabled;
			cfg.save();
			actionBar(mc, Text.translatable(cfg.hudEnabled
					? "playertracker.msg.hud_on"
					: "playertracker.msg.hud_off"));
		}
		while (toggleTracking.wasPressed()) {
			cfg.enabled = !cfg.enabled;
			cfg.save();
			if (cfg.enabled) {
				PlayerTrackerClient.relay.start();
			} else {
				PlayerTrackerClient.relay.stop();
			}
			actionBar(mc, Text.translatable(cfg.enabled
					? "playertracker.msg.tracking_on"
					: "playertracker.msg.tracking_off"));
		}
		while (removeCrate.wasPressed()) {
			removeNearbyCrates(mc);
		}
	}

	/** Removes crate waypoints within {@link #CRATE_CLEAR_RANGE} blocks (3D) of the player. */
	private static void removeNearbyCrates(MinecraftClient mc) {
		if (mc.player == null || mc.world == null) {
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
			actionBar(mc, Text.translatable("playertracker.crate.none_nearby"));
			return;
		}
		cfg.waypoints.removeAll(toRemove);
		cfg.save();
		PlayerTrackerClient.syncWaypoints();
		actionBar(mc, Text.translatable("playertracker.crate.removed", toRemove.size()));
	}

	private static void actionBar(MinecraftClient mc, Text text) {
		if (mc.player != null) {
			mc.player.sendMessage(Text.literal("[Tracker] ").append(text), true);
		}
	}
}
