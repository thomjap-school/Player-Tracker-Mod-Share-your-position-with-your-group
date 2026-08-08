package thomjap.playerbeacon.util;

import net.minecraft.client.MinecraftClient;

/** Dimension identifiers + current dimension. */
public final class Dimensions {
	public static final String OVERWORLD = "minecraft:overworld";
	public static final String NETHER = "minecraft:the_nether";

	private Dimensions() {
	}

	public static String current() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.world != null ? mc.world.getRegistryKey().getValue().toString() : OVERWORLD;
	}
}
