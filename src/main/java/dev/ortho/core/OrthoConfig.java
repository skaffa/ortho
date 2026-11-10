package dev.ortho.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Everything Ortho saves in {@code config/ortho.json}. */
public final class OrthoConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public Stencil stencil = new Stencil();
	/** Blocks further away than this are not drawn. */
	public int renderDistance = 48;
	/** At most this many outlines are drawn per frame (nearest first). */
	public int maxDrawn = 4000;
	/** Draw stencil blocks that already have a block in green. */
	public boolean showDone = true;
	/** Largest shape width/height/length in blocks ({@code /ortho limit size}). */
	public int maxSize = Voxelizer.Limits.DEFAULT_SIZE;
	/** Largest number of stencil blocks ({@code /ortho limit blocks}). */
	public int maxBlocks = Voxelizer.Limits.DEFAULT_BLOCKS;
	/** Mark blocks inside hollow 3D shapes in orange. */
	public boolean warnInterior = true;
	/** Anchor and mirror per world or server, keyed by {@code WorldPlacement.key}. */
	public Map<String, WorldPlacement> worlds = new LinkedHashMap<>();

	/** Where the stencil sits in one world. */
	public static final class WorldPlacement {
		public String dimension;
		public boolean anchored;
		public int x;
		public int y;
		public int z;
		public Mirror mirror = new Mirror();
	}

	public OrthoConfig clamp() {
		if (stencil == null) {
			stencil = new Stencil();
		}
		maxSize = Math.max(16, Math.min(Voxelizer.Limits.HARD_MAX_SIZE, maxSize));
		maxBlocks = Math.max(10_000, Math.min(Voxelizer.Limits.HARD_MAX_BLOCKS, maxBlocks));
		stencil.clamp(maxSize);
		renderDistance = Math.max(8, Math.min(256, renderDistance));
		maxDrawn = Math.max(100, Math.min(50_000, maxDrawn));
		if (worlds == null) {
			worlds = new LinkedHashMap<>();
		}
		worlds.values().removeIf(w -> w == null);
		for (WorldPlacement w : worlds.values()) {
			if (w.mirror == null) {
				w.mirror = new Mirror();
			}
			if (w.mirror.axis == null) {
				w.mirror.axis = Mirror.Axis.OFF;
			}
		}
		return this;
	}

	public Voxelizer.Limits limits() {
		return Voxelizer.Limits.of(maxSize, maxBlocks);
	}

	public WorldPlacement world(String key) {
		return worlds.computeIfAbsent(key, k -> new WorldPlacement());
	}

	public static OrthoConfig load(Path file) {
		if (!Files.exists(file)) {
			return new OrthoConfig();
		}
		try {
			OrthoConfig c = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), OrthoConfig.class);
			return c == null ? new OrthoConfig() : c.clamp();
		} catch (IOException | JsonParseException e) {
			// keep the broken file for the user and start fresh
			try {
				Files.copy(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// nothing more to do
			}
			return new OrthoConfig();
		}
	}

	public void save(Path file) throws IOException {
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(tmp, GSON.toJson(this), StandardCharsets.UTF_8);
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}
}
