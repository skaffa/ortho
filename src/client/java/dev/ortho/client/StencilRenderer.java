package dev.ortho.client;

import dev.ortho.core.Mirror;
import dev.ortho.core.OrthoConfig;
import dev.ortho.core.VoxelSet;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

/**
 * Runs every client tick: draws the nearby part of the stencil with the vanilla gizmo system
 * (collected per tick, like Ghost Mason's boxes), plus the anchor, mirror plane and mirror marks.
 */
final class StencilRenderer {
	static final int TODO_STROKE = 0xE04FA8FF;
	static final int TODO_FILL = 0x204FA8FF;
	static final int DONE_STROKE = 0xB055DD77;
	static final int DONE_FILL = 0x1855DD77;
	static final int BLOCKED_STROKE = 0xF0FF9020;
	static final int BLOCKED_FILL = 0x40FF9020;
	static final int ANCHOR = 0xFFFFD040;
	static final int MIRROR_STROKE = 0xE0C060FF;
	static final int MIRROR_FILL = 0x16C060FF;
	private static final double INSET = 0.04;

	/** Live counts for the screen and /ortho info. */
	static int lastTodo;
	static int lastDone;
	static int lastBlocked;

	private static long[] scratch = new long[1024];

	private StencilRenderer() {
	}

	static void tick(Minecraft mc) {
		Level level = mc.level;
		if (level == null || mc.player == null || !Ortho.visible) {
			return;
		}
		OrthoConfig cfg = Ortho.config;
		Vec3 eye = mc.player.getEyePosition();
		double maxSq = (double) cfg.renderDistance * cfg.renderDistance;

		if (Ortho.anchoredHere()) {
			OrthoConfig.WorldPlacement p = Ortho.placement();
			Gizmos.cuboid(new AABB(p.x + 0.3, p.y + 0.3, p.z + 0.3, p.x + 0.7, p.y + 0.7, p.z + 0.7),
					GizmoStyle.strokeAndFill(ANCHOR, 2.0f, 0x60FFD040));

			VoxelSet v = Ortho.voxels;
			int budget = cfg.maxDrawn;
			int[] counts = drawSet(level, v.shell(), eye, maxSq, budget, false, cfg.showDone);
			lastTodo = counts[0];
			lastDone = counts[1];
			lastBlocked = 0;
			if (cfg.warnInterior && v.interior().length > 0) {
				int left = Math.max(200, budget - counts[2]);
				lastBlocked = drawSet(level, v.interior(), eye, maxSq, left, true, false)[3];
			}
		}

		Mirror mirror = Ortho.mirror();
		if (mirror != null && mirror.active()) {
			drawMirror(mc, level, mirror, eye, maxSq);
		}
	}

	/**
	 * Draws the in-range part of a packed position list, nearest first up to {@code cap}.
	 * Returns {todo, done, drawn, blocked} counts among in-range blocks.
	 */
	private static int[] drawSet(Level level, int[] packed, Vec3 eye, double maxSq, int cap, boolean interior, boolean showDone) {
		Integer layer = Ortho.layer;
		int n = 0;
		double ex = eye.x;
		double ey = eye.y;
		double ez = eye.z;
		// positions are sorted by Y: only scan the layers within reach
		double reach = Math.sqrt(maxSq);
		int yLo = layer != null ? layer : (int) Math.floor(ey - reach);
		int yHi = layer != null ? layer : (int) Math.ceil(ey + reach);
		// gather (distance, index) pairs of in-range blocks, packed into longs for a cheap sort
		for (int i = firstAtOrAbove(packed, yLo) * 3; i < packed.length; i += 3) {
			int y = packed[i + 1];
			if (y > yHi) {
				break;
			}
			double dx = packed[i] + 0.5 - ex;
			double dy = y + 0.5 - ey;
			double dz = packed[i + 2] + 0.5 - ez;
			double d = dx * dx + dy * dy + dz * dz;
			if (d > maxSq) {
				continue;
			}
			if (n == scratch.length) {
				scratch = Arrays.copyOf(scratch, n * 2);
			}
			scratch[n++] = ((long) Float.floatToIntBits((float) d) << 32) | (i / 3);
		}
		if (n > cap) {
			// positive floats sort like their bit patterns
			Arrays.sort(scratch, 0, n);
		}
		int todo = 0;
		int done = 0;
		int drawn = 0;
		int blocked = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int k = 0; k < n; k++) {
			int i = (int) scratch[k] * 3;
			pos.set(packed[i], packed[i + 1], packed[i + 2]);
			BlockState state = level.getBlockState(pos);
			boolean empty = state.isAir() || state.canBeReplaced();
			if (interior) {
				if (!empty) {
					blocked++;
					if (drawn < cap) {
						drawn++;
						Gizmos.cuboid(inset(pos, -0.01), GizmoStyle.strokeAndFill(BLOCKED_STROKE, 2.0f, BLOCKED_FILL));
					}
				}
				continue;
			}
			if (empty) {
				todo++;
				if (drawn < cap) {
					drawn++;
					Gizmos.cuboid(inset(pos, INSET), GizmoStyle.strokeAndFill(TODO_STROKE, 1.5f, TODO_FILL));
				}
			} else {
				done++;
				if (showDone && drawn < cap) {
					drawn++;
					Gizmos.cuboid(inset(pos, -0.01), GizmoStyle.strokeAndFill(DONE_STROKE, 1.0f, DONE_FILL));
				}
			}
		}
		return new int[] {todo, done, drawn, blocked};
	}

	private static void drawMirror(Minecraft mc, Level level, Mirror mirror, Vec3 eye, double maxSq) {
		// a translucent pane through the plane, centered on the player
		double plane = mirror.pos2 / 2.0;
		double r = 24;
		double y0 = eye.y - 12;
		double y1 = eye.y + 12;
		AABB pane = mirror.axis == Mirror.Axis.X
				? new AABB(plane - 0.02, y0, eye.z - r, plane + 0.02, y1, eye.z + r)
				: new AABB(eye.x - r, y0, plane - 0.02, eye.x + r, y1, plane + 0.02);
		Gizmos.cuboid(pane, GizmoStyle.strokeAndFill(MIRROR_STROKE, 1.5f, MIRROR_FILL));

		BlockPos.MutableBlockPos a = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos b = new BlockPos.MutableBlockPos();
		int drawn = 0;
		for (long packed : MirrorMarks.all()) {
			if (drawn >= 2000) {
				break;
			}
			a.set(BlockPos.getX(packed), BlockPos.getY(packed), BlockPos.getZ(packed));
			int[] m = mirror.reflect(a.getX(), a.getY(), a.getZ());
			b.set(m[0], m[1], m[2]);
			if (b.equals(a) || b.distToCenterSqr(eye) > maxSq) {
				continue;
			}
			drawn += drawMirrorPair(level, a, b) ? 1 : 0;
		}
		// the block under the crosshair always shows its counterpart
		HitResult hit = mc.hitResult;
		if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
			BlockPos t = bh.getBlockPos();
			int[] m = mirror.reflect(t.getX(), t.getY(), t.getZ());
			b.set(m[0], m[1], m[2]);
			if (!b.equals(t)) {
				Gizmos.cuboid(inset(b, -0.02), GizmoStyle.stroke(MIRROR_STROKE, 2.5f));
			}
		}
	}

	/** Draws the counterpart of a placed block; returns true if anything was drawn. */
	private static boolean drawMirrorPair(Level level, BlockPos placed, BlockPos counterpart) {
		BlockState source = level.getBlockState(placed);
		if (source.isAir()) {
			return false;
		}
		BlockState there = level.getBlockState(counterpart);
		if (there.isAir() || there.canBeReplaced()) {
			Gizmos.cuboid(inset(counterpart, INSET), GizmoStyle.strokeAndFill(MIRROR_STROKE, 1.5f, 0x30C060FF));
			return true;
		}
		if (there.getBlock() != source.getBlock()) {
			Gizmos.cuboid(inset(counterpart, -0.01), GizmoStyle.strokeAndFill(BLOCKED_STROKE, 2.0f, BLOCKED_FILL));
			return true;
		}
		return false;
	}

	/** Index (in triples) of the first position with y >= {@code y}. */
	private static int firstAtOrAbove(int[] packed, int y) {
		int lo = 0;
		int hi = packed.length / 3;
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (packed[mid * 3 + 1] < y) {
				lo = mid + 1;
			} else {
				hi = mid;
			}
		}
		return lo;
	}

	private static AABB inset(BlockPos p, double in) {
		return new AABB(p.getX() + in, p.getY() + in, p.getZ() + in, p.getX() + 1 - in, p.getY() + 1 - in, p.getZ() + 1 - in);
	}
}
