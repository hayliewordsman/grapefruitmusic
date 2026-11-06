#!/usr/bin/env python3
"""Splash screen and About artwork for Grapefruit TV & Movies, as Music's (tools/brand/logo.py): from the
approved TV icon (docs/tv/brand/icon.png) and Geist Pixel Triangle
(tv/src/main/assets/fonts/GeistPixel-Triangle-TV.ttf). Requires Pillow.
Run from repo root: python3 tools/tv/splash.py

- drawable-*/splash_logo_dark.png, splash_logo_light.png: the stacked logo, 280dp wide at each
  density: the television over "Grapefruit" (accent) and "TV & Movies" (text colour), for the dark and
  light themes. Baked into a picture like Music's, so it shows the moment the app starts (it's also
  the window background Android draws before the app's first frame on Android 4.2 to 11).
- drawable-*/ic_about.png: the television at exactly 88dp for the About page.
- drawable-xxhdpi/splash_icon.png: Android 12+'s own splash icon. Android masks it to a 192dp
  circle inside its 288dp square, so the television is kept to 132dp, clear of the mask.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

root = Path(__file__).resolve().parents[2]
res = root / 'tv/src/main/res'
icon = Image.open(root / 'docs/tv/brand/icon.png').convert('RGBA')
FONT = str(root / 'tv/src/main/assets/fonts/GeistPixel-Triangle-TV.ttf')
DENSITIES = [('mdpi', 1), ('hdpi', 1.5), ('xhdpi', 2), ('xxhdpi', 3), ('xxxhdpi', 4)]
# Brand.java: accent and text colours of Grapefruit Dark and Grapefruit Light.
THEMES = {'dark': ('#FF7F6B', '#FFF1E8'), 'light': ('#E83850', '#4A1622')}


def stacked(accent, text, k=8):
    """The stacked logo at k pixels per dp (drawn large, scaled down for each density)."""
    width = 280 * k
    big = ImageFont.truetype(FONT, 100)
    size = 100 * width / big.getlength('Grapefruit')  # "Grapefruit" spans the width, as in Music
    top_font = ImageFont.truetype(FONT, round(size))
    fast_font = ImageFont.truetype(FONT, round(size * 0.8))
    while fast_font.getlength('TV & Movies') > width:  # the second line never wider than "Grapefruit"
        fast_font = ImageFont.truetype(FONT, fast_font.size - 1)
    tv = icon.resize((176 * k, 176 * k), Image.Resampling.LANCZOS)
    a = top_font.getbbox('Grapefruit')
    b = fast_font.getbbox('TV & Movies')
    gap = 10 * k
    height = tv.height + gap + (a[3] - a[1]) + 8 * k + (b[3] - b[1]) + 6 * k  # room for the triangles' overshoot
    out = Image.new('RGBA', (width, height), (0, 0, 0, 0))
    out.alpha_composite(tv, ((width - tv.width) // 2, 0))
    draw = ImageDraw.Draw(out)
    y = tv.height + gap
    draw.text(((width - top_font.getlength('Grapefruit')) / 2, y - a[1]), 'Grapefruit', font=top_font, fill=accent)
    y += a[3] - a[1] + 8 * k
    draw.text(((width - fast_font.getlength('TV & Movies')) / 2, y - b[1]), 'TV & Movies', font=fast_font, fill=text)
    return out


for name, (accent, text) in THEMES.items():
    logo = stacked(accent, text)
    for density, scale in DENSITIES:
        out = res / ('drawable-' + density)
        out.mkdir(parents=True, exist_ok=True)
        w = round(280 * scale)
        logo.resize((w, round(logo.height * w / logo.width)), Image.Resampling.LANCZOS).save(
            out / ('splash_logo_%s.png' % name), optimize=True)
for density, scale in DENSITIES:
    out = res / ('drawable-' + density)
    icon.resize((round(88 * scale),) * 2, Image.Resampling.LANCZOS).save(out / 'ic_about.png', optimize=True)
    old = out / 'splash_logo.png'
    if old.exists():
        old.unlink()
canvas = Image.new('RGBA', (288 * 3, 288 * 3), (0, 0, 0, 0))
tv = icon.resize((132 * 3, 132 * 3), Image.Resampling.LANCZOS)
canvas.alpha_composite(tv, ((canvas.width - tv.width) // 2, (canvas.height - tv.height) // 2))
canvas.save(res / 'drawable-xxhdpi/splash_icon.png', optimize=True)
