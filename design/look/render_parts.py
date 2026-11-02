#!/usr/bin/env python3
"""
The app's metal frames, redrawn at high resolution (ADR-057).

The rulebook prints its page banner, the cards' title plate, the text boxes and the plate brackets far too small for
a phone: the banner is 827 x 45 in the PDF, its medallion about 60 x 45. Nothing sharper is published, so these parts
are drawn here after the printed ones, as height fields lit like metal, and bundled through the import
(LookAssets.kt, official/SOURCES.md). The printed geometry, colours and motifs are kept; nothing here is game data.

    python design/look/render_parts.py [out_dir] [part ...]     # needs numpy and Pillow; deterministic (fixed seeds)
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).parent / "parts"
SS = 3  # Supersampling: every part is drawn at 3x and averaged down.
LIGHT = np.array([-0.42, -0.62, 0.66])


# ---------- Helpers ----------

def grid(w, h):
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    return x + 0.5, y + 0.5


def clamp(a, lo=0.0, hi=1.0):
    return np.clip(a, lo, hi)


def smooth(e0, e1, x):
    t = clamp((x - e0) / (e1 - e0))
    return t * t * (3 - 2 * t)


def rgb(*c):
    return np.array(c, dtype=np.float32) / 255.0


def over(dst, color, alpha):
    """Source-over onto an RGBA float image (straight alpha)."""
    alpha = alpha[..., None]
    out_a = alpha + dst[..., 3:] * (1 - alpha)
    color = color if color.ndim == 3 else color[None, None, :]
    mixed = (color * alpha + dst[..., :3] * dst[..., 3:] * (1 - alpha)) / np.maximum(out_a, 1e-5)
    return np.concatenate([mixed, out_a], axis=2)


def cover(sd, soft=1.0):
    return clamp(0.5 - sd / soft)


def sd_circle(x, y, cx, cy, r):
    return np.hypot(x - cx, y - cy) - r


def sd_rrect(x, y, x0, y0, x1, y1, r):
    qx = np.abs(x - (x0 + x1) / 2) - (x1 - x0) / 2 + r
    qy = np.abs(y - (y0 + y1) / 2) - (y1 - y0) / 2 + r
    return np.hypot(np.maximum(qx, 0), np.maximum(qy, 0)) + np.minimum(np.maximum(qx, qy), 0) - r


def sd_polygon(x, y, pts):
    """Exact signed distance to a polygon (negative inside)."""
    d = np.full(x.shape, 1e9, dtype=np.float32)
    s = np.ones(x.shape, dtype=np.float32)
    n = len(pts)
    for i in range(n):
        ax, ay = pts[i]
        bx, by = pts[(i + n - 1) % n]
        ex, ey = bx - ax, by - ay
        if ex == 0 and ey == 0:
            continue
        wx, wy = x - ax, y - ay
        t = clamp((wx * ex + wy * ey) / (ex * ex + ey * ey))
        d = np.minimum(d, (wx - ex * t) ** 2 + (wy - ey * t) ** 2)
        c1 = y >= ay
        c2 = y < by
        c3 = ex * wy > ey * wx
        flip = (c1 & c2 & c3) | (~c1 & ~c2 & ~c3)
        s = np.where(flip, -s, s)
    return s * np.sqrt(d)


def star_points(cx, cy, r, inner=0.382, rot=-np.pi / 2, n=5):
    return [
        (cx + (r if i % 2 == 0 else r * inner) * np.cos(rot + i * np.pi / n),
         cy + (r if i % 2 == 0 else r * inner) * np.sin(rot + i * np.pi / n))
        for i in range(2 * n)
    ]


def blur(a, sigma):
    """Gaussian blur through the FFT; numpy only."""
    fy = np.fft.fftfreq(a.shape[0])[:, None]
    fx = np.fft.rfftfreq(a.shape[1])[None, :]
    g = np.exp(-2 * (np.pi * sigma) ** 2 * (fx ** 2 + fy ** 2))
    return np.fft.irfft2(np.fft.rfft2(a) * g, s=a.shape).astype(np.float32)


def noise(w, h, cell_x, cell_y, seed, octaves=4, falloff=0.5):
    """Fractal value noise in 0..1, each octave half the cell."""
    rng = np.random.default_rng(seed)
    out = np.zeros((h, w), dtype=np.float32)
    amp, total = 1.0, 0.0
    for o in range(octaves):
        gw = max(2, int(w / max(cell_x / 2 ** o, 1)) + 2)
        gh = max(2, int(h / max(cell_y / 2 ** o, 1)) + 2)
        layer = Image.fromarray(rng.random((gh, gw), dtype=np.float32), "F").resize((w, h), Image.BICUBIC)
        out += amp * np.asarray(layer, dtype=np.float32)
        total += amp
        amp *= falloff
    return clamp(out / total)


def shade(height, albedo, ambient=0.34, diffuse=0.82, spec=0.55, gloss=26.0):
    """Lights a height field (in pixels) from the upper left, as the printed metal is."""
    gy, gx = np.gradient(height)
    nz = np.ones_like(height)
    length = np.sqrt(gx * gx + gy * gy + nz)
    nx, ny, nz = -gx / length, -gy / length, nz / length
    lambert = clamp(nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2])
    half = LIGHT + np.array([0.0, 0.0, 1.0])
    half = half / np.linalg.norm(half)
    highlight = clamp(nx * half[0] + ny * half[1] + nz * half[2]) ** gloss
    albedo = albedo if albedo.ndim == 3 else np.broadcast_to(albedo, height.shape + (3,))
    return clamp(albedo * (ambient + diffuse * lambert)[..., None] + (spec * highlight)[..., None])


def bevel(sd, width):
    """A rounded edge rising inward from sd = 0 over `width` pixels."""
    t = clamp(-sd / width)
    return np.sqrt(1 - (1 - t) ** 2) * width


def finish(image, name, scale=1.0):
    """
    Averages the supersampling down (with premultiplied alpha) and writes the master. [scale] takes it to the part's
    size at 4x, its largest on a phone: a bilinear draw aliases thin rims when it shrinks a bitmap much more than 2x.
    """
    h, w = image.shape[0] // SS, image.shape[1] // SS
    image = image[: h * SS, : w * SS]
    pre = image.copy()
    pre[..., :3] *= pre[..., 3:]
    pre = pre.reshape(h, SS, w, SS, 4).mean(axis=(1, 3))
    a = pre[..., 3:]
    colour = np.where(a > 1e-4, pre[..., :3] / np.maximum(a, 1e-4), 0)
    out = np.concatenate([colour, a], axis=2)
    OUT.mkdir(parents=True, exist_ok=True)
    master = Image.fromarray((clamp(out) * 255 + 0.5).astype(np.uint8), "RGBA")
    if scale != 1.0:
        master = master.resize((round(w * scale), round(h * scale)), Image.LANCZOS)
    master.save(OUT / f"{name}.png", optimize=True)
    print(name, *master.size)


def blank(w, h):
    return np.zeros((h, w, 4), dtype=np.float32)


# ---------- Parchment ----------

def parchment(w, h, seed):
    """The cards' pale printed parchment: a little warm mottling, faint fibres. Never dark: text sits on it."""
    base = rgb(208, 206, 194)
    blotch = noise(w, h, 190 * SS, 120 * SS, seed, octaves=4, falloff=0.55) - 0.5
    fibre = noise(w, h, 26 * SS, 7 * SS, seed + 1, octaves=2) - 0.5
    grain = noise(w, h, 3 * SS, 3 * SS, seed + 2, octaves=2) - 0.5
    tone = 1 + 0.085 * blotch + 0.028 * fibre + 0.022 * grain
    warm = np.array([1.006, 1.0, 0.985], dtype=np.float32)
    return clamp(base[None, None, :] * warm * tone[..., None])


# ---------- The gear ----------

def gear_radius(theta, root, tip, teeth=12, rot=0.0):
    """A cog's outline by angle: rounded lobes, as the cards print it."""
    lobe = 0.5 + 0.5 * np.cos(teeth * (theta - rot))
    return root + (tip - root) * smooth(0.10, 0.92, lobe)


def draw_gear(image, x, y, cx, cy, r):
    theta = np.arctan2(y - cy, x - cx)
    rho = np.hypot(x - cx, y - cy)
    w, h = x.shape[1], x.shape[0]
    outline = rho - gear_radius(theta, r * 0.855, r, rot=-np.pi / 2)
    wear = noise(w, h, 18 * SS, 18 * SS, 41, octaves=3) - 0.5

    # The cog: bevelled teeth, a ring, then the recessed face.
    height = bevel(outline, r * 0.09) + wear * r * 0.012
    groove = np.abs(rho - r * 0.735)
    height -= r * 0.05 * (1 - smooth(0, r * 0.03, groove))
    face = rho - r * 0.70
    height = np.where(face < 0, height - r * 0.09 * smooth(0, r * 0.05, -face), height)
    albedo = rgb(92, 97, 100)[None, None, :] * (0.85 + 0.42 * wear[..., None])
    cog = shade(height, albedo, ambient=0.38, diffuse=0.80, spec=0.26, gloss=18)

    # The face is near black with a faint engraved hatch, where a card prints its tier.
    hatch = np.sin((x - y) * np.pi / (r * 0.085)) * np.sin((x + y) * np.pi / (r * 0.085))
    sheen = smooth(r * 0.7, 0, np.hypot(x - cx + r * 0.22, y - cy + r * 0.26))
    disc = rgb(30, 33, 36)[None, None, :] * (1 + 0.20 * hatch[..., None]) + rgb(40, 44, 48)[None, None, :] * sheen[..., None]
    cog = np.where((face < 0)[..., None], clamp(disc + cog * 0.22), cog)

    shadow = cover(blur(outline - r * 0.02, r * 0.035), r * 0.05) * 0.55
    image = over(image, rgb(8, 9, 10), shadow)
    return over(image, cog, cover(outline, 1.2))


# ---------- The title plate ----------

# Drawn at 700 x 199, written at 3/4. The written plate's slices (left, top, right, bottom) are 52, 31, 132, 31: at the
# plate's minimum height the corners and the middle scale alike, so the gear stays round. LookParts.kt, HeroPlates.kt
# and the title plate's border-image use the same numbers.
PLATE_W, PLATE_H = 700, 199
PLATE_GEAR = 86.0


def title_plate():
    w, h = PLATE_W * SS, PLATE_H * SS
    gx, gy = grid(w, h)
    x, y = gx / SS, gy / SS
    image = blank(w, h)
    cx = PLATE_W - PLATE_GEAR - 2
    cy = PLATE_H / 2
    top, bottom = cy - 75, cy + 75
    pill = sd_rrect(x, y, 5, top, cx + 8, bottom, 68)

    image = over(image, rgb(16, 18, 20), cover(pill, 0.6) * 0.92)
    wear = noise(w, h, 34 * SS, 12 * SS, 7, octaves=3) - 0.5
    rim_sd = pill + 2.5
    rim = shade(bevel(rim_sd, 6.0) * SS + wear * 1.2, rgb(150, 155, 154) * (1 + 0.20 * wear[..., None]), ambient=0.44, spec=0.26)
    image = over(image, rim, cover(rim_sd, 0.6))
    image = over(image, rgb(46, 49, 50), cover(pill + 10.5, 0.6))
    paper = parchment(w, h, 11)
    stain = smooth(16, 0, -(pill + 12)) * 0.07
    image = over(image, clamp(paper * (1 - stain[..., None])), cover(pill + 12, 0.6))
    return finish(draw_gear(image, gx, gy, cx * SS, cy * SS, PLATE_GEAR * SS), "title-plate", 0.75)


# ---------- The banner ----------

BANNER_H = 240
BANNER_W = 1896
BANNER_CAP = 324  # 1.35 x the height: GoaScaffold.kt and the banner's border-image slice.

# Rows of the band, as the rulebook prints it.
RAIL_TOP = (5, 15)
BLUE = (22, 197)
RAIL_BOTTOM = (204, 232)


def ribbon_strokes(w, h, y0, y1, seed):
    """The band's painted water: many long overlapping strokes, each streaked along itself and lit along one edge."""
    rng = np.random.default_rng(seed)
    x, y = grid(w, h)
    base = np.broadcast_to(rgb(10, 38, 62), (h, w, 3)).copy()
    span = y1 - y0
    dark, mid, light, pale = rgb(8, 30, 54), rgb(20, 72, 112), rgb(40, 112, 156), rgb(124, 190, 216)
    streak = rng.random(512, dtype=np.float32)
    streak = np.convolve(np.concatenate([streak, streak[:8]]), np.ones(3) / 3, mode="same")[:512]
    count = int(w / (24 * SS))
    for i in range(count):
        x0 = rng.uniform(-0.04, 1.0) * w
        length = rng.uniform(300, 760) * SS
        thick = rng.uniform(0.05, 0.24) * span
        centre = rng.uniform(0.0, 1.0) * span + y0
        drop = rng.uniform(0.5, 1.25) * span * (1 if rng.random() < 0.5 else -1)
        wave = rng.uniform(0.03, 0.12) * span
        phase = rng.uniform(0, 6.28)
        left, right = int(max(0, x0)), int(min(w, x0 + length))
        if right <= left:
            continue
        reach = int(abs(drop) / 2 + wave + thick * 1.4) + 2
        top, bottom = int(max(0, centre - reach)), int(min(h, centre + reach))
        if bottom <= top:
            continue
        xs = x[top:bottom, left:right]
        ys = y[top:bottom, left:right]
        t = (xs - x0) / length
        mid_y = centre + drop * (t - 0.5) + wave * np.sin(phase + t * 5.2)
        half = thick * np.sin(np.pi * clamp(t)) ** 0.7 + 0.5
        across = (ys - mid_y) / half
        mask = clamp((1 - np.abs(across)) * half / (1.6 * SS)) * smooth(0, 0.10, t) * smooth(1, 0.90, t)
        pick = rng.random()
        tint = rng.random()
        colour = mid * (1 - tint) + (dark if pick < 0.5 else light) * tint
        ramp = 0.74 + 0.42 * clamp(-across)[..., None]
        index = ((across + 1) * 90 + i * 37).astype(np.int32) % 512
        painted = colour[None, None, :] * ramp * (0.80 + 0.40 * streak[index][..., None])
        lit = smooth(0.16, 0.0, np.abs(across + 0.84)) * (rng.uniform(0.3, 0.85) if pick > 0.72 else rng.uniform(0.0, 0.16))
        painted = painted * (1 - lit[..., None]) + pale * lit[..., None]
        strength = mask[..., None] * rng.uniform(0.55, 0.95)
        base[top:bottom, left:right] = base[top:bottom, left:right] * (1 - strength) + painted * strength
    return clamp(base)


def rail(x, y, top, bottom, seed):
    """A steel rail: rounded across, with long soft reflections along it (the bar is squeezed sideways on a phone)."""
    w, h = x.shape[1], x.shape[0]
    t = clamp((y - top) / (bottom - top))
    profile = np.sqrt(clamp(1 - (2 * t - 1) ** 2))
    along = np.asarray(noise(w, 2, 300 * SS, 8, seed, octaves=3, falloff=0.45))[0]
    polish = smooth(0.30, 0.74, along)[None, :]
    brushed = noise(w, h, 90 * SS, 1.6 * SS, seed + 1, octaves=2) - 0.5
    tone = 0.20 + 0.56 * polish * (0.28 + 0.72 * profile) + 0.05 * brushed + 0.12 * profile
    return clamp(tone[..., None] * rgb(226, 232, 238) + rgb(26, 30, 36))


def grime(w, h, seed, amount):
    """Pits and rubbed patches, to multiply into clean metal: the print's metal is old."""
    pits = smooth(0.50, 0.80, noise(w, h, 6 * SS, 6 * SS, seed, octaves=3, falloff=0.6))
    patches = noise(w, h, 80 * SS, 80 * SS, seed + 1, octaves=3)
    return (1 - amount * pits * (0.5 + patches))[..., None]


def rosette(image, x, y, cx, cy, r):
    """The little rings where the medallion's filigree starts."""
    sd = np.abs(np.hypot(x - cx, y - cy) - r * 0.62) - r * 0.30
    height = bevel(sd, r * 0.20)
    metal = shade(height, rgb(146, 152, 160), ambient=0.40, spec=0.6, gloss=26)
    image = over(image, rgb(6, 8, 11), cover(np.hypot(x - cx, y - cy) - r * 1.15, 4.0))
    return over(image, metal, cover(sd, 2.0))


def banner():
    W, H = BANNER_W * SS, BANNER_H * SS
    x, y = grid(W, H)
    image = np.zeros((H, W, 4), dtype=np.float32)
    image[..., :3] = rgb(6, 9, 13)
    image[..., 3] = 1

    # Water, then the rails over it.
    water = ribbon_strokes(W, H, BLUE[0] * SS, BLUE[1] * SS, 20)
    fade = smooth(BLUE[0] * SS, (BLUE[0] + 16) * SS, y) * smooth(BLUE[1] * SS, (BLUE[1] - 14) * SS, y)
    water = water * (0.52 + 0.48 * fade[..., None])
    image = over(image, water, cover(np.maximum(BLUE[0] * SS - y, y - BLUE[1] * SS), 2.0))
    for (top, bottom), seed in ((RAIL_TOP, 31), (RAIL_BOTTOM, 37)):
        image = over(image, rail(x, y, top * SS, bottom * SS, seed), cover(np.maximum(top * SS - y, y - bottom * SS), 2.0))

    # The cap. The rails run on into its frame; the medallion sits between them.
    s = float(BANNER_H * SS)
    right = W - 0.02 * s
    well_top, well_bottom = (RAIL_TOP[1] + 2) * SS, (RAIL_BOTTOM[0] - 2) * SS
    cy = (well_top + well_bottom) / 2
    r = (well_bottom - well_top) / 2 - 0.012 * s
    cx = right - 0.095 * s - r
    frame = sd_rrect(x, y, -s, 0.012 * s, right, 0.988 * s, 0.22 * s)
    well = sd_rrect(x, y, -s, well_top, right - 0.07 * s, well_bottom, 0.15 * s)

    wear = noise(W, H, 40 * SS, 40 * SS, 53, octaves=4) - 0.5
    dirt = grime(W, H, 61, 0.22)
    frame_h = bevel(frame + 0.006 * s, 0.05 * s) + wear * 2.0
    frame_metal = shade(frame_h, rgb(150, 156, 164) * (1 + 0.5 * wear[..., None]) * dirt, ambient=0.30, spec=0.55, gloss=22)
    # The end of the frame only: left of it the rails stay as they are, faded across so no seam shows.
    capped = smooth(cx - 0.15 * s, cx + 0.12 * s, x)
    image = over(image, frame_metal, cover(frame + 0.012 * s, 2.0) * cover(-well, 2.0) * capped)

    # The dark behind the medallion, coming out of the water.
    behind = smooth(cx - 1.75 * r, cx - 1.05 * r, x)
    image = over(image, rgb(6, 9, 13), behind * cover(well, 2.0))
    image = over(image, rgb(2, 3, 5), cover(np.abs(well) - 0.004 * s, 2.0) * capped * 0.8)

    # The medallion: a ring, a fluted dark disc and a faceted star.
    theta = np.arctan2(y - cy, x - cx)
    rho = np.hypot(x - cx, y - cy)
    ripple = noise(W, H, 26 * SS, 26 * SS, 59, octaves=3) - 0.5

    # Two arms of filigree curve around it from its rosettes to the frame.
    for start, end in ((3.72, 5.52), (0.58, 2.38)):
        sweep = np.mod(theta - start, 2 * np.pi)
        along = clamp(sweep / (end - start))
        taper = (0.022 - 0.012 * along) * s
        arm = np.abs(rho - r * 1.13) - taper / 2
        metal = shade(bevel(arm, taper / 2), rgb(146, 152, 162) * dirt, ambient=0.36, spec=0.7)
        image = over(image, metal, cover(arm, 2.0) * (sweep < end - start) * cover(well, 2.0))

    flutes = np.cos(theta * 30) * r * 0.010
    disc_h = flutes * smooth(0.26 * r, 0.40 * r, rho) * smooth(r * 0.95, r * 0.82, rho)
    disc = shade(disc_h, rgb(54, 57, 64) * (0.9 + 0.6 * ripple[..., None]) * dirt, ambient=0.28, diffuse=0.60, spec=0.14, gloss=14)
    vignette = smooth(r, 0.3 * r, np.hypot(x - cx + 0.12 * r, y - cy + 0.18 * r))
    image = over(image, disc * (0.45 + 0.60 * vignette[..., None]), cover(rho - r, 2.0))

    ring_sd = np.abs(rho - r * 0.97) - r * 0.040
    ring = shade(bevel(ring_sd, r * 0.04), rgb(160, 166, 178) * (1 + 0.45 * ripple[..., None]) * dirt, ambient=0.38, spec=0.75, gloss=26)
    image = over(image, ring, cover(ring_sd, 2.0))

    star = sd_polygon(x, y, star_points(cx, cy, r * 0.80))
    star_h = clamp(-star / (r * 0.15)) * r * 0.19
    star_metal = shade(star_h, rgb(150, 155, 166) * (1 + 0.32 * ripple[..., None]) * dirt, ambient=0.34, diffuse=0.84, spec=0.42, gloss=18)
    image = over(image, rgb(3, 4, 6), cover(blur(star - 0.006 * s, 0.006 * s), 0.02 * s) * 0.8)
    image = over(image, star_metal, cover(star, 2.0))

    # Its centre: a small pit.
    pit = sd_polygon(x, y, star_points(cx, cy, r * 0.17, inner=0.809, rot=np.pi / 2))
    image = over(image, rgb(8, 10, 13), cover(pit, 2.0))
    image = over(image, rgb(120, 126, 136), cover(np.abs(pit) - 0.003 * s, 2.0) * 0.4)

    image = rosette(image, x, y, cx + r * 1.13 * np.cos(3.72), cy + r * 1.13 * np.sin(3.72), 0.052 * s)
    image = rosette(image, x, y, cx + r * 1.13 * np.cos(0.58), cy + r * 1.13 * np.sin(0.58), 0.052 * s)

    # The rounded end is cut out of the page.
    image[..., 3] *= cover(frame, 2.0)
    return finish(image, "banner")


# ---------- The text boxes ----------

# Drawn at 1000 x 400, written at 3/5: all five colours share one geometry. The written box's slices (left, top, right,
# bottom) are 48, 87, 48, 39, and its tab is the top 60 rows: a box with no word for its tab starts there. LookParts.kt
# and the text box's border-image use the same numbers.
BOX_W, BOX_H = 1000, 400
BOX_TAB = 100  # Rows of the tab above the rim, as drawn: 14 dp, room for its word.

TABS = {
    "green": (rgb(58, 158, 62), rgb(40, 118, 44)),
    "gold": (rgb(232, 166, 62), rgb(186, 122, 32)),
    "blue": (rgb(48, 110, 186), rgb(30, 76, 140)),
    "red": (rgb(222, 34, 48), rgb(158, 18, 30)),
    "silver": (rgb(176, 178, 180), rgb(128, 130, 134)),
}


def scroll(x, y, cx, cy, r, mirror):
    """The small printed curl at each end of a tab."""
    u = (x - cx) * (-1 if mirror else 1)
    v = y - cy
    theta = np.arctan2(v, u)
    rho = np.hypot(u, v)
    spiral = np.abs(rho - r * (0.36 + 0.095 * np.mod(theta + np.pi, 2 * np.pi))) - r * 0.055
    tail = sd_rrect(u, v, r * 0.1, r * 0.82, r * 2.4, r * 0.96, r * 0.07)
    return np.minimum(np.where(rho < r * 1.0, spiral, 1e6), tail)


def text_box(name):
    top_colour, low_colour = TABS[name]
    w, h = BOX_W * SS, BOX_H * SS
    x, y = grid(w, h)
    x /= SS
    y /= SS
    image = blank(w, h)

    # The tab, sloping up from the box like a card's.
    run = 58.0
    left, right = BOX_W * 0.205, BOX_W * 0.795
    pts = [(left - run, BOX_TAB + 12)]
    steps = 16
    for i in range(steps + 1):
        t = i / steps
        pts.append((left - run + run * t, BOX_TAB + 12 - (BOX_TAB + 6) * np.sin(t * np.pi / 2) ** 1.25))
    for i in range(steps + 1):
        t = 1 - i / steps
        pts.append((right + run - run * t, BOX_TAB + 12 - (BOX_TAB + 6) * np.sin(t * np.pi / 2) ** 1.25))
    pts.append((right + run, BOX_TAB + 12))
    tab = sd_polygon(x, y, pts)
    image = over(image, rgb(15, 17, 18), cover(tab, 0.5))
    edge = tab + 3.0
    wear = noise(w, h, 40 * SS, 20 * SS, 71, octaves=3) - 0.5
    image = over(image, shade(bevel(edge, 3.5) * SS, rgb(108, 113, 113), ambient=0.46, spec=0.3), cover(edge, 0.5))
    fill = low_colour + (top_colour - low_colour) * smooth(BOX_TAB + 6, 20, y)[..., None]
    fill = clamp(fill * (1 + 0.14 * wear[..., None]))
    image = over(image, fill, cover(tab + 8.0, 0.5))
    # A darker tone of the frame colour for the curls: on gold and silver the word is dark, so they stay quiet.
    curl = np.minimum(
        scroll(x, y, BOX_W * 0.255, BOX_TAB * 0.58, 15.0, False),
        scroll(x, y, BOX_W * 0.745, BOX_TAB * 0.58, 15.0, True),
    )
    image = over(image, clamp(low_colour * 0.92), cover(curl, 0.6) * 0.62)

    # The box: a dark line, a steel rim, the frame colour, then the parchment.
    box = sd_rrect(x, y, 2, BOX_TAB, BOX_W - 2, BOX_H - 2, 34)
    image = over(image, rgb(15, 17, 18), cover(box, 0.5))
    rim_sd = box + 3.0
    rim = shade(bevel(rim_sd, 9.0) * SS + wear * 1.5, rgb(104, 109, 110) * (1 + 0.24 * wear[..., None]), ambient=0.44, spec=0.45)
    image = over(image, rim, cover(rim_sd, 0.5))
    image = over(image, rgb(26, 28, 28), cover(box + 16.0, 0.5))
    line = low_colour + (top_colour - low_colour) * 0.65
    image = over(image, clamp(line), cover(box + 18.5, 0.5))
    paper_sd = box + 30.0
    paper = parchment(w, h, 90 + len(name))
    stain = smooth(22, 0, -paper_sd) * 0.06
    image = over(image, rgb(40, 40, 36), cover(paper_sd, 0.5) * 0.85)
    image = over(image, clamp(paper * (1 - stain[..., None])), cover(paper_sd + 2.0, 0.5))
    return finish(image, f"textbox-{name}", 0.6)


# ---------- The plate's corner brackets ----------

def brackets():
    size = 128
    w = size * SS
    x, y = grid(w, w)
    x /= SS
    y /= SS
    # A teal enamel corner bracket on steel: concave on its inner side, as on the dashboard plate.
    outer = sd_rrect(x, y, 3, 3, size * 2, size * 2, 26)
    bite = sd_circle(x, y, size * 1.06, size * 1.06, size * 0.94)
    arm = np.maximum(np.maximum(outer, -bite), x + y - size * 0.98)
    image = blank(w, w)
    image = over(image, rgb(8, 12, 13), cover(arm, 0.6))
    edge = arm + 2.5
    wear = noise(w, w, 16 * SS, 16 * SS, 83, octaves=3) - 0.5
    steel = shade(bevel(edge, 5.0) * SS, rgb(112, 126, 130), ambient=0.46, spec=0.28, gloss=20)
    image = over(image, steel, cover(edge, 0.6))
    enamel_sd = arm + 8.0
    sweep = smooth(70, 0, (x + y) * 0.5)
    enamel = rgb(16, 92, 100) + (rgb(60, 188, 180) - rgb(16, 92, 100)) * sweep[..., None]
    enamel = clamp(enamel * (1 + 0.2 * wear[..., None]))
    image = over(image, enamel, cover(enamel_sd, 0.6))
    gloss = smooth(11, 0, np.abs(x + y - 44)) * 0.34
    image = over(image, rgb(210, 245, 240), gloss * cover(enamel_sd + 3, 0.6))
    finish(image, "corner-tl", 0.5)
    im = Image.open(OUT / "corner-tl.png")
    im.transpose(Image.FLIP_LEFT_RIGHT).save(OUT / "corner-tr.png", optimize=True)
    im.transpose(Image.FLIP_TOP_BOTTOM).save(OUT / "corner-bl.png", optimize=True)
    im.transpose(Image.ROTATE_180).save(OUT / "corner-br.png", optimize=True)


if __name__ == "__main__":
    only = set(sys.argv[2:])
    jobs = {"plate": title_plate, "banner": banner, "corners": brackets}
    jobs.update({f"box-{n}": (lambda n=n: text_box(n)) for n in TABS})
    for key, job in jobs.items():
        if not only or key in only:
            job()
