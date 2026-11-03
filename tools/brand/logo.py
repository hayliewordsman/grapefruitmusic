#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Grapefruit Audio's logos, as SVG with the lettering as outlines.

The grapefruit is the Telegram channel's picture (a slice on a coral gradient), drawn as vectors
and masked to a circle, wearing headphones. The lettering is Geist Pixel (SIL Open Font License 1.1, Vercel), its
Triangle shape: the variable font's Element Shape axis at 60 (font-variation-settings: "ELSH" 60;
its named instance "Triangle", which Geist Pixel also ships as GeistPixel-Triangle). The outlines
are taken from the font, shaped with HarfBuzz, so the SVGs need no font to show correctly.

  logo.svg / logo-dark.svg                   grapefruit, then "Grapefruit Audio"
  logo-stacked.svg / logo-stacked-dark.svg   grapefruit, "Grapefruit Audio" over
                                             "Music, Podcasts, and Audiobooks"
  ("-dark" for dark backgrounds)

With a third argument, also the splash screen's pictures and the app's icons for Android (PNG:
Android 4.2 has no vector drawables) into that res folder: the launcher icon (the grapefruit, round,
the same in every theme), its adaptive layers for Android 8 and later, and the About page's icon.

Needs Python 3 with fontTools, uharfbuzz and brotli (cairosvg and pngquant for the PNGs), and the
geist npm package (1.9.0) unpacked:
  python3 tools/brand/logo.py path/to/geist/package docs/brand app/src/main/res
"""
import io
import math
import sys
from pathlib import Path

import uharfbuzz as hb
from fontTools.pens.recordingPen import DecomposingRecordingPen
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

TRIANGLE = 60  # Geist Pixel's Element Shape (ELSH) axis: 1 square, 20 circle, 40 grid, 60 triangle, 80 line

# Colours: the Telegram picture's, and the app's Grapefruit Light / Dark text.
TOP, BOTTOM = "#FF8C6B", "#EC485C"
RIND, PITH, FLESH = "#FFF7EE", "#FFD6C8", "#E83A52"
# The headphones: the plum of the app's light-theme text, their cushions a shade lighter.
BAND, CUSHION = "#4A1622", "#6E2434"

NAME, AUDIO = "Grapefruit", "Audio"
TAGLINE = "Music, Podcasts, and Audiobooks"
LIGHT_BG = dict(name="#D8324A", rest="#4A1622")
DARK_BG = dict(name="#FF7F6B", rest="#FFF1E8")


class Lettering:
    def __init__(self, geist):
        pixel = Path(geist) / "dist/fonts/geist-pixel"
        # The variable font's Triangle is its ELSH 60 instance. Geist Pixel also ships that instance
        # as a font of its own, drawn cleanly (each pixel a plain triangle, where interpolating the
        # variable font gives curved near-triangles): its outlines are used, once checked to be the
        # same instance (the same advance for every character).
        vf = TTFont(pixel / "GeistPixelVF.woff2")
        named = {vf["name"].getDebugName(i.subfamilyNameID): i.coordinates for i in vf["fvar"].instances}
        assert named["Triangle"] == {"ELSH": TRIANGLE}, named
        instance = instantiateVariableFont(vf, {"ELSH": TRIANGLE})
        self.font = TTFont(pixel / "GeistPixel-Triangle.woff2")
        mine, theirs = self.font.getBestCmap(), instance.getBestCmap()
        for text in (NAME + " " + AUDIO, TAGLINE):
            for c in map(ord, text):
                assert self.font["hmtx"][mine[c]] == instance["hmtx"][theirs[c]], chr(c)
        assert all(self.font["hmtx"][mine[c]] == instance["hmtx"][theirs[c]] for c in mine if c in theirs)
        data = io.BytesIO()
        self.font.flavor = None
        self.font.save(data)
        self.hb = hb.Font(hb.Face(data.getvalue()))
        self.upem = self.font["head"].unitsPerEm
        self.glyphs = self.font.getGlyphSet()
        self.order = self.font.getGlyphOrder()
        os2 = self.font["OS/2"]
        self.cap = os2.sCapHeight / self.upem
        self.ascent = self.font["hhea"].ascent / self.upem
        self.descent = -self.font["hhea"].descent / self.upem

    def width(self, text, size):
        buf = self.shape(text)
        return sum(p.x_advance for p in buf.glyph_positions) * size / self.upem

    def shape(self, text):
        buf = hb.Buffer()
        buf.add_str(text)
        buf.guess_segment_properties()
        hb.shape(self.hb, buf, {"kern": True, "liga": True})
        return buf

    def outline(self, name):
        """A glyph's outline as compact SVG path data, in font units (y up), relative moves."""
        pen = DecomposingRecordingPen(self.glyphs)
        self.glyphs[name].draw(pen)
        out = []
        cx = cy = 0
        start = (0, 0)

        def num(v):
            v = round(v, 1)
            return str(int(v)) if v == int(v) else str(v)

        for op, pts in pen.value:
            if op == "moveTo":
                (x, y), = pts
                out.append("M%s %s" % (num(x), num(y)))
                cx, cy = x, y
                start = (x, y)
            elif op == "lineTo":
                (x, y), = pts
                out.append("l%s %s" % (num(x - cx), num(y - cy)))
                cx, cy = x, y
            elif op == "qCurveTo":
                # TrueType: implied on-curve points between off-curve ones.
                *offs, end = pts
                if end is None:
                    raise ValueError("closed off-curve contour")
                points = []
                for i, off in enumerate(offs):
                    nxt = offs[i + 1] if i + 1 < len(offs) else None
                    on = ((off[0] + nxt[0]) / 2, (off[1] + nxt[1]) / 2) if nxt else end
                    points.append((off, on))
                for off, on in points:
                    out.append("q%s %s %s %s" % (num(off[0] - cx), num(off[1] - cy), num(on[0] - cx), num(on[1] - cy)))
                    cx, cy = on
            elif op == "curveTo":
                (a, b, c) = pts
                out.append("c%s %s %s %s %s %s" % tuple(num(v) for v in (a[0] - cx, a[1] - cy, b[0] - cx,
                                                                       b[1] - cy, c[0] - cx, c[1] - cy)))
                cx, cy = c
            elif op in ("closePath", "endPath"):
                out.append("z")
                cx, cy = start
        return "".join(out).replace(" -", "-")

    def uses(self, text, size, x, baseline, defs):
        """The text as <use>s of glyphs defined once in defs (name -> path), at size px per em."""
        buf = self.shape(text)
        s = size / self.upem
        out = []
        pen_x = 0
        for info, pos in zip(buf.glyph_infos, buf.glyph_positions):
            name = self.order[info.codepoint]
            if name not in defs:
                defs[name] = self.outline(name)
            if defs[name]:
                gx = x + (pen_x + pos.x_offset) * s
                gy = baseline - pos.y_offset * s
                out.append('<use xlink:href="#%s" transform="translate(%.2f %.2f) scale(%.5g %.5g)"/>'
                           % (glyph_id(name), gx, gy, s, -s))
            pen_x += pos.x_advance
        return "".join(out)


def glyph_id(name):
    return "glyph-" + "".join(c if c.isalnum() else "_" for c in name)


def glyph_defs(defs):
    return "".join('<path id="%s" d="%s"/>' % (glyph_id(n), d) for n, d in sorted(defs.items()) if d)


def slice_of(cx, cy, d):
    """The grapefruit slice of the Telegram picture, for a picture of diameter d at (cx, cy)."""
    r = d * 0.34
    r3 = r * 0.80
    w = r * 0.07
    spokes = []
    for i in range(10):
        a = 2 * math.pi * i / 10 - math.pi / 2
        spokes.append('<line x1="%.2f" y1="%.2f" x2="%.2f" y2="%.2f"/>'
                      % (cx, cy, cx + math.cos(a) * r3, cy + math.sin(a) * r3))
    return f"""<circle cx="{cx:.2f}" cy="{cy:.2f}" r="{r:.2f}" fill="{RIND}"/>
    <circle cx="{cx:.2f}" cy="{cy:.2f}" r="{r * 0.88:.2f}" fill="{PITH}"/>
    <circle cx="{cx:.2f}" cy="{cy:.2f}" r="{r3:.2f}" fill="{FLESH}"/>
    <g stroke="{PITH}" stroke-width="{w:.2f}">{"".join(spokes)}</g>
    <circle cx="{cx:.2f}" cy="{cy:.2f}" r="{r * 0.10:.2f}" fill="{PITH}"/>"""


def headphones(cx, cy, d):
    """Headphones on the slice of a picture of diameter d at (cx, cy): a band over its top and a cup
    over each side, all inside the circle (and an adaptive icon's 66dp safe zone)."""
    r = d * 0.355          # the band's radius, from a centre a little above the slice's
    by = cy - d * 0.06
    w = d * 0.058          # the band's thickness
    cup_w, cup_h = d * 0.135, d * 0.275
    cup_y = cy - d * 0.105
    cups = []
    for side in (-1, 1):
        x = cx + side * r - cup_w / 2
        # The cushion, toward the slice; the cup's shell outside it.
        pad_x = x if side > 0 else x + cup_w * 0.45
        cups.append(f"""<rect x="{x:.2f}" y="{cup_y:.2f}" width="{cup_w:.2f}" height="{cup_h:.2f}" rx="{cup_w * 0.42:.2f}" fill="{BAND}"/>
    <rect x="{pad_x:.2f}" y="{cup_y + cup_h * 0.12:.2f}" width="{cup_w * 0.55:.2f}" height="{cup_h * 0.76:.2f}" rx="{cup_w * 0.26:.2f}" fill="{CUSHION}"/>""")
    return f"""<path d="M{cx - r:.2f} {by:.2f}A{r:.2f} {r:.2f} 0 0 1 {cx + r:.2f} {by:.2f}" fill="none" stroke="{BAND}" stroke-width="{w:.2f}" stroke-linecap="round"/>
    {"".join(cups)}"""


def coral(uid, top, bottom):
    return f"""<linearGradient id="{uid}-coral" x1="0" y1="{top:.2f}" x2="0" y2="{bottom:.2f}" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="{TOP}"/><stop offset="1" stop-color="{BOTTOM}"/>
    </linearGradient>"""


def grapefruit(cx, cy, d, uid):
    """The Telegram picture, masked to a circle of diameter d centred at (cx, cy)."""
    return f"""<defs>
    {coral(uid, cy - d / 2, cy + d / 2)}
    <clipPath id="{uid}-round"><circle cx="{cx:.2f}" cy="{cy:.2f}" r="{d / 2:.2f}"/></clipPath>
  </defs>
  <g clip-path="url(#{uid}-round)">
    <rect x="{cx - d / 2:.2f}" y="{cy - d / 2:.2f}" width="{d:.2f}" height="{d:.2f}" fill="url(#{uid}-coral)"/>
    {slice_of(cx, cy, d)}
    {headphones(cx, cy, d)}
  </g>"""


def adaptive_layer(uid):
    """An adaptive icon's foreground: the Telegram picture filling the 108dp square, laid out for
    the 72dp in the middle that launchers show (masked to their shape: a circle, on most)."""
    return f"""<defs>{coral(uid, 18, 90)}</defs>
  <rect x="0" y="0" width="108" height="108" fill="url(#{uid}-coral)"/>
  {slice_of(54, 54, 72)}
  {headphones(54, 54, 72)}"""


def svg(width, height, title, body):
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<!-- Grapefruit Audio. Made by tools/brand/logo.py. Lettering: Geist Pixel Triangle
     (font-variation-settings: "ELSH" {TRIANGLE}), SIL Open Font License 1.1, as outlines. -->
<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" viewBox="0 0 {width:.2f} {height:.2f}" width="{width:.0f}" height="{height:.0f}" role="img" aria-label="{title}">
  <title>{title}</title>
  {body}
</svg>
"""


def horizontal(text, colours):
    d = 256.0
    size = 124.0
    gap = d * 0.17
    x = d + gap
    # The lettering's capitals sit centred on the grapefruit.
    baseline = d / 2 + size * text.cap / 2
    name, rest = NAME, " " + AUDIO
    w1 = text.width(name, size)
    w2 = text.width(rest, size)
    pad = size * 0.06
    width = x + w1 + w2 + pad
    defs = {}
    first = text.uses(name, size, x, baseline, defs)
    second = text.uses(rest, size, x + w1, baseline, defs)
    body = grapefruit(d / 2, d / 2, d, "g") + f"""
  <defs>{glyph_defs(defs)}</defs>
  <g fill="{colours['name']}">{first}</g>
  <g fill="{colours['rest']}">{second}</g>"""
    return svg(width, d, "Grapefruit Audio", body)


def stacked(text, colours):
    """The grapefruit, then "Grapefruit Audio" ("Grapefruit" in the name's colour), then the tagline."""
    d = 256.0
    big = 92.0
    medium = 44.0
    space = " "
    w_name = text.width(NAME + space, big)
    w1 = w_name + text.width(AUDIO, big)
    w2 = text.width(TAGLINE, medium)
    width = max(d, w1, w2) + 16
    cx = width / 2
    top = 8.0
    line1 = top + d + d * 0.16 + big * text.cap
    line2 = line1 + big * 0.40 + medium * text.cap
    height = line2 + medium * text.descent + 8
    defs = {}
    first = text.uses(NAME, big, cx - w1 / 2, line1, defs)
    audio = text.uses(AUDIO, big, cx - w1 / 2 + w_name, line1, defs)
    second = text.uses(TAGLINE, medium, cx - w2 / 2, line2, defs)
    body = grapefruit(cx, top + d / 2, d, "s") + f"""
  <defs>{glyph_defs(defs)}</defs>
  <g fill="{colours['name']}">{first}</g>
  <g fill="{colours['rest']}">{audio}{second}</g>"""
    return svg(width, height, "Grapefruit Audio: Music, Podcasts, and Audiobooks", body)


DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def png(svg_text, width, path):
    """Rendered with a transparent background, then made small with pngquant (a dithered palette,
    so the gradient doesn't band)."""
    import subprocess
    import cairosvg
    cairosvg.svg2png(bytestring=svg_text.encode(), output_width=round(width), write_to=str(path))
    subprocess.run(["pngquant", "--force", "--skip-if-larger", "--quality=80-100", "--speed=1",
                    "--strip", "--output", str(path), str(path)], check=False)


def android(text, res):
    """The splash screen's pictures: the stacked logo (light themes, dark themes) 280dp wide at each
    density, and Android 12's splash icon (the grapefruit, 192dp in a 288dp square)."""
    for density, scale in DENSITIES.items():
        folder = Path(res) / ("drawable-" + density)
        folder.mkdir(parents=True, exist_ok=True)
        png(stacked(text, LIGHT_BG), 280 * scale, folder / "splash_logo_light.png")
        png(stacked(text, DARK_BG), 280 * scale, folder / "splash_logo_dark.png")
    icon = svg(288, 288, "Grapefruit", grapefruit(144, 144, 192, "i"))
    folder = Path(res) / "drawable-xxhdpi"
    png(icon, 288 * 3, folder / "splash_icon.png")
    icons(res)
    # In the app's own assets, which take the place of the shared UI library's copy (lettered
    # for the old name) in the app.
    wordmark_font(text, Path(res).parent / "assets" / "fonts" / "GeistPixel-Triangle-Wordmark.ttf")


def icons(res):
    """The app's icon, the same in every theme: the launcher's (48dp; the grapefruit round, edge to
    edge but for a hairline), its adaptive layers for Android 8 and later (the picture filling a
    108dp layer, on the picture's own colour; launchers mask it, round on most), the About
    page's (88dp), and the store listing's (512 px)."""
    for density, scale in DENSITIES.items():
        mipmap = Path(res) / ("mipmap-" + density)
        mipmap.mkdir(parents=True, exist_ok=True)
        png(svg(48, 48, "Grapefruit", grapefruit(24, 24, 46, "l")), 48 * scale, mipmap / "ic_launcher.png")
        png(svg(108, 108, "Grapefruit", adaptive_layer("a")), 108 * scale, mipmap / "ic_launcher_foreground.png")
        drawable = Path(res) / ("drawable-" + density)
        png(svg(88, 88, "Grapefruit", grapefruit(44, 44, 88, "b")), 88 * scale, drawable / "ic_about.png")
    store_icon(Path(res).parents[3] / "fastlane" / "metadata" / "android" / "en-US" / "images" / "icon.png")
    anydpi = Path(res) / "mipmap-anydpi-v26"
    anydpi.mkdir(parents=True, exist_ok=True)
    (anydpi / "ic_launcher.xml").write_text("""<?xml version="1.0" encoding="utf-8"?>
<!-- Android 8 and later: the grapefruit picture as an adaptive icon (tools/brand/logo.py). -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/icon_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
""")


def store_icon(path):
    """The store listing's icon (F-Droid, 512 px): the launcher icon, larger."""
    png(svg(48, 48, "Grapefruit", grapefruit(24, 24, 46, "l")), 512, path)


WORDMARK = NAME + " " + AUDIO + TAGLINE


def wordmark_font(text, path):
    """The About page's wordmark, set in Geist Pixel Triangle: the font, as TTF (Android 4.2 can't
    read WOFF2), with only the letters of the name in it. (Geist Pixel's OFL names no Reserved Font
    Name, so a subset may keep its name.)"""
    from fontTools import subset
    options = subset.Options()
    options.layout_features = ["kern"]
    options.name_IDs = ["*"]
    options.notdef_outline = True
    sub = subset.Subsetter(options)
    font = TTFont(io.BytesIO(text_font_bytes(text)))
    sub.populate(text=WORDMARK)
    sub.subset(font)
    font.flavor = None
    path.parent.mkdir(parents=True, exist_ok=True)
    font.save(path)


def text_font_bytes(text):
    data = io.BytesIO()
    text.font.save(data)
    return data.getvalue()


def main():
    geist, out = sys.argv[1], Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    text = Lettering(geist)
    (out / "logo.svg").write_text(horizontal(text, LIGHT_BG))
    (out / "logo-dark.svg").write_text(horizontal(text, DARK_BG))
    (out / "logo-stacked.svg").write_text(stacked(text, LIGHT_BG))
    (out / "logo-stacked-dark.svg").write_text(stacked(text, DARK_BG))
    for f in sorted(out.glob("logo*.svg")):
        print(f, f.stat().st_size, "bytes")
    if len(sys.argv) > 3:
        android(text, sys.argv[3])


if __name__ == "__main__":
    main()
