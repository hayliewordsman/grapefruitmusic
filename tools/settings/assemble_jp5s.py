#!/usr/bin/env python3
"""Assemble the uploaded JP5S frontend with Grapefruit's new UI; never takes SettingsProvider.apk.

Run after :settings:assembleDebug. Requires Java, Apktool 2.12.1, zipalign and apksigner.
The input hash and certificate are pinned to the user's supplied Settings.apk.
"""
import argparse
import hashlib
import json
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path
from device_bundle import detach, TEST_PACKAGE

ROOT = Path(__file__).resolve().parents[2]
A = "{http://schemas.android.com/apk/res/android}"
PKG = "io.github.hayliewordsman.grapefruitsettings"
ET.register_namespace("android", A[1:-1])


def run(*args):
    subprocess.run([str(a) for a in args], check=True)


def assemble(args):
    global PKG
    PKG = "com.android.settings" if args.mode == "replacement" else TEST_PACKAGE
    source = Path(args.input).resolve()
    expected = json.loads((ROOT / "settings/input.json").read_text())
    if hashlib.sha256(source.read_bytes()).hexdigest() != expected["sha256"]:
        raise SystemExit("Input is not the inspected JP5S Settings.apk. Do not use SettingsProvider.apk.")
    out = Path(args.work).resolve()
    out.mkdir(parents=True, exist_ok=True)
    original, frontend, framework = out / "original", out / "frontend", out / "framework"
    framework.mkdir(exist_ok=True)
    run("java", "-jar", args.apktool, "d", "-f", "-p", framework, source, "-o", original)
    run("java", "-jar", args.apktool, "d", "-f", "-p", framework,
        ROOT / "settings/build/outputs/apk/debug/settings-debug.apk", "-o", frontend)
    input_manifest = out / "input-manifest.xml"
    shutil.copyfile(original / "AndroidManifest.xml", input_manifest)

    manifest = ET.parse(original / "AndroidManifest.xml")
    root = manifest.getroot()
    root.set("package", PKG)
    root.attrib.pop("coreApp", None)
    if args.mode == "replacement":
        root.set("coreApp", "true")
    for item in list(root):
        if item.tag == "original-package":
            root.remove(item)
    app = root.find("application")
    app.set(A + "name", "com.android.settings.Screenshot")  # OEM display page calls this Application helper.
    app.set(A + "label", "Grapefruit Settings")
    app.set(A + "allowBackup", "false")
    app.set(A + "theme", "@style/SettingsTheme")
    app.set(A + "taskAffinity", PKG)
    for child in list(app):
        if child.tag == "provider":
            app.remove(child)
            continue
        if child.tag not in ("activity", "activity-alias", "receiver", "service"):
            continue
        for key in ("name", "targetActivity", "parentActivityName"):
            value = child.get(A + key)
            if value:
                child.set(A + key, "com.android.settings" + value if value.startswith(".") else
                          "com.android.settings." + value if "." not in value else value)
        helper = child.tag in ("receiver", "service")
        interactive_bluetooth = child.get(A + "name").rsplit(".", 1)[-1] in ("BluetoothPairingRequest", "BluetoothPermissionRequest", "BluetoothAuthorizeRequest")
        if args.mode == "test":
            child.set(A + "exported", "true" if interactive_bluetooth else "false")
        if not helper:
            child.set(A + "taskAffinity", PKG)
        if helper and args.mode == "test":
            for filt in list(child.findall("intent-filter")):
                if interactive_bluetooth:
                    filt.set(A + "priority", "1001")
                    child.set(A + "permission", "android.permission.BLUETOOTH_ADMIN")
                else:
                    child.remove(filt)
        # Private route actions avoid taking over Android's normal Settings intent resolution,
        # including calls by other apps that share the OEM's system UID.
        if not helper and args.mode == "test":
            for action in child.findall("intent-filter/action"):
                action.set(A + "name", PKG + ".route." + action.get(A + "name"))
        for filt in list(child.findall("intent-filter")):
            if any(c.get(A + "name") == "android.intent.category.LAUNCHER" for c in filt):
                for category in list(filt.findall("category")):
                    if category.get(A + "name") == "android.intent.category.LAUNCHER":
                        filt.remove(category)
    activity = ET.SubElement(app, "activity", {A + "name": "com.grapefruitsettings.MainActivity",
                                               A + "exported": "true", A + "label": "Grapefruit Settings"})
    filt = ET.SubElement(activity, "intent-filter")
    ET.SubElement(filt, "action", {A + "name": "android.intent.action.MAIN"})
    ET.SubElement(filt, "category", {A + "name": "android.intent.category.LAUNCHER"})
    ET.SubElement(app, "provider", {A + "name": "com.grapefruitsettings.ThemeProvider",
                                  A + "authorities": TEST_PACKAGE + ".theme", A + "exported": "true",
                                  A + "grantUriPermissions": "false"})
    manifest.write(original / "AndroidManifest.xml", encoding="utf-8", xml_declaration=True)
    manifest_path = original / "AndroidManifest.xml"
    manifest_path.write_text(manifest_path.read_text().replace(
        "@android:style/Animation.DropDownDown", "@*android:style/Animation.DropDownDown").replace(
        "@android:style/ActiveWallpaperSettings", "@*android:style/ActiveWallpaperSettings"))

    # Pin original resource IDs. Remap only our compiled style constants into unused entries.
    public = ET.parse(original / "res/values/public.xml")
    next_ids = {}
    for entry in public.getroot():
        kind = entry.get("type")
        next_ids[kind] = max(next_ids.get(kind, 0), int(entry.get("id"), 16) + 1)
    mapping = {}
    for entry in ET.parse(frontend / "res/values/public.xml").getroot():
        kind = entry.get("type")
        if kind in ("style", "layout"):
            new_id = f"0x{next_ids[kind]:08x}"
            mapping[entry.get("id")] = new_id
            ET.SubElement(public.getroot(), "public", {"type": kind, "name": entry.get("name"), "id": new_id})
            next_ids[kind] += 1
    public.write(original / "res/values/public.xml", encoding="utf-8", xml_declaration=True)
    merged = ET.Element("resources")
    for file in ("styles.xml", "legacy.xml"):
        for element in ET.parse(ROOT / "settings/src/main/res/values" / file).getroot():
            merged.append(element)
    ET.ElementTree(merged).write(original / "res/values/grapefruit.xml", encoding="utf-8", xml_declaration=True)
    layouts = ROOT / "settings/src/main/res/layout"
    if layouts.exists():
        shutil.copytree(layouts, original / "res/layout", dirs_exist_ok=True)
    for folder in frontend.glob("smali*"):
        for smali in folder.rglob("*.smali"):
            target = original / "smali" / smali.relative_to(folder)
            target.parent.mkdir(parents=True, exist_ok=True)
            text = smali.read_text()
            for old, new in mapping.items():
                text = re.sub(r"\b" + old + r"\b", new, text)
            target.write_text(text)
    if (frontend / "assets").exists():
        shutil.copytree(frontend / "assets", original / "assets", dirs_exist_ok=True)

    # Hooks preserve original Activity/fragment logic and system APIs.
    smalis = list((original / "smali/com/android/settings").rglob("*.smali"))
    parents = {}
    for file in smalis:
        text = file.read_text()
        cls = re.search(r"^\.class[^\n]* (L[^;]+;)", text, re.M)
        sup = re.search(r"^\.super (L[^;]+;)", text, re.M)
        if cls and sup:
            parents[cls[1]] = sup[1]

    def extends(cls, bases):
        seen = set()
        while cls and cls not in seen:
            if cls in bases:
                return True
            seen.add(cls)
            cls = parents.get(cls)
        return False

    activities = {"Landroid/app/Activity;", "Landroid/preference/PreferenceActivity;",
                  "Landroid/app/ListActivity;", "Lcom/android/internal/app/AlertActivity;"}
    dialogs = {"Landroid/app/Dialog;", "Landroid/app/AlertDialog;", "Landroid/app/ProgressDialog;"}
    count = {"activities": 0, "dialog_returns": 0, "dialog_shows": 0, "switch_constructors": 0, "preference_subclasses": 0}
    for file in smalis:
        text = file.read_text()
        cls = re.search(r"^\.class[^\n]* (L[^;]+;)", text, re.M)
        if cls and extends(cls[1], activities):
            def hook(match):
                method = match[0]
                line = re.search(r"^    \.(?:locals|registers) \d+.*$", method, re.M)
                if line:
                    count["activities"] += 1
                    method = method[:line.end()] + "\n    invoke-static {p0}, Lcom/grapefruitsettings/LegacyTheme;->apply(Landroid/app/Activity;)V\n" + method[line.end():]
                return method
            text = re.sub(r"\.method [^\n]* onCreate\(Landroid/os/Bundle;\)V\n.*?\.end method", hook, text, flags=re.S)
        def dialog_hook(match):
            method = match[0]
            ret = re.search(r"\)(L[^;]+;)\n", method)
            if ret and extends(ret[1], dialogs):
                def insert(m):
                    count["dialog_returns"] += 1
                    return "    invoke-static {" + m[1] + "}, Lcom/grapefruitsettings/LegacyTheme;->prepareDialog(Landroid/app/Dialog;)V\n" + m[0]
                method = re.sub(r"^    return-object ([vp]\d+)$", insert, method, flags=re.M)
            return method
        text = re.sub(r"\.method [^\n]*\)L[^;]+;\n.*?\.end method", dialog_hook, text, flags=re.S)
        def show_hook(match):
            register, owner = match[1], match[2]
            if extends(owner, dialogs):
                count["dialog_shows"] += 1
                return "invoke-static {" + register + "}, Lcom/grapefruitsettings/LegacyTheme;->show(Landroid/app/Dialog;)V"
            return match[0]
        text = re.sub(r"invoke-virtual \{([vp]\d+)\}, (L[^;]+;)->show\(\)V", show_hook, text)
        count["dialog_shows"] += text.count("Landroid/app/AlertDialog$Builder;->show()Landroid/app/AlertDialog;")
        text = re.sub(r"invoke-virtual (\{[vp]\d+\}), Landroid/app/AlertDialog\$Builder;->show\(\)Landroid/app/AlertDialog;",
                      r"invoke-static \1, Lcom/grapefruitsettings/LegacyTheme;->show(Landroid/app/AlertDialog$Builder;)Landroid/app/AlertDialog;", text)
        count["switch_constructors"] += text.count("Landroid/widget/Switch;-><init>")
        text = re.sub(r"(new-instance [^\n]+, )Landroid/widget/Switch;", r"\1Lcom/grapefruitsettings/LegacySwitch;", text)
        text = text.replace("Landroid/widget/Switch;-><init>", "Lcom/grapefruitsettings/LegacySwitch;-><init>")
        text = re.sub(r"(new-instance [^\n]+, )Landroid/widget/CheckBox;", r"\1Lcom/grapefruitsettings/LegacyCheckBox;", text)
        text = text.replace("Landroid/widget/CheckBox;-><init>", "Lcom/grapefruitsettings/LegacyCheckBox;-><init>")
        for base, replacement in (("CheckBoxPreference", "LegacyCheckBoxPreference"), ("SwitchPreference", "LegacySwitchPreference"), ("EditTextPreference", "LegacyEditTextPreference")):
            old_type = "Landroid/preference/" + base + ";"
            new_type = "Lcom/grapefruitsettings/" + replacement + ";"
            text = re.sub(r"(new-instance [^\n]+, )" + old_type, r"\1" + new_type, text)
            text = text.replace(old_type + "-><init>", new_type + "-><init>")
            if ".super " + old_type in text:
                count["preference_subclasses"] += 1
                text = text.replace(".super " + old_type, ".super " + new_type)
                text = text.replace(old_type + "->", new_type + "->")
        if ".super Landroid/preference/CheckBoxPreference;" in text:
            text = text.replace(".super Landroid/preference/CheckBoxPreference;", ".super Lcom/grapefruitsettings/LegacyCheckBoxPreference;")
            text = text.replace("Landroid/preference/CheckBoxPreference;->", "Lcom/grapefruitsettings/LegacyCheckBoxPreference;->")
        if ".super Landroid/preference/EditTextPreference;" in text:
            count["preference_subclasses"] += 1
            text = text.replace(".super Landroid/preference/EditTextPreference;", ".super Lcom/grapefruitsettings/LegacyEditTextPreference;")
            text = text.replace("Landroid/preference/EditTextPreference;->", "Lcom/grapefruitsettings/LegacyEditTextPreference;->")
        file.write_text(text)

    for file in (original / "res").rglob("*.xml"):
        text = file.read_text()
        text = text.replace("@android:dimen/action_bar_stacked_max_height", "@*android:dimen/action_bar_stacked_max_height")
        text = text.replace("@android:style/Animation.DropDownDown", "@*android:style/Animation.DropDownDown")
        text = re.sub(r"(<\/?)(?:android.widget.)?Switch\b", r"\1com.grapefruitsettings.LegacySwitch", text)
        text = re.sub(r"(<\/?)(?:android.preference.)?EditTextPreference\b", r"\1com.grapefruitsettings.LegacyEditTextPreference", text)
        text = re.sub(r"(<\/?)(?:android.widget.)?CheckBox\b", r"\1com.grapefruitsettings.LegacyCheckBox", text)
        text = re.sub(r"(<\/?)(?:android.preference.)?CheckBoxPreference\b", r"\1com.grapefruitsettings.LegacyCheckBoxPreference", text)
        text = re.sub(r"(<\/?)(?:android.preference.)?SwitchPreference\b", r"\1com.grapefruitsettings.LegacySwitchPreference", text)
        text = text.replace("@android:", "@*android:")
        text = text.replace("http://schemas.android.com/apk/res/com.android.settings", "http://schemas.android.com/apk/res/" + PKG)
        text = re.sub(r'(<string name="settings_label(?:_launcher)?">)[^<]*', r"\1Grapefruit Settings", text)
        file.write_text(text)
    for icon in (original / "res").rglob("ic_launcher_settings.png"):
        density = next((d for d in ("xxxhdpi", "xxhdpi", "xhdpi", "hdpi", "mdpi") if "-" + d in icon.parent.name), "xxhdpi")
        shutil.copyfile(ROOT / f"settings/src/main/res/mipmap-{density}/ic_launcher.png", icon)
    config = original / "apktool.yml"
    text = config.read_text()
    text = re.sub(r"versionCode: .*", "versionCode: " + ("10003" if args.mode == "replacement" else "3"), text)
    text = re.sub(r"versionName: .*", "versionName: 1.0-jp5s-beta3" + ("-replacement-experimental" if args.mode == "replacement" else ""), text)
    config.write_text(text)
    (out / "assembly-report.json").write_text(json.dumps(count, indent=2) + "\n")
    dependency_report = detach(original, input_manifest, PKG, args.mode)
    run("java", "-jar", args.apktool, "b", "-p", framework, original, "-o", out / "jp5s-unsigned.apk")
    print(json.dumps(count))
    print(json.dumps(dependency_report))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True)
    parser.add_argument("--apktool", required=True)
    parser.add_argument("--work", default=ROOT / "work/jp5s")
    parser.add_argument("--mode", choices=("test", "replacement"), default="test",
                        help="Replacement is an unsigned firmware-integration candidate; do not install it during side-by-side testing.")
    assemble(parser.parse_args())
