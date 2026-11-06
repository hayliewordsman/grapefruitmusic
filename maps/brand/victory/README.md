# Victory Break caricature

`head-source.png` is the original generated, transparent caricature artwork,
supplied as GPL-3.0-or-later artwork with this project. It depicts a satirical
side profile of Donald Trump wearing an unlettered red cap. No third-party
photograph, emoji font or external image pack is used.

The editable PNG source is 1254 × 1254 RGBA. `head-prompt.txt` records the
generation prompt; the image-generation tool's transparent-background option
was enabled. The original generated file is preserved in source. A free raster
editor can change the source directly; generating another image is not a build
requirement.

`python3 tools/maps/victory_assets.py` packages a 384 × 384 PNG for the APK
using Pillow. It decodes to about 576 KiB and is drawn locally on Android 4.2.2
and later. The large source PNG is not included in the APK. No image service is
called by the app or its normal build.

The mouth origin is normalized to (0.844, 0.688) within the source image.
`EggView.java` renders the sprite, mouth origin, mainland outline and flying
poop on the same canvas, so the animation starts at the mouth at any density.
The poop and map artwork remain original GPL-3.0-or-later Canvas shapes. The
retained drops are bounded to 100, while the counter records all clicks.
