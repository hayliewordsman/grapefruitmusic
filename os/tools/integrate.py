#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Preflight/apply pinned AOSP changes without downloading source or touching hardware."""
import argparse
import hashlib
import json
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def preflight(aosp):
    lock = json.loads((ROOT / "os/upstream.json").read_text())
    pending = []
    for project, info in lock["projects"].items():
        target = aosp / project
        patch = ROOT / "os/patches" / (project.replace("/", "_") + ".patch")
        if not target.is_dir():
            raise ValueError("Missing source project: " + str(target))
        def gitcheck(reverse=False):
            args = ["git", "-C", str(target), "apply", "--check"]
            if reverse:
                args.append("--reverse")
            return subprocess.run(args + [str(patch)], capture_output=True, text=True)
        if gitcheck(True).returncode == 0:
            print(project + ": already integrated")
            continue
        for entry in info["files"].values():
            path = target / entry["path"]
            if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != entry["sha256"]:
                raise ValueError("Source differs from pinned baseline: " + str(path))
        result = gitcheck()
        if result.returncode:
            raise ValueError(result.stderr)
        pending.append((target, patch))
        print(project + ": patch preflight passed")
    icon = aosp / "packages/apps/Launcher3/quickstep/res/drawable-xxxhdpi/ic_launcher_home.png"
    source_icon = ROOT / "launcher/src/main/res/mipmap-xxxhdpi/ic_launcher.png"
    if icon.exists() and icon.read_bytes() != source_icon.read_bytes():
        raise ValueError("A different launcher brand asset exists: " + str(icon))
    return pending, source_icon, icon


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--aosp", type=Path, required=True)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--check", action="store_true")
    mode.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    if (args.aosp / "vendor/grapefruit").resolve() != ROOT:
        parser.error("Place this checkout at AOSP_ROOT/vendor/grapefruit (or symlink it for validation)")
    pending, source_icon, icon = preflight(args.aosp.resolve())
    if args.apply:
        for target, patch in pending:
            subprocess.run(["git", "-C", str(target), "apply", str(patch)], check=True)
        icon.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source_icon, icon)
        print("Applied AOSP integration; next stage signed app APKs before the platform build.")
    else:
        print("Preflight complete; source and device state were not changed.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, subprocess.CalledProcessError) as error:
        raise SystemExit(str(error))
