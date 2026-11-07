#!/bin/sh
# Export the approved app-specific master into Android 4.x bitmap densities.
set -eu
module_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
for pair in mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192; do
    density=${pair%:*}
    pixels=${pair#*:}
    target="$module_dir/src/main/res/mipmap-$density/ic_launcher_calculator.png"
    mkdir -p "$(dirname -- "$target")"
    convert "$module_dir/brand/icon.png" -resize "${pixels}x${pixels}" -strip "PNG32:$target"
done
