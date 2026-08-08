package thomjap.playertracker.screen;

import thomjap.playertracker.PlayerTrackerClient;
import thomjap.playertracker.config.TrackerConfig;
import thomjap.playertracker.crate.CrateWatcher;
import thomjap.playertracker.model.Waypoint;
import thomjap.playertracker.util.Dimensions;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Waypoint management screen: tabs (all / private / shared / crates), add, share,
 * color, hide and delete. The "Shared" tab also includes other players'
 * waypoints (with a hide option).
 */
public class WaypointsScreen extends BaseTrackerScreen {
	private static final int PER_PAGE = 6;
	private static final int ROW_H = 20;
	private static final int TOP = 54;

	private static final int[] CYCLE = {
			0xFFFFDD44, 0xFFFF5555, 0xFF55FF55, 0xFF5599FF, 0xFFFF55FF,
			0xFF55FFFF, 0xFFFFAA33, 0xFFAA66FF, 0xFFFFFFFF
	};

	private static final String[] TAB_KEYS = {
			"playertracker.waypoints.tab_all", "playertracker.waypoints.tab_private",
			"playertracker.waypoints.tab_shared", "playertracker.waypoints.tab_crates"
	};

	private int tab = 0;
	private int page = 0;
	private EditBox nameField;

	public WaypointsScreen() {
		super(Component.translatable("playertracker.waypoints.title"));
	}

	private int listW() {
		return Math.min(480, this.width - 40);
	}

	private int listX() {
		return this.width / 2 - listW() / 2;
	}

	private boolean matchesTab(Waypoint w) {
		switch (tab) {
			case 1:
				return !w.shared && !CrateWatcher.TAG.equals(w.tag);
			case 2:
				return w.shared;
			case 3:
				return CrateWatcher.TAG.equals(w.tag);
			default:
				return true;
		}
	}

	/** Entries of the current tab: my waypoints + (Shared tab) other players'. */
	private List<Waypoint> entries() {
		List<Waypoint> out = new ArrayList<>();
		for (Waypoint w : PlayerTrackerClient.config.waypoints) {
			if (matchesTab(w)) {
				out.add(w);
			}
		}
		if (tab == 2) {
			out.addAll(PlayerTrackerClient.relay.sharedWaypoints);
		}
		return out;
	}

	private static boolean isHidden(Waypoint w) {
		return w.owner == null
				? w.hidden
				: PlayerTrackerClient.config.hiddenShared.contains(Waypoint.sharedKey(w.owner, w.name));
	}

	private void toggleHidden(Waypoint w) {
		TrackerConfig cfg = PlayerTrackerClient.config;
		if (w.owner == null) {
			w.hidden = !w.hidden;
		} else {
			String key = Waypoint.sharedKey(w.owner, w.name);
			if (!cfg.hiddenShared.remove(key)) {
				cfg.hiddenShared.add(key);
			}
		}
		cfg.save();
		this.rebuildWidgets();
	}

	@Override
	protected void init() {
		TrackerConfig cfg = PlayerTrackerClient.config;
		int lx = listX();
		int lw = listW();

		int tabW = lw / 4 - 3;
		for (int t = 0; t < 4; t++) {
			final int tt = t;
			Button btn = Button.builder(Component.translatable(TAB_KEYS[t]), b -> {
				tab = tt;
				page = 0;
				this.rebuildWidgets();
			}).bounds(lx + t * (tabW + 4), 30, tabW, 18).build();
			btn.active = (tab != t);
			this.addRenderableWidget(btn);
		}

		List<Waypoint> list = entries();
		int maxPage = Math.max(0, (list.size() - 1) / PER_PAGE);
		page = Math.max(0, Math.min(page, maxPage));
		int start = page * PER_PAGE;

		for (int i = 0; i < PER_PAGE && start + i < list.size(); i++) {
			final Waypoint w = list.get(start + i);
			int ry = TOP + i * ROW_H;
			if (w.owner == null) {
				// My waypoints: Share, Hide, Color, Delete.
				this.addRenderableWidget(Button.builder(shareLabel(w), b -> {
					w.shared = !w.shared;
					cfg.save();
					PlayerTrackerClient.syncWaypoints();
					this.rebuildWidgets();
				}).bounds(lx + lw - 232, ry, 58, 18).build());
				this.addRenderableWidget(Button.builder(hideLabel(w), b -> toggleHidden(w))
						.bounds(lx + lw - 170, ry, 58, 18).build());
				this.addRenderableWidget(Button.builder(Component.translatable("playertracker.waypoints.color"), b -> {
					w.color = nextColor(w.color);
					cfg.save();
					PlayerTrackerClient.syncWaypoints();
				}).bounds(lx + lw - 108, ry, 52, 18).build());
				this.addRenderableWidget(Button.builder(Component.translatable("playertracker.waypoints.delete"), b -> {
					cfg.waypoints.remove(w);
					cfg.save();
					PlayerTrackerClient.syncWaypoints();
					this.rebuildWidgets();
				}).bounds(lx + lw - 52, ry, 52, 18).build());
			} else {
				// Another player's waypoint: only Hide/Show.
				this.addRenderableWidget(Button.builder(hideLabel(w), b -> toggleHidden(w))
						.bounds(lx + lw - 60, ry, 60, 18).build());
			}
		}

		int ay = this.height - 50;
		nameField = new EditBox(this.font, lx, ay, lw - 230, 20, Component.literal("name"));
		nameField.setMaxLength(48);
		this.addRenderableWidget(nameField);
		this.addRenderableWidget(Button.builder(Component.translatable("playertracker.waypoints.add_here"), b -> addHere())
				.bounds(lx + lw - 226, ay, 108, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("playertracker.waypoints.delete_all"), b -> {
			cfg.waypoints.removeAll(entries()); // only removes mine (others are not in the config)
			cfg.save();
			PlayerTrackerClient.syncWaypoints();
			this.rebuildWidgets();
		}).bounds(lx + lw - 114, ay, 114, 20).build());

		int py = this.height - 26;
		this.addRenderableWidget(Button.builder(Component.literal("<"), b -> {
			page--;
			this.rebuildWidgets();
		}).bounds(lx, py, 40, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal(">"), b -> {
			page++;
			this.rebuildWidgets();
		}).bounds(lx + 44, py, 40, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("playertracker.config.close"), b -> this.onClose())
				.bounds(lx + lw - 80, py, 80, 20).build());
	}

	private static Component shareLabel(Waypoint w) {
		return Component.translatable(w.shared
				? "playertracker.waypoints.shared" : "playertracker.waypoints.private");
	}

	private static Component hideLabel(Waypoint w) {
		return Component.translatable(isHidden(w)
				? "playertracker.waypoints.hidden" : "playertracker.waypoints.visible");
	}

	private void addHere() {
		Minecraft mc = this.minecraft;
		if (mc.player == null || mc.level == null) {
			return;
		}
		String name = nameField.getValue().trim();
		if (name.isEmpty()) {
			name = "Waypoint " + (PlayerTrackerClient.config.waypoints.size() + 1);
		}
		String dim = Dimensions.current();
		PlayerTrackerClient.config.waypoints.add(new Waypoint(name,
				Math.floor(mc.player.getX()) + 0.5,
				Math.round(mc.player.getY()),
				Math.floor(mc.player.getZ()) + 0.5,
				dim, Waypoint.DEFAULT_COLOR));
		PlayerTrackerClient.config.save();
		PlayerTrackerClient.syncWaypoints();
		this.rebuildWidgets();
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
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		dimBackground(g, 0xC8000000);
		g.centeredText(this.font, this.title, this.width / 2, 14, 0xFFFFFFFF);

		List<Waypoint> list = entries();
		int lx = listX();

		if (list.isEmpty()) {
			g.centeredText(this.font,
					Component.translatable("playertracker.waypoints.empty"), this.width / 2, TOP + 16, 0xFF9AA4B2);
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
			String txt = w.name + (w.owner != null ? " — " + w.owner : "")
					+ "  (" + (int) w.x + " " + (int) w.y + " " + (int) w.z + ")";
			int col = isHidden(w) ? 0xFF7A828C : 0xFFE6E6E6;
			g.text(this.font, txt, lx + 18, ry + 5, col, true);
		}

		int maxPage = Math.max(0, (list.size() - 1) / PER_PAGE);
		g.centeredText(this.font,
				Component.literal((page + 1) + " / " + (maxPage + 1)), this.width / 2, this.height - 22, 0xFF9AA4B2);

		super.extractRenderState(g, mouseX, mouseY, delta);
	}
}
