package dev.ortho.core;

/** Shape settings: what to draw and how it is oriented. Saved as JSON, so fields stay public and plain. */
public final class Stencil {
	public static final int MAX_SIZE = 256;
	public static final int MAX_THICKNESS = 16;

	public ShapeType shape = ShapeType.CIRCLE;
	/** Width (local X), height (local Y) and length (local Z) in blocks; shapes use what they need. */
	public int sizeX = 17;
	public int sizeY = 9;
	public int sizeZ = 25;
	/** Extrusion of planar shapes. */
	public int depth = 1;
	public int sides = 6;
	/** Torus tube diameter. */
	public int tube = 5;
	/** Frustum top diameter. */
	public int top = 7;
	/** End caps (cylinder, cone, frustum) or floor (dome). */
	public boolean caps = false;
	public boolean quarter = false;
	public boolean hollow = true;
	public int thickness = 1;

	public Axis up = Axis.Y;
	/** Rotation around the up axis, degrees, multiple of 15. */
	public int spin = 0;
	/** Tilt around the local X axis, degrees, multiple of 15. */
	public int tilt = 0;

	public enum Axis {
		Y, X, Z
	}

	public int get(ShapeType.Param.Kind k) {
		return switch (k) {
			case SIZE_X -> sizeX;
			case SIZE_Y -> sizeY;
			case SIZE_Z -> sizeZ;
			case DEPTH -> depth;
			case SIDES -> sides;
			case TUBE -> tube;
			case TOP -> top;
			case CAPS -> caps ? 1 : 0;
			case QUARTER -> quarter ? 1 : 0;
		};
	}

	public void set(ShapeType.Param.Kind k, int v) {
		switch (k) {
			case SIZE_X -> sizeX = v;
			case SIZE_Y -> sizeY = v;
			case SIZE_Z -> sizeZ = v;
			case DEPTH -> depth = v;
			case SIDES -> sides = v;
			case TUBE -> tube = v;
			case TOP -> top = v;
			case CAPS -> caps = v != 0;
			case QUARTER -> quarter = v != 0;
		}
		clamp();
	}

	public static int min(ShapeType.Param.Kind k) {
		return switch (k) {
			case SIDES -> 3;
			case TOP -> 0;
			case CAPS, QUARTER -> 0;
			default -> 1;
		};
	}

	public static int max(ShapeType.Param.Kind k) {
		return switch (k) {
			case SIDES -> 16;
			case DEPTH -> 64;
			case CAPS, QUARTER -> 1;
			default -> MAX_SIZE;
		};
	}

	/** Brings every value into range (also fixes hand-edited or old config files). */
	public Stencil clamp() {
		if (shape == null) {
			shape = ShapeType.CIRCLE;
		}
		if (up == null) {
			up = Axis.Y;
		}
		for (ShapeType.Param.Kind k : ShapeType.Param.Kind.values()) {
			if (!k.isToggle()) {
				int v = get(k);
				int c = Math.max(min(k), Math.min(max(k), v));
				if (c != v) {
					setRaw(k, c);
				}
			}
		}
		thickness = Math.max(1, Math.min(MAX_THICKNESS, thickness));
		spin = snap(spin);
		tilt = snap(tilt);
		return this;
	}

	private void setRaw(ShapeType.Param.Kind k, int v) {
		switch (k) {
			case SIZE_X -> sizeX = v;
			case SIZE_Y -> sizeY = v;
			case SIZE_Z -> sizeZ = v;
			case DEPTH -> depth = v;
			case SIDES -> sides = v;
			case TUBE -> tube = v;
			case TOP -> top = v;
			default -> {
			}
		}
	}

	/** Rounds to a 15 degree step in [0, 360). */
	static int snap(int degrees) {
		int s = Math.round(degrees / 15f) * 15;
		return Math.floorMod(s, 360);
	}

	public Stencil copy() {
		Stencil c = new Stencil();
		c.shape = shape;
		c.sizeX = sizeX;
		c.sizeY = sizeY;
		c.sizeZ = sizeZ;
		c.depth = depth;
		c.sides = sides;
		c.tube = tube;
		c.top = top;
		c.caps = caps;
		c.quarter = quarter;
		c.hollow = hollow;
		c.thickness = thickness;
		c.up = up;
		c.spin = spin;
		c.tilt = tilt;
		return c;
	}

	/** Short description like "Sphere 17, hollow". */
	public String describe() {
		StringBuilder b = new StringBuilder(shape.label);
		for (ShapeType.Param p : shape.params) {
			if (p.kind() == ShapeType.Param.Kind.SIZE_X || p.kind() == ShapeType.Param.Kind.SIZE_Y
					|| p.kind() == ShapeType.Param.Kind.SIZE_Z) {
				b.append(b.charAt(b.length() - 1) == ' ' || !Character.isDigit(b.charAt(b.length() - 1)) ? " " : "x").append(get(p.kind()));
			}
		}
		b.append(hollow ? ", hollow" : ", filled");
		return b.toString();
	}
}
