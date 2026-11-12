#!/usr/bin/env python3
"""Draws Grapefruit AI's launcher icon (PNG; Android 4.2 has no vector drawables).

A grapefruit slice on the Grapefruit coral gradient (#FF8C6B to #EC485C), with a four-point
spark for "AI". Run: python3 tools/icon.py app/src/main/res
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

TOP, BOTTOM = (0xFF, 0x8C, 0x6B), (0xEC, 0x48, 0x5C)
RIND, PITH, FLESH, CREAM = (0xFF, 0xF7, 0xEE), (0xFF, 0xD6, 0xC8), (0xE8, 0x3A, 0x52), (0xFF, 0xF1, 0xE8)
SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def draw(size):
    s = size * 4  # supersample
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    grad = Image.new("RGBA", (s, s))
    gd = ImageDraw.Draw(grad)
    for y in range(s):
        t = y / (s - 1)
        gd.line([(0, y), (s, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(TOP, BOTTOM)) + (255,))
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).ellipse([s * 0.04, s * 0.04, s * 0.96, s * 0.96], fill=255)
    img.paste(grad, (0, 0), mask)
    d = ImageDraw.Draw(img)
    cx, cy, r = s * 0.46, s * 0.54, s * 0.30
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=RIND)
    r2 = r * 0.90
    d.ellipse([cx - r2, cy - r2, cx + r2, cy + r2], fill=PITH)
    r3 = r * 0.82
    for i in range(8):
        a0 = math.radians(i * 45 + 4)
        a1 = math.radians(i * 45 + 41)
        pts = [(cx + r * 0.08 * math.cos((a0 + a1) / 2), cy + r * 0.08 * math.sin((a0 + a1) / 2))]
        for k in range(13):
            a = a0 + (a1 - a0) * k / 12
            pts.append((cx + r3 * math.cos(a), cy + r3 * math.sin(a)))
        d.polygon(pts, fill=FLESH)
    # Spark, upper right.
    sx, sy, sr = s * 0.74, s * 0.27, s * 0.13
    w = sr * 0.28
    d.polygon([(sx, sy - sr), (sx + w, sy - w), (sx + sr, sy), (sx + w, sy + w),
               (sx, sy + sr), (sx - w, sy + w), (sx - sr, sy), (sx - w, sy - w)], fill=CREAM)
    return img.resize((size, size), Image.LANCZOS)


if __name__ == "__main__":
    res = Path(sys.argv[1])
    for name, px in SIZES.items():
        out = res / ("mipmap-" + name)
        out.mkdir(parents=True, exist_ok=True)
        draw(px).save(out / "ic_launcher.png", optimize=True)
    draw(512).save(res.parent.parent.parent.parent / "docs" / "icon-512.png") if (res.parent.parent.parent.parent / "docs").exists() else None
