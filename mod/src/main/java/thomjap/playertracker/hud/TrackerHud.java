package thomjap.playertracker.hud;

import thomjap.playertracker.PlayerTrackerClient;
import thomjap.playertracker.config.TrackerConfig;
import thomjap.playertracker.net.RelayClient;
import thomjap.playertracker.model.TrackedPlayer;
import thomjap.playertracker.model.Waypoint;
import thomjap.playertracker.util.Dimensions;
import thomjap.playertracker.util.Waypoints;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD element: draws a compass at the top of the screen and a list of
 * tracked players (direction + distance).
 *
 * <p>Minecraft conventions used:
 * <ul>
 *   <li>+X = Est, -X = Ouest, +Z = Sud, -Z = Nord.</li>
 *   <li>The player yaw is 0 towards South (+Z) and increases towards West.</li>
 * </ul>
 * So a target's heading is computed with {@code atan2(-dx, dz)}, in the same
 * frame as the yaw, which makes the relative angle {@code heading - yaw} directly
 * usable (0 = in front, positive = to the right).
 */
public class TrackerHud implements HudElement {

	@Override
	public void render(DrawContext context, RenderTickCounter tickCounter) {
		MinecraftClient mc = MinecraftClient.getInstance();
		TrackerConfig cfg = PlayerTrackerClient.config;
		RelayClient relay = PlayerTrackerClient.relay;

		if (cfg == null || relay == null || !cfg.enabled || !cfg.hudEnabled) {
			return;
		}
		if (mc.player == null || mc.world == null || mc.options.hudHidden) {
			return;
		}

		final double px = mc.player.getX();
		final double pz = mc.player.getZ();
		final float yaw = mc.player.getYaw();
		final String selfDim = mc.world.getRegistryKey().getValue().toString();
		final long now = System.currentTimeMillis();

		// Players that are still "fresh".
		List<TrackedPlayer> visible = new ArrayList<>();
		for (TrackedPlayer tp : relay.players.values()) {
			if (now - tp.lastUpdateMs <= cfg.staleTimeoutMs) {
				visible.add(tp);
			}
		}

		// Waypoints to display: mine + those shared by others (not hidden).
		List<Waypoint> wps = Waypoints.visible(selfDim);

		TextRenderer tr = mc.textRenderer;
		int screenW = context.getScaledWindowWidth();

		// -------- Connection indicator (movable + to scale) --------
		if (cfg.showStatus) {
			boolean connected = relay.isConnected();
			Text label = connected
					? Text.translatable("playertracker.status.online", visible.size())
					: Text.translatable("playertracker.status.offline");
			int sx = cfg.statusX;
			int sy = cfg.statusY;
			dot(context, sx, sy, 7, connected ? 0xFF3FD44B : 0xFFD43F3F);
			context.drawTextWithShadow(tr, label, sx + 11, sy, connected ? 0xFFBFBFBF : 0xFFE08585);
		}

		// -------- Compass (top, centered) --------
		if (cfg.showCompass) {
			int barW = Math.min(240, screenW - 20);
			int barX = cfg.compassX >= 0 ? cfg.compassX : (screenW - barW) / 2;
			barX = Math.max(0, Math.min(barX, screenW - barW)); // stays on screen
			int barY = cfg.compassY;
			int barH = 12;
			context.fill(barX, barY, barX + barW, barY + barH, 0x80000000);

			for (TrackedPlayer tp : visible) {
				if (tp.dim.equals(selfDim)) { // same dimension only
					drawCompassMarker(context, barX, barY, barW, barH, px, pz, yaw, tp.x, tp.z, colorFor(tp.name));
				}
			}
			for (Waypoint w : wps) {
				drawCompassMarker(context, barX, barY, barW, barH, px, pz, yaw, w.x, w.z, w.color);
			}

			// Center marker = look direction.
			int cx = barX + barW / 2;
			context.fill(cx, barY - 2, cx + 1, barY + barH + 2, 0xFFFFFFFF);
		}

		// -------- List (left) --------
		if (cfg.showList && (!visible.isEmpty() || !wps.isEmpty())) {
			visible.sort((a, b) -> Double.compare(dist2(px, pz, a.x, a.z), dist2(px, pz, b.x, b.z)));

			int lx = cfg.listX;
			int ly = cfg.listY;
			context.drawTextWithShadow(tr, Text.translatable("playertracker.hud.list_header"), lx, ly, 0xFFFFFF55);
			ly += 12;

			for (TrackedPlayer tp : visible) {
				String coords = coordsSuffix(cfg, tp.x, tp.y, tp.z);
				if (tp.dim.equals(selfDim)) {
					context.drawTextWithShadow(tr,
							targetLine(px, pz, yaw, tp.x, tp.z, tp.name, coords), lx, ly, colorFor(tp.name));
				} else {
					// Dimension color dot + name + (coords) + dimension.
					dot(context, lx, ly, 7, Dimensions.color(tp.dim));
					context.drawTextWithShadow(tr, tp.name + coords + "  " + Dimensions.name(tp.dim), lx + 11, ly, Dimensions.color(tp.dim));
				}
				ly += 11;
			}

			// Waypoints (personal markers), with their color.
			for (Waypoint w : wps) {
				String dn = w.owner != null ? w.name + " (" + w.owner + ")" : w.name;
				context.drawTextWithShadow(tr,
						targetLine(px, pz, yaw, w.x, w.z, dn, coordsSuffix(cfg, w.x, w.y, w.z)),
						lx, ly, w.color);
				ly += 11;
			}
		}
	}

	/** Small filled square with a black outline (dimension dot / indicator). */
	private static void dot(DrawContext ctx, int x, int y, int size, int fill) {
		ctx.fill(x, y, x + size, y + size, fill);
		ctx.fill(x, y, x + size, y + 1, 0xFF000000);
		ctx.fill(x, y + size - 1, x + size, y + size, 0xFF000000);
		ctx.fill(x, y, x + 1, y + size, 0xFF000000);
		ctx.fill(x + size - 1, y, x + size, y + size, 0xFF000000);
	}

	/** Squared horizontal distance between (px,pz) and (x,z). */
	private static double dist2(double px, double pz, double x, double z) {
		double dx = x - px;
		double dz = z - pz;
		return dx * dx + dz * dz;
	}

	/** " (x y z)" suffix if coordinate display is enabled. */
	private static String coordsSuffix(TrackerConfig cfg, double x, double y, double z) {
		return cfg.showCoords ? String.format(" (%d %d %d)", (int) x, (int) y, (int) z) : "";
	}

	/** Draws a marker on the compass towards a target, at its relative direction. */
	private static void drawCompassMarker(DrawContext ctx, int barX, int barY, int barW, int barH,
			double px, double pz, float yaw, double tx, double tz, int color) {
		double rel = MathHelper.wrapDegrees(bearingTo(px, pz, tx, tz) - yaw);
		double clamped = Math.max(-90, Math.min(90, rel)); // beyond that, stick to the edges
		int markerX = barX + (int) ((clamped + 90) / 180.0 * barW);
		ctx.fill(markerX - 1, barY, markerX + 2, barY + barH, color);
	}

	/** List line "arrow name (coords) distm cardinal" towards a target. */
	private static String targetLine(double px, double pz, float yaw,
			double tx, double tz, String name, String coords) {
		double bearing = bearingTo(px, pz, tx, tz);
		double rel = MathHelper.wrapDegrees(bearing - yaw);
		double dist = Math.sqrt(dist2(px, pz, tx, tz));
		return String.format("%s %s%s  %dm  %s", arrow(rel), name, coords, (int) dist, cardinal(bearing));
	}

	/** Heading towards a target (0 = South/+Z), in the same frame as the player yaw. */
	private static double bearingTo(double px, double pz, double tx, double tz) {
		return Math.toDegrees(Math.atan2(-(tx - px), tz - pz));
	}

	/** 4-direction ASCII arrow based on the angle relative to the look direction. */
	private static String arrow(double rel) {
		double a = ((rel % 360) + 360) % 360; // 0..360
		if (a < 45 || a >= 315) {
			return "^";
		} else if (a < 135) {
			return ">";
		} else if (a < 225) {
			return "v";
		} else {
			return "<";
		}
	}

	/** Absolute cardinal direction of the target (heading 0 = South), localized. */
	private static final String[] DIR_KEYS = {
			"playertracker.dir.s", "playertracker.dir.sw", "playertracker.dir.w", "playertracker.dir.nw",
			"playertracker.dir.n", "playertracker.dir.ne", "playertracker.dir.e", "playertracker.dir.se"
	};

	private static String cardinal(double bearing) {
		double b = ((bearing % 360) + 360) % 360;
		return Text.translatable(DIR_KEYS[(int) Math.round(b / 45.0) % 8]).getString();
	}

	/** Palette of bright, well-distinguished colors to tell players apart. */
	private static final int[] PALETTE = {
			0xFFFF5555, // red
			0xFF55FF55, // green
			0xFF5599FF, // blue
			0xFFFFFF55, // yellow
			0xFFFF55FF, // magenta
			0xFF55FFFF, // cyan
			0xFFFFAA33, // orange
			0xFFAA66FF, // purple
			0xFF00FFAA, // turquoise
			0xFFFF88BB, // pink
			0xFFBBFF33, // lime
			0xFF66CCFF, // sky blue
			0xFFFFCC66, // gold
			0xFF33FFCC, // mint
			0xFFFF7744, // coral
			0xFFCC88FF, // lavender
	};

	/** Stable, distinct color derived from the username. */
	private static int colorFor(String name) {
		return PALETTE[Math.floorMod(name.hashCode(), PALETTE.length)];
	}
}
