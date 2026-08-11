package thomjap.playertracker.screen;

import thomjap.playertracker.PlayerTrackerClient;
import thomjap.playertracker.config.TrackerConfig;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * HUD edit screen (M key): drag to move the compass, the
 * list and the connection indicator; wheel to resize them;
 * buttons to show/hide them. Saved in the config.
 */
public class HudEditScreen extends BaseTrackerScreen {
	private static final int COMPASS_H = 12;
	private static final int LIST_W = 132;
	private static final int LIST_H = 46;
	private static final int STATUS_W = 108;
	private static final int STATUS_H = 10;

	private static final int NONE = 0;
	private static final int COMPASS = 1;
	private static final int LIST = 2;
	private static final int STATUS = 3;

	private int dragging = NONE;
	private double grabOffX;
	private double grabOffY;

	// Hidden easter egg: the Konami code opens the crate settings screen.
	private static final int[] KONAMI = {
			GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_DOWN,
			GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT,
			GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_A
	};
	private int konami = 0;

	public HudEditScreen() {
		super(Text.translatable("playertracker.editor.title"));
	}

	@Override
	protected void init() {
		int leftX = this.width / 2 - 152;
		int rightX = this.width / 2 + 2;
		int row1 = this.height - 100;
		int row2 = this.height - 78;

		this.addDrawableChild(ButtonWidget.builder(compassLabel(), b -> {
			TrackerConfig cfg = PlayerTrackerClient.config;
			cfg.showCompass = !cfg.showCompass;
			cfg.save();
			b.setMessage(compassLabel());
		}).dimensions(leftX, row1, 150, 20).build());

		this.addDrawableChild(ButtonWidget.builder(listLabel(), b -> {
			TrackerConfig cfg = PlayerTrackerClient.config;
			cfg.showList = !cfg.showList;
			cfg.save();
			b.setMessage(listLabel());
		}).dimensions(rightX, row1, 150, 20).build());

		this.addDrawableChild(ButtonWidget.builder(statusLabel(), b -> {
			TrackerConfig cfg = PlayerTrackerClient.config;
			cfg.showStatus = !cfg.showStatus;
			cfg.save();
			b.setMessage(statusLabel());
		}).dimensions(leftX, row2, 150, 20).build());

		this.addDrawableChild(ButtonWidget.builder(coordsLabel(), b -> {
			TrackerConfig cfg = PlayerTrackerClient.config;
			cfg.showCoords = !cfg.showCoords;
			cfg.save();
			b.setMessage(coordsLabel());
		}).dimensions(rightX, row2, 150, 20).build());

		this.addDrawableChild(ButtonWidget.builder(Text.translatable("playertracker.editor.server_button"),
						b -> this.client.setScreen(new TrackerConfigScreen()))
				.dimensions(this.width / 2 - 152, this.height - 52, 150, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("playertracker.waypoints.title"),
						b -> this.client.setScreen(new WaypointsScreen()))
				.dimensions(this.width / 2 + 2, this.height - 52, 150, 20).build());
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		int keyCode = input.key();
		if (keyCode == KONAMI[konami]) {
			konami++;
			if (konami >= KONAMI.length) {
				konami = 0;
				this.client.setScreen(new CrateSettingsScreen());
			}
			return true;
		}
		konami = (keyCode == KONAMI[0]) ? 1 : 0;
		return super.keyPressed(input);
	}

	private static Text toggleLabel(String key, boolean on) {
		return Text.translatable(key, on ? "ON" : "OFF");
	}

	private static Text compassLabel() {
		return toggleLabel("playertracker.toggle.compass", PlayerTrackerClient.config.showCompass);
	}

	private static Text listLabel() {
		return toggleLabel("playertracker.toggle.list", PlayerTrackerClient.config.showList);
	}

	private static Text statusLabel() {
		return toggleLabel("playertracker.toggle.status", PlayerTrackerClient.config.showStatus);
	}

	private static Text coordsLabel() {
		return toggleLabel("playertracker.toggle.coords", PlayerTrackerClient.config.showCoords);
	}

	private int compassW() {
		return Math.min(240, this.width - 20);
	}

	private int compassX() {
		TrackerConfig cfg = PlayerTrackerClient.config;
		int x = cfg.compassX >= 0 ? cfg.compassX : (this.width - compassW()) / 2;
		return Math.max(0, Math.min(x, this.width - compassW()));
	}

	// Preview box dimensions.
	private int compassBoxW() {
		return compassW();
	}

	private int compassBoxH() {
		return COMPASS_H;
	}

	private int listBoxW() {
		return LIST_W;
	}

	private int listBoxH() {
		return LIST_H;
	}

	private int statusBoxW() {
		return STATUS_W;
	}

	private int statusBoxH() {
		return STATUS_H;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		dimBackground(context, 0xC0000000);
		TrackerConfig cfg = PlayerTrackerClient.config;

		// Compass (preview, to scale)
		int cx = compassX();
		int cy = cfg.compassY;
		int cw = compassBoxW();
		int ch = compassBoxH();
		context.fill(cx, cy, cx + cw, cy + ch, 0x99000000);
		outline(context, cx, cy, cw, ch, hover(mouseX, mouseY, cx, cy, cw, ch) ? 0xFFFFEE55 : 0xFFFFFFFF);
		context.fill(cx + cw / 2, cy - 2, cx + cw / 2 + 1, cy + ch + 2, 0xFFFFFFFF);
		context.drawTextWithShadow(this.textRenderer, Text.translatable(
				cfg.showCompass ? "playertracker.editor.compass" : "playertracker.editor.compass_hidden"),
				cx + 4, cy + 2, 0xFFFFFF55);

		// List (preview, to scale)
		int lx = cfg.listX;
		int ly = cfg.listY;
		int lw = listBoxW();
		int lh = listBoxH();
		context.fill(lx, ly, lx + lw, ly + lh, 0x99000000);
		outline(context, lx, ly, lw, lh, hover(mouseX, mouseY, lx, ly, lw, lh) ? 0xFFFFEE55 : 0xFFFFFFFF);
		context.drawTextWithShadow(this.textRenderer, Text.translatable(
				cfg.showList ? "playertracker.hud.list_header" : "playertracker.editor.list_hidden"),
				lx + 4, ly + 4, 0xFFFFFF55);

		// Connection indicator (preview, to scale)
		int stx = cfg.statusX;
		int sty = cfg.statusY;
		int stw = statusBoxW();
		int sth = statusBoxH();
		context.fill(stx, sty, stx + stw, sty + sth, 0x99000000);
		outline(context, stx, sty, stw, sth, hover(mouseX, mouseY, stx, sty, stw, sth) ? 0xFFFFEE55 : 0xFFFFFFFF);
		context.fill(stx + 2, sty + 2, stx + 8, sty + 8, 0xFF3FD44B);
		context.drawTextWithShadow(this.textRenderer, Text.translatable(
				cfg.showStatus ? "playertracker.editor.status" : "playertracker.editor.status_hidden"),
				stx + 11, sty + 1, 0xFFCFCFCF);

		// Help
		context.drawCenteredTextWithShadow(this.textRenderer,
				Text.translatable("playertracker.editor.help"),
				this.width / 2, this.height - 16, 0xFFFFFFFF);

		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		double mx = click.x();
		double my = click.y();
		TrackerConfig cfg = PlayerTrackerClient.config;

		int cx = compassX();
		if (hover(mx, my, cx, cfg.compassY, compassBoxW(), compassBoxH())) {
			dragging = COMPASS;
			grabOffX = mx - cx;
			grabOffY = my - cfg.compassY;
			return true;
		}
		if (hover(mx, my, cfg.listX, cfg.listY, listBoxW(), listBoxH())) {
			dragging = LIST;
			grabOffX = mx - cfg.listX;
			grabOffY = my - cfg.listY;
			return true;
		}
		if (hover(mx, my, cfg.statusX, cfg.statusY, statusBoxW(), statusBoxH())) {
			dragging = STATUS;
			grabOffX = mx - cfg.statusX;
			grabOffY = my - cfg.statusY;
			return true;
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		TrackerConfig cfg = PlayerTrackerClient.config;
		double mx = click.x();
		double my = click.y();
		if (dragging == COMPASS) {
			cfg.compassX = clampX((int) (mx - grabOffX), compassBoxW());
			cfg.compassY = clampY((int) (my - grabOffY), compassBoxH());
			return true;
		}
		if (dragging == LIST) {
			cfg.listX = clampX((int) (mx - grabOffX), listBoxW());
			cfg.listY = clampY((int) (my - grabOffY), listBoxH());
			return true;
		}
		if (dragging == STATUS) {
			cfg.statusX = clampX((int) (mx - grabOffX), statusBoxW());
			cfg.statusY = clampY((int) (my - grabOffY), statusBoxH());
			return true;
		}
		return super.mouseDragged(click, offsetX, offsetY);
	}

	@Override
	public boolean mouseReleased(Click click) {
		dragging = NONE;
		return super.mouseReleased(click);
	}

	@Override
	public void close() {
		PlayerTrackerClient.config.save();
		super.close();
	}

	private int clampX(int x, int w) {
		return Math.max(0, Math.min(x, this.width - w));
	}

	private int clampY(int y, int h) {
		return Math.max(0, Math.min(y, this.height - h));
	}

	private static boolean hover(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx <= x + w && my >= y && my <= y + h;
	}

	/** Rectangle outline (drawBorder does not exist in this version). */
	private static void outline(DrawContext ctx, int x, int y, int w, int h, int color) {
		ctx.fill(x, y, x + w, y + 1, color);
		ctx.fill(x, y + h - 1, x + w, y + h, color);
		ctx.fill(x, y, x + 1, y + h, color);
		ctx.fill(x + w - 1, y, x + w, y + h, color);
	}
}
