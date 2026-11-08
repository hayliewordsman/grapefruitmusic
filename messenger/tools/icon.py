#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Draw Grapefruitgram Messenger's launcher and notification icons.

A cream speech bubble holding a grapefruit slice, on the Grapefruit apps' coral-to-ruby gradient.
Original artwork (no Telegram marks). Run from the repository root:

    python3 messenger/tools/icon.py

Requires CairoSVG. Writes messenger/brand/*.svg and the PNGs under messenger/overlay/app/src/main/res.
"""
import math
from pathlib import Path

import cairosvg

ROOT = Path(__file__).resolve().parents[1]
BRAND = ROOT / "brand"
RES = ROOT / "overlay" / "app" / "src" / "main" / "res"
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}

GRADIENT = '<linearGradient id="bg" x1="0" y1="0" x2="0" y2="1"><stop stop-color="#FF8C6B"/><stop offset="1" stop-color="#EC485C"/></linearGradient>'
# Rounded bubble with a tail at the lower left.
BUBBLE = ("M256 92 C352 92 428 158 428 240 C428 322 352 388 256 388 C232 388 209 384 188 377 "
          "L112 414 C104 418 96 410 100 402 L124 344 C98 316 84 280 84 240 C84 158 160 92 256 92 Z")


def slice_paths(cx, cy, r, colors=("#EC3B56", "#F2545B")):
    out = []
    for i in range(8):
        a = math.radians(i * 45 + 4)
        z = math.radians((i + 1) * 45 - 4)
        x1, y1 = cx + math.cos(a) * r, cy + math.sin(a) * r
        x2, y2 = cx + math.cos(z) * r, cy + math.sin(z) * r
        out.append(f'<path d="M{cx} {cy} L{x1:.3f} {y1:.3f} A{r} {r} 0 0 1 {x2:.3f} {y2:.3f} Z" fill="{colors[i % 2]}"/>')
    return "\n  ".join(out)


def artwork():
    return f'''<path d="{BUBBLE}" transform="translate(0 10)" fill="#851936" opacity=".22"/>
  <path d="{BUBBLE}" fill="#FFF7EE"/>
  <circle cx="256" cy="240" r="112" fill="#FFD6C8"/>
  <circle cx="256" cy="240" r="100" fill="#FFF7EE"/>
  {slice_paths(256, 240, 92)}
  <circle cx="256" cy="240" r="6" fill="#FFF7EE"/>'''


def icon(round_shape):
    shape = '<circle cx="256" cy="256" r="256" fill="url(#bg)"/>' if round_shape else '<rect width="512" height="512" rx="112" fill="url(#bg)"/>'
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">'
            f'<title>Grapefruitgram Messenger</title>'
            f'<desc>A speech bubble holding a grapefruit slice. Original GPL-3.0-or-later artwork.</desc>'
            f'<defs>{GRADIENT}</defs>{shape}\n  {artwork()}</svg>\n')


def notification():
    # Status-bar icons are white silhouettes: the bubble, with the slice's segments cut out.
    # One path, even-odd: the wedges sit inside the bubble without overlapping, so each is a hole.
    cx, cy, r = 256, 240, 104
    wedges = []
    for i in range(8):
        a = math.radians(i * 45 + 6)
        z = math.radians((i + 1) * 45 - 6)
        x1, y1 = cx + math.cos(a) * r, cy + math.sin(a) * r
        x2, y2 = cx + math.cos(z) * r, cy + math.sin(z) * r
        ox, oy = cx + math.cos((a + z) / 2) * 14, cy + math.sin((a + z) / 2) * 14
        wedges.append(f"M{ox:.3f} {oy:.3f} L{x1:.3f} {y1:.3f} A{r} {r} 0 0 1 {x2:.3f} {y2:.3f} Z")
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">'
            f'<path d="{BUBBLE} {" ".join(wedges)}" fill="#FFFFFF" fill-rule="evenodd"/></svg>\n')


def main():
    BRAND.mkdir(parents=True, exist_ok=True)
    sources = {"app_launcher": icon(False), "app_launcher_round": icon(True), "app_notification": notification()}
    for name, svg in sources.items():
        (BRAND / f"{name}.svg").write_text(svg, encoding="utf-8")
    for density, scale in DENSITIES.items():
        folder = RES / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        for name, svg in sources.items():
            size = round((24 if name == "app_notification" else 48) * scale)
            cairosvg.svg2png(bytestring=svg.encode(), write_to=str(folder / f"{name}.png"),
                             output_width=size, output_height=size)
    print("icons written")


if __name__ == "__main__":
    main()
