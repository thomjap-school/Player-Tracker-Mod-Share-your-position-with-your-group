package thomjap.playerbeacon.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.argument.ColorArgumentType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import thomjap.playerbeacon.PlayerBeaconClient;
import thomjap.playerbeacon.config.BeaconConfig;
import thomjap.playerbeacon.model.Waypoint;
import thomjap.playerbeacon.util.Dimensions;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Emitter commands (all waypoints are broadcast to the room):
 *   /beacon add waypoint &lt;x&gt; &lt;y&gt; &lt;z&gt; [color] &lt;name&gt;
 *   /beacon add calcwaypoint …   (links Overworld ↔ Nether)
 *   /beacon remove waypoint &lt;name&gt;
 *   /beacon list
 */
public final class BeaconCommands {
	private BeaconCommands() {
	}

	@FunctionalInterface
	private interface WpAction {
		int run(FabricClientCommandSource src, int x, int y, int z, int color, String name);
	}

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
				dispatcher.register(literal("beacon")
						.then(literal("add")
								.then(branch("waypoint", BeaconCommands::addWaypoint))
								.then(branch("calcwaypoint", BeaconCommands::addCalcWaypoint)))
						.then(literal("remove").then(literal("waypoint")
								.then(argument("name", StringArgumentType.greedyString())
										.executes(ctx -> removeWaypoint(ctx.getSource(),
												StringArgumentType.getString(ctx, "name"))))))
						.then(literal("list")
								.executes(ctx -> list(ctx.getSource())))));
	}

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

	private static int rgb(Formatting f) {
		Integer c = f.getColorValue();
		return c != null ? (0xFF000000 | c) : Waypoint.DEFAULT_COLOR;
	}

	private static void addOne(BeaconConfig cfg, String name, int x, int y, int z, String dim, int color) {
		cfg.waypoints.removeIf(w -> w.name.equalsIgnoreCase(name) && w.dim.equals(dim));
		cfg.waypoints.add(new Waypoint(name, x + 0.5, y, z + 0.5, dim, color));
	}

	private static int addWaypoint(FabricClientCommandSource src, int x, int y, int z, int color, String name) {
		BeaconConfig cfg = PlayerBeaconClient.config;
		addOne(cfg, name, x, y, z, Dimensions.current(), color);
		cfg.save();
		PlayerBeaconClient.syncWaypoints();
		src.sendFeedback(Text.translatable("playerbeacon.cmd.added", name, x, y, z));
		return 1;
	}

	private static int addCalcWaypoint(FabricClientCommandSource src, int x, int y, int z, int color, String name) {
		BeaconConfig cfg = PlayerBeaconClient.config;
		String dim = Dimensions.current();
		addOne(cfg, name, x, y, z, dim, color);
		if (dim.equals(Dimensions.OVERWORLD)) {
			addOne(cfg, name + " (Nether)", Math.floorDiv(x, 8), y, Math.floorDiv(z, 8), Dimensions.NETHER, color);
		} else if (dim.equals(Dimensions.NETHER)) {
			addOne(cfg, name + " (Overworld)", x * 8, y, z * 8, Dimensions.OVERWORLD, color);
		}
		cfg.save();
		PlayerBeaconClient.syncWaypoints();
		src.sendFeedback(Text.translatable("playerbeacon.cmd.calc_added", name, x, y, z));
		return 1;
	}

	private static int removeWaypoint(FabricClientCommandSource src, String name) {
		BeaconConfig cfg = PlayerBeaconClient.config;
		boolean removed = cfg.waypoints.removeIf(w -> w.name.equalsIgnoreCase(name));
		cfg.save();
		PlayerBeaconClient.syncWaypoints();
		src.sendFeedback(Text.translatable(
				removed ? "playerbeacon.cmd.removed" : "playerbeacon.cmd.notfound", name));
		return 1;
	}

	private static int list(FabricClientCommandSource src) {
		BeaconConfig cfg = PlayerBeaconClient.config;
		if (cfg.waypoints.isEmpty()) {
			src.sendFeedback(Text.translatable("playerbeacon.cmd.empty"));
			return 1;
		}
		src.sendFeedback(Text.translatable("playerbeacon.cmd.list_header", cfg.waypoints.size()));
		for (Waypoint w : cfg.waypoints) {
			src.sendFeedback(Text.literal(
					" - " + w.name + "  (" + (int) w.x + " " + (int) w.y + " " + (int) w.z + ")"));
		}
		return 1;
	}
}
