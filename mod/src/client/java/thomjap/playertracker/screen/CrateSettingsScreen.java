package thomjap.playertracker.screen;

import thomjap.playertracker.PlayerTrackerClient;
import thomjap.playertracker.config.TrackerConfig;
import thomjap.playertracker.crate.CrateWatcher;
import thomjap.playertracker.model.Waypoint;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Hidden "crate settings" screen, reachable only via the Konami code in the HUD
 * editor. Holds everything crate-related (kept out of the normal UI as an easter
 * egg): toggle crate detection, bind the crate-clear key, and manage crate
 * waypoints.
 */
public class CrateSettingsScreen extends BaseTrackerScreen {
	private static final int PER_PAGE = 6;
	private static final int ROW_H = 20;
	private static final int TOP = 66;

	private static final int[] CYCLE = {
			0xFFFFDD44, 0xFFFF5555, 0xFF55FF55, 0xFF5599FF, 0xFFFF55FF,
			0xFF55FFFF, 0xFFFFAA33, 0xFFAA66FF, 0xFFFFFFFF
	};

	private int page = 0;
	private boolean listening = false;

	public CrateSettingsScreen() {
		super(Component.translatable("playertracker.crates.title"));
	}

	private int listW() {
		return Math.min(480, this.width - 40);
	}

	private int listX() {
		return this.width / 2 - listW() / 2;
	}

	private static List<Waypoint> crates() {
		List<Waypoint> out = new ArrayList<>();
		for (Waypoint w : PlayerTrackerClient.config.waypoints) {
			if (CrateWatcher.TAG.equals(w.tag)) {
				out.add(w);
			}
		}
		return out;
	}

	private Component keyName() {
		int k = PlayerTrackerClient.config.crateClearKey;
		return k < 0
				? Component.translatable("playertracker.crates.none")
				: InputConstants.Type.KEYSYM.getOrCreate(k).getDisplayName();
	}

	private Component detectionLabel() {
		return Component.translatable("playertracker.crates.detection",
				PlayerTrackerClient.config.crateWaypoints ? "ON" : "OFF");
	}

	private Component keyLabel() {
		return listening
				? Component.translatable("playertracker.crates.press")
				: Component.translatable("playertracker.crates.key", keyName());
	}

	@Override
	protected void init() {
		TrackerConfig cfg = PlayerTrackerClient.config;
		int lx = listX();
		int lw = listW();

		// --- Header controls: detection toggle + crate-clear key rebind ---
		this.addRenderableWidget(Button.builder(detectionLabel(), b -> {
			cfg.crateWaypoints = !cfg.crateWaypoints;
			cfg.save();
			b.setMessage(detectionLabel());
		}).bounds(lx, 30, 150, 20).build());

		this.addRenderableWidget(Button.builder(keyLabel(), b -> {
			listening = true;
			b.setMessage(keyLabel());
		}).bounds(lx + 158, 30, lw - 158, 20).build());

		// --- Crate waypoint list ---
		List<Waypoint> list = crates();
		int maxPage = Math.max(0, (list.size() - 1) / PER_PAGE);
		page = Math.max(0, Math.min(page, maxPage));
		int start = page * PER_PAGE;

		for (int i = 0; i < PER_PAGE && start + i < list.size(); i++) {
			final Waypoint w = list.get(start + i);
			int ry = TOP + i * ROW_H;
			this.addRenderableWidget(Button.builder(hideLabel(w), b -> {
				w.hidden = !w.hidden;
				cfg.save();
				this.rebuildWidgets();
			}).bounds(lx + lw - 170, ry, 58, 18).build());
			this.addRenderableWidget(Button.builder(Component.translatable("playertracker.waypoints.color"), b -> {
				w.color = nextColor(w.color);
				cfg.save();
			}).bounds(lx + lw - 108, ry, 52, 18).build());
			this.addRenderableWidget(Button.builder(Component.translatable("playertracker.waypoints.delete"), b -> {
				cfg.waypoints.remove(w);
				cfg.save();
				this.rebuildWidgets();
			}).bounds(lx + lw - 52, ry, 52, 18).build());
		}

		// --- Footer: pagination + clear-all + close ---
		int py = this.height - 30;
		this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
			page--;
			this.rebuildWidgets();
		}).bounds(lx, py, 40, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
			page++;
			this.rebuildWidgets();
		}).bounds(lx + 44, py, 40, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("playertracker.crates.clear_all"), b -> {
			cfg.waypoints.removeAll(crates());
			cfg.save();
			this.rebuildWidgets();
		}).bounds(lx + lw - 174, py, 90, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("playertracker.config.close"), b -> this.onClose())
				.bounds(lx + lw - 80, py, 80, 20).build());
	}

	private static Component hideLabel(Waypoint w) {
		return Component.translatable(w.hidden
				? "playertracker.waypoints.hidden" : "playertracker.waypoints.visible");
	}

	private static int nextColor(int current) {
		for (int i = 0; i < CYCLE.length; i++) {
			if (CYCLE[i] == current) {
				return CYCLE[(i + 1) % CYCLE.length];
			}
		}
		return CYCLE[0];
	}

	@Override
	public boolean keyPressed(KeyEvent input) {
		if (listening) {
			int keyCode = input.key();
			TrackerConfig cfg = PlayerTrackerClient.config;
			if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
				listening = false; // cancel, keep current binding
			} else {
				cfg.crateClearKey = (keyCode == GLFW.GLFW_KEY_DELETE
						|| keyCode == GLFW.GLFW_KEY_BACKSPACE) ? -1 : keyCode;
				cfg.save();
				listening = false;
			}
			this.rebuildWidgets();
			return true;
		}
		return super.keyPressed(input);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		dimBackground(g, 0xC8000000);
		g.centeredText(this.font, this.title, this.width / 2, 12, 0xFFFFFF55);

		List<Waypoint> list = crates();
		int lx = listX();

		if (list.isEmpty()) {
			g.centeredText(this.font,
					Component.translatable("playertracker.crates.empty"), this.width / 2, TOP + 16, 0xFF9AA4B2);
		}

		int start = page * PER_PAGE;
		for (int i = 0; i < PER_PAGE && start + i < list.size(); i++) {
			Waypoint w = list.get(start + i);
			int ry = TOP + i * ROW_H;
			g.fill(lx, ry + 3, lx + 12, ry + 15, w.color);
			g.fill(lx, ry + 3, lx + 12, ry + 4, 0xFF000000);
			g.fill(lx, ry + 14, lx + 12, ry + 15, 0xFF000000);
			g.fill(lx, ry + 3, lx + 1, ry + 15, 0xFF000000);
			g.fill(lx + 11, ry + 3, lx + 12, ry + 15, 0xFF000000);
			String txt = w.name + "  (" + (int) w.x + " " + (int) w.y + " " + (int) w.z + ")";
			int col = w.hidden ? 0xFF7A828C : 0xFFE6E6E6;
			g.text(this.font, txt, lx + 18, ry + 5, col, true);
		}

		int maxPage = Math.max(0, (list.size() - 1) / PER_PAGE);
		g.centeredText(this.font,
				Component.literal((page + 1) + " / " + (maxPage + 1)), this.width / 2, this.height - 26, 0xFF9AA4B2);

		super.extractRenderState(g, mouseX, mouseY, delta);
	}
}
