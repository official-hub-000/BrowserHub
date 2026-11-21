#!/usr/bin/env python3
"""
Renders BrowserHub's launcher icon from vectors, at every Android density.

Why a script instead of a PNG: the previous icon was a 996 KB raster scaled
into a 66dp adaptive-icon layer, which is why it looked soft and muddy at
launcher size. Everything here is drawn mathematically at the target
resolution, so it is crisp at 48px and at 512px.

Design: a globe (the web) with a mouse cursor over it (browsing) — readable
as "browser" at 48px, which a shield-and-compass never was. Palette matches
ui/theme/Color.kt: OLED black, FlagshipCyan #00D4FF, FlagshipViolet #A855F7.

Adaptive-icon safety: Android masks the outer ~1/3 of a 108dp layer. All
content is kept inside a 72dp centred safe zone (66% of the canvas).

Usage:
    python3 tools/render-icon.py /tmp/icon-out
    # then copy into app/src/main/res as documented below

Outputs ic_launcher_{density}.png and ic_launcher_round_{density}.png for
mdpi..xxxhdpi, plus ic_launcher_512.png for the Play listing and the adaptive
foreground.

Installing:
    for d in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
      cp /tmp/icon-out/ic_launcher_$d.png       app/src/main/res/mipmap-$d/ic_launcher.png
      cp /tmp/icon-out/ic_launcher_round_$d.png app/src/main/res/mipmap-$d/ic_launcher_round.png
    done
    cp /tmp/icon-out/ic_launcher_512.png app/src/main/res/drawable/ic_browserhub_logo_v2.png

Requires Pillow (pip install pillow). No SVG renderer needed.
"""
import math
import os
import sys

from PIL import Image, ImageDraw

# --- palette (from app/src/main/java/com/browserhub/app/ui/theme/Color.kt) ---
BG_TOP = (6, 13, 23)          # #060D17, matches ic_launcher_background
BG_BOTTOM = (3, 6, 10)        # #03060A OledElevatedBg
CYAN = (0, 212, 255)          # #00D4FF FlagshipCyan
CYAN_DEEP = (4, 38, 51)       # #042633 FlagshipCyanDarkBg
VIOLET = (168, 85, 247)       # #A855F7 FlagshipViolet
FROST = (125, 211, 252)       # #7DD3FC FlagshipFrost

SS = 4  # supersample factor for smooth edges


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def vertical_gradient(size, top, bottom):
    img = Image.new("RGB", (1, size), top)
    px = img.load()
    for y in range(size):
        px[0, y] = lerp(top, bottom, y / max(1, size - 1))
    return img.resize((size, size), Image.BILINEAR)


def draw_globe(d, cx, cy, r, width, color):
    """Outer ring + equator + one meridian.

    Deliberately only three strokes. An earlier version had latitude arcs
    too, which looked right at 512px and turned to mush at 48px — the size
    that actually matters on a launcher.
    """
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=color, width=width)
    d.line([cx - r, cy, cx + r, cy], fill=color, width=width)
    d.arc([cx - r * 0.45, cy - r, cx + r * 0.45, cy + r], 0, 360, fill=color, width=width)


def draw_cursor(d, x, y, s, fill, outline, ow):
    """Arrow cursor pointing up-left, the universal 'pointer' shape."""
    pts = [
        (x, y),
        (x, y + s * 1.00),
        (x + s * 0.26, y + s * 0.75),
        (x + s * 0.44, y + s * 1.14),
        (x + s * 0.60, y + s * 1.06),
        (x + s * 0.43, y + s * 0.68),
        (x + s * 0.72, y + s * 0.66),
    ]
    d.polygon(pts, fill=fill, outline=outline, width=ow)


def build(px):
    """Render one icon at px*px with the adaptive safe-zone composition."""
    size = px * SS
    img = vertical_gradient(size, BG_TOP, BG_BOTTOM).convert("RGBA")
    d = ImageDraw.Draw(img)

    cx = cy = size / 2
    # Safe zone: content inside 66% of the canvas for adaptive masking.
    safe = size * 0.66
    r = safe * 0.40          # globe radius
    lw = max(2, int(size * 0.042))   # globe stroke (thicker for 48px legibility)

    # Soft cyan glow behind the globe, as concentric alpha rings.
    glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    for i in range(18, 0, -1):
        rr = r * (1.0 + i * 0.055)
        a = int(26 * (1 - i / 19))
        gd.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=CYAN + (a,))
    img = Image.alpha_composite(img, glow)
    d = ImageDraw.Draw(img)

    # Globe in cyan
    draw_globe(d, cx, cy, r, lw, CYAN)

    # Filled inner disc for contrast behind the cursor
    inner = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    idr = ImageDraw.Draw(inner)
    idr.ellipse([cx - r + lw, cy - r + lw, cx + r - lw, cy + r - lw],
                fill=(6, 20, 30, 205))
    img = Image.alpha_composite(img, inner)
    # redraw the globe outline so it stays on top of the disc
    d = ImageDraw.Draw(img)
    draw_globe(d, cx, cy, r, lw, CYAN)

    # Cursor in violet, lower-right, overlapping the globe edge
    cs = size * 0.38
    cxs = cx + r * 0.30
    cys = cy - r * 0.26
    # drop shadow so it separates from the globe grid
    draw_cursor(d, cxs + size * 0.012, cys + size * 0.012, cs, (0, 0, 0, 110), None, 0)
    draw_cursor(d, cxs, cys, cs, VIOLET, FROST, max(2, int(size * 0.011)))

    return img.resize((px, px), Image.LANCZOS)


def build_round(px):
    """Round launcher variant: same art, circular crop with a ring."""
    img = build(px)
    mask = Image.new("L", (px * 4, px * 4), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, px * 4 - 1, px * 4 - 1], fill=255)
    mask = mask.resize((px, px), Image.LANCZOS)
    out = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "res"
    for name, px in DENSITIES.items():
        os.makedirs(out, exist_ok=True)
        build(px).save(os.path.join(out, f"ic_launcher_{name}.png"))
        build_round(px).save(os.path.join(out, f"ic_launcher_round_{name}.png"))
        print(f"  {name:8} {px}x{px}")

    # Store / adaptive-icon foreground, 512 for Play, plus a 108dp-layer source.
    build(512).save(os.path.join(out, "ic_launcher_512.png"))
    print("  store    512x512")
    print("  (foreground PNG: use ic_launcher_512.png inside the 66dp layer)")


if __name__ == "__main__":
    main()
