package thomjap.playertracker.screen;

import thomjap.playertracker.PlayerTrackerClient;
import thomjap.playertracker.config.TrackerConfig;
import thomjap.playertracker.net.RelayClient;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Écran de configuration en jeu : régler l'URL du serveur relais, le salon et
 * le pseudo, puis se reconnecter — sans éditer le fichier JSON à la main.
 */
public class TrackerConfigScreen extends BaseTrackerScreen {
	private TextFieldWidget urlField;
	private TextFieldWidget roomField;
	private TextFieldWidget nameField;

	private int fx;
	private int urlY;
	private int roomY;
	private int nameY;
	private String status = "";

	public TrackerConfigScreen() {
		super(Text.translatable("playertracker.config.title"));
	}

	@Override
	protected void init() {
		TrackerConfig cfg = PlayerTrackerClient.config;
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
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("playertracker.config.save"), b -> save())
				.dimensions(fx, y, fw, 20).build());

		y += 24;
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("playertracker.config.move_hud"),
						b -> this.client.setScreen(new HudEditScreen()))
				.dimensions(fx, y, fw / 2 - 2, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("playertracker.config.close"), b -> this.close())
				.dimensions(fx + fw / 2 + 2, y, fw / 2 - 2, 20).build());

		this.setInitialFocus(urlField);
	}

	private void save() {
		TrackerConfig cfg = PlayerTrackerClient.config;
		cfg.serverUrl = urlField.getText().trim();
		cfg.room = roomField.getText().trim();
		cfg.playerName = nameField.getText().trim();
		cfg.save();

		RelayClient relay = PlayerTrackerClient.relay;
		relay.stop();
		if (cfg.enabled) {
			relay.start();
		}
		status = Text.translatable("playertracker.config.saved", cfg.serverUrl).getString();
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		dimBackground(ctx, 0xC8000000);
		ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 85, 0xFFFFFFFF);

		ctx.drawTextWithShadow(this.textRenderer,
				Text.translatable("playertracker.config.url_label"), fx, urlY - 10, 0xFFAAAAAA);
		ctx.drawTextWithShadow(this.textRenderer,
				Text.translatable("playertracker.config.room_label"), fx, roomY - 10, 0xFFAAAAAA);
		ctx.drawTextWithShadow(this.textRenderer,
				Text.translatable("playertracker.config.name_label"), fx, nameY - 10, 0xFFAAAAAA);

		super.render(ctx, mouseX, mouseY, delta);

		if (!status.isEmpty()) {
			ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(status),
					this.width / 2, this.height / 2 + 78, 0xFF55FF55);
		}
	}
}
