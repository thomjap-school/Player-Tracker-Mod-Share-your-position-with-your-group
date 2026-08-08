package thomjap.playerbeacon.model;

/** A waypoint broadcast by the emitter (always shared with the room). */
public class Waypoint {
	public static final int DEFAULT_COLOR = 0xFFFFDD44;

	public String name;
	public double x;
	public double y;
	public double z;
	public String dim;
	public int color = DEFAULT_COLOR;

	public Waypoint() {
		// required by GSON
	}

	public Waypoint(String name, double x, double y, double z, String dim, int color) {
		this.name = name;
		this.x = x;
		this.y = y;
		this.z = z;
		this.dim = dim;
		this.color = color;
	}
}
