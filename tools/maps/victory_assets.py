#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Package the editable Victory Break source artwork for old Android devices.

Requires Pillow. Run from the repo root: python3 tools/maps/victory_assets.py
Only converts the bundled source PNG to a smaller PNG; no service is contacted.
"""
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
source = ROOT / 'maps/brand/victory/head-source.png'
output = ROOT / 'maps/src/main/res/drawable-nodpi/victory_head.png'
with Image.open(source) as image:
    assert image.mode == 'RGBA', 'Keep the transparent source artwork'
    sprite = image.resize((384, 384), Image.Resampling.LANCZOS)
    output.parent.mkdir(parents=True, exist_ok=True)
    sprite.save(output, optimize=True)
print(f'{output.relative_to(ROOT)}: 384 x 384, {output.stat().st_size} bytes')
