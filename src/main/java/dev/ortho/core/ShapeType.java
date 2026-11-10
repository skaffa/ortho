package dev.ortho.core;

import java.util.List;

/**
 * The shapes Ortho can draw. Each shape builds a {@link Geometry} in its local frame: local Y is the
 * shape's "up", base-anchored shapes start at local y = 0, and the rest are centered on the origin.
 */
public enum ShapeType {
	CIRCLE("Circle", true, List.of(Param.of(Param.Kind.SIZE_X, "Diameter"), Param.of(Param.Kind.DEPTH, "Depth"))),
	ELLIPSE("Ellipse", true, List.of(Param.of(Param.Kind.SIZE_X, "Width"), Param.of(Param.Kind.SIZE_Z, "Length"),
			Param.of(Param.Kind.DEPTH, "Depth"))),
	POLYGON("Polygon", true, List.of(Param.of(Param.Kind.SIZE_X, "Diameter"), Param.of(Param.Kind.SIDES, "Sides"),
			Param.of(Param.Kind.DEPTH, "Depth"))),
	ARCH("Parabolic arch", true, List.of(Param.of(Param.Kind.SIZE_X, "Span"), Param.of(Param.Kind.SIZE_Y, "Height"),
			Param.of(Param.Kind.DEPTH, "Depth"))),
	SPHERE("Sphere", false, List.of(Param.of(Param.Kind.SIZE_X, "Diameter"))),
	ELLIPSOID("Ellipsoid", false, List.of(Param.of(Param.Kind.SIZE_X, "Width"), Param.of(Param.Kind.SIZE_Y, "Height"),
			Param.of(Param.Kind.SIZE_Z, "Length"))),
	CYLINDER("Cylinder", false, List.of(Param.of(Param.Kind.SIZE_X, "Diameter"), Param.of(Param.Kind.SIZE_Y, "Height"),
			Param.of(Param.Kind.CAPS, "End caps"))),
	CONE("Cone", false, List.of(Param.of(Param.Kind.SIZE_X, "Base diameter"), Param.of(Param.Kind.SIZE_Y, "Height"),
			Param.of(Param.Kind.CAPS, "Base cap"))),
	FRUSTUM("Frustum", false, List.of(Param.of(Param.Kind.SIZE_X, "Bottom diameter"), Param.of(Param.Kind.TOP, "Top diameter"),
			Param.of(Param.Kind.SIZE_Y, "Height"), Param.of(Param.Kind.CAPS, "End caps"))),
	DOME("Dome", false, List.of(Param.of(Param.Kind.SIZE_X, "Diameter"), Param.of(Param.Kind.SIZE_Y, "Height"),
			Param.of(Param.Kind.QUARTER, "Quarter dome"), Param.of(Param.Kind.CAPS, "Floor"))),
	TORUS("Torus", false, List.of(Param.of(Param.Kind.SIZE_X, "Outer diameter"), Param.of(Param.Kind.TUBE, "Tube diameter")));

	public final String label;
	/** Planar shapes are a 2D profile extruded by {@code depth}. */
	public final boolean planar;
	public final List<Param> params;

	ShapeType(String label, boolean planar, List<Param> params) {
		this.label = label;
		this.planar = planar;
		this.params = params;
	}

	/** One editable setting of a shape, with the label this shape uses for it. */
	public record Param(Kind kind, String label) {
		public enum Kind {
			SIZE_X, SIZE_Y, SIZE_Z, DEPTH, SIDES, TUBE, TOP, CAPS, QUARTER;

			public boolean isToggle() {
				return this == CAPS || this == QUARTER;
			}
		}

		static Param of(Kind kind, String label) {
			return new Param(kind, label);
		}
	}

	/** Local-frame description of a shape: bounding box and two membership tests. */
	public interface Geometry {
		/** {minX, minY, minZ, maxX, maxY, maxZ} in local coordinates. */
		double[] box();

		/** True if the local point is inside the shape. */
		boolean inside(double x, double y, double z);

		/**
		 * Like {@link #inside}, but also true in regions that should not create a surface: past an
		 * open end (no caps, open floor, arch base) or along the extrusion axis of planar shapes.
		 * Always true where {@code inside} is.
		 */
		boolean open(double x, double y, double z);
	}

	public Geometry geometry(Stencil s) {
		return geometry(s, 1);
	}

	/**
	 * @param slab widens the extrusion of planar shapes. A tilted 1-block slab sampled at block
	 *     centers falls apart into diagonal-only steps; scaling its half-depth by the sum of the
	 *     normal's absolute world components gives a gap-free (face-connected) plane. 1 when the
	 *     normal is axis-aligned.
	 */
	public Geometry geometry(Stencil s, double slab) {
		double sx = s.sizeX;
		double sy = s.sizeY;
		double sz = s.sizeZ;
		double d = s.depth * slab;
		boolean caps = s.caps;
		return switch (this) {
			case CIRCLE -> ellipse(sx, sx, d);
			case ELLIPSE -> ellipse(sx, sz, d);
			case POLYGON -> polygon(sx, s.sides, d);
			case ARCH -> arch(sx, sy, d);
			case SPHERE -> ellipsoid(sx, sx, sx);
			case ELLIPSOID -> ellipsoid(sx, sy, sz);
			case CYLINDER -> frustum(sx, sx, sy, caps);
			case CONE -> frustum(sx, 0, sy, caps);
			case FRUSTUM -> frustum(sx, s.top, sy, caps);
			case DOME -> dome(sx, sy, s.quarter, caps);
			case TORUS -> torus(sx, Math.min(s.tube, sx));
		};
	}

	private static Geometry ellipse(double w, double l, double depth) {
		double a = w / 2;
		double c = l / 2;
		double h = depth / 2;
		return new Geometry() {
			public double[] box() {
				return new double[] {-a, -h, -c, a, h, c};
			}

			public boolean inside(double x, double y, double z) {
				return inSlab(y, h) && open(x, y, z);
			}

			public boolean open(double x, double y, double z) {
				return sq(x / a) + sq(z / c) <= 1;
			}
		};
	}

	private static Geometry polygon(double diameter, int sides, double depth) {
		double r = diameter / 2;
		int n = Math.max(3, Math.min(16, sides));
		double sector = 2 * Math.PI / n;
		double apothem = r * Math.cos(Math.PI / n);
		double h = depth / 2;
		return new Geometry() {
			public double[] box() {
				return new double[] {-r, -h, -r, r, h, r};
			}

			public boolean inside(double x, double y, double z) {
				return inSlab(y, h) && open(x, y, z);
			}

			public boolean open(double x, double y, double z) {
				double dist = Math.hypot(x, z);
				if (dist == 0) {
					return true;
				}
				// angle from the middle of the edge this point faces (vertices sit at angle 0)
				double t = Math.atan2(z, x);
				double phi = (t % sector + sector) % sector - sector / 2;
				return dist * Math.cos(phi) <= apothem;
			}
		};
	}

	private static Geometry arch(double span, double height, double depth) {
		double half = span / 2;
		double h = depth / 2;
		return new Geometry() {
			public double[] box() {
				return new double[] {-half, 0, -h, half, height, h};
			}

			public boolean inside(double x, double y, double z) {
				return inSlab(z, h) && profile(x, y);
			}

			public boolean open(double x, double y, double z) {
				// no base line: below the base counts as open
				return profile(x, y) || (y < 0 && Math.abs(x) <= half);
			}

			private boolean profile(double x, double y) {
				return y >= 0 && y <= height * (1 - sq(x / half));
			}
		};
	}

	private static Geometry ellipsoid(double w, double hgt, double l) {
		double a = w / 2;
		double b = hgt / 2;
		double c = l / 2;
		return new Geometry() {
			public double[] box() {
				return new double[] {-a, -b, -c, a, b, c};
			}

			public boolean inside(double x, double y, double z) {
				return sq(x / a) + sq(y / b) + sq(z / c) <= 1;
			}

			public boolean open(double x, double y, double z) {
				return inside(x, y, z);
			}
		};
	}

	/** Cylinder (top == bottom), cone (top == 0) and frustum; base at y = 0. */
	private static Geometry frustum(double bottom, double top, double height, boolean caps) {
		double rb = bottom / 2;
		double rt = top / 2;
		return new Geometry() {
			public double[] box() {
				double r = Math.max(rb, rt);
				return new double[] {-r, 0, -r, r, height, r};
			}

			public boolean inside(double x, double y, double z) {
				if (y < 0 || y > height) {
					return false;
				}
				double r = rb + (rt - rb) * (y / height);
				return Math.hypot(x, z) <= r;
			}

			public boolean open(double x, double y, double z) {
				if (inside(x, y, z)) {
					return true;
				}
				if (caps) {
					return false;
				}
				double dist = Math.hypot(x, z);
				return (y < 0 && dist <= rb) || (y > height && dist <= rt);
			}
		};
	}

	private static Geometry dome(double diameter, double height, boolean quarter, boolean floor) {
		double a = diameter / 2;
		double b = height;
		return new Geometry() {
			public double[] box() {
				return new double[] {-a, 0, quarter ? 0 : -a, a, b, a};
			}

			public boolean inside(double x, double y, double z) {
				return y >= 0 && (!quarter || z >= 0) && shell(x, y, z);
			}

			public boolean open(double x, double y, double z) {
				if (floor) {
					// closed floor; a quarter dome still has an open cut face
					return inside(x, y, z) || (quarter && y >= 0 && shell(x, y, z));
				}
				return shell(x, Math.max(y, 0), z);
			}

			private boolean shell(double x, double y, double z) {
				return sq(x / a) + sq(y / b) + sq(z / a) <= 1;
			}
		};
	}

	private static Geometry torus(double outer, double tube) {
		double r = tube / 2;
		double major = (outer - tube) / 2;
		double o = outer / 2;
		return new Geometry() {
			public double[] box() {
				return new double[] {-o, -r, -o, o, r, o};
			}

			public boolean inside(double x, double y, double z) {
				return sq(Math.hypot(x, z) - major) + sq(y) <= r * r;
			}

			public boolean open(double x, double y, double z) {
				return inside(x, y, z);
			}
		};
	}

	/** Half-open so a slab that is a whole number of blocks thick never picks up an extra layer on a tie. */
	private static boolean inSlab(double v, double h) {
		return v >= -h - 1e-9 && v < h - 1e-9;
	}

	/** Local axis planar shapes are extruded along: Y, except Z for the upright arch. */
	public int depthAxis() {
		return this == ARCH ? 2 : 1;
	}

	private static double sq(double v) {
		return v * v;
	}
}
