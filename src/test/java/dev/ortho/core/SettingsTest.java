package dev.ortho.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SettingsTest {
	@Test
	void mirrorReflectsAroundCenterAndBoundary() {
		Mirror m = new Mirror();
		m.centerOn(Mirror.Axis.X, 120);
		assertEquals(120, m.reflect(120));
		assertEquals(125, m.reflect(115));
		assertEquals("X = 120.5", m.describe());
		m.pos2 = 240;
		assertEquals(120, m.reflect(119));
		assertEquals("X = 120", m.describe());
		m.pos2 = -1;
		assertEquals("X = -0.5", m.describe());
		assertEquals(-2, m.reflect(0), "plane at -0.5 is the center of block -1");
		assertEquals(241, Mirror.parsePos2("120.5"));
		m.axis = Mirror.Axis.Z;
		m.pos2 = 0;
		assertArrayEquals(new int[] {5, 6, -8}, m.reflect(5, 6, 7));
	}

	@Test
	void clampFixesOutOfRangeValues() {
		Stencil s = new Stencil();
		s.sizeX = 9999;
		s.sides = 1;
		s.thickness = 0;
		s.spin = 400;
		s.tilt = -20;
		s.shape = null;
		s.clamp();
		assertEquals(Voxelizer.Limits.DEFAULT_SIZE, s.sizeX);
		assertEquals(3, s.sides);
		assertEquals(1, s.thickness);
		assertEquals(45, s.spin);
		assertEquals(345, s.tilt);
		assertEquals(ShapeType.CIRCLE, s.shape);
	}

	@Test
	void configRoundTripsAndSurvivesBrokenFile(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("ortho").resolve("config.json");
		OrthoConfig c = new OrthoConfig();
		c.stencil.shape = ShapeType.DOME;
		c.stencil.sizeX = 31;
		OrthoConfig.WorldPlacement w = c.world("sp:Test");
		w.anchored = true;
		w.x = 4;
		w.mirror.centerOn(Mirror.Axis.Z, 7);
		c.save(file);
		OrthoConfig back = OrthoConfig.load(file);
		assertEquals(ShapeType.DOME, back.stencil.shape);
		assertEquals(31, back.stencil.sizeX);
		assertEquals(4, back.world("sp:Test").x);
		assertEquals(Mirror.Axis.Z, back.world("sp:Test").mirror.axis);

		Files.writeString(file, "{ not json");
		OrthoConfig fresh = OrthoConfig.load(file);
		assertEquals(ShapeType.CIRCLE, fresh.stencil.shape);
		assertTrue(Files.exists(dir.resolve("ortho").resolve("config.json.broken")));
	}

	@Test
	void describeNamesTheShape() {
		Stencil s = new Stencil();
		s.shape = ShapeType.ELLIPSOID;
		s.sizeX = 5;
		s.sizeY = 7;
		s.sizeZ = 9;
		assertEquals("Ellipsoid 5x7x9, hollow", s.describe());
	}
}
