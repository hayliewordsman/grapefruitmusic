#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Exercise patch preflight, application, repeat application, and drift rejection on pinned files."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]


def verify(upstream):
    lock = json.loads((ROOT / "os/upstream.json").read_text())
    with tempfile.TemporaryDirectory() as directory:
        fixture = Path(directory)
        for project, info in lock["projects"].items():
            dest = fixture / project
            dest.mkdir(parents=True)
            subprocess.run(["git", "init", "-q", str(dest)], check=True)
            for name, entry in info["files"].items():
                target = dest / entry["path"]
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(upstream / name, target)
        (fixture / "vendor").mkdir()
        (fixture / "vendor/grapefruit").symlink_to(ROOT, target_is_directory=True)
        tool = ROOT / "os/tools/integrate.py"
        command = ["python3", str(tool), "--aosp", str(fixture)]
        # All project preflights run before any mutations. A drift failure must leave inputs alone.
        bad = fixture / "packages/apps/Settings/AndroidManifest.xml"
        original = bad.read_bytes()
        bad.write_bytes(original + b"<!-- unrelated OEM modification -->\n")
        result = subprocess.run(command + ["--apply"], capture_output=True)
        if result.returncode == 0:
            raise AssertionError("Unreviewed upstream drift was accepted")
        systemui = fixture / "frameworks/base/packages/SystemUI/src/com/grapefruit/os/theme/GrapefruitPalette.java"
        if systemui.exists():
            raise AssertionError("Preflight failure wrote another project")
        bad.write_bytes(original)
        subprocess.run(command + ["--check"], check=True)
        subprocess.run(command + ["--apply"], check=True)
        subprocess.run(command + ["--apply"], check=True)
        subprocess.run(command + ["--check"], check=True)
        # Parse generated manifest/resource changes, not just patch syntax.
        import xml.etree.ElementTree as ET
        for path in fixture.rglob("*.xml"):
            ET.parse(path)
        print("Pinned AOSP patch validation passed, including idempotence and preflight drift rejection.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--upstream", required=True, type=Path)
    verify(parser.parse_args().upstream)
