"""Relocate and audit the JP5S frontend. Android framework/provider packages are not rewritten."""
from pathlib import Path
import json
import re
import shutil
import xml.etree.ElementTree as ET

A = "{http://schemas.android.com/apk/res/android}"
OLD = "com.android.settings"
DEVICE = "com.grapefruitsettings.device"
TEST_PACKAGE = "io.github.hayliewordsman.grapefruitsettings"
EXTERNAL_EXTENSIONS = (OLD + ".OEM_RADIO_INFO", OLD + ".OPERATOR_APPLICATION_SETTING",
                       OLD + ".MANUFACTURER_APPLICATION_SETTING")
MAIN_PAGES = {
    "android.settings.SETTINGS": "home", "android.settings.WIFI_SETTINGS": "networks",
    "android.settings.WIFI_IP_SETTINGS": "wifi_advanced", "android.settings.SOUND_SETTINGS": "sound",
    "android.settings.DISPLAY_SETTINGS": "display", "android.settings.DEVICE_INFO_SETTINGS": "about",
    "android.settings.APPLICATION_SETTINGS": "apps", "android.settings.MANAGE_APPLICATIONS_SETTINGS": "apps",
    "android.settings.MANAGE_ALL_APPLICATIONS_SETTINGS": "apps", "android.settings.INTERNAL_STORAGE_SETTINGS": "storage",
    "android.settings.MEMORY_CARD_SETTINGS": "storage", "android.intent.action.POWER_USAGE_SUMMARY": "battery",
}


def qualified(name):
    if name.startswith("."):
        return OLD + name
    return name if "." in name else OLD + "." + name


def moved_class(name):
    return DEVICE + qualified(name)[len(OLD):]


def relocate(text, package=TEST_PACKAGE):
    for n, contract in enumerate(EXTERNAL_EXTENSIONS):
        text = text.replace(contract, f"__GRAPEFRUIT_EXTENSION_{n}__")
    # Resource namespace and application IDs differ from the relocated Java namespace.
    text = text.replace("http://schemas.android.com/apk/res/" + OLD,
                        "http://schemas.android.com/apk/res/" + package)
    text = text.replace("Lcom/android/settings/", "Lcom/grapefruitsettings/device/")
    text = text.replace(OLD + ".", DEVICE + ".")
    text = text.replace(OLD, package)
    for n, contract in enumerate(EXTERNAL_EXTENSIONS):
        text = text.replace(f"__GRAPEFRUIT_EXTENSION_{n}__", contract)
    return text


def catalog(manifest, mode="test"):
    app = ET.parse(manifest).getroot().find("application")
    routes = {}
    for component in app:
        if component.tag not in ("activity", "activity-alias"):
            continue
        for action in component.findall("intent-filter/action"):
            value = action.get(A + "name")
            if value not in ("android.intent.action.MAIN", "android.intent.action.VIEW",
                             "android.intent.action.EDIT", "android.intent.action.INSERT"):
                # Private OEM action names are rewritten consistently with their implementations.
                key = DEVICE + value[len(OLD):] if value.startswith(OLD + ".") else value
                routes.setdefault(key, moved_class(component.get(A + "name")))
    routes["android.settings.APPLICATION_DETAILS_SETTINGS"] = DEVICE + ".applications.InstalledAppDetailsTop"
    return {"schema": 1, "mode": mode, "class_namespace": DEVICE, "activities": dict(sorted(routes.items()))}


def activity_base(text):
    bases = {
        "Landroid/app/Activity;": "Lcom/grapefruitsettings/DeviceActivity;",
        "Landroid/preference/PreferenceActivity;": "Lcom/grapefruitsettings/DevicePreferenceActivity;",
        "Landroid/app/ListActivity;": "Lcom/grapefruitsettings/DeviceListActivity;",
        "Landroid/app/LauncherActivity;": "Lcom/grapefruitsettings/DeviceLauncherActivity;",
        "Lcom/android/internal/app/AlertActivity;": "Lcom/grapefruitsettings/DeviceAlertActivity;",
    }
    for old, new in bases.items():
        if ".super " + old in text:
            text = text.replace(".super " + old, ".super " + new)
            text = text.replace(old + "-><init>", new + "-><init>")
    return text


def alert_bridge():
    """AlertActivity is a real API 17 framework class omitted from public android.jar."""
    methods = [".class public Lcom/grapefruitsettings/DeviceAlertActivity;\n.super Lcom/android/internal/app/AlertActivity;\n",
               ".method public constructor <init>()V\n    .locals 0\n    invoke-direct {p0}, Lcom/android/internal/app/AlertActivity;-><init>()V\n    return-void\n.end method\n"]
    for signature, params, registers in [
        ("startActivityForResult(Landroid/content/Intent;I)V", "p0, p1, p2", 3),
        ("startActivityForResult(Landroid/content/Intent;ILandroid/os/Bundle;)V", "p0, p1, p2, p3", 4),
        ("startActivityFromFragment(Landroid/app/Fragment;Landroid/content/Intent;I)V", "p0, p1, p2, p3", 4),
        ("startActivityFromFragment(Landroid/app/Fragment;Landroid/content/Intent;ILandroid/os/Bundle;)V", "p0, p1, p2, p3, p4", 5),
    ]:
        intent = "p2" if "FromFragment" in signature else "p1"
        methods.append(f".method public {signature}\n    .locals 0\n    invoke-static {{p0, {intent}}}, Lcom/grapefruitsettings/DeviceSettings;->route(Landroid/content/Context;Landroid/content/Intent;)Landroid/content/Intent;\n    move-result-object {intent}\n    invoke-super {{{params}}}, Lcom/android/internal/app/AlertActivity;->{signature}\n    return-void\n.end method\n")
    return "\n".join(methods)


def detach(root, original_manifest, package=TEST_PACKAGE, mode="test"):
    root = Path(root)
    routes = catalog(original_manifest, mode)
    assets = root / "assets"
    assets.mkdir(exist_ok=True)
    (assets / "device-routes.json").write_text(json.dumps(routes, indent=2) + "\n")
    source = root / "smali/com/android/settings"
    rewritten_launches = 0
    guarded_receivers = 0
    for file in source.rglob("*.smali"):
        text = file.read_text()
        text = activity_base(text)
        # Context-owned launches do not pass through an Activity's virtual methods.
        for owner in ("Landroid/content/Context;", "Landroid/app/Activity;"):
            for suffix, arguments in (("(Landroid/content/Intent;)V", "(Landroid/content/Context;Landroid/content/Intent;)V"),
                                      ("(Landroid/content/Intent;Landroid/os/Bundle;)V", "(Landroid/content/Context;Landroid/content/Intent;Landroid/os/Bundle;)V")):
                pattern = r"invoke-virtual(\/range)? (\{[^}]+\}), " + re.escape(owner + "->startActivity" + suffix)
                text, n = re.subn(pattern, lambda m: "invoke-static" + (m[1] or "") + " " + m[2] + ", Lcom/grapefruitsettings/DeviceSettings;->start" + arguments, text)
                rewritten_launches += n
        for name, method, returns in (("resolveActivity", "resolve", "Landroid/content/pm/ResolveInfo;"),
                                       ("queryIntentActivities", "query", "Ljava/util/List;")):
            pattern = r"invoke-virtual(\/range)? (\{[^}]+\}), Landroid/content/pm/PackageManager;->" + name + r"\(Landroid/content/Intent;I\)" + returns
            text, n = re.subn(pattern, lambda m: "invoke-static" + (m[1] or "") + " " + m[2] + ", Lcom/grapefruitsettings/DeviceSettings;->" + method + "(Landroid/content/pm/PackageManager;Landroid/content/Intent;I)" + returns, text)
            rewritten_launches += n
        pattern = r"invoke-static(\/range)? (\{[^}]+\}), Landroid/app/PendingIntent;->getActivity(\(Landroid/content/Context;ILandroid/content/Intent;I(?:Landroid/os/Bundle;)?\)Landroid/app/PendingIntent;)"
        text, n = re.subn(pattern, lambda m: "invoke-static" + (m[1] or "") + " " + m[2] + ", Lcom/grapefruitsettings/DeviceSettings;->pendingActivity" + m[3], text)
        rewritten_launches += n
        if file.stem in ("BluetoothPairingRequest", "BluetoothPermissionRequest", "BluetoothAuthorizeRequest"):
            pattern = r"(\.method public onReceive\(Landroid/content/Context;Landroid/content/Intent;\)V\n    \.locals \d+)"
            hook = "\n    invoke-static {p0, p1, p2}, Lcom/grapefruitsettings/DeviceSettings;->acceptBluetooth(Landroid/content/BroadcastReceiver;Landroid/content/Context;Landroid/content/Intent;)Z\n    move-result v0\n    if-nez v0, :grapefruit_owned_request\n    return-void\n    :grapefruit_owned_request\n"
            text, n = re.subn(pattern, lambda m: m[1] + hook, text)
            guarded_receivers += n
        if file.stem == "Screenshot":
            text = text.replace("    invoke-super {p0}, Landroid/app/Application;->onCreate()V", "    invoke-super {p0}, Landroid/app/Application;->onCreate()V\n    invoke-static {p0}, Lcom/grapefruitsettings/DeviceSettings;->install(Landroid/app/Application;)V")
        file.write_text(relocate(text, package))
    destination = root / "smali/com/grapefruitsettings/device"
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(source, destination)
    (root / "smali/com/grapefruitsettings/DeviceAlertActivity.smali").write_text(alert_bridge())
    for folder in (root / "res",):
        for file in folder.rglob("*.xml"):
            file.write_text(relocate(file.read_text(), package))
    manifest = root / "AndroidManifest.xml"
    manifest.write_text(relocate(manifest.read_text(), package))
    repaired, retired = repair_declarations(root)
    if mode == "replacement":
        replacement_contracts(root, original_manifest)
    report = audit(root, routes, package, mode)
    report.update({"hardened_navigation_calls": rewritten_launches, "scoped_bluetooth_receivers": guarded_receivers,
                   "fragment_hosts_repaired": repaired, "orphan_missing_declarations_removed": retired})
    (root.parent / "dependency-audit.json").write_text(json.dumps(report, indent=2) + "\n")
    return report


def repair_declarations(root):
    """Repair the OEM USB wrapper and remove its orphaned, unimplemented statistics receiver."""
    manifest = ET.parse(root / "AndroidManifest.xml")
    app = manifest.getroot().find("application")
    repaired, retired = [], []
    for component in list(app):
        if component.tag not in ("activity", "receiver", "service"):
            continue
        name = component.get(A + "name")
        path = root / "smali" / (name.replace(".", "/") + ".smali")
        if path.exists():
            continue
        fragment = next((x.get(A + "value") for x in component.findall("meta-data")
                         if x.get(A + "name") == DEVICE + ".FRAGMENT_CLASS"), None)
        if component.tag == "activity" and fragment and (root / "smali" / (fragment.replace(".", "/") + ".smali")).exists():
            descriptor = name.replace(".", "/")
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(f".class public L{descriptor};\n.super Lcom/grapefruitsettings/device/Settings;\n\n.method public constructor <init>()V\n    .locals 0\n    invoke-direct {{p0}}, Lcom/grapefruitsettings/device/Settings;-><init>()V\n    return-void\n.end method\n")
            repaired.append(name)
        elif component.get(A + "enabled") == "false" or name == DEVICE + ".DeviceInfo$StatsReportReceiver":
            retired.append(name)
            app.remove(component)
    for alias in app.findall("activity-alias"):
        if alias.get(A + "name") == DEVICE + ".UsbSettings":
            alias.set(A + "targetActivity", DEVICE + ".Settings$UsbSettingsActivity")
    manifest.write(root / "AndroidManifest.xml", encoding="utf-8", xml_declaration=True)
    return repaired, retired


def replacement_contracts(root, original_manifest):
    """Prepare canonical-package contracts without installing or signing a replacement on the JP5S."""
    manifest = ET.parse(root / "AndroidManifest.xml")
    app = manifest.getroot().find("application")
    main = next(x for x in app.findall("activity") if x.get(A + "name") == "com.grapefruitsettings.MainActivity")
    next(x for x in app.findall("activity") if x.get(A + "name") == DEVICE + ".Settings").set(A + "exported", "false")
    for component in app.findall("activity") + app.findall("activity-alias"):
        if component is main:
            continue
        for filt in component.findall("intent-filter"):
            for action in list(filt.findall("action")):
                if action.get(A + "name") in MAIN_PAGES:
                    filt.remove(action)
    entry = ET.SubElement(main, "intent-filter")
    ET.SubElement(entry, "category", {A + "name": "android.intent.category.DEFAULT"})
    for action in MAIN_PAGES:
        ET.SubElement(entry, "action", {A + "name": action})
    details = next(x for x in app.findall("activity") if x.get(A + "name") == DEVICE + ".applications.InstalledAppDetailsTop")
    details.set(A + "exported", "true")
    filt = ET.SubElement(details, "intent-filter")
    ET.SubElement(filt, "action", {A + "name": "android.settings.APPLICATION_DETAILS_SETTINGS"})
    ET.SubElement(filt, "category", {A + "name": "android.intent.category.DEFAULT"})
    ET.SubElement(filt, "data", {A + "scheme": "package"})
    source = ET.parse(original_manifest).getroot().find("application")
    for old in source:
        if old.tag not in ("activity", "activity-alias"):
            continue
        name = qualified(old.get(A + "name"))
        target = moved_class(old.get(A + "targetActivity") if old.tag == "activity-alias" else old.get(A + "name"))
        if name == OLD + ".Settings":
            target = "com.grapefruitsettings.MainActivity"
        if name == OLD + ".UsbSettings":
            target = DEVICE + ".Settings$UsbSettingsActivity"
        attrs = {A + "name": name, A + "targetActivity": target,
                 A + "exported": old.get(A + "exported", "true" if old.findall("intent-filter") else "false")}
        if old.get(A + "permission"):
            attrs[A + "permission"] = old.get(A + "permission")
        alias = ET.SubElement(app, "activity-alias", attrs)
        for metadata in old.findall("meta-data"):
            alias.append(ET.fromstring(relocate(ET.tostring(metadata, encoding="unicode"), OLD)))
    manifest.write(root / "AndroidManifest.xml", encoding="utf-8", xml_declaration=True)


def audit(root, routes, package=TEST_PACKAGE, mode="test"):
    manifest = ET.parse(root / "AndroidManifest.xml").getroot()
    app = manifest.find("application")
    components = {x.get(A + "name"): x for x in app if x.tag in ("activity", "activity-alias", "receiver", "service", "provider")}
    if manifest.get("package") != package:
        raise ValueError("Unexpected application identity")
    if mode == "test":
        for node in app:
            if node.tag in ("activity", "activity-alias"):
                for action in node.findall("intent-filter/action"):
                    if action.get(A + "name") in MAIN_PAGES:
                        raise ValueError("Side-by-side build must not claim the system Settings role")
    else:
        canonical = components.get(OLD + ".Settings")
        if canonical is None or canonical.get(A + "targetActivity") != "com.grapefruitsettings.MainActivity":
            raise ValueError("Replacement must provide the canonical Settings entry point")
    missing = sorted(set(routes["activities"].values()) - set(components))
    if missing:
        raise ValueError("Bundled route targets are missing: " + str(missing))
    descriptors = set()
    referenced = set()
    old_references = []
    for file in (root / "smali").rglob("*.smali"):
        text = file.read_text()
        match = re.search(r"^\.class[^\n]* (L[^;]+;)", text, re.M)
        if match:
            descriptors.add(match[1])
        referenced.update(re.findall(r"Lcom/grapefruitsettings/[A-Za-z0-9_/$]+;", text))
        checked = text
        for extension in EXTERNAL_EXTENSIONS:
            checked = checked.replace(extension, "")
        if "Lcom/android/settings/" in text or (OLD in checked and mode == "test" and file.name != "DeviceSettings.smali"):
            old_references.append(str(file.relative_to(root)))
    for name, node in components.items():
        if node.tag == "activity-alias":
            name = node.get(A + "targetActivity")
        if "L" + name.replace(".", "/") + ";" not in descriptors:
            raise ValueError("Manifest class is missing: " + name)
    if "L" + app.get(A + "name").replace(".", "/") + ";" not in descriptors:
        raise ValueError("Application helper is missing")
    if mode == "test":
        for file in [root / "AndroidManifest.xml", *(root / "res").rglob("*.xml")]:
            checked = file.read_text()
            for extension in EXTERNAL_EXTENSIONS:
                checked = checked.replace(extension, "")
            if OLD in checked:
                old_references.append(str(file.relative_to(root)))
    if old_references:
        raise ValueError("Unrelocated stock frontend references: " + str(old_references))
    missing_classes = sorted(referenced - descriptors)
    if missing_classes:
        raise ValueError("Owned classes referenced but not bundled: " + str(missing_classes))
    if "Lcom/grapefruitsettings/DeviceSettings;" not in descriptors:
        raise ValueError("Compile the new frontend before assembling the device APK")
    providers = [x.get(A + "authorities") for x in app.findall("provider")]
    if providers != [TEST_PACKAGE + ".theme"]:
        raise ValueError("Android SettingsProvider must not be replaced")
    return {"mode": mode, "application_id": package, "stock_frontend_launch_targets": [],
            "unrelocated_device_classes": [], "bundled_activity_count": len(app.findall("activity")),
            "bundled_alias_count": len(app.findall("activity-alias")), "bundled_services": len(app.findall("service")),
            "bundled_receivers": len(app.findall("receiver")), "owned_route_count": len(routes["activities"]),
            "provider_authorities": providers, "settings_provider_unchanged": True,
            "preserved_vendor_extension_actions": list(EXTERNAL_EXTENSIONS),
            "compatibility_input_guard": "DeviceSettings accepts legacy intents and retargets them to this APK",
            "device_validation": "not performed; do not remove stock Settings during side-by-side testing"}
