package dev.ortho.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Remembers blocks the player places this session (client side only), so the mirror can show
 * where their counterparts go. A use-block click with a block item becomes a candidate; it is
 * recorded once a block shows up there within a few ticks.
 */
final class MirrorMarks {
	private static final int MAX = 4096;
	private static final LinkedHashSet<Long> MARKS = new LinkedHashSet<>();
	private static final List<long[]> PENDING = new ArrayList<>();
	private static long tick;

	private MirrorMarks() {
	}

	static Iterable<Long> all() {
		return MARKS;
	}

	static int size() {
		return MARKS.size();
	}

	static void clear() {
		MARKS.clear();
		PENDING.clear();
	}

	/** UseBlockCallback: runs on the client for the local player. Never changes the interaction. */
	static InteractionResult onUse(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (!level.isClientSide() || player != Minecraft.getInstance().player) {
			return InteractionResult.PASS;
		}
		var mirror = Ortho.mirror();
		if (mirror == null || !mirror.active() || !(player.getItemInHand(hand).getItem() instanceof BlockItem)) {
			return InteractionResult.PASS;
		}
		BlockPos clicked = hit.getBlockPos();
		BlockPos target = level.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(hit.getDirection());
		PENDING.add(new long[] {target.asLong(), tick + 10});
		return InteractionResult.PASS;
	}

	static void tick(Minecraft mc) {
		tick++;
		if (PENDING.isEmpty() || mc.level == null) {
			return;
		}
		Iterator<long[]> it = PENDING.iterator();
		while (it.hasNext()) {
			long[] p = it.next();
			BlockPos pos = BlockPos.of(p[0]);
			if (!mc.level.getBlockState(pos).isAir() && !mc.level.getBlockState(pos).canBeReplaced()) {
				MARKS.remove(p[0]);
				MARKS.add(p[0]);
				if (MARKS.size() > MAX) {
					Iterator<Long> oldest = MARKS.iterator();
					oldest.next();
					oldest.remove();
				}
				it.remove();
			} else if (tick > p[1]) {
				it.remove();
			}
		}
	}
}
