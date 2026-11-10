"""Generates the Ortho icon as 32x32 pixel art, matching the Ghost Mason icon style.

Style rules (from the Ghost Mason icon): 32x32 grid scaled up with nearest neighbour,
dark rounded-square badge (#10161C) with 4-px stepped corners, a soft radial glow
behind the subject, a block character with a cute face, and cream 4-point sparkles.

Subject: the Ghost Mason style cube in Ortho's stencil blue, framed by a voxel circle ring
(a real 1-thick block circle). The lower-left arc of the ring is green (built), the rest blue
(still to place), matching the in-game colors.

Usage: python tools/make_icon.py  -> media/icon.png (512), media/icon-128.png (128)
"""
import math
import os
import sys

from PIL import Image

N = 32
BG = (16, 22, 28, 255)
GLOW = (24, 44, 72)            # glow tint at the centre of the badge

# cube (Ortho's stencil blue)
TOP = (170, 214, 255, 255)
RIM = (225, 242, 255, 255)
LEFT = (79, 168, 255, 255)     # StencilRenderer.TODO_STROKE
RIGHT = (50, 126, 214, 255)
EDGE = (26, 74, 150, 255)
EYE = (10, 24, 40, 255)
EYE_HI = (235, 245, 255, 255)
# voxel circle ring around the cube
RING = (120, 190, 255, 255)
RING_SHADE = (70, 140, 225, 255)
DONE = (85, 221, 119, 255)     # StencilRenderer.DONE_STROKE
DONE_SHADE = (52, 168, 86, 255)
SPARK = (255, 236, 140)

RING_D = 13                    # ring diameter in 2-px blocks


def blend(dst, src):
    """Alpha-composite src (r,g,b,a) over dst (r,g,b,a)."""
    sa = src[3] / 255
    da = dst[3] / 255
    oa = sa + da * (1 - sa)
    if oa == 0:
        return (0, 0, 0, 0)
    c = tuple(round((src[i] * sa + dst[i] * da * (1 - sa)) / oa) for i in range(3))
    return c + (round(oa * 255),)


def corner_cut(x, y):
    """Stepped rounded corners: 4,3,2,1 transparent pixels on rows 0..3 (mirrored)."""
    steps = [4, 3, 2, 1]
    for yy, xx in ((y, x), (N - 1 - y, x), (y, N - 1 - x), (N - 1 - y, N - 1 - x)):
        if yy < 4 and xx < steps[yy]:
            return True
    return False


def main(out_dir):
    px = [[(0, 0, 0, 0)] * N for _ in range(N)]

    def put(x, y, c):
        if 0 <= x < N and 0 <= y < N and not corner_cut(x, y):
            px[y][x] = blend(px[y][x], c) if c[3] < 255 else c

    # 1. badge + radial glow
    for y in range(N):
        for x in range(N):
            if corner_cut(x, y):
                continue
            d = math.hypot(x + 0.5 - 16, y + 0.5 - 17) / 14
            t = max(0.0, 1 - d) ** 1.6
            c = tuple(round(BG[i] + (GLOW[i] - BG[i]) * t) for i in range(3))
            px[y][x] = c + (255,)

    # 2. voxel circle ring: the 1-thick shell of a 13-block circle, 2x2 px per block
    r = RING_D / 2
    inside = {(i, j) for i in range(RING_D) for j in range(RING_D)
              if (i + 0.5 - r) ** 2 + (j + 0.5 - r) ** 2 <= r * r}
    ring = [(i, j) for (i, j) in inside
            if any(n not in inside for n in ((i + 1, j), (i - 1, j), (i, j + 1), (i, j - 1)))]
    off = (N - RING_D * 2) // 2
    for i, j in ring:
        # lower-left arc is "built" (green), the rest is still to place (blue)
        ang = math.degrees(math.atan2(j + 0.5 - r, i + 0.5 - r))  # y grows down
        done = 95 <= ang <= 200
        light, shade = (DONE, DONE_SHADE) if done else (RING, RING_SHADE)
        x0, y0 = off + i * 2, off + j * 2
        put(x0, y0, light)
        put(x0 + 1, y0, light)
        put(x0, y0 + 1, light)
        put(x0 + 1, y0 + 1, shade)

    # 3. isometric cube with a face, like the Ghost Mason cube
    xc, yc, W, H, F = 16, 12, 8, 4, 8
    for y in range(N):
        for x in range(N):
            fx, fy = x + 0.5, y + 0.5
            if abs(fx - xc) / W + abs(fy - yc) / H <= 1.0:
                put(x, y, TOP)
                continue
            if xc - W <= fx < xc:
                top_edge = yc + (fx - (xc - W)) / 2
                if top_edge <= fy < top_edge + F:
                    put(x, y, LEFT)
            elif xc <= fx < xc + W:
                top_edge = yc + ((xc + W) - fx) / 2
                if top_edge <= fy < top_edge + F:
                    put(x, y, RIGHT)
    for x in range(xc - W, xc + W):
        fx = x + 0.5
        top = yc + ((fx - (xc - W)) if fx < xc else ((xc + W) - fx)) / 2
        put(x, math.floor(top - 0.01), RIM)
    for y in range(yc + H, yc + H + F):
        put(16, y, EDGE)
    for ex, ey in ((10, 17), (13, 19)):
        for dy in range(3):
            for dx in range(2):
                put(ex + dx, ey + dy, EYE)
        put(ex, ey, EYE_HI)

    # 4. cream sparkles (Ghost Mason brand detail)
    def sparkle(x, y, strong=True):
        put(x, y, SPARK + (255,))
        a = 200 if strong else 140
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            put(x + dx, y + dy, SPARK + (a,))

    sparkle(28, 4)
    sparkle(3, 28, strong=False)
    sparkle(28, 28, strong=False)

    img = Image.new("RGBA", (N, N))
    img.putdata([c for row in px for c in row])
    os.makedirs(out_dir, exist_ok=True)
    img.resize((512, 512), Image.NEAREST).save(os.path.join(out_dir, "icon.png"))
    img.resize((128, 128), Image.NEAREST).save(os.path.join(out_dir, "icon-128.png"))
    print("wrote", out_dir)


if __name__ == "__main__":
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(root, "media"))
