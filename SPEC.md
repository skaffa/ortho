# Ortho — client-side geometric stencil overlay

Ortho draws block-by-block outlines of geometric shapes in the world, like a draftsman's ruler
for survival builders. It is 100% client-side: no packets, no server install, no block placement.

This is the revised spec. It started from a first draft; the changes are listed at the end.

## 1. Shapes

All sizes are **widths in blocks**, not radii. An odd width is centered on the anchor block; an even
width is centered on a block corner, so both odd and even circles are possible (a radius cannot
express a 16-wide circle).

| Shape | Kind | Parameters |
|---|---|---|
| Circle | planar | diameter, depth |
| Ellipse | planar | width, length, depth |
| Polygon | planar | diameter, sides (3–16), depth |
| Parabolic arch | planar (upright) | span, height, depth |
| Sphere | 3D | diameter |
| Ellipsoid | 3D | width, height, length |
| Cylinder | 3D | diameter, height, end caps |
| Cone | 3D | base diameter, height, base cap |
| Frustum | 3D | bottom diameter, top diameter, height, end caps |
| Dome | 3D | diameter, height, half or quarter, floor |
| Torus | 3D | outer diameter, tube diameter |

* Planar shapes lie in the local X/Z plane (flat on the ground with Up = Y), except the arch, which
  stands in the local X/Y plane. **Depth** extrudes a planar shape into a wall or slab.
* Base-anchored shapes (arch, cylinder, cone, frustum, dome) start at the anchor block and grow up.
  Other shapes are centered on the anchor.
* **Hollow** draws a shell of the given **thickness** (1–16); otherwise the shape is filled.
  A 1-thick shell is the thinnest shell without gaps (no holes you can see through face-on).
* Open ends: cylinders/cones/frustums have no caps unless "end caps" is on; domes have an open floor
  unless "floor" is on; the arch has no base line.

## 2. Placement

* **Anchor** — aim at a block and press `G`: the anchor becomes the block in front of the face you
  aim at (where a block would be placed). No target: the block at your feet.
* **Orientation** — Up axis (Y, X or Z) plus spin (around the up axis) and tilt, both in 15° steps.
  Steps that are not multiples of 90° give stair-stepped results; that is expected for voxels.
* **Nudge** — hold the Ortho modifier (`Left Alt`) and scroll: moves the layer slice when layer
  mode is on, otherwise moves the anchor up/down. The hotbar does not scroll while the modifier is held.
* **Layer mode** — show one world Y level only, to build layer by layer.
* **Mirror** — a mirror plane on X or Z (on a block center or between blocks). Ortho remembers the
  blocks you place and outlines their mirror positions: purple = still to place, orange = a
  different block is there. The block you aim at also shows its counterpart.
* The anchor and mirror are saved per world/server; shape settings are global.

## 3. Rendering and feedback

Drawn with the vanilla gizmo system, which the game itself uses for debug shapes; no custom render
pipeline. Colors:

* **Blue** — stencil block still to place (air or replaceable, like grass or water).
* **Green** — stencil block that already has a block (any block; a stencil has no material).
  Can be hidden.
* **Orange** — a block inside the hollow interior of a 3D shape (it blocks the inside).

Only blocks within the render distance (default 48) are drawn, nearest first, up to a cap
(default 4000), so large shapes never cost more than the cap.

## 4. Controls

| Key (rebindable) | Action |
|---|---|
| `G` | Anchor at target |
| `H` | Show/hide stencil |
| `J` | Open the Ortho screen |
| `Left Alt` + scroll | Nudge layer or anchor Y |
| (unbound) | Toggle layer mode |

Command: `/ortho` with `anchor`, `show`, `hide`, `layer`, `mirror`, `info`, `clearmarks`.
The Ortho screen holds every setting and is also reachable from Mod Menu.

## 5. Technical

* Fabric, Minecraft 26.3, Java 25. All shape math lives in `dev.ortho.core` with no Minecraft
  imports, so it is unit tested with plain JUnit and can be reused by a NeoForge port later.
* Voxelization runs on a background thread whenever a setting changes; the result is a set of int
  arrays. Each tick only the nearby part is checked against the world and drawn.
* Size limit 256 per axis and a grid limit, so a typo cannot freeze the game.

## Changes from the first draft

1. **Fabric only for now.** Doing Fabric and NeoForge at once doubles the work. The core is
   loader-independent so a NeoForge port (or a Stonecutter multi-loader setup) stays cheap.
2. **`WorldRenderEvents` does not exist in 26.x** (it became `LevelRenderEvents`), and a custom
   "Sodium/Iris compatible render pass" is not needed: the vanilla gizmo system already draws
   outlines and is compatible with them. "Anti-aliased" lines are not something a mod controls.
3. **Sizes are widths, not radii**, so even widths work (see §1).
4. **"Green = correctly placed" was not possible**: a stencil has no block material, so green means
   "a block is there". Orange was redefined as "a block in the hollow interior" (the draft's
   "protruding into stencil space" had no clear meaning for a shape with no material).
5. **Symmetry mirroring was underspecified** ("highlights symmetric block positions" of what?).
   It now mirrors the blocks you place and your aim target.
6. **Rotation**: "rotate freely in 15° steps" is kept as spin + tilt, with a note that non-90° steps
   stair-step.
7. **No key opened the UI** in the draft; `J` does now. `Alt + scroll` would also scroll the
   hotbar; Ortho now blocks that while the modifier is held.
8. **"Zero FPS impact" was not true as written**: the voxel cache was never the cost; drawing tens
   of thousands of outlines is. Render distance + nearest-first cap fixes that.
9. **"Anti-cheat safe"**: Ortho sends no packets, but some servers ban any overlay or "schematic"
   mod by rule. Users should check server rules; no technical claim can cover that.
10. **Name**: the Modrinth slug `ortho` is free, but "OrthoCamera" exists, which may cause confusion
    in search.
