"""Draws Grapefruit Music's app icon in each theme's colours.

The geometry is the original icon's (1024 px: a rounded tile 943 px across with 225 px corners;
a slice with a 338 px rind, 298 px pith and 270 px flesh cut by ten spokes), drawn 4x larger
and scaled down. Writes the launcher icons (mipmap-*/ic_launcher_<theme>.png) and the About
page's (drawable-*/ic_about_<theme>.png) into app/src/main/res.

    python3 tools/icons/make_icons.py [--preview out.png]
"""
import math
import os
import sys

from PIL import Image, ImageDraw

SS = 4                      # supersampling
BASE = 1024
TILE = (40.5, 983.5)        # tile edges at 1024 px
CORNER = 225
RIND, PITH, FLESH = 338, 298, 270.5
SPOKES, SPOKE_WIDTH = 10, 22.5

THEMES = {
    # The original icon.
    "grapefruit_light": dict(tile=("#FE886A", "#EC4B5C"), rind=("#FFF6ED", "#FFF6ED"),
                             pith="#FFD4C5", flesh="#E83B53"),
    # After dark: the fruit on the theme's red-black.
    "grapefruit_dark": dict(tile=("#4A1A23", "#1C0A0E"), rind=("#FFF1E8", "#FFF1E8"),
                            pith="#F2D0C6", flesh="#E83B53"),
    # White, like iOS: the peel takes the original tile's colours.
    "starlight": dict(tile=("#FFFFFF", "#ECECF1"), rind=("#FE886A", "#EC4B5C"),
                      pith="#FFFFFF", flesh="#F2545B"),
    # Black: a bright slice, its pith cut in black.
    "pitch_black": dict(tile=("#2C2C2E", "#000000"), rind=("#FE886A", "#EC4B5C"),
                        pith="#000000", flesh="#FF6B6B"),
}

LAUNCHER = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
ABOUT = {"mdpi": 88, "hdpi": 132, "xhdpi": 176, "xxhdpi": 264, "xxxhdpi": 352}


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def gradient(size, top, bottom, y0, y1):
    """A vertical gradient from top (at y0) to bottom (at y1), as an RGBA image."""
    t, b = rgb(top), rgb(bottom)
    col = Image.new("RGBA", (1, size))
    for y in range(size):
        f = min(1.0, max(0.0, (y - y0) / float(y1 - y0)))
        col.putpixel((0, y), tuple(int(round(t[i] + (b[i] - t[i]) * f)) for i in range(3)) + (255,))
    return col.resize((size, size))


def circle_mask(size, r):
    m = Image.new("L", (size, size), 0)
    c = size / 2.0
    ImageDraw.Draw(m).ellipse((c - r, c - r, c + r, c + r), fill=255)
    return m


def draw(theme):
    p = THEMES[theme]
    n = BASE * SS
    s = lambda v: v * SS
    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))

    tile_mask = Image.new("L", (n, n), 0)
    ImageDraw.Draw(tile_mask).rounded_rectangle((s(TILE[0]), s(TILE[0]), s(TILE[1]), s(TILE[1])),
                                                radius=s(CORNER), fill=255)
    img.paste(gradient(n, p["tile"][0], p["tile"][1], s(TILE[0]), s(TILE[1])), (0, 0), tile_mask)

    img.paste(gradient(n, p["rind"][0], p["rind"][1], s(512 - RIND), s(512 + RIND)), (0, 0),
              circle_mask(n, s(RIND)))
    img.paste(Image.new("RGBA", (n, n), rgb(p["pith"]) + (255,)), (0, 0), circle_mask(n, s(PITH)))
    img.paste(Image.new("RGBA", (n, n), rgb(p["flesh"]) + (255,)), (0, 0), circle_mask(n, s(FLESH)))

    # Ten spokes from the middle to the pith, the first straight up.
    d = ImageDraw.Draw(img)
    c = n / 2.0
    for i in range(SPOKES):
        a = math.radians(i * 360.0 / SPOKES)
        dx, dy = math.sin(a), -math.cos(a)
        r = s(FLESH + 2)
        d.line((c, c, c + dx * r, c + dy * r), fill=rgb(p["pith"]) + (255,), width=int(s(SPOKE_WIDTH)))
    return img


def main():
    res = os.path.join(os.path.dirname(__file__), "..", "..", "app", "src", "main", "res")
    previews = []
    for theme in THEMES:
        big = draw(theme)
        for density, px in LAUNCHER.items():
            out = os.path.join(res, "mipmap-" + density, "ic_launcher_" + theme + ".png")
            big.resize((px, px), Image.LANCZOS).save(out, optimize=True)
        for density, px in ABOUT.items():
            out = os.path.join(res, "drawable-" + density, "ic_about_" + theme + ".png")
            big.resize((px, px), Image.LANCZOS).save(out, optimize=True)
        previews.append(big.resize((256, 256), Image.LANCZOS))
    if "--preview" in sys.argv:
        sheet = Image.new("RGBA", (256 * len(previews) + 40 * (len(previews) + 1), 336), (128, 128, 128, 255))
        for i, im in enumerate(previews):
            sheet.paste(im, (40 + i * 296, 40), im)
        sheet.save(sys.argv[sys.argv.index("--preview") + 1])


if __name__ == "__main__":
    main()
