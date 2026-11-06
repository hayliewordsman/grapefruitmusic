#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Fetch only the hash-verified AOSP files needed to validate this integration."""
import argparse
import base64
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import urllib.request

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dest", required=True, type=Path)
    args = parser.parse_args()
    args.dest.mkdir(parents=True, exist_ok=True)
    lock = json.loads((ROOT / "os/upstream.json").read_text())
    inputs = [(project, value["revision"], name, entry)
              for project, value in lock["projects"].items() for name, entry in value["files"].items()]

    def fetch(item):
        project, revision, name, entry = item
        url = f'https://android.googlesource.com/platform/{project}/+/{revision}/{entry["path"]}?format=TEXT'
        data = base64.b64decode(urllib.request.urlopen(url, timeout=30).read().strip(), validate=True)
        if hashlib.sha256(data).hexdigest() != entry["sha256"]:
            raise ValueError("Upstream checksum mismatch: " + name)
        (args.dest / name).write_bytes(data)
        return name
    with ThreadPoolExecutor(max_workers=4) as pool:
        for name in pool.map(fetch, inputs):
            print("Verified " + name)


if __name__ == "__main__":
    main()
