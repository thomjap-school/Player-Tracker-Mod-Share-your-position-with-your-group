package thomjap.playertracker.model;

/**
 * Last known position of a tracked player.
 */
public class TrackedPlayer {
	public final String name;
	public final double x;
	public final double y;
	public final double z;
	public final String dim;
	public final long lastUpdateMs;

	public TrackedPlayer(String name, double x, double y, double z, String dim) {
		this.name = name;
		this.x = x;
		this.y = y;
		this.z = z;
		this.dim = dim;
		this.lastUpdateMs = System.currentTimeMillis();
	}
}
