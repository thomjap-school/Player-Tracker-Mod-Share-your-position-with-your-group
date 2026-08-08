package thomjap.playerbeacon.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import thomjap.playerbeacon.PlayerBeaconClient;
import thomjap.playerbeacon.config.BeaconConfig;

/** Beacon config screen (M key): URL, room, username + connection. */
public class BeaconConfigScreen extends Screen {
	private TextFieldWidget urlField;
	private TextFieldWidget roomField;
	private TextFieldWidget nameField;

	private int fx;
	private int urlY;
	private int roomY;
	private int nameY;
	private String status = "";

	public BeaconConfigScreen() {
		super(Text.translatable("playerbeacon.config.title"));
	}

	@Override
	protected void init() {
		BeaconConfig cfg = PlayerBeaconClient.config;
		int cx = this.width / 2;
		int fw = Math.min(320, this.width - 40);
		fx = cx - fw / 2;
		int y = this.height / 2 - 55;

		urlY = y;
		urlField = new TextFieldWidget(this.textRenderer, fx, y, fw, 20, Text.literal("URL"));
		urlField.setMaxLength(256);
		urlField.setText(cfg.serverUrl);
		this.addDrawableChild(urlField);

		y += 42;
		roomY = y;
		roomField = new TextFieldWidget(this.textRenderer, fx, y, fw, 20, Text.literal("Salon"));
		roomField.setMaxLength(64);
		roomField.setText(cfg.room);
		this.addDrawableChild(roomField);

		y += 42;
		nameY = y;
		nameField = new TextFieldWidget(this.textRenderer, fx, y, fw, 20, Text.literal("Pseudo"));
		nameField.setMaxLength(32);
		nameField.setText(cfg.playerName);
		this.addDrawableChild(nameField);

		y += 40;
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("playerbeacon.config.save"), b -> save())
				.dimensions(fx, y, fw, 20).build());

		y += 24;
		this.addDrawableChild(ButtonWidget.builder(enabledLabel(), b -> {
			cfg.enabled = !cfg.enabled;
			cfg.save();
			if (cfg.enabled) {
				PlayerBeaconClient.relay.start();
			} else {
				PlayerBeaconClient.relay.stop();
			}
			this.clearAndInit();
		}).dimensions(fx, y, fw / 2 - 2, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("playerbeacon.config.close"), b -> this.close())
				.dimensions(fx + fw / 2 + 2, y, fw / 2 - 2, 20).build());

		this.setInitialFocus(urlField);
	}

	private static Text enabledLabel() {
		return Text.translatable(PlayerBeaconClient.config.enabled
				? "playerbeacon.config.on" : "playerbeacon.config.off");
	}

	private void save() {
		BeaconConfig cfg = PlayerBeaconClient.config;
		cfg.serverUrl = urlField.getText().trim();
		cfg.room = roomField.getText().trim();
		cfg.playerName = nameField.getText().trim();
		cfg.save();
		PlayerBeaconClient.relay.stop();
		if (cfg.enabled) {
			PlayerBeaconClient.relay.start();
		}
		status = Text.translatable("playerbeacon.config.saved", cfg.serverUrl).getString();
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		// Simple dim (not renderBackground: its blur crashes on a non-pausing screen).
		ctx.fill(0, 0, this.width, this.height, 0xC8000000);
		ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 85, 0xFFFFFFFF);

		ctx.drawTextWithShadow(this.textRenderer,
				Text.translatable("playerbeacon.config.url_label"), fx, urlY - 10, 0xFFAAAAAA);
		ctx.drawTextWithShadow(this.textRenderer,
				Text.translatable("playerbeacon.config.room_label"), fx, roomY - 10, 0xFFAAAAAA);
		ctx.drawTextWithShadow(this.textRenderer,
				Text.translatable("playerbeacon.config.name_label"), fx, nameY - 10, 0xFFAAAAAA);

		super.render(ctx, mouseX, mouseY, delta);

		if (!status.isEmpty()) {
			ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(status),
					this.width / 2, this.height / 2 + 78, 0xFF55FF55);
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
