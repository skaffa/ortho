package dev.ortho.core;

import java.util.BitSet;
import java.util.function.BooleanSupplier;

/**
 * Turns a {@link Stencil} at an anchor block into world block positions.
 *
 * <p>Every block whose center lies inside the shape is "inside". With hollow on, the shell is the
 * inside blocks within {@code thickness} face-steps of a block that is not open (see
 * {@link ShapeType.Geometry#open}); the rest of the inside is the interior. A 1-thick shell is the
 * thinnest one with no face-on gaps.
 */
public final class Voxelizer {
	/** Largest grid (cells) one generation may scan. */
	public static final long MAX_CELLS = 48_000_000L;
	/** Interior blocks kept for the "blocked interior" check; larger interiors are dropped. */
	public static final int MAX_INTERIOR = 2_000_000;

	private Voxelizer() {
	}

	/** Thrown when the shape is too large or the job was cancelled. */
	public static final class Aborted extends RuntimeException {
		public Aborted(String msg) {
			super(msg, null, false, false);
		}
	}

	public static VoxelSet generate(Stencil s, int ax, int ay, int az) {
		return generate(s, ax, ay, az, () -> false);
	}

	public static VoxelSet generate(Stencil s, int ax, int ay, int az, BooleanSupplier cancelled) {
		s = s.copy().clamp();
		Orientation rot = Orientation.of(s.up, s.spin, s.tilt);
		double[] m = rot.m;
		double slab = 1;
		if (s.shape.planar) {
			int c = s.shape.depthAxis();
			slab = Math.abs(m[c]) + Math.abs(m[3 + c]) + Math.abs(m[6 + c]);
		}
		ShapeType.Geometry g = s.shape.geometry(s, slab);
		double[] box = g.box();

		// world-space bounds of the local box, relative to the origin
		double[] wmin = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
		double[] wmax = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
		for (int i = 0; i < 8; i++) {
			double[] w = rot.apply(box[(i & 1) == 0 ? 0 : 3], box[(i & 2) == 0 ? 1 : 4], box[(i & 4) == 0 ? 2 : 5]);
			for (int k = 0; k < 3; k++) {
				wmin[k] = Math.min(wmin[k], w[k]);
				wmax[k] = Math.max(wmax[k], w[k]);
			}
		}
		// Shift the origin inside the anchor block so the shape's box lands on block boundaries:
		// odd widths center on the anchor block, even widths on its lower corner, bases on its floor.
		double[] origin = new double[3];
		int[] anchor = {ax, ay, az};
		for (int k = 0; k < 3; k++) {
			double half = Math.rint(wmin[k] * 2) / 2;
			double off = -half - Math.floor(-half);
			origin[k] = anchor[k] + off;
		}

		int pad = (s.hollow ? s.thickness : 0) + 1;
		int x0 = (int) Math.floor(origin[0] + wmin[0]) - pad;
		int y0 = (int) Math.floor(origin[1] + wmin[1]) - pad;
		int z0 = (int) Math.floor(origin[2] + wmin[2]) - pad;
		int nx = (int) Math.ceil(origin[0] + wmax[0]) + pad - x0;
		int ny = (int) Math.ceil(origin[1] + wmax[1]) + pad - y0;
		int nz = (int) Math.ceil(origin[2] + wmax[2]) + pad - z0;
		long cells = (long) nx * ny * nz;
		if (cells > MAX_CELLS) {
			throw new Aborted("Shape too large (" + cells / 1_000_000 + "M cells to scan, limit "
					+ MAX_CELLS / 1_000_000 + "M). Make it smaller or remove the tilt/spin.");
		}
		int n = (int) cells;
		int sxy = nx;
		int sxyz = nx * nz;
		// index = (y * nz + z) * nx + x: layers first, so output comes out sorted by Y
		BitSet inside = new BitSet(n);
		BitSet open = new BitSet(n);
		for (int y = 0; y < ny; y++) {
			if (cancelled.getAsBoolean()) {
				throw new Aborted("cancelled");
			}
			double dy = y0 + y + 0.5 - origin[1];
			for (int z = 0; z < nz; z++) {
				double dz = z0 + z + 0.5 - origin[2];
				int row = (y * nz + z) * nx;
				for (int x = 0; x < nx; x++) {
					double dx = x0 + x + 0.5 - origin[0];
					// world -> local is the transpose
					double lx = m[0] * dx + m[3] * dy + m[6] * dz;
					double ly = m[1] * dx + m[4] * dy + m[7] * dz;
					double lz = m[2] * dx + m[5] * dy + m[8] * dz;
					if (g.inside(lx, ly, lz)) {
						inside.set(row + x);
						open.set(row + x);
					} else if (g.open(lx, ly, lz)) {
						open.set(row + x);
					}
				}
			}
		}

		BitSet shell;
		if (!s.hollow) {
			shell = inside;
		} else {
			shell = new BitSet(n);
			int[] frontier = new int[256];
			int fn = 0;
			for (int i = inside.nextSetBit(0); i >= 0; i = inside.nextSetBit(i + 1)) {
				// grid padding guarantees all six neighbours exist
				if (!open.get(i - 1) || !open.get(i + 1) || !open.get(i - sxy) || !open.get(i + sxy)
						|| !open.get(i - sxyz) || !open.get(i + sxyz)) {
					shell.set(i);
					if (fn == frontier.length) {
						frontier = java.util.Arrays.copyOf(frontier, fn * 2);
					}
					frontier[fn++] = i;
				}
			}
			int[] offsets = {-1, 1, -sxy, sxy, -sxyz, sxyz};
			for (int layer = 2; layer <= s.thickness && fn > 0; layer++) {
				if (cancelled.getAsBoolean()) {
					throw new Aborted("cancelled");
				}
				int[] next = new int[Math.max(16, fn * 2)];
				int nn = 0;
				for (int f = 0; f < fn; f++) {
					for (int o : offsets) {
						int j = frontier[f] + o;
						if (inside.get(j) && !shell.get(j)) {
							shell.set(j);
							if (nn == next.length) {
								next = java.util.Arrays.copyOf(next, nn * 2);
							}
							next[nn++] = j;
						}
					}
				}
				frontier = next;
				fn = nn;
			}
		}

		int[] shellOut = collect(shell, nx, nz, x0, y0, z0, Integer.MAX_VALUE);
		int[] interiorOut = new int[0];
		boolean interiorTruncated = false;
		if (s.hollow && !s.shape.planar) {
			BitSet interior = (BitSet) inside.clone();
			interior.andNot(shell);
			int count = interior.cardinality();
			if (count <= MAX_INTERIOR) {
				interiorOut = collect(interior, nx, nz, x0, y0, z0, count);
			} else {
				interiorTruncated = true;
			}
		}
		return new VoxelSet(shellOut, interiorOut, interiorTruncated, new int[] {ax, ay, az});
	}

	private static int[] collect(BitSet bits, int nx, int nz, int x0, int y0, int z0, int limit) {
		int count = Math.min(bits.cardinality(), limit);
		int[] out = new int[count * 3];
		int k = 0;
		for (int i = bits.nextSetBit(0); i >= 0 && k < out.length; i = bits.nextSetBit(i + 1)) {
			int x = i % nx;
			int rest = i / nx;
			int z = rest % nz;
			int y = rest / nz;
			out[k++] = x0 + x;
			out[k++] = y0 + y;
			out[k++] = z0 + z;
		}
		return out;
	}
}
