package thomjap.playerbeacon.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import thomjap.playerbeacon.PlayerBeaconClient;
import thomjap.playerbeacon.config.BeaconConfig;

/** Beacon config screen (M key): URL, room, username + connection. */
public class BeaconConfigScreen extends Screen {
	private EditBox urlField;
	private EditBox roomField;
	private EditBox nameField;

	private int fx;
	private int urlY;
	private int roomY;
	private int nameY;
	private String status = "";

	public BeaconConfigScreen() {
		super(Component.translatable("playerbeacon.config.title"));
	}

	@Override
	protected void init() {
		BeaconConfig cfg = PlayerBeaconClient.config;
		int cx = this.width / 2;
		int fw = Math.min(320, this.width - 40);
		fx = cx - fw / 2;
		int y = this.height / 2 - 55;

		urlY = y;
		urlField = new EditBox(this.font, fx, y, fw, 20, Component.literal("URL"));
		urlField.setMaxLength(256);
		urlField.setValue(cfg.serverUrl);
		this.addRenderableWidget(urlField);

		y += 42;
		roomY = y;
		roomField = new EditBox(this.font, fx, y, fw, 20, Component.literal("Room"));
		roomField.setMaxLength(64);
		roomField.setValue(cfg.room);
		this.addRenderableWidget(roomField);

		y += 42;
		nameY = y;
		nameField = new EditBox(this.font, fx, y, fw, 20, Component.literal("Username"));
		nameField.setMaxLength(32);
		nameField.setValue(cfg.playerName);
		this.addRenderableWidget(nameField);

		y += 40;
		this.addRenderableWidget(Button.builder(Component.translatable("playerbeacon.config.save"), b -> save())
				.bounds(fx, y, fw, 20).build());

		y += 24;
		this.addRenderableWidget(Button.builder(enabledLabel(), b -> {
			cfg.enabled = !cfg.enabled;
			cfg.save();
			if (cfg.enabled) {
				PlayerBeaconClient.relay.start();
			} else {
				PlayerBeaconClient.relay.stop();
			}
			this.rebuildWidgets();
		}).bounds(fx, y, fw / 2 - 2, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("playerbeacon.config.close"), b -> this.onClose())
				.bounds(fx + fw / 2 + 2, y, fw / 2 - 2, 20).build());

		this.setInitialFocus(urlField);
	}

	private static Component enabledLabel() {
		return Component.translatable(PlayerBeaconClient.config.enabled
				? "playerbeacon.config.on" : "playerbeacon.config.off");
	}

	private void save() {
		BeaconConfig cfg = PlayerBeaconClient.config;
		cfg.serverUrl = urlField.getValue().trim();
		cfg.room = roomField.getValue().trim();
		cfg.playerName = nameField.getValue().trim();
		cfg.save();
		PlayerBeaconClient.relay.stop();
		if (cfg.enabled) {
			PlayerBeaconClient.relay.start();
		}
		status = Component.translatable("playerbeacon.config.saved", cfg.serverUrl).getString();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		// Simple dim (not renderBackground: its blur crashes on a non-pausing screen).
		g.fill(0, 0, this.width, this.height, 0xC8000000);
		g.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 85, 0xFFFFFFFF);

		g.text(this.font,
				Component.translatable("playerbeacon.config.url_label"), fx, urlY - 10, 0xFFAAAAAA, true);
		g.text(this.font,
				Component.translatable("playerbeacon.config.room_label"), fx, roomY - 10, 0xFFAAAAAA, true);
		g.text(this.font,
				Component.translatable("playerbeacon.config.name_label"), fx, nameY - 10, 0xFFAAAAAA, true);

		super.extractRenderState(g, mouseX, mouseY, delta);

		if (!status.isEmpty()) {
			g.centeredText(this.font, Component.literal(status),
					this.width / 2, this.height / 2 + 78, 0xFF55FF55);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
