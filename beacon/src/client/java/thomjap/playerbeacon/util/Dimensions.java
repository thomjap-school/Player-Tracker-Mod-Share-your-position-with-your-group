package thomjap.playerbeacon.util;

import net.minecraft.client.Minecraft;

/** Dimension identifiers + current dimension. */
public final class Dimensions {
	public static final String OVERWORLD = "minecraft:overworld";
	public static final String NETHER = "minecraft:the_nether";

	private Dimensions() {
	}

	public static String current() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null ? mc.level.dimension().identifier().toString() : OVERWORLD;
	}
}
