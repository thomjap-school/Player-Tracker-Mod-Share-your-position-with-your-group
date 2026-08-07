package thomjap.playertracker.screen;

import thomjap.playertracker.PlayerTrackerClient;
import thomjap.playertracker.config.TrackerConfig;
import thomjap.playertracker.crate.CrateWatcher;
import thomjap.playertracker.model.Waypoint;
import thomjap.playertracker.util.Dimensions;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Écran de gestion des waypoints : onglets (tous / privés / partagés / caisses),
 * ajout, partage, coloration, masquage et suppression. L'onglet « Partagés »
 * inclut aussi les waypoints des autres joueurs (avec option masquer).
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
	private TextFieldWidget nameField;

	public WaypointsScreen() {
		super(Text.translatable("playertracker.waypoints.title"));
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

	/** Entrées de l'onglet courant : mes waypoints + (onglet Partagés) ceux des autres. */
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
		this.clearAndInit();
	}

	@Override
	protected void init() {
		TrackerConfig cfg = PlayerTrackerClient.config;
		int lx = listX();
		int lw = listW();

		int tabW = lw / 4 - 3;
		for (int t = 0; t < 4; t++) {
			final int tt = t;
			ButtonWidget btn = ButtonWidget.builder(Text.translatable(TAB_KEYS[t]), b -> {
				tab = tt;
				page = 0;
				this.clearAndInit();
			}).dimensions(lx + t * (tabW + 4), 30, tabW, 18).build();
			btn.active = (tab != t);
			this.addDrawableChild(btn);
		}

		List<Waypoint> list = entries();
		int maxPage = Math.max(0, (list.size() - 1) / PER_PAGE);
		page = Math.max(0, Math.min(page, maxPage));
		int start = page * PER_PAGE;

		for (int i = 0; i < PER_PAGE && start + i < list.size(); i++) {
			final Waypoint w = list.get(start + i);
			int ry = TOP + i * ROW_H;
			if (w.owner == null) {
				// Mes waypoints : Partager, Masquer, Couleur, Supprimer.
				this.addDrawableChild(ButtonWidget.builder(shareLabel(w), b -> {
					w.shared = !w.shared;
					cfg.save();
					PlayerTrackerClient.syncWaypoints();
					this.clearAndInit();
				}).dimensions(lx + lw - 232, ry, 58, 18).build());
				this.addDrawableChild(ButtonWidget.builder(hideLabel(w), b -> toggleHidden(w))
						.dimensions(lx + lw - 170, ry, 58, 18).build());
				this.addDrawableChild(ButtonWidget.builder(Text.translatable("playertracker.waypoints.color"), b -> {
					w.color = nextColor(w.color);
					cfg.save();
					PlayerTrackerClient.syncWaypoints();
				}).dimensions(lx + lw - 108, ry, 52, 18).build());
				this.addDrawableChild(ButtonWidget.builder(Text.translatable("playertracker.waypoints.delete"), b -> {
					cfg.waypoints.remove(w);
					cfg.save();
					PlayerTrackerClient.syncWaypoints();
					this.clearAndInit();
				}).dimensions(lx + lw - 52, ry, 52, 18).build());
			} else {
				// Waypoint d'un autre joueur : uniquement Masquer/Afficher.
				this.addDrawableChild(ButtonWidget.builder(hideLabel(w), b -> toggleHidden(w))
						.dimensions(lx + lw - 60, ry, 60, 18).build());
			}
		}

		int ay = this.height - 50;
		nameField = new TextFieldWidget(this.textRenderer, lx, ay, lw - 230, 20, Text.literal("nom"));
		nameField.setMaxLength(48);
		this.addDrawableChild(nameField);
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("playertracker.waypoints.add_here"), b -> addHere())
				.dimensions(lx + lw - 226, ay, 108, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("playertracker.waypoints.delete_all"), b -> {
			cfg.waypoints.removeAll(entries()); // ne retire que les miens (les autres ne sont pas dans la config)
			cfg.save();
			PlayerTrackerClient.syncWaypoints();
			this.clearAndInit();
		}).dimensions(lx + lw - 114, ay, 114, 20).build());

		int py = this.height - 26;
		this.addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
			page--;
			this.clearAndInit();
		}).dimensions(lx, py, 40, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
			page++;
			this.clearAndInit();
		}).dimensions(lx + 44, py, 40, 20).build());
		this.addDrawableChild(ButtonWidget.builder(Text.translatable("playertracker.config.close"), b -> this.close())
				.dimensions(lx + lw - 80, py, 80, 20).build());
	}

	private static Text shareLabel(Waypoint w) {
		return Text.translatable(w.shared
				? "playertracker.waypoints.shared" : "playertracker.waypoints.private");
	}

	private static Text hideLabel(Waypoint w) {
		return Text.translatable(isHidden(w)
				? "playertracker.waypoints.hidden" : "playertracker.waypoints.visible");
	}

	private void addHere() {
		MinecraftClient mc = this.client;
		if (mc.player == null || mc.world == null) {
			return;
		}
		String name = nameField.getText().trim();
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
		this.clearAndInit();
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
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		dimBackground(ctx, 0xC8000000);
		ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 14, 0xFFFFFFFF);

		List<Waypoint> list = entries();
		int lx = listX();

		if (list.isEmpty()) {
			ctx.drawCenteredTextWithShadow(this.textRenderer,
					Text.translatable("playertracker.waypoints.empty"), this.width / 2, TOP + 16, 0xFF9AA4B2);
		}

		int start = page * PER_PAGE;
		for (int i = 0; i < PER_PAGE && start + i < list.size(); i++) {
			Waypoint w = list.get(start + i);
			int ry = TOP + i * ROW_H;
			ctx.fill(lx, ry + 3, lx + 12, ry + 15, w.color);
			ctx.fill(lx, ry + 3, lx + 12, ry + 4, 0xFF000000);
			ctx.fill(lx, ry + 14, lx + 12, ry + 15, 0xFF000000);
			ctx.fill(lx, ry + 3, lx + 1, ry + 15, 0xFF000000);
			ctx.fill(lx + 11, ry + 3, lx + 12, ry + 15, 0xFF000000);
			String txt = w.name + (w.owner != null ? " — " + w.owner : "")
					+ "  (" + (int) w.x + " " + (int) w.y + " " + (int) w.z + ")";
			int col = isHidden(w) ? 0xFF7A828C : 0xFFE6E6E6;
			ctx.drawTextWithShadow(this.textRenderer, txt, lx + 18, ry + 5, col);
		}

		int maxPage = Math.max(0, (list.size() - 1) / PER_PAGE);
		ctx.drawCenteredTextWithShadow(this.textRenderer,
				Text.literal((page + 1) + " / " + (maxPage + 1)), this.width / 2, this.height - 22, 0xFF9AA4B2);

		super.render(ctx, mouseX, mouseY, delta);
	}
}
