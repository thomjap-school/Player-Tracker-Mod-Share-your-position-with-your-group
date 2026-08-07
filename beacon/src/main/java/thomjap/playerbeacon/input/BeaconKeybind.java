package thomjap.playerbeacon.input;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import thomjap.playerbeacon.PlayerBeaconClient;
import thomjap.playerbeacon.config.BeaconConfig;
import thomjap.playerbeacon.screen.BeaconConfigScreen;

/** Raccourcis : ouvrir la config (M) et couper/activer la diffusion (non lié). */
public final class BeaconKeybind {
	private static final KeyBinding.Category CATEGORY =
			KeyBinding.Category.create(Identifier.of(PlayerBeaconClient.MOD_ID, "main"));

	private static KeyBinding openConfig;
	private static KeyBinding toggle;

	private BeaconKeybind() {
	}

	public static void register() {
		openConfig = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.playerbeacon.open_config",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_M,
				CATEGORY));

		toggle = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.playerbeacon.toggle",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN,
				CATEGORY));
	}

	public static void handle(MinecraftClient mc) {
		while (openConfig.wasPressed()) {
			mc.setScreen(new BeaconConfigScreen());
		}
		while (toggle.wasPressed()) {
			BeaconConfig cfg = PlayerBeaconClient.config;
			cfg.enabled = !cfg.enabled;
			cfg.save();
			if (cfg.enabled) {
				PlayerBeaconClient.relay.start();
			} else {
				PlayerBeaconClient.relay.stop();
			}
			if (mc.player != null) {
				mc.player.sendMessage(Text.translatable(
						cfg.enabled ? "playerbeacon.on" : "playerbeacon.off"), true);
			}
		}
	}
}
