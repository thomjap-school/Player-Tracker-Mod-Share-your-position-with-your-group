package thomjap.playertracker.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/** Identifiants de dimensions et helpers associés (nom lisible, couleur, conversion). */
public final class Dimensions {
	public static final String OVERWORLD = "minecraft:overworld";
	public static final String NETHER = "minecraft:the_nether";
	public static final String END = "minecraft:the_end";

	private Dimensions() {
	}

	/** Dimension du monde client courant (ou overworld par défaut). */
	public static String current() {
		MinecraftClient mc = MinecraftClient.getInstance();
		return mc.world != null ? mc.world.getRegistryKey().getValue().toString() : OVERWORLD;
	}

	/** Nom localisé et lisible d'une dimension. */
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

	/** Couleur (ARGB) associée à une dimension. */
	public static int color(String dim) {
		switch (dim) {
			case OVERWORLD:
				return 0xFF6DBB5A; // vert
			case NETHER:
				return 0xFFD1483B; // rouge
			case END:
				return 0xFFC59BE0; // violet clair
			default:
				return 0xFFAAAAAA; // gris (dimension moddée / inconnue)
		}
	}
}
