package dev.ortho.core;

/**
 * Result of one voxelization: block positions as packed {x, y, z} triples, sorted by Y.
 *
 * @param shell blocks to build
 * @param interior blocks inside a hollow 3D shape (empty for planar or filled shapes)
 * @param interiorTruncated true when the interior was too large to keep
 * @param anchor the anchor block the set was generated for
 */
public record VoxelSet(int[] shell, int[] interior, boolean interiorTruncated, int[] anchor) {
	public static final VoxelSet EMPTY = new VoxelSet(new int[0], new int[0], false, new int[3]);

	public int size() {
		return shell.length / 3;
	}

	public int interiorSize() {
		return interior.length / 3;
	}

	/** Number of shell blocks on world layer {@code y}. */
	public int countOnLayer(int y) {
		int c = 0;
		for (int i = 1; i < shell.length; i += 3) {
			if (shell[i] == y) {
				c++;
			}
		}
		return c;
	}

	/** {minY, maxY} of the shell, or null when empty. */
	public int[] yRange() {
		if (shell.length == 0) {
			return null;
		}
		// sorted by Y
		return new int[] {shell[1], shell[shell.length - 2]};
	}

	public boolean contains(int x, int y, int z) {
		for (int i = 0; i < shell.length; i += 3) {
			if (shell[i] == x && shell[i + 1] == y && shell[i + 2] == z) {
				return true;
			}
		}
		return false;
	}
}
