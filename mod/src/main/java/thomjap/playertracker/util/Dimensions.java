package thomjap.playertracker.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/** Dimension identifiers and related helpers (readable name, color, conversion). */
public final class Dimensions {
	public static final String OVERWORLD = "minecraft:overworld";
	public static final String NETHER = "minecraft:the_nether";
	public static final String END = "minecraft:the_end";

	private Dimensions() {
	}

	/** Dimension of the current client level (or overworld by default). */
	public static String current() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.world != null ? mc.world.getRegistryKey().getValue().toString() : OVERWORLD;
	}

	/** Localized, readable name of a dimension. */
	public static String name(String dim) {
		switch (dim) {
			case OVERWORLD:
				return Text.translatable("playertracker.dim.overworld").getString();
			case NETHER:
				return Text.translatable("playertracker.dim.nether").getString();
			case END:
				return Text.translatable("playertracker.dim.end").getString();
			default:
				int i = dim.indexOf(':');
				return (i >= 0 ? dim.substring(i + 1) : dim).replace('_', ' ');
		}
	}

	/** Color (ARGB) associated with a dimension. */
	public static int color(String dim) {
		switch (dim) {
			case OVERWORLD:
				return 0xFF6DBB5A; // green
			case NETHER:
				return 0xFFD1483B; // red
			case END:
				return 0xFFC59BE0; // light purple
			default:
				return 0xFFAAAAAA; // gray (modded / unknown dimension)
		}
	}
}
