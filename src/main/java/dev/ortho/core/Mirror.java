package dev.ortho.core;

/**
 * A mirror plane perpendicular to X or Z. The plane position is stored doubled, so it can sit on a
 * block center (odd value) or between two blocks (even value).
 */
public final class Mirror {
	public enum Axis {
		OFF, X, Z
	}

	public Axis axis = Axis.OFF;
	/** Twice the plane coordinate: block center of x = 120 is 241, the boundary x = 120 is 240. */
	public int pos2;

	public boolean active() {
		return axis != null && axis != Axis.OFF;
	}

	/** Plane through the center of block coordinate {@code c}. */
	public void centerOn(Axis a, int c) {
		axis = a;
		pos2 = 2 * c + 1;
	}

	/** Mirrored block coordinate along the mirror axis. */
	public int reflect(int c) {
		return pos2 - c - 1;
	}

	/** Mirrors a block position; returns a new {x, y, z}. */
	public int[] reflect(int x, int y, int z) {
		return switch (axis) {
			case X -> new int[] {reflect(x), y, z};
			case Z -> new int[] {x, y, reflect(z)};
			default -> new int[] {x, y, z};
		};
	}

	/** Plane position as shown to users, e.g. "120.5" (center of block 120) or "120". */
	public String describe() {
		if (!active()) {
			return "off";
		}
		String p = pos2 % 2 == 0 ? Integer.toString(pos2 / 2) : Double.toString(pos2 / 2.0);
		return axis + " = " + p;
	}

	/** Parses "120" or "120.5" into a doubled position. */
	public static int parsePos2(String s) {
		double v = Double.parseDouble(s.trim());
		return (int) Math.round(v * 2);
	}
}
