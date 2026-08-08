package thomjap.playertracker.input;

import thomjap.playertracker.PlayerTrackerClient;
import thomjap.playertracker.config.TrackerConfig;
import thomjap.playertracker.screen.HudEditScreen;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/**
 * Key bindings: toggle the HUD display and position sharing.
 */
public class TrackerKeybinds {
	// Since MC 1.21.9, key categories are identified by an Identifier.
	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(PlayerTrackerClient.MOD_ID, "main"));

	private static KeyMapping toggleHud;
	private static KeyMapping toggleTracking;
	private static KeyMapping toggleSharing;
	private static KeyMapping openEditor;

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
	}

	public static void handle(Minecraft mc) {
		TrackerConfig cfg = PlayerTrackerClient.config;

		while (openEditor.consumeClick()) {
			mc.setScreenAndShow(new HudEditScreen());
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
	}

	private static void actionBar(Minecraft mc, Component text) {
		if (mc.player != null) {
			mc.player.sendOverlayMessage(Component.literal("[Tracker] ").append(text));
		}
	}
}
