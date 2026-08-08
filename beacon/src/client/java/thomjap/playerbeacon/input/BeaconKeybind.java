package thomjap.playerbeacon.input;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import thomjap.playerbeacon.PlayerBeaconClient;
import thomjap.playerbeacon.config.BeaconConfig;
import thomjap.playerbeacon.screen.BeaconConfigScreen;

/** Key bindings: open the config (M) and toggle broadcasting (unbound). */
public final class BeaconKeybind {
	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(PlayerBeaconClient.MOD_ID, "main"));

	private static KeyMapping openConfig;
	private static KeyMapping toggle;

	private BeaconKeybind() {
	}

	public static void register() {
		openConfig = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.playerbeacon.open_config",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_M,
				CATEGORY));

		toggle = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.playerbeacon.toggle",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN,
				CATEGORY));
	}

	public static void handle(Minecraft mc) {
		while (openConfig.consumeClick()) {
			mc.setScreen(new BeaconConfigScreen());
		}
		while (toggle.consumeClick()) {
			BeaconConfig cfg = PlayerBeaconClient.config;
			cfg.enabled = !cfg.enabled;
			cfg.save();
			if (cfg.enabled) {
				PlayerBeaconClient.relay.start();
			} else {
				PlayerBeaconClient.relay.stop();
			}
			if (mc.player != null) {
				mc.player.sendOverlayMessage(Component.translatable(
						cfg.enabled ? "playerbeacon.on" : "playerbeacon.off"));
			}
		}
	}
}
