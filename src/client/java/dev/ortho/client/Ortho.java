package dev.ortho.client;

import dev.ortho.core.Mirror;
import dev.ortho.core.OrthoConfig;
import dev.ortho.core.Stencil;
import dev.ortho.core.VoxelSet;
import dev.ortho.core.Voxelizer;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Client state: config, current world placement and the generated voxels. Client thread only, except generation. */
public final class Ortho {
	public static final String MOD_ID = "ortho";
	public static final Logger LOG = LoggerFactory.getLogger("Ortho");
	/** All Ortho files live in their own folder: {@code config/ortho/}. */
	private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
	private static final Path FILE = DIR.resolve("config.json");
	/** Where older versions kept the config; moved into {@link #DIR} on first load. */
	private static final Path LEGACY_FILE = FabricLoader.getInstance().getConfigDir().resolve("ortho.json");

	public static OrthoConfig config = new OrthoConfig();
	/** Key of the world the player is in, or null when not in a world. */
	public static String worldKey;
	public static boolean visible = true;
	/** Layer mode: only this world Y is drawn; null = all layers. */
	public static Integer layer;

	/** Latest finished generation (drawn), and whether a newer one is running. */
	public static volatile VoxelSet voxels = VoxelSet.EMPTY;
	public static volatile String error;
	public static volatile boolean generating;

	private static final AtomicInteger GENERATION = new AtomicInteger();
	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Ortho voxelizer");
		t.setDaemon(true);
		t.setPriority(Thread.NORM_PRIORITY - 1);
		return t;
	});
	private static boolean dirty;
	private static long saveAt;

	private Ortho() {
	}

	public static void load() {
		migrateLegacyFile();
		config = OrthoConfig.load(FILE).clamp();
	}

	private static void migrateLegacyFile() {
		if (Files.exists(FILE) || !Files.isRegularFile(LEGACY_FILE)) {
			return;
		}
		try {
			Files.createDirectories(DIR);
			Files.move(LEGACY_FILE, FILE);
			LOG.info("Moved {} to {}", LEGACY_FILE, FILE);
		} catch (IOException e) {
			LOG.warn("Could not move {} to {}", LEGACY_FILE, FILE, e);
		}
	}

	/** Saves soon (coalesces bursts of changes, such as holding a + button). */
	public static void markDirty() {
		dirty = true;
		saveAt = System.currentTimeMillis() + 1500;
	}

	static void tickSave() {
		if (dirty && System.currentTimeMillis() >= saveAt) {
			saveNow();
		}
	}

	public static void saveNow() {
		dirty = false;
		try {
			config.save(FILE);
		} catch (IOException e) {
			LOG.warn("Could not save {}", FILE, e);
		}
	}

	public static Stencil stencil() {
		return config.stencil;
	}

	/** Placement for the current world, or null when not in a world. */
	public static OrthoConfig.WorldPlacement placement() {
		return worldKey == null ? null : config.world(worldKey);
	}

	public static Mirror mirror() {
		OrthoConfig.WorldPlacement p = placement();
		return p == null ? null : p.mirror;
	}

	/** True when the stencil is anchored in the dimension the player is in. */
	public static boolean anchoredHere() {
		OrthoConfig.WorldPlacement p = placement();
		Minecraft mc = Minecraft.getInstance();
		return p != null && p.anchored && mc.level != null
				&& mc.level.dimension().identifier().toString().equals(p.dimension);
	}

	public static void anchor(int x, int y, int z) {
		OrthoConfig.WorldPlacement p = placement();
		Minecraft mc = Minecraft.getInstance();
		if (p == null || mc.level == null) {
			return;
		}
		p.anchored = true;
		p.dimension = mc.level.dimension().identifier().toString();
		p.x = x;
		p.y = y;
		p.z = z;
		visible = true;
		markDirty();
		regenerate();
	}

	/** Called after any setting change: saves and rebuilds the voxels in the background. */
	public static void changed() {
		config.clamp();
		markDirty();
		regenerate();
	}

	public static void regenerate() {
		int id = GENERATION.incrementAndGet();
		if (!anchoredHere()) {
			voxels = VoxelSet.EMPTY;
			generating = false;
			error = null;
			return;
		}
		OrthoConfig.WorldPlacement p = placement();
		Stencil snapshot = config.stencil.copy();
		Voxelizer.Limits limits = config.limits();
		int x = p.x;
		int y = p.y;
		int z = p.z;
		generating = true;
		WORKER.execute(() -> {
			if (GENERATION.get() != id) {
				return;
			}
			try {
				VoxelSet v = Voxelizer.generate(snapshot, x, y, z, limits, () -> GENERATION.get() != id);
				if (GENERATION.get() == id) {
					voxels = v;
					error = null;
					generating = false;
				}
			} catch (Voxelizer.Aborted e) {
				if (GENERATION.get() == id) {
					error = e.getMessage();
					generating = false;
				}
			} catch (RuntimeException | OutOfMemoryError e) {
				LOG.error("Voxelizing {} failed", snapshot.describe(), e);
				if (GENERATION.get() == id) {
					error = "Generation failed: " + e;
					generating = false;
				}
			}
		});
	}

	/** Called when the player joins a world or changes dimension. */
	static void enterWorld(String key) {
		worldKey = key;
		layer = null;
		regenerate();
	}

	static void leaveWorld() {
		if (dirty) {
			saveNow();
		}
		worldKey = null;
		layer = null;
		GENERATION.incrementAndGet();
		voxels = VoxelSet.EMPTY;
	}

	public static void say(String msg) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.sendOverlayMessage(Component.literal("Ortho: " + msg).withStyle(ChatFormatting.AQUA));
		}
	}

	public static void chat(Component msg) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.sendSystemMessage(Component.literal("[Ortho] ").withStyle(ChatFormatting.AQUA).append(msg));
		}
	}
}
