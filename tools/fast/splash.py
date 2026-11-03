#!/usr/bin/env python3
"""Splash screen artwork for Grapefruit FAST, from the approved TV icon (docs/fast/brand/icon.png).
Requires Pillow. Run from repo root: python3 tools/fast/splash.py

- drawable-*/splash_logo.png: the icon at 120dp for each density (the in-app splash, the About page
  and the window background Android shows while the app starts on Android 4.2 to 11).
- drawable-xxhdpi/splash_icon.png: Android 12+'s own splash icon. Android masks it to a 192dp
  circle inside its 288dp square, so the television is kept to 132dp, clear of the mask.
"""
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parents[2]
res = root / 'fast/src/main/res'
icon = Image.open(root / 'docs/fast/brand/icon.png').convert('RGBA')
for density, scale in [('mdpi', 1), ('hdpi', 1.5), ('xhdpi', 2), ('xxhdpi', 3), ('xxxhdpi', 4)]:
    out = res / ('drawable-' + density)
    out.mkdir(parents=True, exist_ok=True)
    size = round(120 * scale)
    icon.resize((size, size), Image.Resampling.LANCZOS).save(out / 'splash_logo.png', optimize=True)
canvas = Image.new('RGBA', (288 * 3, 288 * 3), (0, 0, 0, 0))
tv = icon.resize((132 * 3, 132 * 3), Image.Resampling.LANCZOS)
canvas.alpha_composite(tv, ((canvas.width - tv.width) // 2, (canvas.height - tv.height) // 2))
canvas.save(res / 'drawable-xxhdpi/splash_icon.png', optimize=True)
