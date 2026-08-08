package thomjap.playertracker.screen;

import thomjap.playertracker.PlayerTrackerClient;
import thomjap.playertracker.config.TrackerConfig;
import thomjap.playertracker.net.RelayClient;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * In-game configuration screen: set the relay server URL, the room and the
 * username, then reconnect — without editing the JSON file by hand.
 */
public class TrackerConfigScreen extends BaseTrackerScreen {
	private EditBox urlField;
	private EditBox roomField;
	private EditBox nameField;

	private int fx;
	private int urlY;
	private int roomY;
	private int nameY;
	private String status = "";

	public TrackerConfigScreen() {
		super(Component.translatable("playertracker.config.title"));
	}

	@Override
	protected void init() {
		TrackerConfig cfg = PlayerTrackerClient.config;
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
		this.addRenderableWidget(Button.builder(Component.translatable("playertracker.config.save"), b -> save())
				.bounds(fx, y, fw, 20).build());

		y += 24;
		this.addRenderableWidget(Button.builder(Component.translatable("playertracker.config.move_hud"),
						b -> this.minecraft.setScreenAndShow(new HudEditScreen()))
				.bounds(fx, y, fw / 2 - 2, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("playertracker.config.close"), b -> this.onClose())
				.bounds(fx + fw / 2 + 2, y, fw / 2 - 2, 20).build());

		this.setInitialFocus(urlField);
	}

	private void save() {
		TrackerConfig cfg = PlayerTrackerClient.config;
		cfg.serverUrl = urlField.getValue().trim();
		cfg.room = roomField.getValue().trim();
		cfg.playerName = nameField.getValue().trim();
		cfg.save();

		RelayClient relay = PlayerTrackerClient.relay;
		relay.stop();
		if (cfg.enabled) {
			relay.start();
		}
		status = Component.translatable("playertracker.config.saved", cfg.serverUrl).getString();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		dimBackground(g, 0xC8000000);
		g.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 85, 0xFFFFFFFF);

		g.text(this.font,
				Component.translatable("playertracker.config.url_label"), fx, urlY - 10, 0xFFAAAAAA, true);
		g.text(this.font,
				Component.translatable("playertracker.config.room_label"), fx, roomY - 10, 0xFFAAAAAA, true);
		g.text(this.font,
				Component.translatable("playertracker.config.name_label"), fx, nameY - 10, 0xFFAAAAAA, true);

		super.extractRenderState(g, mouseX, mouseY, delta);

		if (!status.isEmpty()) {
			g.centeredText(this.font, Component.literal(status),
					this.width / 2, this.height / 2 + 78, 0xFF55FF55);
		}
	}
}
