#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Draw Grapefruit Maps' original map-and-pin icon and Android launcher assets.

Run from the repository root: python3 tools/maps/icon.py
Requires CairoSVG. All shapes are original GPL-3.0-or-later vector artwork;
no external images or fonts are used. SVG masters remain editable in maps/brand.
"""
from pathlib import Path
import math
import cairosvg

ROOT = Path(__file__).resolve().parents[2]
BRAND = ROOT / 'maps/brand'
RES = ROOT / 'maps/src/main/res'


def artwork():
    segments = []
    for i in range(8):
        a = math.radians(i * 45 + 3)
        z = math.radians((i + 1) * 45 - 3)
        x1, y1 = 256 + math.cos(a) * 43, 170 + math.sin(a) * 43
        x2, y2 = 256 + math.cos(z) * 43, 170 + math.sin(z) * 43
        segments.append(f'<path d="M256 170 L{x1:.3f} {y1:.3f} A43 43 0 0 1 {x2:.3f} {y2:.3f} Z" fill="{["#EC3B56", "#F2545B"][i % 2]}"/>')
    return '''
  <defs>
    <clipPath id="map"><path d="M72 214 L194 176 L318 212 L440 174 L440 382 L318 420 L194 384 L72 422 Z"/></clipPath>
    <linearGradient id="pin" x1="0" y1="0" x2="0.8" y2="1"><stop stop-color="#D63250"/><stop offset="1" stop-color="#A71E40"/></linearGradient>
  </defs>
  <path d="M72 226 L194 188 L318 224 L440 186 L440 394 L318 432 L194 396 L72 434 Z" fill="#851936" opacity=".2"/>
  <path d="M72 214 L194 176 L194 384 L72 422 Z" fill="#FFF7EE"/>
  <path d="M194 176 L318 212 L318 420 L194 384 Z" fill="#FFD6C8"/>
  <path d="M318 212 L440 174 L440 382 L318 420 Z" fill="#FFF7EE"/>
  <g clip-path="url(#map)" fill="none" stroke-linejoin="round">
    <path d="M48 356 L166 318 L226 340 L312 304 L462 346" stroke="#E8BEA4" stroke-width="29"/>
    <path d="M48 356 L166 318 L226 340 L312 304 L462 346" stroke="#FFFFFF" stroke-width="14"/>
    <path d="M122 180 L122 270 L164 318 L164 446 M380 172 L380 282 L334 316 L334 434" stroke="#E8BEA4" stroke-width="21"/>
    <path d="M122 180 L122 270 L164 318 L164 446 M380 172 L380 282 L334 316 L334 434" stroke="#FFFFFF" stroke-width="10"/>
    <path d="M194 176 L194 384 M318 212 L318 420" stroke="#9B644B" stroke-opacity=".12" stroke-width="4"/>
  </g>
  <ellipse cx="256" cy="315" rx="42" ry="13" fill="#87233A" opacity=".18"/>
  <path d="M256 80 C204 80 166 118 166 170 C166 225 256 310 256 310 C256 310 346 225 346 170 C346 118 308 80 256 80 Z" fill="url(#pin)" stroke="#FFF7EE" stroke-width="7" stroke-linejoin="round"/>
  <circle cx="256" cy="170" r="56" fill="#FFF7EE"/>
  <circle cx="256" cy="170" r="49" fill="#FFD6C8"/>
''' + '\n'.join(segments) + '\n  <circle cx="256" cy="170" r="3" fill="#FFF7EE"/>'


def svg(foreground=False):
    background = '' if foreground else '''<defs><linearGradient id="background" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#FF8C6B"/><stop offset="1" stop-color="#EC485C"/></linearGradient></defs><rect width="512" height="512" rx="112" fill="url(#background)"/>'''
    art = artwork()
    if foreground:
        # Leave the adaptive launcher's central safe zone clear of mask clipping.
        art = '<g transform="translate(256 256) scale(.66) translate(-256 -256)">' + art + '</g>'
    return '<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512"><title>Grapefruit Maps</title><desc>A folded map and a grapefruit location pin. Original GPL-3.0-or-later artwork.</desc>' + background + art + '</svg>\n'


def main():
    BRAND.mkdir(parents=True, exist_ok=True)
    normal, foreground = svg(), svg(True)
    (BRAND / 'icon.svg').write_text(normal)
    (BRAND / 'icon-foreground.svg').write_text(foreground)
    for density, size, adaptive in [('mdpi', 48, 108), ('hdpi', 72, 162), ('xhdpi', 96, 216), ('xxhdpi', 144, 324), ('xxxhdpi', 192, 432)]:
        directory = RES / ('mipmap-' + density)
        directory.mkdir(parents=True, exist_ok=True)
        for source, name, pixels in [(normal, 'ic_launcher.png', size), (foreground, 'ic_launcher_foreground.png', adaptive)]:
            cairosvg.svg2png(bytestring=source.encode(), write_to=str(directory / name), output_width=pixels, output_height=pixels)
        about = RES / ('drawable-' + density)
        about.mkdir(parents=True, exist_ok=True)
        about_size = size * 88 // 48
        cairosvg.svg2png(bytestring=normal.encode(), write_to=str(about / 'ic_about.png'), output_width=about_size, output_height=about_size)
    print('Wrote original Maps SVGs, legacy and adaptive icons, and density-specific 88dp About artwork.')


if __name__ == '__main__':
    main()
