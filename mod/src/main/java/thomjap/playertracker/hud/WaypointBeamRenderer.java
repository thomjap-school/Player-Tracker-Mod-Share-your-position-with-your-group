package thomjap.playertracker.hud;

import thomjap.playertracker.PlayerTrackerClient;
import thomjap.playertracker.config.TrackerConfig;
import thomjap.playertracker.model.Waypoint;
import thomjap.playertracker.util.Dimensions;
import thomjap.playertracker.util.Waypoints;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.entity.BeaconBlockEntityRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

/**
 * Dessine un faisceau vertical (type beacon) dans le monde à chaque waypoint
 * visible de la dimension courante, via le renderer de faisceau vanilla.
 */
public final class WaypointBeamRenderer {
	private WaypointBeamRenderer() {
	}

	public static void register() {
		WorldRenderEvents.AFTER_ENTITIES.register(ctx -> {
			MinecraftClient mc = MinecraftClient.getInstance();
			TrackerConfig cfg = PlayerTrackerClient.config;
			// Suit le HUD : caché si beams off, HUD désactivé (H) ou masqué (F1).
			if (cfg == null || !cfg.showBeams || !cfg.hudEnabled
					|| mc.world == null || mc.gameRenderer == null || mc.options.hudHidden) {
				return;
			}
			MatrixStack matrices = ctx.matrices();
			OrderedRenderCommandQueue queue = ctx.commandQueue();
			if (matrices == null || queue == null) {
				return;
			}
			Vec3d camPos = mc.gameRenderer.getCamera().getCameraPos();
			for (Waypoint w : Waypoints.visible(Dimensions.current())) {
				beam(matrices, queue, camPos, w);
			}
		});
	}

	private static void beam(MatrixStack matrices, OrderedRenderCommandQueue queue, Vec3d camPos, Waypoint w) {
		matrices.push();
		matrices.translate(w.x - camPos.x, w.y - camPos.y, w.z - camPos.z);
		// (matrices, queue, texture, tickDelta, heightScale, yOffset, maxY, color, innerRadius, outerRadius)
		BeaconBlockEntityRenderer.renderBeam(matrices, queue, BeaconBlockEntityRenderer.BEAM_TEXTURE,
				0f, 1.0f, 0, BeaconBlockEntityRenderer.MAX_BEAM_HEIGHT, w.color, 0.2f, 0.25f);
		matrices.pop();
	}
}
