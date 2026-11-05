# Grapefruit Settings · JP5S test branch

[Download Settings](../downloads/jp5s/Grapefruit-Settings-1.0-jp5s-beta1.apk?raw=true).

This is a frontend mod of the supplied JP5S Android 4.2.2 Settings.apk. It installs
as **Grapefruit Settings**, package `io.github.hayliewordsman.grapefruitsettings`,
beside `com.android.settings`. Do not replace, remove or disable stock Settings.
SettingsProvider.apk is neither an input nor an output, and its authority,
database schema and package are unchanged. Ordinary settings changes still go
through Android's existing services and SettingsProvider, as expected.

The new home and everyday controls use Grapefruit's typography, icons, switches
and four Launcher palettes. Grapefruit Dark is the default. Original JP5S
Activity/fragment implementations remain embedded for device-specific pages,
with palette, icon tint, switch and text-dialog hooks. Native preference pages
retain their original layouts; the new home and common controls have the full
Grapefruit layout. The original Settings app remains accessible from the home.

The inspected frontend is signed with the public AOSP test certificate, SHA-256
`a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc`.
The assembled APK deliberately uses that same certificate and preserves
`android.uid.system`, needed by the retained frontend's privileged calls.
It is exclusively for this JP5S firmware. No platform key was extracted from
the device. The source input's exact hash is in [input.json](input.json).

Original services, receivers and widgets are left with stock Settings so the
test installation does not duplicate its background components. The OEM
Screenshot Application is retained because the OEM display page calls it.
Embedded device Activities are private to this package. This is a device-test
beta: actual JP5S installation and hardware behavior still need verification.

## Universal Grapefruit Apps Theme

Settings, Launcher, Audio and TV sources on this branch implement a read-only
palette contract. Existing releases do not understand it. Install the signed
companion updates from [downloads/jp5s](../downloads/jp5s) to enable it in those
apps; the branch-only CI job builds these with the existing Grapefruit app key.
The Settings APK uses the OEM certificate above, not the companion app key.
Audio/TV updates use their existing permanent certificate. Earlier Launcher
betas use a different debug certificate: an in-place update of those betas needs
the original beta signing key. The CI Launcher build uses the permanent key and
is suitable for a fresh installation; it does not overwrite a debug-signed beta.

- Dark, Light and Pitch Black propagate to all supported apps on resume.
- Victory Blue propagates to Settings and Launcher. It is **not available in
  standard Audio or TV/Movies**, which retain their own theme until one of the
  other three themes is selected universally.
- Separate media revisions prevent a Blue selection from undoing an app's
  manual theme choice. Re-selecting a supported theme universally reapplies it.
- Users can still choose a theme in each app until the next universal selection.
- `content://io.github.hayliewordsman.grapefruitsettings.theme/theme` exposes only
  palette IDs and revision tokens. Insert, update and delete are rejected. This
  provider is unrelated to Android's SettingsProvider.

## Text dialogs

Below Android 6, editable dialog fields suppress the action-mode/long-press path
that crashes on Jelly Bean. Select all, Copy, Cut and Paste remain available as
visible controls. Paste replaces the selection or inserts at the cursor, keeping
whitespace intact. Android 6+ retains native text selection. Hooks cover OEM
dialog-return methods, direct Dialog/Builder show calls and EditTextPreference
subclasses, including Wi-Fi, Bluetooth names, VPN and credential dialogs.

## Build

The ordinary `:settings:assembleDebug` APK is a UI preview. The downloadable
**JP5S** APK also contains the inspected OEM frontend. Build that with:

```sh
./gradlew :settings:assembleDebug :settings:testDebugUnitTest :settings:lintDebug
python3 tools/settings/assemble_jp5s.py \
  --input /path/to/the/supplied/Settings.apk \
  --apktool /path/to/apktool_2.12.1.jar --work work/jp5s
zipalign -f 4 work/jp5s/jp5s-unsigned.apk work/jp5s/jp5s-aligned.apk
apksigner sign --key /path/to/aosp/testkey.pk8 \
  --cert /path/to/aosp/testkey.x509.pem --min-sdk-version 17 \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled false \
  --out Grapefruit-Settings-1.0-jp5s-beta1.apk work/jp5s/jp5s-aligned.apk
apksigner verify --min-sdk-version 17 --verbose Grapefruit-Settings-1.0-jp5s-beta1.apk
```

Public test certificate/key: AOSP `platform/build`,
`target/product/security/testkey.x509.pem` and `testkey.pk8`.
The assembler pins the input, preserves OEM resource IDs, remaps new resource
references, and records its hooks in `assembly-report.json`. It never accepts or
builds SettingsProvider. The branch has no F-Droid metadata or publishing path
for Settings, and creates no release/tag for it.

Validation: 12 Robolectric checks on API 21/28, UI renders at 320/360dp, API 17
lint, original resource-ID checks, APK identity and v1 signing verification.
The test framework does not emulate API 17 or JP5S hardware.

GPL-3.0-or-later for new Grapefruit code, matching this repository. The supplied
frontend retains its original code and notices. Geist: SIL OFL 1.1, included in
the APK assets. The new launcher artwork is in `brand/ic_launcher_source.png`.
