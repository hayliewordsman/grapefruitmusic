#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Blue Grapefruit's approved headset-free icon and two-line Geist Pixel Triangle wordmark.

Uses the original fruit geometry and verified Triangle font instance. Standard Grapefruit assets
are never overwritten. Run: python tools/brand/blue_victory.py path/to/geist/package
"""
import importlib.util
from pathlib import Path
import sys
import base64
import cairosvg

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / 'blue-victory/src/main/res'
BRAND = ROOT / 'blue-victory/brand'
ASSETS = ROOT / 'blue-victory/src/main/assets'

def png(source, width, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    cairosvg.svg2png(bytestring=source.encode(), output_width=round(width), write_to=str(path))

def stacked(original, text, colours):
    d, big, medium = 256.0, 92.0, 92.0 * 0.78
    primary, secondary = 'Blue Grapefruit', 'Victory Edition'
    first_width = text.width(primary, big)
    second_width = text.width(secondary, medium)
    width = max(d, first_width, second_width) + 8
    cx, top = width / 2, 8.0
    line1 = top + d + d * 0.16 + big * text.cap
    line2 = line1 + big * 0.40 + medium * text.cap
    height = line2 + medium * text.descent + 8
    definitions = {}
    name = text.uses('Blue', big, cx - first_width / 2, line1, definitions)
    rest = text.uses('Grapefruit', big, cx - first_width / 2 + text.width('Blue ', big), line1, definitions)
    tagline = text.uses(secondary, medium, cx - second_width / 2, line2, definitions)
    body = original.grapefruit(cx, top + d / 2, d, 's') + f'''
  <defs>{original.glyph_defs(definitions)}</defs>
  <g fill="{colours['name']}">{name}</g>
  <g fill="{colours['rest']}">{rest}{tagline}</g>'''
    return original.svg(width, height, 'Blue Grapefruit: Victory Edition', body)

def main():
    geist = Path(sys.argv[1])
    spec = importlib.util.spec_from_file_location('original_brand', ROOT / 'tools/brand/logo.py')
    original = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(original)
    original.TOP, original.BOTTOM = '#75CEFF', '#2368D8'
    original.RIND, original.PITH, original.FLESH = '#F0F8FF', '#BDDDF6', '#246CE0'
    # Omit the headphones entirely, retaining the exact original fruit drawing.
    original.headphones = lambda cx, cy, d: ''
    # Owner-approved Beta 2 artwork. Embed the supplied PNG without changing its design.
    approved = BRAND / 'approved-icon.png'
    if approved.is_file():
        data = base64.b64encode(approved.read_bytes()).decode('ascii')
        original.grapefruit = lambda cx, cy, d, uid: f'<image x="{cx-d/2:.2f}" y="{cy-d/2:.2f}" width="{d:.2f}" height="{d:.2f}" xlink:href="data:image/png;base64,{data}"/>'
        original.adaptive_layer = lambda uid: original.grapefruit(54, 54, 66, uid)

    original.NAME, original.AUDIO, original.TAGLINE = 'Blue', 'Grapefruit', 'Victory Edition'
    original.WORDMARK = 'Blue GrapefruitVictory Edition'
    text = original.Lettering(geist)
    light_colours = dict(name='#2373D7', rest='#102C50')
    dark_colours = dict(name='#67C7FF', rest='#EAF4FF')
    light, dark = stacked(original, text, light_colours), stacked(original, text, dark_colours)
    BRAND.mkdir(parents=True, exist_ok=True)
    (BRAND / 'logo-stacked.svg').write_text(light)
    (BRAND / 'logo-stacked-dark.svg').write_text(dark)
    for filename, colours in [('logo.svg', light_colours), ('logo-dark.svg', dark_colours)]:
        source = original.horizontal(text, colours).replace('Grapefruit Audio', 'Blue Grapefruit')
        (BRAND / filename).write_text(source)
    for qualifier, width in original.SPLASH_WIDTHS:
        for density, scale in original.DENSITIES.items():
            folder = RES / ('drawable' + qualifier + '-' + density)
            png(dark, width * scale, folder / 'splash_logo_dark.png')
            png(light, width * scale, folder / 'splash_logo_light.png')
    icon = original.svg(288, 288, 'Blue Grapefruit', original.grapefruit(144, 144, 192, 'i'))
    png(icon, 864, RES / 'drawable-xxhdpi/splash_icon.png')
    for density, scale in original.DENSITIES.items():
        png(original.svg(48, 48, 'Blue Grapefruit', original.grapefruit(24, 24, 46, 'l')),
            48 * scale, RES / ('mipmap-' + density) / 'ic_launcher.png')
        png(original.svg(108, 108, 'Blue Grapefruit', original.adaptive_layer('a')),
            108 * scale, RES / ('mipmap-' + density) / 'ic_launcher_foreground.png')
        png(original.svg(88, 88, 'Blue Grapefruit', original.grapefruit(44, 44, 88, 'b')),
            88 * scale, RES / ('drawable-' + density) / 'ic_about.png')
    original.wordmark_font(text, ASSETS / 'fonts/GeistPixel-Triangle-Wordmark.ttf')
    print('Blue Grapefruit / Victory Edition generated in Geist Pixel Triangle, without headphones.')

if __name__ == '__main__':
    main()
