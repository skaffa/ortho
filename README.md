# Ortho

Client-side geometric stencils for Minecraft (Fabric 26.3). Ortho draws block-by-block outlines of
circles, ellipses, polygons, parabolic arches, spheres, ellipsoids, cylinders, cones, frustums,
domes and toruses in the world, so you can build curves by hand. It sends no packets and needs
nothing on the server.

## Use

1. Aim at a block and press **G**: the shape anchors in front of that face.
2. Press **J** to pick the shape, its size (widths in blocks, odd or even), hollow/filled, wall
   thickness, up axis, spin and tilt.
3. Build: **blue** = still to place, **green** = done, **orange** = a block inside a hollow 3D shape.

* **H** shows/hides the stencil.
* **Left Alt + scroll** moves the layer slice (in layer mode) or the anchor up and down.
* Layer mode (screen, `/ortho layer`, or a key you bind) shows one Y level at a time.
* Mirror (screen or `/ortho mirror x|z [pos]`): outlines where the mirror image of each block you
  place goes, and the counterpart of the block you aim at.

All keys are rebindable under Controls → Ortho. `/ortho info` shows a summary.

## Build

Needs JDK 25.

```
./gradlew build      # jar in build/libs, runs the JUnit tests
./gradlew runClient  # dev client
```

The shape math (`src/main/java/dev/ortho/core`) has no Minecraft imports and is tested with plain
JUnit. See [SPEC.md](SPEC.md) for the design.
