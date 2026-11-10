package dev.ortho.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VoxelizerTest {
	private static final int AX = 100;
	private static final int AY = 64;
	private static final int AZ = -40;

	private static Stencil stencil(ShapeType shape) {
		Stencil s = new Stencil();
		s.shape = shape;
		return s;
	}

	private static Set<Long> set(int[] packed) {
		Set<Long> out = new HashSet<>();
		for (int i = 0; i < packed.length; i += 3) {
			out.add(key(packed[i], packed[i + 1], packed[i + 2]));
		}
		return out;
	}

	private static long key(int x, int y, int z) {
		return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
	}

	private static int[] range(int[] packed, int axis) {
		int lo = Integer.MAX_VALUE;
		int hi = Integer.MIN_VALUE;
		for (int i = axis; i < packed.length; i += 3) {
			lo = Math.min(lo, packed[i]);
			hi = Math.max(hi, packed[i]);
		}
		return new int[] {lo, hi};
	}

	/**
	 * Flood fills from {@code start} through non-shell cells (6-connected) within the box
	 * [min - 1, max + 1]; returns true if it escapes the shape's bounding box.
	 */
	private static boolean leaks(int[] shell, int sx, int sy, int sz, boolean planarY) {
		Set<Long> walls = set(shell);
		int[] rx = range(shell, 0);
		int[] ry = range(shell, 1);
		int[] rz = range(shell, 2);
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		Set<Long> seen = new HashSet<>();
		queue.add(new int[] {sx, sy, sz});
		seen.add(key(sx, sy, sz));
		int[][] dirs = planarY
				? new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}}
				: new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		while (!queue.isEmpty()) {
			int[] p = queue.poll();
			if (p[0] < rx[0] || p[0] > rx[1] || p[1] < ry[0] || p[1] > ry[1] || p[2] < rz[0] || p[2] > rz[1]) {
				return true;
			}
			for (int[] d : dirs) {
				int x = p[0] + d[0];
				int y = p[1] + d[1];
				int z = p[2] + d[2];
				long k = key(x, y, z);
				if (!walls.contains(k) && seen.add(k)) {
					queue.add(new int[] {x, y, z});
				}
			}
		}
		return false;
	}

	@Test
	void oddCircleIsCenteredOnAnchorAndClosed() {
		Stencil s = stencil(ShapeType.CIRCLE);
		s.sizeX = 17;
		VoxelSet v = Voxelizer.generate(s, AX, AY, AZ);
		assertArrayEquals(new int[] {AY, AY}, range(v.shell(), 1), "flat on the anchor layer");
		assertArrayEquals(new int[] {AX - 8, AX + 8}, range(v.shell(), 0));
		assertArrayEquals(new int[] {AZ - 8, AZ + 8}, range(v.shell(), 2));
		Set<Long> blocks = set(v.shell());
		for (int i = 0; i < v.shell().length; i += 3) {
			int dx = v.shell()[i] - AX;
			int dz = v.shell()[i + 2] - AZ;
			assertTrue(blocks.contains(key(AX - dx, AY, AZ + dz)), "mirror symmetric in X");
			assertTrue(blocks.contains(key(AX + dz, AY, AZ + dx)), "symmetric across the diagonal");
		}
		assertFalse(blocks.contains(key(AX, AY, AZ)), "hollow");
		assertFalse(leaks(v.shell(), AX, AY, AZ, true), "ring has no gaps");
	}

	@Test
	void thinRingHasNoRedundantBlocks() {
		Stencil s = stencil(ShapeType.CIRCLE);
		s.sizeX = 31;
		int[] shell = Voxelizer.generate(s, 0, 0, 0).shell();
		Set<Long> blocks = set(shell);
		// a 1-thick ring never contains a full 2x2 square of blocks
		for (int i = 0; i < shell.length; i += 3) {
			int x = shell[i];
			int z = shell[i + 2];
			boolean square = blocks.contains(key(x + 1, 0, z)) && blocks.contains(key(x, 0, z + 1))
					&& blocks.contains(key(x + 1, 0, z + 1));
			assertFalse(square, "2x2 block at " + x + "," + z);
		}
	}

	@Test
	void evenCircleCentersOnBlockCorner() {
		Stencil s = stencil(ShapeType.CIRCLE);
		s.sizeX = 16;
		VoxelSet v = Voxelizer.generate(s, 0, 0, 0);
		assertArrayEquals(new int[] {-8, 7}, range(v.shell(), 0));
		assertArrayEquals(new int[] {-8, 7}, range(v.shell(), 2));
		Set<Long> blocks = set(v.shell());
		for (int i = 0; i < v.shell().length; i += 3) {
			assertTrue(blocks.contains(key(-1 - v.shell()[i], 0, v.shell()[i + 2])), "symmetric around x = 0 boundary");
		}
		assertFalse(leaks(v.shell(), 0, 0, 0, true));
	}

	@Test
	void sphereIsClosedAndSymmetric() {
		Stencil s = stencil(ShapeType.SPHERE);
		s.sizeX = 17;
		VoxelSet v = Voxelizer.generate(s, AX, AY, AZ);
		assertArrayEquals(new int[] {AY - 8, AY + 8}, range(v.shell(), 1));
		assertFalse(leaks(v.shell(), AX, AY, AZ, false), "sphere surface is closed");
		Set<Long> blocks = set(v.shell());
		for (int i = 0; i < v.shell().length; i += 3) {
			int dx = v.shell()[i] - AX;
			int dy = v.shell()[i + 1] - AY;
			int dz = v.shell()[i + 2] - AZ;
			assertTrue(blocks.contains(key(AX + dy, AY + dz, AZ + dx)), "symmetric under axis swap");
		}
		assertTrue(v.interiorSize() > 0);
		Set<Long> interior = set(v.interior());
		interior.retainAll(blocks);
		assertTrue(interior.isEmpty(), "interior and shell do not overlap");
	}

	@Test
	void filledSphereVolumeMatchesFormula() {
		Stencil s = stencil(ShapeType.SPHERE);
		s.sizeX = 33;
		s.hollow = false;
		VoxelSet v = Voxelizer.generate(s, 0, 0, 0);
		double expected = 4.0 / 3 * Math.PI * Math.pow(16.5, 3);
		assertEquals(expected, v.size(), expected * 0.03);
		assertEquals(0, v.interiorSize());
	}

	@Test
	void thickerShellContainsThinnerShell() {
		Stencil s = stencil(ShapeType.SPHERE);
		s.sizeX = 25;
		Set<Long> thin = set(Voxelizer.generate(s, 0, 0, 0).shell());
		s.thickness = 3;
		VoxelSet thick = Voxelizer.generate(s, 0, 0, 0);
		Set<Long> thickSet = set(thick.shell());
		assertTrue(thickSet.containsAll(thin));
		assertTrue(thickSet.size() > thin.size() * 2);
		assertTrue(thickSet.contains(key(0, 12 - 2, 0)), "third layer below the top");
		assertFalse(thickSet.contains(key(0, 12 - 3, 0)), "fourth layer is interior");
	}

	@Test
	void cylinderCapsAreOptional() {
		Stencil s = stencil(ShapeType.CYLINDER);
		s.sizeX = 9;
		s.sizeY = 5;
		VoxelSet open = Voxelizer.generate(s, 0, 10, 0);
		assertArrayEquals(new int[] {10, 14}, range(open.shell(), 1), "grows up from the anchor");
		assertFalse(set(open.shell()).contains(key(0, 10, 0)), "no bottom cap");
		assertFalse(set(open.shell()).contains(key(0, 14, 0)), "no top cap");
		assertEquals(open.countOnLayer(10), open.countOnLayer(12), "walls only: same ring on every layer");
		s.caps = true;
		Set<Long> capped = set(Voxelizer.generate(s, 0, 10, 0).shell());
		assertTrue(capped.contains(key(0, 10, 0)));
		assertTrue(capped.contains(key(0, 14, 0)));
	}

	@Test
	void domeHasOpenFloorAndQuarterOption() {
		Stencil s = stencil(ShapeType.DOME);
		s.sizeX = 17;
		s.sizeY = 9;
		VoxelSet v = Voxelizer.generate(s, 0, 0, 0);
		assertArrayEquals(new int[] {0, 8}, range(v.shell(), 1));
		assertFalse(set(v.shell()).contains(key(0, 0, 0)), "open floor");
		assertTrue(set(v.shell()).contains(key(0, 8, 0)), "top");
		s.quarter = true;
		VoxelSet q = Voxelizer.generate(s, 0, 0, 0);
		assertTrue(range(q.shell(), 2)[0] >= 0, "quarter dome only on +Z");
		assertFalse(set(q.shell()).contains(key(0, 4, 0)), "cut face is open");
	}

	@Test
	void upAxisXStandsCircleUpright() {
		Stencil s = stencil(ShapeType.CIRCLE);
		s.sizeX = 11;
		s.up = Stencil.Axis.X;
		VoxelSet v = Voxelizer.generate(s, 5, 70, 5);
		assertArrayEquals(new int[] {5, 5}, range(v.shell(), 0));
		assertArrayEquals(new int[] {65, 75}, range(v.shell(), 1));
		assertArrayEquals(new int[] {0, 10}, range(v.shell(), 2));
	}

	@Test
	void spinNinetySwapsEllipseAxes() {
		Stencil a = stencil(ShapeType.ELLIPSE);
		a.sizeX = 9;
		a.sizeZ = 25;
		a.spin = 90;
		Stencil b = stencil(ShapeType.ELLIPSE);
		b.sizeX = 25;
		b.sizeZ = 9;
		assertEquals(set(Voxelizer.generate(b, 0, 0, 0).shell()), set(Voxelizer.generate(a, 0, 0, 0).shell()));
	}

	@Test
	void archHasNoBaseLine() {
		Stencil s = stencil(ShapeType.ARCH);
		s.sizeX = 15;
		s.sizeY = 10;
		VoxelSet v = Voxelizer.generate(s, 0, 0, 0);
		assertArrayEquals(new int[] {0, 9}, range(v.shell(), 1));
		assertArrayEquals(new int[] {0, 0}, range(v.shell(), 2), "one block deep");
		assertFalse(set(v.shell()).contains(key(0, 0, 0)), "no base under the arch");
		assertTrue(set(v.shell()).contains(key(0, 9, 0)), "keystone");
		assertTrue(set(v.shell()).contains(key(-7, 0, 0)) && set(v.shell()).contains(key(7, 0, 0)), "feet");
	}

	@Test
	void torusHasAHole() {
		Stencil s = stencil(ShapeType.TORUS);
		s.sizeX = 21;
		s.tube = 5;
		VoxelSet v = Voxelizer.generate(s, 0, 0, 0);
		assertFalse(set(v.shell()).contains(key(0, 0, 0)));
		assertArrayEquals(new int[] {-2, 2}, range(v.shell(), 1));
		assertArrayEquals(new int[] {-10, 10}, range(v.shell(), 0));
	}

	@Test
	void polygonAndConeGenerate() {
		Stencil p = stencil(ShapeType.POLYGON);
		p.sizeX = 21;
		p.sides = 6;
		VoxelSet hex = Voxelizer.generate(p, 0, 0, 0);
		assertTrue(hex.size() > 30);
		assertFalse(leaks(hex.shell(), 0, 0, 0, true));
		Stencil c = stencil(ShapeType.CONE);
		c.sizeX = 15;
		c.sizeY = 12;
		VoxelSet cone = Voxelizer.generate(c, 0, 0, 0);
		assertTrue(cone.countOnLayer(0) > cone.countOnLayer(10), "narrows toward the tip");
	}

	@Test
	void tiltedShapesStillGenerate() {
		Stencil s = stencil(ShapeType.CIRCLE);
		s.sizeX = 21;
		s.tilt = 45;
		VoxelSet v = Voxelizer.generate(s, 0, 0, 0);
		assertTrue(v.size() > 40);
		int[] ry = range(v.shell(), 1);
		assertTrue(ry[1] - ry[0] >= 12, "tilted circle spans many layers");
	}

	@Test
	void tiltedFilledDiskIsFaceConnected() {
		for (int tilt : new int[] {15, 30, 45, 60}) {
			Stencil s = stencil(ShapeType.CIRCLE);
			s.sizeX = 21;
			s.hollow = false;
			s.tilt = tilt;
			s.spin = 30;
			int[] packed = Voxelizer.generate(s, 0, 0, 0).shell();
			Set<Long> all = set(packed);
			ArrayDeque<int[]> queue = new ArrayDeque<>();
			Set<Long> seen = new HashSet<>();
			queue.add(new int[] {packed[0], packed[1], packed[2]});
			seen.add(key(packed[0], packed[1], packed[2]));
			int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
			while (!queue.isEmpty()) {
				int[] p = queue.poll();
				for (int[] d : dirs) {
					long k = key(p[0] + d[0], p[1] + d[1], p[2] + d[2]);
					if (all.contains(k) && seen.add(k)) {
						queue.add(new int[] {p[0] + d[0], p[1] + d[1], p[2] + d[2]});
					}
				}
			}
			assertEquals(all.size(), seen.size(), "tilt " + tilt + ": disk falls apart into diagonal-only pieces");
			assertTrue(all.size() < 21 * 21 * 2, "tilt " + tilt + ": slab is not overly thick");
		}
	}

	@Test
	void hugeRotatedShapeIsRejected() {
		Stencil s = stencil(ShapeType.ELLIPSOID);
		s.sizeX = 256;
		s.sizeY = 256;
		s.sizeZ = 256;
		s.spin = 45;
		s.tilt = 45;
		assertThrows(Voxelizer.Aborted.class, () -> Voxelizer.generate(s, 0, 0, 0));
	}

	@Test
	void cancellationStopsGeneration() {
		Stencil s = stencil(ShapeType.SPHERE);
		s.sizeX = 101;
		assertThrows(Voxelizer.Aborted.class, () -> Voxelizer.generate(s, 0, 0, 0, () -> true));
	}

	@Test
	void everyShapeGeneratesWithDefaults() {
		for (ShapeType t : ShapeType.values()) {
			VoxelSet v = Voxelizer.generate(stencil(t), 0, 0, 0);
			assertTrue(v.size() > 0, t + " is empty");
		}
	}
}
