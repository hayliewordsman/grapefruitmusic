#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Stage signed app builds; Quickstep and the wallpaper are built by AOSP from source."""
import argparse
import json
import re
import shutil
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PACKAGES = {
    "GrapefruitAudio": ("audio", "io.github.hayliewordsman.grapefruitmusic"),
    "GrapefruitTV": ("tv", "io.github.hayliewordsman.grapefruitfast"),
    "BlueGrapefruitVictory": ("victory", "io.github.hayliewordsman.bluegrapefruit.victory"),
}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for arg in ("audio", "tv", "victory"):
        p.add_argument("--" + arg, type=Path)
    p.add_argument("--aapt2", required=True, type=Path)
    p.add_argument("--apksigner", required=True, type=Path)
    args = p.parse_args()
    edition = json.loads((ROOT / "os/edition.json").read_text())
    modules, copies = [], []
    for module in edition["apps"]:
        if module == "Launcher3QuickStep":
            continue
        arg, package = PACKAGES[module]
        apk = getattr(args, arg)
        if apk is None or not apk.is_file():
            raise SystemExit("Supply --" + arg + " with the signed release APK")
        badging = subprocess.check_output([str(args.aapt2), "dump", "badging", str(apk)], text=True)
        if not re.search(r"package: name='" + re.escape(package) + "'", badging):
            raise SystemExit("Unexpected application ID for " + module)
        if "application-debuggable" in badging:
            raise SystemExit("Debuggable APK cannot be preinstalled: " + module)
        if module == "BlueGrapefruitVictory":
            with zipfile.ZipFile(apk) as archive:
                try:
                    catalog = json.loads(archive.read("assets/victory/catalog.json"))
                except KeyError:
                    raise SystemExit("The Victory OS preinstall must include Bonus Bundled Content")
                tracks = catalog.get("tracks", [])
                if (catalog.get("reviewStatus") != "approved" or len(tracks) != 5
                        or catalog.get("copyLabel") != "Bonus Bundled Content"
                        or "Bonus Bundled Content" not in catalog.get("album", "")
                        or any(not t.get("title", "").endswith(" (Bonus Bundled Content)") for t in tracks)):
                    raise SystemExit("Use the approved five-track Bonus Bundled Content catalog")
                required = {"assets/victory/bundle.key", "assets/victory/" + catalog.get("coverFile", "cover.jpg")}
                required.update("assets/victory/%02d.bgv" % i for i in range(1, 6))
                if not required.issubset(archive.namelist()):
                    raise SystemExit("The Victory OS music bundle is incomplete")
            resources = subprocess.check_output([str(args.aapt2), "dump", "resources", str(apk)], text=True)
            if not re.search(r"bool/os_victory\s+\(\)\s+true", resources):
                raise SystemExit("The Victory OS preinstall must use the osVictory build")
        certificate = subprocess.check_output([str(args.apksigner), "verify", "--verbose", "--print-certs", str(apk)], text=True)
        if "CN=Android Debug" in certificate or "CN=Blue Grapefruit Local Beta" in certificate:
            raise SystemExit("Replace the development signing key for " + module)
        copies.append((apk, ROOT / "os/prebuilts" / (module + ".apk")))
        overrides = '\n    overrides: ["Music"],' if module in ("GrapefruitAudio", "BlueGrapefruitVictory") else ""
        modules.append('android_app_import {\n    name: "' + module + '",\n    apk: "' + module +
                       '.apk",\n    product_specific: true,\n    licenses: ["grapefruit_apps_license"],\n    presigned: true,' + overrides + '\n}\n')
    for source, dest in copies:
        shutil.copyfile(source, dest)
    (ROOT / "os/prebuilts/Android.bp").write_text(
        "// Generated signed inputs; do not commit APKs. SPDX-License-Identifier: Apache-2.0\n" + "\n".join(modules))
    print("Staged " + edition["edition"] + " apps. No keys or APKs are added to Git.")


if __name__ == "__main__":
    main()
