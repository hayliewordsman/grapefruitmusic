#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Compile/link the generated keyboard drawables with public SDK resources."""
import argparse
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk-root", type=Path, required=True)
    args = parser.parse_args()
    aapt = args.sdk_root / "build-tools/35.0.0/aapt2"
    android = args.sdk_root / "platforms/android-36/android.jar"
    with tempfile.TemporaryDirectory() as directory:
        scratch = Path(directory)
        manifest = scratch / "AndroidManifest.xml"
        manifest.write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
                            'package="dev.grapefruit.keyboard.resources"><uses-sdk android:minSdkVersion="31" '
                            'android:targetSdkVersion="36"/><application/></manifest>')
        compiled = scratch / "compiled.zip"
        subprocess.run([str(aapt), "compile", "--dir", str(ROOT / "os/platform/latinime/res"),
                        "-o", str(compiled)], check=True)
        subprocess.run([str(aapt), "link", "-I", str(android), "--manifest", str(manifest),
                        "-o", str(scratch / "resource-check.apk"), str(compiled)], check=True)
    print("Keyboard drawable resource linking passed. Full LatinIME build remains an AOSP check.")


if __name__ == "__main__":
    main()
