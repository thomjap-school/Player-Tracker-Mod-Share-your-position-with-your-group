package thomjap.playertracker.command;

import thomjap.playertracker.PlayerTrackerClient;
import thomjap.playertracker.config.TrackerConfig;
import thomjap.playertracker.model.Waypoint;
import thomjap.playertracker.util.Dimensions;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.argument.ColorArgumentType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Commandes client :
 *   /tracker add waypoint &lt;x&gt; &lt;y&gt; &lt;z&gt; [color] &lt;name&gt;
 *   /tracker remove waypoint &lt;name&gt;
 *   /tracker list
 */
public final class TrackerCommands {
	private TrackerCommands() {
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
				dispatcher.register(literal("tracker")
						.then(literal("add")
								.then(branch("waypoint", (s, x, y, z, c, n) -> addWaypoint(s, x, y, z, c, n, false)))
								.then(branch("sharewaypoint", (s, x, y, z, c, n) -> addWaypoint(s, x, y, z, c, n, true)))
								.then(branch("calcwaypoint", TrackerCommands::addCalcWaypoint)))
						.then(literal("change").then(literal("waypoint").then(literal("color")
								.then(argument("name", StringArgumentType.string())
										.then(argument("color", ColorArgumentType.color())
												.executes(ctx -> changeColor(ctx.getSource(),
														StringArgumentType.getString(ctx, "name"),
														rgb(ctx.getArgument("color", Formatting.class)))))))))
						.then(literal("share").then(literal("waypoint")
								.then(argument("name", StringArgumentType.greedyString())
										.executes(ctx -> shareWaypoint(ctx.getSource(),
												StringArgumentType.getString(ctx, "name"))))))
						.then(literal("beams")
								.then(literal("on").executes(ctx -> setBeams(ctx.getSource(), true)))
								.then(literal("off").executes(ctx -> setBeams(ctx.getSource(), false))))
						.then(literal("remove").then(literal("waypoint")
								.then(argument("name", StringArgumentType.greedyString())
										.executes(ctx -> removeWaypoint(ctx.getSource(),
												StringArgumentType.getString(ctx, "name"))))))
						.then(literal("list")
								.executes(ctx -> listWaypoints(ctx.getSource())))));
	}

	/** Waypoint add action (x y z color name) -> brigadier return code. */
	@FunctionalInterface
	private interface WpAction {
		int run(FabricClientCommandSource src, int x, int y, int z, int color, String name);
	}

	/** Shared sub-tree for the add commands: x y z [color] name, with a chosen action. */
	private static LiteralArgumentBuilder<FabricClientCommandSource> branch(String sub, WpAction action) {
		return literal(sub).then(argument("x", IntegerArgumentType.integer())
				.then(argument("y", IntegerArgumentType.integer())
						.then(argument("z", IntegerArgumentType.integer())
								.then(argument("color", ColorArgumentType.color())
										.then(argument("name", StringArgumentType.greedyString())
												.executes(ctx -> action.run(ctx.getSource(),
														IntegerArgumentType.getInteger(ctx, "x"),
														IntegerArgumentType.getInteger(ctx, "y"),
														IntegerArgumentType.getInteger(ctx, "z"),
														rgb(ctx.getArgument("color", Formatting.class)),
														StringArgumentType.getString(ctx, "name")))))
								.then(argument("name", StringArgumentType.greedyString())
										.executes(ctx -> action.run(ctx.getSource(),
												IntegerArgumentType.getInteger(ctx, "x"),
												IntegerArgumentType.getInteger(ctx, "y"),
												IntegerArgumentType.getInteger(ctx, "z"),
												Waypoint.DEFAULT_COLOR,
												StringArgumentType.getString(ctx, "name")))))));
	}

	/** Converts a chat color (Formatting) to opaque ARGB. */
	private static int rgb(Formatting formatting) {
		Integer c = formatting.getColorValue();
		return c != null ? (0xFF000000 | c) : Waypoint.DEFAULT_COLOR;
	}

	/** Adds (or replaces) a waypoint in a given dimension. */
	private static void addOne(TrackerConfig cfg, String name, int x, int y, int z, String dim, int color, boolean shared) {
		cfg.waypoints.removeIf(w -> w.name.equalsIgnoreCase(name) && w.dim.equals(dim));
		Waypoint w = new Waypoint(name, x + 0.5, y, z + 0.5, dim, color);
		w.shared = shared;
		cfg.waypoints.add(w);
	}

	private static int addWaypoint(FabricClientCommandSource src, int x, int y, int z, int color, String name, boolean shared) {
		TrackerConfig cfg = PlayerTrackerClient.config;
		addOne(cfg, name, x, y, z, Dimensions.current(), color, shared);
		cfg.save();
		PlayerTrackerClient.syncWaypoints();
		src.sendFeedback(Text.translatable(shared ? "playertracker.cmd.added_shared" : "playertracker.cmd.added", name, x, y, z));
		return 1;
	}

	/**
	 * "Linked" Overworld <-> Nether waypoint, forced shared: creates the point in
	 * the current dimension AND its equivalent in the other world (/8 OW->Nether, x8 Nether->OW).
	 */
	private static int addCalcWaypoint(FabricClientCommandSource src, int x, int y, int z, int color, String name) {
		TrackerConfig cfg = PlayerTrackerClient.config;
		String dim = Dimensions.current();
		addOne(cfg, name, x, y, z, dim, color, true);
		if (dim.equals(Dimensions.OVERWORLD)) {
			addOne(cfg, name + " (Nether)", Math.floorDiv(x, 8), y, Math.floorDiv(z, 8),
					Dimensions.NETHER, color, true);
		} else if (dim.equals(Dimensions.NETHER)) {
			addOne(cfg, name + " (Overworld)", x * 8, y, z * 8, Dimensions.OVERWORLD, color, true);
		}
		cfg.save();
		PlayerTrackerClient.syncWaypoints();
		src.sendFeedback(Text.translatable("playertracker.cmd.calc_added", name, x, y, z));
		return 1;
	}

	private static int shareWaypoint(FabricClientCommandSource src, String name) {
		TrackerConfig cfg = PlayerTrackerClient.config;
		Waypoint found = null;
		for (Waypoint w : cfg.waypoints) {
			if (w.name.equalsIgnoreCase(name)) {
				found = w;
			}
		}
		if (found == null) {
			src.sendFeedback(Text.translatable("playertracker.cmd.notfound", name));
			return 1;
		}
		found.shared = !found.shared;
		cfg.save();
		PlayerTrackerClient.syncWaypoints();
		src.sendFeedback(Text.translatable(
				found.shared ? "playertracker.cmd.shared" : "playertracker.cmd.unshared", name));
		return 1;
	}

	private static int setBeams(FabricClientCommandSource src, boolean on) {
		TrackerConfig cfg = PlayerTrackerClient.config;
		cfg.showBeams = on;
		cfg.save();
		src.sendFeedback(Text.translatable(on ? "playertracker.cmd.beams_on" : "playertracker.cmd.beams_off"));
		return 1;
	}

	private static int changeColor(FabricClientCommandSource src, String name, int color) {
		TrackerConfig cfg = PlayerTrackerClient.config;
		boolean found = false;
		for (Waypoint w : cfg.waypoints) {
			if (w.name.equalsIgnoreCase(name)) {
				w.color = color;
				found = true;
			}
		}
		cfg.save();
		PlayerTrackerClient.syncWaypoints();
		src.sendFeedback(Text.translatable(
				found ? "playertracker.cmd.color_changed" : "playertracker.cmd.notfound", name));
		return 1;
	}

	private static int removeWaypoint(FabricClientCommandSource src, String name) {
		TrackerConfig cfg = PlayerTrackerClient.config;
		boolean removed = cfg.waypoints.removeIf(w -> w.name.equalsIgnoreCase(name));
		cfg.save();
		PlayerTrackerClient.syncWaypoints();
		src.sendFeedback(Text.translatable(
				removed ? "playertracker.cmd.removed" : "playertracker.cmd.notfound", name));
		return 1;
	}

	private static int listWaypoints(FabricClientCommandSource src) {
		TrackerConfig cfg = PlayerTrackerClient.config;
		if (cfg.waypoints.isEmpty()) {
			src.sendFeedback(Text.translatable("playertracker.cmd.empty"));
			return 1;
		}
		src.sendFeedback(Text.translatable("playertracker.cmd.list_header", cfg.waypoints.size()));
		for (Waypoint w : cfg.waypoints) {
			src.sendFeedback(Text.literal(
					String.format(" - %s  (%d %d %d)", w.name, (int) w.x, (int) w.y, (int) w.z)));
		}
		return 1;
	}
}
