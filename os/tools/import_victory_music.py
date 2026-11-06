#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Retag the owner's approved Victory APK music as Bonus Bundled Content for OEM builds.

All extracted audio, bundle material and review files remain in the private output
directory. The original APK and its compressed audio are never modified.
Requires the dependencies used by tools/blue_victory/prepare.py and FFmpeg.
"""
import argparse
import hashlib
import hmac
import json
import os
from pathlib import Path
import subprocess
import sys
import zipfile
from cryptography.hazmat.primitives import padding
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

ROOT = Path(__file__).resolve().parents[2]


def unpack(data, key):
    if len(key) != 48 or len(data) < 68 or data[:4] != b"BGV1":
        raise ValueError("Invalid Victory bundle")
    if not hmac.compare_digest(hmac.digest(key[16:], data[:-32], "sha256"), data[-32:]):
        raise ValueError("Victory bundle authentication failed")
    decoder = Cipher(algorithms.AES(key[:16]), modes.CBC(data[4:20])).decryptor()
    raw = decoder.update(data[20:-32]) + decoder.finalize()
    unpad = padding.PKCS7(128).unpadder()
    return unpad.update(raw) + unpad.finalize()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--sha256", required=True, help="Expected hash from the approved release record")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if hashlib.sha256(args.apk.read_bytes()).hexdigest() != args.sha256:
        raise ValueError("APK does not match the approved release checksum")
    private = args.output.resolve()
    if private.exists() and any(private.iterdir()):
        raise ValueError("Use an empty private output directory to preserve previous review inputs")
    if private.is_relative_to(ROOT):
        ignored = subprocess.run(["git", "check-ignore", "--quiet", str(private)], cwd=ROOT)
        if ignored.returncode:
            raise ValueError("The private output must be outside the repository or ignored by Git")
    private.mkdir(parents=True, exist_ok=True, mode=0o700)
    raw_dir = private / "original-tagged-copies"
    raw_dir.mkdir(mode=0o700)
    metadata = json.loads((ROOT / "tools/blue_victory/metadata.json").read_text())
    with zipfile.ZipFile(args.apk) as apk:
        prefix = "assets/victory/"
        catalog = json.loads(apk.read(prefix + "catalog.json"))
        if catalog.get("reviewStatus") != "approved" or len(catalog["tracks"]) != 5:
            raise ValueError("The approved five-track music catalog is required")
        key = apk.read(prefix + "bundle.key")
        cover_name = catalog.get("coverFile", "cover.jpg")
        if cover_name not in ("cover.jpg", "cover.png"):
            raise ValueError("Unexpected cover filename")
        cover = private / cover_name
        cover.write_bytes(apk.read(prefix + cover_name))
        for expected, track in zip(metadata["tracks"], catalog["tracks"]):
            if track["track"] != expected["track"] or track["asset"] != "%02d.bgv" % expected["track"]:
                raise ValueError("Unexpected source music order")
            raw = unpack(apk.read(prefix + track["asset"]), key)
            if hashlib.sha256(raw).hexdigest() != track["sha256"]:
                raise ValueError("Music payload checksum mismatch")
            output = raw_dir / expected["input"]
            output.write_bytes(raw)
            os.chmod(output, 0o600)
    subprocess.run([sys.executable, str(ROOT / "tools/blue_victory/prepare.py"),
                    "--music-dir", str(raw_dir), "--cover", str(cover), "--preserve-cover",
                    "--output", str(private / "assets"), "--tagged", str(private / "review"),
                    "--approved"], check=True)
    print("Prepared Bonus Bundled Content; all five compressed audio streams verified unchanged.")
    print("Use the private assets directory as BLUE_VICTORY_BUNDLE for assembleOsVictory.")


if __name__ == "__main__":
    main()
