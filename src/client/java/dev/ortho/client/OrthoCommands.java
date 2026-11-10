package dev.ortho.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import dev.ortho.core.Mirror;
import dev.ortho.core.OrthoConfig;
import dev.ortho.core.ShapeType;
import dev.ortho.core.Stencil;
import dev.ortho.core.VoxelSet;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Locale;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/** {@code /ortho}: everything the keys and screen do, for typing or macros. */
final class OrthoCommands {
	private OrthoCommands() {
	}

	static void register(CommandDispatcher<FabricClientCommandSource> d) {
		d.register(literal("ortho")
				.executes(c -> {
					OrthoClient.openScreenNextTick();
					return 1;
				})
				.then(literal("anchor")
						.executes(c -> {
							OrthoClient.anchorAtTarget(Minecraft.getInstance());
							return 1;
						})
						.then(argument("x", IntegerArgumentType.integer())
								.then(argument("y", IntegerArgumentType.integer())
										.then(argument("z", IntegerArgumentType.integer()).executes(c -> {
											int x = IntegerArgumentType.getInteger(c, "x");
											int y = IntegerArgumentType.getInteger(c, "y");
											int z = IntegerArgumentType.getInteger(c, "z");
											Ortho.anchor(x, y, z);
											return ok(c, "Anchored at " + x + " " + y + " " + z);
										})))))
				.then(literal("show").executes(c -> {
					Ortho.visible = true;
					return ok(c, "Stencil shown");
				}))
				.then(literal("hide").executes(c -> {
					Ortho.visible = false;
					return ok(c, "Stencil hidden");
				}))
				.then(literal("shape").then(argument("shape", StringArgumentType.word())
						.suggests((c, b) -> {
							for (ShapeType t : ShapeType.values()) {
								b.suggest(t.name().toLowerCase(Locale.ROOT));
							}
							return b.buildFuture();
						})
						.executes(c -> {
							String name = StringArgumentType.getString(c, "shape").toUpperCase(Locale.ROOT);
							try {
								Ortho.stencil().shape = ShapeType.valueOf(name);
							} catch (IllegalArgumentException e) {
								return fail(c, "Unknown shape: " + name.toLowerCase(Locale.ROOT));
							}
							Ortho.changed();
							return ok(c, Ortho.stencil().describe());
						})))
				.then(literal("size")
						.then(argument("x", IntegerArgumentType.integer(1, Stencil.MAX_SIZE)).executes(c -> size(c, 1))
								.then(argument("y", IntegerArgumentType.integer(1, Stencil.MAX_SIZE)).executes(c -> size(c, 2))
										.then(argument("z", IntegerArgumentType.integer(1, Stencil.MAX_SIZE)).executes(c -> size(c, 3))))))
				.then(literal("layer")
						.executes(c -> {
							OrthoClient.toggleLayer(Minecraft.getInstance());
							return 1;
						})
						.then(literal("off").executes(c -> {
							Ortho.layer = null;
							return ok(c, "Showing all layers");
						}))
						.then(argument("y", IntegerArgumentType.integer()).executes(c -> {
							Ortho.layer = IntegerArgumentType.getInteger(c, "y");
							return ok(c, "Layer Y=" + Ortho.layer + " (" + Ortho.voxels.countOnLayer(Ortho.layer) + " blocks)");
						})))
				.then(literal("mirror")
						.then(literal("off").executes(c -> {
							Mirror m = Ortho.mirror();
							if (m != null) {
								m.axis = Mirror.Axis.OFF;
								Ortho.markDirty();
							}
							return ok(c, "Mirror off");
						}))
						.then(mirrorAxis(Mirror.Axis.X))
						.then(mirrorAxis(Mirror.Axis.Z)))
				.then(literal("clearmarks").executes(c -> {
					int n = MirrorMarks.size();
					MirrorMarks.clear();
					return ok(c, "Forgot " + n + " placed blocks");
				}))
				.then(literal("info").executes(OrthoCommands::info)));
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<FabricClientCommandSource> mirrorAxis(Mirror.Axis axis) {
		String name = axis.name().toLowerCase(Locale.ROOT);
		return literal(name)
				.executes(c -> {
					Mirror m = Ortho.mirror();
					Minecraft mc = Minecraft.getInstance();
					if (m == null || mc.player == null) {
						return fail(c, "Not in a world");
					}
					m.centerOn(axis, axis == Mirror.Axis.X ? mc.player.blockPosition().getX() : mc.player.blockPosition().getZ());
					Ortho.markDirty();
					return ok(c, "Mirror " + m.describe() + " (through the block you stand on)");
				})
				.then(argument("pos", StringArgumentType.word()).executes(c -> {
					Mirror m = Ortho.mirror();
					if (m == null) {
						return fail(c, "Not in a world");
					}
					try {
						m.pos2 = Mirror.parsePos2(StringArgumentType.getString(c, "pos"));
					} catch (NumberFormatException e) {
						return fail(c, "Use a number like 120 (between blocks) or 120.5 (block center)");
					}
					m.axis = axis;
					Ortho.markDirty();
					return ok(c, "Mirror " + m.describe());
				}));
	}

	private static int size(CommandContext<FabricClientCommandSource> c, int args) {
		Stencil s = Ortho.stencil();
		int x = IntegerArgumentType.getInteger(c, "x");
		s.sizeX = x;
		if (args >= 2) {
			s.sizeY = IntegerArgumentType.getInteger(c, "y");
		}
		if (args >= 3) {
			s.sizeZ = IntegerArgumentType.getInteger(c, "z");
		}
		Ortho.changed();
		return ok(c, s.describe());
	}

	private static int info(CommandContext<FabricClientCommandSource> c) {
		Stencil s = Ortho.stencil();
		OrthoConfig.WorldPlacement p = Ortho.placement();
		VoxelSet v = Ortho.voxels;
		StringBuilder b = new StringBuilder(s.describe());
		if (p != null && p.anchored) {
			b.append("\nAnchor: ").append(p.x).append(' ').append(p.y).append(' ').append(p.z).append(" in ").append(p.dimension);
		} else {
			b.append("\nNot anchored (aim and press G)");
		}
		b.append("\nBlocks: ").append(v.size());
		int[] yr = v.yRange();
		if (yr != null) {
			b.append(" on Y ").append(yr[0]).append("..").append(yr[1]);
		}
		b.append("\nNearby: ").append(StencilRenderer.lastTodo).append(" to place, ").append(StencilRenderer.lastDone).append(" done");
		if (StencilRenderer.lastBlocked > 0) {
			b.append(", ").append(StencilRenderer.lastBlocked).append(" blocking the inside");
		}
		if (Ortho.layer != null) {
			b.append("\nLayer: Y=").append(Ortho.layer).append(" (").append(v.countOnLayer(Ortho.layer)).append(" blocks)");
		}
		Mirror m = Ortho.mirror();
		if (m != null && m.active()) {
			b.append("\nMirror: ").append(m.describe()).append(", ").append(MirrorMarks.size()).append(" placed blocks tracked");
		}
		if (Ortho.error != null) {
			b.append("\n").append(Ortho.error);
		}
		c.getSource().sendFeedback(Component.literal(b.toString()));
		return 1;
	}

	private static int ok(CommandContext<FabricClientCommandSource> c, String msg) {
		c.getSource().sendFeedback(Component.literal(msg).withStyle(ChatFormatting.AQUA));
		return 1;
	}

	private static int fail(CommandContext<FabricClientCommandSource> c, String msg) {
		c.getSource().sendError(Component.literal(msg));
		return 0;
	}
}
