#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Tag private MP3 copies and package them for Blue Grapefruit, without transcoding audio.

The input music, tagged copies, cover and output bundle must stay outside tracked source.
Dependencies: mutagen, Pillow, cryptography. FFmpeg verifies untouched compressed audio.
"""
import argparse
import hashlib
import hmac
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import time

from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.primitives import padding
from mutagen.id3 import ID3, TIT2, TALB, TPE1, TPE2, TEXT, TCOP, TPUB, TDRC, TRCK, TPOS, APIC, delete
from mutagen.mp3 import MP3
from PIL import Image

def audio_hash(path):
    result = subprocess.run(["ffmpeg", "-v", "error", "-i", str(path), "-map", "0:a:0",
                             "-c", "copy", "-f", "hash", "-hash", "sha256", "-"],
                            check=True, capture_output=True, text=True)
    return result.stdout.strip().split("=", 1)[1]

def encrypt(data, key):
    iv = os.urandom(16)
    padder = padding.PKCS7(128).padder()
    padded = padder.update(data) + padder.finalize()
    cipher = Cipher(algorithms.AES(key[:16]), modes.CBC(iv)).encryptor()
    body = b"BGV1" + iv + cipher.update(padded) + cipher.finalize()
    return body + hmac.digest(key[16:], body, "sha256")

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--music-dir", type=Path, required=True)
    parser.add_argument("--cover", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True, help="Private assets directory, outside Git")
    parser.add_argument("--tagged", type=Path, required=True, help="Private review copies, outside Git")
    parser.add_argument("--metadata", type=Path, default=Path(__file__).with_name("metadata.json"))
    parser.add_argument("--preserve-cover", action="store_true", help="Keep original cover encoding instead of an optimized display copy")
    parser.add_argument("--approved", action="store_true", help="Use only after the owner approves tags and artwork")
    args = parser.parse_args()
    metadata = json.loads(args.metadata.read_text())
    if len(metadata["tracks"]) != 5 or [t["track"] for t in metadata["tracks"]] != list(range(1, 6)):
        raise ValueError("Expected the approved five-track order")
    for path in (args.output, args.tagged):
        path.mkdir(parents=True, exist_ok=True, mode=0o700)
        ignored = subprocess.run(["git", "check-ignore", "--quiet", str(path.resolve())],
                                 cwd=Path(__file__).resolve().parents[2], capture_output=True)
        repo = Path(__file__).resolve().parents[2]
        if path.resolve().is_relative_to(repo) and ignored.returncode != 0:
            raise ValueError("Private output must be ignored by Git")
    asset_dir = args.output / "victory"
    asset_dir.mkdir(exist_ok=True, mode=0o700)
    # Use a compact display copy in all five ID3 tags and the player. Music bytes are untouched.
    picture = Image.open(args.cover)
    if picture.format not in ("JPEG", "PNG"):
        raise ValueError("Supply a PNG or JPEG cover")
    source_cover_hash = hashlib.sha256(args.cover.read_bytes()).hexdigest()
    if args.preserve_cover:
        cover = args.cover.read_bytes()
        cover_file = "cover.png" if picture.format == "PNG" else "cover.jpg"
        cover_mime = "image/png" if picture.format == "PNG" else "image/jpeg"
    else:
        picture = picture.convert("RGB")
        picture.thumbnail((1000, 1000), Image.Resampling.LANCZOS)
        encoded = io.BytesIO()
        picture.save(encoded, format="JPEG", quality=88, optimize=True, progressive=False)
        cover = encoded.getvalue()
        cover_file, cover_mime = "cover.jpg", "image/jpeg"
    (asset_dir / cover_file).write_bytes(cover)
    key = os.urandom(48)
    (asset_dir / "bundle.key").write_bytes(key)
    catalog = {k: metadata[k] for k in ("album", "artist", "albumArtist", "year")}
    catalog.update(reviewStatus="approved" if args.approved else "draft", added=int(time.time() * 1000),
                   coverFile=cover_file, copyLabel=metadata.get("copyLabel", "Bonus Bundled Content"), tracks=[])
    review = dict(metadata)
    review["cover"] = {"sha256": hashlib.sha256(cover).hexdigest(), "width": picture.width, "height": picture.height,
                       "sourceSha256": source_cover_hash, "mime": cover_mime, "type": "Front cover (3)", "file": cover_file}
    review["tracks"] = []
    for item in metadata["tracks"]:
        paths = list(args.music_dir.rglob(item["input"]))
        if len(paths) != 1:
            raise ValueError("Expected exactly one input: " + item["input"])
        source = paths[0]
        title = item["name"] + " (Bonus Bundled Content)"
        filename = "%02d %s.mp3" % (item["track"], title)
        destination = args.tagged / filename
        if destination.resolve() == source.resolve():
            raise ValueError("Never overwrite the uploaded original")
        original_audio = audio_hash(source)
        shutil.copyfile(source, destination)
        delete(destination, delete_v1=True, delete_v2=True)
        tags = ID3()
        for frame, value in ((TIT2, title), (TALB, metadata["album"]), (TPE1, metadata["artist"]),
                             (TPE2, metadata["albumArtist"]), (TEXT, metadata["lyricist"]),
                             (TCOP, metadata["copyright"]), (TPUB, metadata["publisher"]),
                             (TDRC, str(metadata["year"])), (TRCK, "%d/5" % item["track"]), (TPOS, "1/1")):
            tags.add(frame(encoding=1, text=[value]))
        tags.add(APIC(encoding=1, mime=cover_mime, type=3, desc="Front cover", data=cover))
        tags.save(destination, v2_version=3, v1=0)
        if audio_hash(destination) != original_audio:
            raise ValueError("Compressed audio changed while tagging " + filename)
        reread = ID3(destination)
        if reread.version != (2, 3, 0) or reread.getall("USLT") or reread.getall("SYLT"):
            raise ValueError("Unexpected ID3 version or remaining lyrics")
        raw = destination.read_bytes()
        encrypted = encrypt(raw, key)
        asset = "%02d.bgv" % item["track"]
        (asset_dir / asset).write_bytes(encrypted)
        entry = dict(track=item["track"], title=title, filename=filename, asset=asset,
                     durationMs=round(MP3(destination).info.length * 1000), bytes=len(raw),
                     encryptedBytes=len(encrypted), sha256=hashlib.sha256(raw).hexdigest())
        catalog["tracks"].append(entry)
        review["tracks"].append(dict(entry, audioSha256=original_audio,
                                     tags={k: str(v) for k, v in reread.items() if not k.startswith("APIC")}))
        print(filename + " — ID3v2.3 verified, compressed audio unchanged")
    identity = json.dumps({"tracks": catalog["tracks"], "cover": review["cover"]}, sort_keys=True).encode()
    catalog["bundleId"] = hashlib.sha256(identity).hexdigest()
    (asset_dir / "catalog.json").write_text(json.dumps(catalog, indent=2) + "\n")
    (args.tagged / "metadata-review.json").write_text(json.dumps(review, indent=2, ensure_ascii=False) + "\n")
    for directory in (args.output, args.tagged):
        for path in directory.rglob("*"):
            os.chmod(path, 0o700 if path.is_dir() else 0o600)
    print("Bundle prepared privately. Review status:", catalog["reviewStatus"])

if __name__ == "__main__":
    main()
