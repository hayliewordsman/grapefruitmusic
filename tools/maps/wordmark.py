#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Build the Maps-specific Geist Pixel Triangle font and outlined wordmarks.

Run: python3 tools/maps/wordmark.py
Requires fontTools. The shared static Triangle font is Vercel's Geist Pixel
under SIL OFL 1.1 (grapefruit-ui/src/main/assets/fonts/Geist-OFL.txt).
The font is subset to 'Grapefruit Maps' and saved as static TTF for Android 4.2.2.
SVG masters use glyph outlines so previews do not depend on installed fonts.
"""
from pathlib import Path
from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen

ROOT = Path(__file__).resolve().parents[2]
WORDMARK = 'Grapefruit Maps'
SOURCE = ROOT / 'grapefruit-ui/src/main/assets/fonts/GeistPixel-Triangle-Wordmark.ttf'
OUTPUT = ROOT / 'maps/src/main/assets/fonts/GeistPixel-Triangle-Maps.ttf'


def main():
    font = TTFont(SOURCE, recalcTimestamp=False)
    assert 'fvar' not in font, 'Use a static Triangle font for old Android devices'
    assert set(map(ord, WORDMARK)).issubset(font.getBestCmap()), 'Missing wordmark glyphs'
    options = subset.Options()
    options.layout_features = ['kern']
    options.name_IDs = ['*']
    options.notdef_outline = True
    subsetter = subset.Subsetter(options)
    subsetter.populate(text=WORDMARK)
    subsetter.subset(font)
    font.flavor = None
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    font.save(OUTPUT)
    glyphs, cmap = font.getGlyphSet(), font.getBestCmap()
    scale = 40 / font['head'].unitsPerEm
    elements, advance = [], 0
    for i, char in enumerate(WORDMARK):
        name = cmap[ord(char)]
        pen = SVGPathPen(glyphs)
        glyphs[name].draw(pen)
        if pen.getCommands():
            color = 'ACCENT' if i < len('Grapefruit') else 'TEXT'
            elements.append(f'<path fill="{color}" transform="translate({advance:.3f} 48) scale({scale:.8f} {-scale:.8f})" d="{pen.getCommands()}"/>')
        advance += font['hmtx'][name][0] * scale
    brand = ROOT / 'maps/brand'
    for theme, accent, text in [('dark', '#FF7F6B', '#FFF1E8'), ('light', '#E83850', '#4A1622'), ('black', '#FF7F6B', '#FFF1E8'), ('blue', '#67C7FF', '#EAF4FF')]:
        paths = '\n'.join(elements).replace('ACCENT', accent).replace('TEXT', text)
        svg = f'<svg xmlns="http://www.w3.org/2000/svg" width="{advance:.3f}" height="64" viewBox="0 0 {advance:.3f} 64"><title>Grapefruit Maps</title><desc>Geist Pixel Triangle by Vercel, SIL OFL 1.1. Custom Maps wordmark.</desc>\n{paths}\n</svg>\n'
        (brand / f'wordmark-{theme}.svg').write_text(svg)
    print(f'{OUTPUT.relative_to(ROOT)}: static TTF, {len(font.getBestCmap())} glyphs, {OUTPUT.stat().st_size} bytes')
    print(f'Wordmark width at 32sp: {advance * 32 / 40:.1f}sp before fitting')


if __name__ == '__main__':
    main()
