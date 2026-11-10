package dev.ortho.client;

import dev.ortho.core.Mirror;
import dev.ortho.core.OrthoConfig;
import dev.ortho.core.ShapeType;
import dev.ortho.core.Stencil;
import dev.ortho.core.VoxelSet;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** All Ortho settings on one screen: shape on the left, placement and display on the right. */
public final class OrthoScreen extends Screen {
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFFA8A8A8;
	private static final int ACCENT = 0xFF4FD8E0;
	private static final int BAD = 0xFFFF6666;
	private static final int ROW = 22;

	private final Screen parent;
	private final List<Label> labels = new ArrayList<>();
	private final List<Runnable> refreshers = new ArrayList<>();
	private int colW;
	private int leftX;
	private int rightX;
	private int topY;

	private record Label(String text, int x, int y, int color) {
	}

	public OrthoScreen(Screen parent) {
		super(Component.literal("Ortho"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		build();
	}

	private void build() {
		clearWidgets();
		labels.clear();
		refreshers.clear();
		colW = Math.min(210, (width - 36) / 2);
		leftX = width / 2 - colW - 8;
		rightX = width / 2 + 8;
		topY = 30;
		Stencil s = Ortho.stencil();

		// ---- shape column ----
		int y = topY;
		labels.add(new Label("Shape", leftX, y, ACCENT));
		y += 12;
		addRenderableWidget(Button.builder(Component.literal(s.shape.label + "  ▶"), b -> {
			ShapeType[] all = ShapeType.values();
			s.shape = all[(s.shape.ordinal() + 1) % all.length];
			Ortho.changed();
			build();
		}).bounds(leftX, y, colW, 20).tooltip(Tooltip.create(Component.literal("Click for the next shape"))).build());
		y += ROW;
		for (ShapeType.Param p : s.shape.params) {
			ShapeType.Param.Kind k = p.kind();
			if (k.isToggle()) {
				y = toggle(leftX, y, p.label(), () -> s.get(k) != 0, v -> {
					s.set(k, v ? 1 : 0);
					Ortho.changed();
				});
			} else {
				y = stepper(leftX, y, p.label(), () -> s.get(k), v -> {
					s.set(k, v);
					Ortho.changed();
				}, 1, Stencil.min(k), Stencil.max(k));
			}
		}
		y = toggle(leftX, y, "Hollow", () -> s.hollow, v -> {
			s.hollow = v;
			Ortho.changed();
			build();
		});
		if (s.hollow) {
			y = stepper(leftX, y, "Wall thickness", () -> s.thickness, v -> {
				s.thickness = v;
				Ortho.changed();
			}, 1, 1, Stencil.MAX_THICKNESS);
		}

		// ---- placement column ----
		y = topY;
		labels.add(new Label("Placement", rightX, y, ACCENT));
		y += 12;
		addRenderableWidget(Button.builder(Component.literal("Anchor at my feet"), b -> {
			if (minecraft.player != null) {
				var pos = minecraft.player.blockPosition();
				Ortho.anchor(pos.getX(), pos.getY(), pos.getZ());
			}
		}).bounds(rightX, y, colW, 20).tooltip(Tooltip.create(Component.literal(
				"Or aim at a block in the world and press the anchor key (G)"))).build());
		y += ROW;
		y = cycle(rightX, y, "Up axis", List.of(Stencil.Axis.values()), () -> s.up, v -> {
			s.up = v;
			Ortho.changed();
		});
		y = stepper(rightX, y, "Spin °", () -> s.spin, v -> {
			s.spin = v;
			Ortho.changed();
		}, 15, -360, 360);
		y = stepper(rightX, y, "Tilt °", () -> s.tilt, v -> {
			s.tilt = v;
			Ortho.changed();
		}, 15, -360, 360);
		y = layerRow(rightX, y);
		y = mirrorRow(rightX, y);

		labels.add(new Label("Display", rightX, y + 2, ACCENT));
		y += 14;
		OrthoConfig cfg = Ortho.config;
		y = toggle(rightX, y, "Show finished", () -> cfg.showDone, v -> {
			cfg.showDone = v;
			Ortho.markDirty();
		});
		y = toggle(rightX, y, "Warn inside", () -> cfg.warnInterior, v -> {
			cfg.warnInterior = v;
			Ortho.markDirty();
		});
		y = stepper(rightX, y, "Distance", () -> cfg.renderDistance, v -> {
			cfg.renderDistance = v;
			cfg.clamp();
			Ortho.markDirty();
		}, 8, 8, 256);

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(width / 2 - 50, height - 26, 100, 20).build());
	}

	private int toggle(int x, int y, String label, java.util.function.BooleanSupplier get, java.util.function.Consumer<Boolean> set) {
		labels.add(new Label(label, x, y + 6, GRAY));
		int bw = colW / 2;
		addRenderableWidget(Button.builder(onOff(get.getAsBoolean()), b -> {
			set.accept(!get.getAsBoolean());
			b.setMessage(onOff(get.getAsBoolean()));
		}).bounds(x + colW - bw, y, bw, 20).build());
		return y + ROW;
	}

	private static Component onOff(boolean v) {
		return Component.literal(v ? "On" : "Off");
	}

	private <T> int cycle(int x, int y, String label, List<T> options, java.util.function.Supplier<T> get, java.util.function.Consumer<T> set) {
		labels.add(new Label(label, x, y + 6, GRAY));
		int bw = colW / 2;
		addRenderableWidget(Button.builder(Component.literal(String.valueOf(get.get())), b -> {
			T next = options.get((options.indexOf(get.get()) + 1) % options.size());
			set.accept(next);
			b.setMessage(Component.literal(String.valueOf(get.get())));
		}).bounds(x + colW - bw, y, bw, 20).build());
		return y + ROW;
	}

	/** Label, then [-] [number] [+]. */
	private int stepper(int x, int y, String label, IntSupplier get, IntConsumer set, int step, int min, int max) {
		labels.add(new Label(label, x, y + 6, GRAY));
		int bw = colW / 2;
		int bx = x + colW - bw;
		EditBox box = new EditBox(font, bx + 21, y + 1, bw - 42, 18, Component.literal(label));
		box.setMaxLength(5);
		box.setValue(Integer.toString(get.getAsInt()));
		box.setResponder(text -> {
			try {
				int v = Integer.parseInt(text.trim());
				if (v >= min && v <= max && v != get.getAsInt()) {
					set.accept(v);
				}
			} catch (NumberFormatException ignored) {
				// keep typing
			}
		});
		Runnable refresh = () -> {
			String now = Integer.toString(get.getAsInt());
			if (!box.isFocused() && !box.getValue().equals(now)) {
				box.setValue(now);
			}
		};
		refreshers.add(refresh);
		addRenderableWidget(Button.builder(Component.literal("-"), b -> {
			set.accept(Math.max(min, get.getAsInt() - step));
			box.setValue(Integer.toString(get.getAsInt()));
		}).bounds(bx, y, 20, 20).build());
		addRenderableWidget(box);
		addRenderableWidget(Button.builder(Component.literal("+"), b -> {
			set.accept(Math.min(max, get.getAsInt() + step));
			box.setValue(Integer.toString(get.getAsInt()));
		}).bounds(bx + bw - 20, y, 20, 20).build());
		return y + ROW;
	}

	private int layerRow(int x, int y) {
		labels.add(new Label("Layer", x, y + 6, GRAY));
		int bw = colW / 2;
		int bx = x + colW - bw;
		Button main = Button.builder(layerText(), b -> {
			if (Ortho.layer != null) {
				Ortho.layer = null;
			} else if (minecraft.player != null) {
				Ortho.layer = minecraft.player.blockPosition().getY();
			}
			b.setMessage(layerText());
		}).bounds(bx + 21, y, bw - 42, 20).tooltip(Tooltip.create(Component.literal(
				"Show one Y level only. Hold Left Alt and scroll in the world to move it."))).build();
		addRenderableWidget(Button.builder(Component.literal("-"), b -> {
			if (Ortho.layer != null) {
				Ortho.layer--;
				main.setMessage(layerText());
			}
		}).bounds(bx, y, 20, 20).build());
		addRenderableWidget(main);
		addRenderableWidget(Button.builder(Component.literal("+"), b -> {
			if (Ortho.layer != null) {
				Ortho.layer++;
				main.setMessage(layerText());
			}
		}).bounds(bx + bw - 20, y, 20, 20).build());
		refreshers.add(() -> main.setMessage(layerText()));
		return y + ROW;
	}

	private static Component layerText() {
		return Component.literal(Ortho.layer == null ? "All" : "Y " + Ortho.layer);
	}

	private int mirrorRow(int x, int y) {
		labels.add(new Label("Mirror", x, y + 6, GRAY));
		int bw = colW / 2;
		int bx = x + colW - bw;
		Mirror m = Ortho.mirror();
		Button main = Button.builder(mirrorText(), b -> {
			Mirror mm = Ortho.mirror();
			if (mm == null || minecraft.player == null) {
				return;
			}
			var pos = minecraft.player.blockPosition();
			switch (mm.axis) {
				case OFF -> mm.centerOn(Mirror.Axis.X, pos.getX());
				case X -> mm.centerOn(Mirror.Axis.Z, pos.getZ());
				case Z -> mm.axis = Mirror.Axis.OFF;
			}
			Ortho.markDirty();
			b.setMessage(mirrorText());
		}).bounds(bx, y, bw, 20).tooltip(Tooltip.create(Component.literal(
				"Off -> X -> Z. The plane goes through the block you stand on; use /ortho mirror x|z <pos> for an exact spot."))).build();
		main.active = m != null;
		addRenderableWidget(main);
		refreshers.add(() -> main.setMessage(mirrorText()));
		return y + ROW;
	}

	private static Component mirrorText() {
		Mirror m = Ortho.mirror();
		return Component.literal(m == null ? "Not in world" : m.describe());
	}

	@Override
	public void tick() {
		super.tick();
		refreshers.forEach(Runnable::run);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		super.extractRenderState(g, mx, my, delta);
		g.centeredText(font, title, width / 2, 8, WHITE);
		for (Label l : labels) {
			g.text(font, Component.literal(l.text()), l.x(), l.y(), l.color());
		}
		g.centeredText(font, Component.literal(status()), width / 2, height - 40, Ortho.error != null ? BAD : GRAY);
	}

	private static String status() {
		if (Ortho.error != null) {
			return Ortho.error;
		}
		if (!Ortho.anchoredHere()) {
			return "Not anchored here: aim at a block and press G, or use \"Anchor at my feet\".";
		}
		if (Ortho.generating) {
			return "Generating...";
		}
		VoxelSet v = Ortho.voxels;
		StringBuilder b = new StringBuilder(v.size() + " blocks");
		int[] yr = v.yRange();
		if (yr != null) {
			b.append(", Y ").append(yr[0]).append("..").append(yr[1]);
		}
		if (Ortho.layer != null) {
			b.append(", ").append(v.countOnLayer(Ortho.layer)).append(" on layer");
		}
		if (!Ortho.visible) {
			b.append(" (hidden: press H)");
		}
		return b.toString();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		Ortho.saveNow();
		minecraft.gui.setScreen(parent);
	}
}
