package thomjap.playertracker.hud;

import thomjap.playertracker.PlayerTrackerClient;
import thomjap.playertracker.config.TrackerConfig;
import thomjap.playertracker.model.Waypoint;
import thomjap.playertracker.util.Dimensions;
import thomjap.playertracker.util.Waypoints;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a vertical (beacon-style) beam in the world at every visible waypoint of
 * the current dimension, using the vanilla beam renderer.
 *
 * <p>Since MC 26.1 the world render pipeline is submit-based: we hook the
 * {@code COLLECT_SUBMITS} phase and push a beacon beam onto the submit node
 * collector for each waypoint.
 */
public final class WaypointBeamRenderer {
	private WaypointBeamRenderer() {
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> {
			Minecraft mc = Minecraft.getInstance();
			TrackerConfig cfg = PlayerTrackerClient.config;
			// Follows the HUD: hidden if beams off, HUD disabled (H) or hidden (F1).
			if (cfg == null || !cfg.showBeams || !cfg.hudEnabled
					|| mc.level == null || mc.gameRenderer == null || mc.options.hideGui) {
				return;
			}
			PoseStack matrices = ctx.poseStack();
			SubmitNodeCollector queue = ctx.submitNodeCollector();
			if (matrices == null || queue == null) {
				return;
			}
			Vec3 camPos = mc.gameRenderer.getMainCamera().position();
			for (Waypoint w : Waypoints.visible(Dimensions.current())) {
				beam(matrices, queue, camPos, w);
			}
		});
	}

	private static void beam(PoseStack matrices, SubmitNodeCollector queue, Vec3 camPos, Waypoint w) {
		matrices.pushPose();
		matrices.translate(w.x - camPos.x, w.y - camPos.y, w.z - camPos.z);
		// (poseStack, collector, texture, tickDelta, heightScale, yOffset, maxY, color, innerRadius, outerRadius)
		BeaconRenderer.submitBeaconBeam(matrices, queue, BeaconRenderer.BEAM_LOCATION,
				0f, 1.0f, 0, BeaconRenderer.MAX_RENDER_Y, w.color,
				BeaconRenderer.SOLID_BEAM_RADIUS, BeaconRenderer.BEAM_GLOW_RADIUS);
		matrices.popPose();
	}
}
