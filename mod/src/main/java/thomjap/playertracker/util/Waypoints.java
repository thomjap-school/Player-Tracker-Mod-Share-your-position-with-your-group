package thomjap.playertracker.util;

import thomjap.playertracker.PlayerTrackerClient;
import thomjap.playertracker.config.TrackerConfig;
import thomjap.playertracker.model.Waypoint;

import java.util.ArrayList;
import java.util.List;

/** Sélection des waypoints à afficher (HUD + faisceaux). */
public final class Waypoints {
	private Waypoints() {
	}

	/**
	 * Waypoints à afficher dans une dimension : les miens (non masqués) plus ceux
	 * partagés par les autres (non masqués de ma vue).
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
