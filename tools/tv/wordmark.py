#!/usr/bin/env python3
"""Grapefruit TV's wordmark font: Geist Pixel in its Triangle shape (Vercel, SIL Open Font License 1.1),
from the geist npm package (1.9.0, as tools/brand/logo.py uses), cut down to the letters of
"Grapefruit TV & Movies" and saved as TTF (Android 4.2 can't read WOFF2). Geist Pixel's OFL names no Reserved
Font Name, so the subset may keep its name. Needs fontTools and brotli.

  npm pack geist@1.9.0 && tar -xzf geist-1.9.0.tgz
  python3 tools/tv/wordmark.py package
"""
import sys
from pathlib import Path
from fontTools import subset
from fontTools.ttLib import TTFont

WORDMARK = "Grapefruit TV & Movies"
root = Path(__file__).resolve().parents[2]
source = Path(sys.argv[1]) / "dist/fonts/geist-pixel/GeistPixel-Triangle.woff2"
out = root / "tv/src/main/assets/fonts/GeistPixel-Triangle-TV.ttf"
font = TTFont(source)
options = subset.Options()
options.layout_features = ["kern"]
options.name_IDs = ["*"]
options.notdef_outline = True
sub = subset.Subsetter(options)
sub.populate(text=WORDMARK)
sub.subset(font)
font.flavor = None
font.save(out)
print(out, sorted(chr(c) for c in TTFont(out).getBestCmap()))
