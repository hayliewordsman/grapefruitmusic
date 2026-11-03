#!/usr/bin/env python3
"""Build Android density assets and an outlined Geist Pixel Triangle wordmark.
Requires Pillow and fontTools. The approved TV illustration is retained in docs/fast/brand/icon.png.
Run from repo root: python3 tools/fast/brand.py
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
import base64

root=Path(__file__).resolve().parents[2]
brand=root/'docs/fast/brand';res=root/'fast/src/main/res'
icon=Image.open(brand/'icon.png').convert('RGBA')
for density,size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    out=res/('mipmap-'+density);out.mkdir(parents=True,exist_ok=True)
    icon.resize((size,size),Image.Resampling.LANCZOS).save(out/'ic_launcher.png')
fontfile=root/'fast/src/main/assets/fonts/GeistPixel-Triangle-FAST.ttf'
font=TTFont(fontfile);glyphs=font.getGlyphSet();cmap=font.getBestCmap();scale=40/font['head'].unitsPerEm
x=0;paths=[]
for char in 'Grapefruit FAST':
    name=cmap[ord(char)];pen=SVGPathPen(glyphs);glyphs[name].draw(pen)
    paths.append(f'<path transform="translate({x:.3f} 0)" d="{pen.getCommands()}"/>');x+=font['hmtx'][name][0]*scale
encoded=base64.b64encode((brand/'icon.png').read_bytes()).decode()
word='';x=0
for char in 'Grapefruit FAST':
    name=cmap[ord(char)];pen=SVGPathPen(glyphs);glyphs[name].draw(pen)
    word+=f'<path transform="translate({160+x:.3f} 91) scale({scale:.6f} {-scale:.6f})" d="{pen.getCommands()}"/>';x+=font['hmtx'][name][0]*scale
(brand/'logo.svg').write_text(f'<svg xmlns="http://www.w3.org/2000/svg" width="{int(180+x)}" height="144" viewBox="0 0 {int(180+x)} 144"><title>Grapefruit FAST</title><image x="0" y="0" width="144" height="144" href="data:image/png;base64,{encoded}"/><g fill="#FF7F6B">{word}</g></svg>\n')
# Android TV's banner is 320 x 180, with the name readable at launcher size.
banner=Image.new('RGBA',(320,180),'#1C0A0E');banner.alpha_composite(icon.resize((156,156),Image.Resampling.LANCZOS),(82,-7))
draw=ImageDraw.Draw(banner);textfont=ImageFont.truetype(str(fontfile),23);label='Grapefruit FAST'
width=draw.textlength(label,font=textfont);draw.text(((320-width)/2,140),label,font=textfont,fill='#FF7F6B')
out=res/'drawable-nodpi';out.mkdir(parents=True,exist_ok=True);banner.convert('RGB').save(out/'tv_banner.png')
