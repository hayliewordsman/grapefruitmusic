#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Subset Geist 1.9.0's static Triangle font for Grapefruit Launcher (fontTools + brotli).

python tools/launcher/wordmark.py path/to/unpacked/geist/package
The upstream font is SIL OFL 1.1; its licence is bundled in the shared font assets.
"""
import sys
from pathlib import Path
from fontTools import subset
from fontTools.ttLib import TTFont

text = "Grapefruit Launcher"
source = Path(sys.argv[1]) / "dist/fonts/geist-pixel/GeistPixel-Triangle.woff2"
target = Path(__file__).resolve().parents[2] / "launcher/src/main/assets/fonts/GeistPixel-Triangle-Launcher.ttf"
font = TTFont(source, recalcTimestamp=False)
assert set(map(ord, text)) <= set(font.getBestCmap())
options = subset.Options()
options.layout_features = ["kern"]
options.name_IDs = ["*"]
options.notdef_outline = True
subsetter = subset.Subsetter(options)
subsetter.populate(text=text)
subsetter.subset(font)
font.flavor = None
target.parent.mkdir(parents=True, exist_ok=True)
font.save(target)
assert set(map(ord, text)) <= set(TTFont(target).getBestCmap())
print(target)
