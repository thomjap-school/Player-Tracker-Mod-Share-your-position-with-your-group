package thomjap.playertracker.crate;

import thomjap.playertracker.PlayerTrackerClient;
import thomjap.playertracker.config.TrackerConfig;
import thomjap.playertracker.model.Waypoint;
import thomjap.playertracker.util.Dimensions;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Watches the chat: for each NON-common event crate announcement, creates
 * a (private) waypoint colored by rarity automatically.
 *
 * <p>Example line: {@code . # Epic -> 281 102 -905}.
 */
public final class CrateWatcher {
	private CrateWatcher() {
	}

	public static final String TAG = "caisse";
	private static final Pattern INT = Pattern.compile("-?\\d+");

	/** {accent-free keyword, label, ARGB color}. Order = test priority. */
	private static final Object[][] RARITIES = {
			{"legendaire", "Légendaire", 0xFFFFAA00},
			{"mythique", "Mythique", 0xFFFF5555},
			{"epique", "Épique", 0xFFCC66FF},
			{"tres rare", "Très rare", 0xFF66FFCC},
			{"rare", "Rare", 0xFF55DDFF},
	};

	public static void register() {
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (!overlay) {
				handle(message.getString());
			}
		});
		ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, ts) ->
				handle(message.getString()));
	}

	private static String noAccent(String s) {
		return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
	}

	private static void handle(String rawText) {
		TrackerConfig cfg = PlayerTrackerClient.config;
		MinecraftClient mc = MinecraftClient.getInstance();
		if (cfg == null || !cfg.crateWaypoints || rawText == null || mc.world == null) {
			return;
		}
		String s = noAccent(rawText);

		String label = null;
		int color = 0;
		for (Object[] r : RARITIES) {
			if (s.contains((String) r[0])) {
				label = (String) r[1];
				color = (Integer) r[2];
				break;
			}
		}
		if (label == null) {
			return; // common or not a crate
		}

		int[] xyz = lastThreeInts(rawText);
		if (xyz == null) {
			return;
		}
		String dim = Dimensions.current();

		// De-dup: same crate (same coords) already present?
		for (Waypoint w : cfg.waypoints) {
			if (TAG.equals(w.tag) && (int) w.x == xyz[0] && (int) w.y == xyz[1] && (int) w.z == xyz[2]) {
				return;
			}
		}

		Waypoint w = new Waypoint(label, xyz[0] + 0.5, xyz[1], xyz[2] + 0.5, dim, color);
		w.tag = TAG;
		cfg.waypoints.add(w);
		cfg.save();
		if (mc.player != null) {
			mc.player.sendMessage(Text.translatable("playertracker.crate.added", label, xyz[0], xyz[1], xyz[2]), true);
		}
	}

	/** The last three integers of the line (= x y z). */
	private static int[] lastThreeInts(String s) {
		Matcher m = INT.matcher(s);
		List<Integer> nums = new ArrayList<>();
		while (m.find()) {
			try {
				nums.add(Integer.parseInt(m.group()));
			} catch (NumberFormatException ignored) {
			}
		}
		if (nums.size() < 3) {
			return null;
		}
		int n = nums.size();
		return new int[]{nums.get(n - 3), nums.get(n - 2), nums.get(n - 1)};
	}
}
