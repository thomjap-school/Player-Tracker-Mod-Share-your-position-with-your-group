package thomjap.playertracker.util;

import thomjap.playertracker.PlayerTrackerClient;
import thomjap.playertracker.config.TrackerConfig;
import thomjap.playertracker.model.Waypoint;

import java.util.ArrayList;
import java.util.List;

/** Selection of waypoints to display (HUD + beams). */
public final class Waypoints {
	private Waypoints() {
	}

	/**
	 * Waypoints to display in a dimension: mine (not hidden) plus those
	 * shared by others (not hidden from my view).
	 */
	public static List<Waypoint> visible(String dim) {
		TrackerConfig cfg = PlayerTrackerClient.config;
		List<Waypoint> out = new ArrayList<>();
		for (Waypoint w : cfg.waypoints) {
			if (w.dim.equals(dim) && !w.hidden) {
				out.add(w);
			}
		}
		for (Waypoint w : PlayerTrackerClient.relay.sharedWaypoints) {
			if (w.dim.equals(dim) && !cfg.hiddenShared.contains(Waypoint.sharedKey(w.owner, w.name))) {
				out.add(w);
			}
		}
		return out;
	}
}
