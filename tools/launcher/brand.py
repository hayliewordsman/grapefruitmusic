#!/usr/bin/env python3
"""Draw Grapefruit Launcher's house/app-grid mark, including API 17 raster icons.

Run from the repository root. Requires Pillow, as do the other brand tools.
"""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2] / "launcher/src/main/res"


def mark(size, foreground=False, monochrome=False):
    scale = 4
    image = Image.new("RGBA", (size * scale, size * scale))
    draw = ImageDraw.Draw(image)
    unit = size * scale / 108

    def box(coords):
        return tuple(round(x * unit) for x in coords)

    if not foreground:
        draw.rounded_rectangle(box((3, 3, 105, 105)), radius=round(38 * unit), fill="#1C0A0E")
    draw.line(box((27, 49, 54, 27, 81, 49)), fill="#FF7F6B", width=max(1, round(7 * unit)), joint="curve")
    draw.rounded_rectangle(box((32, 47, 76, 81)), radius=round(6 * unit), fill="#FF7F6B")
    for row in range(2):
        for col in range(2):
            x, y = 42 + col * 15, 55 + row * 14
            draw.rounded_rectangle(box((x, y, x + 9, y + 9)), radius=round(2 * unit), fill=(0, 0, 0, 0) if monochrome else "#2A0A10")
    return image.resize((size, size), Image.Resampling.LANCZOS)


for density, factor in (("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4)):
    dest = ROOT / ("mipmap-" + density)
    dest.mkdir(parents=True, exist_ok=True)
    mark(round(48 * factor)).save(dest / "ic_launcher.png")
    mark(round(108 * factor), True).save(dest / "ic_launcher_foreground.png")
    mark(round(108 * factor), True, True).save(dest / "ic_launcher_monochrome.png")
