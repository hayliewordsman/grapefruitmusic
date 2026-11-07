# JP5S Grapefruit OS — SystemUI, Settings and Framework Resources Assessment

Inspection date: 2026-11-06. Source: user-supplied `JP5S-System-UI.zip` extracted from working unlocked Android 4.2.2 environment.

## Safety and scope

- Static offline inspection only. No APK, DEX, scripts or other uploaded binaries were executed or installed.
- This is **not** a flashable ROM, source tree, complete driver bundle, or proof of runtime compatibility.
- Normal-boot JPay environment, existing boot mechanism and data are not modified. No kiosk/sync concealment work is included.

## Integrity and components

- Outer ZIP SHA-256: `b3721742976e3dbd00279a65b50cb74a0f99c3daee0073ae38b8cff5b08ffeac`
- Both the outer ZIP and all three embedded APK ZIPs pass CRC integrity checking.

| Android package | Bytes | SHA-256 | Embedded code |
|---|---:|---|---|
| `Settings.apk` | 2,004,277 | `11e5af05c1b62f10ff614cf332beefb917b978e110b6f6dd5a1579349d4dac89` | DEX |
| `SystemUI.apk` | 1,600,568 | `3881a25ef754f1437b221b68c924be3ee25079be2b508545647472d658cb76a6` | DEX |
| `framework-res.apk` | 8,656,587 | `e549050d39df691c11c5866c1bce64efe33f3cafe74536cffa9d89ab75647f8d` | Resource-only |

## Signing identity — critical

- **Settings.apk**: certificate `1.2.840.113549.1.9.1=android@android.com,CN=Android,OU=Android,O=Android,L=Mountain View,ST=California,C=US`; SHA-256 fingerprint `a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc`.
- **SystemUI.apk**: certificate `1.2.840.113549.1.9.1=android@android.com,CN=Android,OU=Android,O=Android,L=Mountain View,ST=California,C=US`; SHA-256 fingerprint `a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc`.
- **framework-res.apk**: certificate `1.2.840.113549.1.9.1=NA,CN=jp5platform,OU=JPay New Products,O=JPay Inc.,L=Miramar,ST=Florida,C=US`; SHA-256 fingerprint `ed09abf7186b79c5d7b450024c02dd6b4d221e729172e5b5e970ab5385ca3071`.

`Settings.apk` and `SystemUI.apk` share the public Android/AOSP test certificate. `framework-res.apk` instead carries a JPay `jp5platform` certificate. This is an observable signing difference, **not** access to any private signing key. Do not assume system packages can be swapped or updated in place without evaluating signature permissions, shared UIDs, package identities, and platform/framework consistency. A newly built ROM should use a consistent documented signing plan and device-specific tests.

## Useful implementation findings

### SystemUI

- Has pre-existing phone and tablet status bar implementations, including `PhoneStatusBar` and `TabletStatusBar` classes.
- Includes `NavigationBarView` and `res/layout/navigation_bar.xml`, plus `res/layout-sw480dp/navigation_bar.xml` and `res/layout-sw600dp/navigation_bar.xml` variants.
- Contains `QuickSettings`, `quick_settings.xml` and a catalog of Quick Settings tile layouts (Wi-Fi, Bluetooth, brightness, rotation, etc.). An **editable/reorderable** tile editor is a new feature to develop, not proven to exist in these APKs.
- Contains existing screenshot implementation classes `TakeScreenshotService` and `GlobalScreenshot`; intended new screenshot button should use supported internal SystemUI screenshot routing after inspection.
- Because a 1024 x 600 tablet may follow tablet status/navigation routes, the proposed navigation change must cover tablet and phone layout variants and actual orientation/density behavior.

### Settings

- Has `res/xml/settings_headers.xml`, `res/xml/display_settings.xml`, `res/xml/hdmi_settings.xml`, and `res/xml/screenshot.xml` resources.
- Has device/vendor functionality that should be preserved during the Grapefruit redesign rather than replaced blindly by generic upstream Settings.

### framework-res.apk

- Resource-only framework APK; includes system layout and configuration resources. It does not contain Java framework code.
- It is a signed framework resource package and must be treated as a tightly version-matched part of the running 4.2.2 system.

## Locked-in Grapefruit OS interface requirements

### Navigation (seven equal logical slots)

| 1 | 2 | 3 | **4 (exact center)** | 5 | 6 | 7 |
|---|---|---|---|---|---|---|
| Screenshot | Volume down | Back | **Home** | Recents | Volume up | Empty, inert spacer |

- Six actionable buttons plus a seventh blank slot; Home must be centered at slot four.
- Preserve legible icons, accessibility labels and generous touch targets in portrait and landscape; automatically follow all four Grapefruit themes.
- Screenshot action should use original Android screenshot service, with existing content protections intact.

### Universal themes and Quick Settings

- Grapefruit Dark, Grapefruit Light, Pitch Black, Victory Blue, propagated through native Settings/SystemUI, Launcher, Keyboard, Calculator, Maps, Audio and TV (add missing Victory Blue support where needed).
- Implement first-party Quick Settings editing: add/hide/reorder supported tiles, reset defaults and persist choices. Do not require Xposed or GravityBox.
- Preserve working platform settings and vendor controls, including Wi-Fi, display, audio, power and any required OEM extensions.

## Codex development recommendations

1. Use this ZIP as **reference/input**, not a reason to blindly overwrite a running `/system/app/SystemUI.apk` or `framework-res.apk`.
2. Match the framework APK and Settings/SystemUI binaries to the original device build and the corresponding AOSP/Rockchip Jelly Bean sources; compare APK resources and bytecode to determine OEM divergences.
3. Implement Grapefruit system changes in source where possible; package and sign coherent firmware images only after confirming compatibility.
4. Build an instrumented test of the seven-slot navigation arrangement for both tablet and phone UI paths, landscape/portrait, and keyboard/IME visibility.
5. Add reliable backup/rollback and test on an isolated copy or spare device before any deployment; never assume an error can be fixed using normal recovery (that boot path is occupied).
6. Keep firmware/library binaries private unless redistribution rights are established. Do not commit them to the public Grapefruit repository.

## Limitations and remaining inputs

- This ZIP does not include `framework.jar`, `services.jar`, kernel sources, full OEM build tree, or the device partition/mount map.
- Exact ROM-signing, privileged UID, boot support and OTA compatibility cannot be established from three APKs alone.
- In-place data-preserving upgrade from Android 4.2.2 to KitKat remains unverified.

## Notable embedded resource/class references

### Settings.apk

**Resource paths:**
- `res/xml/display_settings.xml`
- `res/xml/hdmi_settings.xml`
- `res/xml/screenshot.xml`
- `res/xml/settings_headers.xml`

**DEX classes:**
- `Lcom/android/settings/ScreenshotSetting;`

### SystemUI.apk

**Resource paths:**
- `res/layout-sw480dp/navigation_bar.xml`
- `res/layout-sw600dp/navigation_bar.xml`
- `res/layout-sw600dp/system_bar.xml`
- `res/layout/navigation_bar.xml`
- `res/layout/quick_settings.xml`
- `res/layout/quick_settings_tile.xml`
- `res/layout/status_bar.xml`
- `res/layout/status_bar_expanded.xml`
- `res/layout/super_status_bar.xml`
- `res/layout/system_bar.xml`

**DEX classes:**
- `Lcom/android/systemui/statusbar/phone/NavigationBarView;`
- `Lcom/android/systemui/statusbar/phone/QuickSettings;`
- `Lcom/android/systemui/statusbar/phone/PhoneStatusBar;`
- `Lcom/android/systemui/statusbar/tablet/TabletStatusBar;`
- `Lcom/android/systemui/screenshot/TakeScreenshotService;`
- `Lcom/android/systemui/screenshot/GlobalScreenshot;`

### framework-res.apk

**Resource paths:**

