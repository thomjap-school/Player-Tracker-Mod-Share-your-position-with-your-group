package thomjap.playertracker.input;

import thomjap.playertracker.PlayerTrackerClient;
import thomjap.playertracker.config.TrackerConfig;
import thomjap.playertracker.screen.HudEditScreen;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Raccourcis clavier : basculer l'affichage du HUD et le partage de position.
 */
public class TrackerKeybinds {
	// Depuis MC 1.21.9, les catégories de touches sont identifiées par un Identifier.
	private static final KeyBinding.Category CATEGORY =
			KeyBinding.Category.create(Identifier.of(PlayerTrackerClient.MOD_ID, "main"));

	private static KeyBinding toggleHud;
	private static KeyBinding toggleTracking;
	private static KeyBinding toggleSharing;
	private static KeyBinding openEditor;

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

		// Non lié par défaut : à définir dans les options de commandes.
		toggleTracking = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.playertracker.toggle_tracking",
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
				// Se retirer tout de suite du radar des autres.
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
	}

	private static void actionBar(MinecraftClient mc, Text text) {
		if (mc.player != null) {
			mc.player.sendMessage(Text.literal("[Tracker] ").append(text), true);
		}
	}
}
