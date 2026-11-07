#!/usr/bin/env bash
# Source compilation only. Intentionally no image, OTA, boot or device-writing targets.
set -euo pipefail
if [[ $# != 1 || ! -f "$1/build/envsetup.sh" || ! -d "$1/.repo" ]]; then
    echo 'Usage: build_framework.sh /absolute/path/to/full/patched/AOSP-4.2.2_r1' >&2
    exit 2
fi
cd "$1"
if ! java -version 2>&1 | rg -q 'version "1\.6\.'; then
    echo 'Original AOSP 4.2.2 build requires a supported JDK 6 host; current Java is incompatible.' >&2
    exit 2
fi
source build/envsetup.sh
export GRAPEFRUIT_FRAMEWORK_ONLY=true
lunch grapefruit_jp5s-userdebug
# BUILD_PACKAGE can compile these without guessing the JP5S partition/ramdisk geometry.
make -j"${GRAPEFRUIT_BUILD_JOBS:-4}" framework-res Settings SystemUI
