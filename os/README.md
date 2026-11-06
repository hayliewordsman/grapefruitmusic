# Grapefruit OS

Android 17 AOSP integration for the Grapefruit app family. This is a development
foundation, not a release ROM or a claim that the N-XR boots Android 17.

| Branch | Preinstalled media apps | Factory theme |
| --- | --- | --- |
| `grapefruit-os` | Grapefruit Audio and Grapefruit TV | Grapefruit Dark |
| `grapefruit-os-victory` | Blue Grapefruit: Victory Edition with Bonus Bundled Content | Victory Blue |

Both editions use an Android 17 Launcher3/Quickstep derivative branded Grapefruit
Launcher, stock AOSP Settings with an integrated universal-theme page, Geist as
the normal system sans-serif family, a theme-following wallpaper, and rounded
notifications and Quick Settings, and a teardrop-styled AOSP keyboard. Users can select Grapefruit Dark, Grapefruit
Light, Pitch Black or Victory Blue in **Settings → Display → Grapefruit theme**.

**Hardware target: original Neway N-XR / MT6580.** Board support is blocked pending
a verified Android 17 BSP, maintained kernel and drivers from the OEM/SoC vendor.
See [the N-XR assessment and OEM deliverables](devices/neway_n_xr/README.md).
The only product definition supplied here is a Cuttlefish development target.

## What is implemented

- `themes/catalog.json` preserves the existing Audio/Blue palettes. A generator
  supplies app theme resources, shared Java colors and edition defaults.
- The SystemUI patch makes the selected Grapefruit palette authoritative, keeps
  Android's HCT tonal generation as the compatibility/contrast backend, and maps
  Grapefruit's exact semantic colors onto public dynamic-color resources.
- The Settings patch adds a page and a read-only provider to **the stock Settings
  application**. It reuses the existing universal-theme authority, so the legacy
  standalone launcher can also follow the OS. The separate Grapefruit Settings
  companion APK is not installed in the OS.
- The modern launcher retains Quickstep's service, manifest, privileges, Home
  activity, overview pipeline, widgets, folders, work-profile handling and
  gestures. Its appearance patch adds solid palette backgrounds, Geist labels,
  label chips, icon backings, a floating dock pill and Grapefruit branding.
  The legacy `launcher/` app remains a separate APK with its existing package ID.
- SystemUI's Compose Quick Settings tile/icon corners derive from half their
  actual target height. Notification corner resources make compact notifications
  capsule-like and expanded content rounded. Existing grouping, focus, actions,
  accessibility and expansion behavior remain in AOSP's implementation.
- The default wallpaper service draws only its own surface; it never calls a
  wallpaper-setting API. AOSP handles separate user-chosen lock-screen photos.
  A theme change cannot overwrite that photo.
- AOSP LatinIME is the system keyboard, branded Grapefruit Keyboard. Character,
  space, action and popup keys use teardrop drawables, with pressed/checked/focus
  states and accent outlines. Key geometry and input hit areas remain native.
  Colors, suggestions and previews read the same system palette; palette changes
  invalidate the keyboard theme cache even when both themes use dark mode.
  Keyboard appearance opens the system Grapefruit theme controls. This applies
  to the included keyboard, not separately installed keyboards such as Gboard.
- Audio, TV and Blue can read the OS selection. The OS-specific Audio theme
  resources use distinct names so Blue's original resource overrides cannot
  accidentally recolor the other selections.

Dynamic-color apps must actually consume Android's dynamic resources. Apps with
fixed colors or embedded fonts cannot be universally reskinned by an OS theme.
Third-party app shapes also remain app-owned. Native monochrome adaptive icons
follow the public palette; launcher icon backings cover the other app icons.
Notification icons, artwork and brand colors are not arbitrarily repainted.
Older Material libraries may additionally gate dynamic color by manufacturer.

## Development build

Use a Linux AOSP workstation with the upstream [build requirements](https://source.android.com/docs/setup/start/requirements)
(at least 400 GB free storage and 64 GB RAM). The small app workspace used for
this integration cannot hold a full AOSP checkout/build.

```sh
mkdir grapefruit-aosp
cd grapefruit-aosp
repo init -u https://android.googlesource.com/platform/manifest -b 29ace668ae756c7b8917c57abb440f6518844b0c
mkdir -p .repo/local_manifests
# Obtain os/manifests/grapefruit.xml from the desired Grapefruit OS branch.
cp /path/to/grapefruitmusic/os/manifests/grapefruit.xml .repo/local_manifests/grapefruit.xml
repo sync -c -j8
python3 vendor/grapefruit/os/tools/generate.py --check
python3 vendor/grapefruit/os/tools/integrate.py --aosp "$PWD" --check
python3 vendor/grapefruit/os/tools/integrate.py --aosp "$PWD" --apply
```

`upstream.json` pins and hashes the patched platform components. The development
manifest still tracks the other AOSP projects on `android17-release`. Before a
release, freeze **all** projects with `repo manifest -r`, archive the source and
patches, and build/test that exact snapshot. Rebase the reviewed patches for
current security maintenance; do not ship the initial baseline unmaintained.

Build app inputs from this checkout using its Gradle wrapper, JDK 21, Android SDK
36 and build tools 35.0.0. Standard inputs are `:app:assembleRelease` and
`:tv:assembleRelease`. The Victory OS input is `:blue-victory:assembleOsVictory` with `BLUE_VICTORY_BUNDLE` set to the prepared private music assets.
These existing apps compile against SDK 36; the platform/Quickstep source targets
the Android 17 platform. Android 4.2's API 17 is unrelated to Android 17's API.

Sign the selected release APKs with the intended app-update certificates, then
stage them. App signing certificates must be retained across future OTA/app
updates. Media apps do not need platform keys or privileged installation.

```sh
# Standard branch; paths are your own signed release outputs.
python3 vendor/grapefruit/os/tools/stage_apks.py \
  --aapt2 /path/to/build-tools/35.0.0/aapt2 \
  --apksigner /path/to/build-tools/35.0.0/apksigner \
  --audio /path/to/signed-audio.apk --tv /path/to/signed-tv.apk

# Victory branch: use --victory /path/to/signed-victory-os.apk instead.
source build/envsetup.sh
lunch grapefruit_cf_x86_64_phone-trunk_staging-userdebug
m
```

The staging tool checks application IDs, verifies signatures and rejects known
debug certificates and debuggable packages. It generates only the selected
edition's APK modules. The selected Audio/Blue module overrides AOSP `Music`;
there is no Android-wide default-music role to set. Quickstep and the wallpaper
are built directly from source. Staged APKs and their generated Blueprint are
ignored by Git.

**Do not flash the Cuttlefish output onto the N-XR.** A real N-XR target requires
the actual OEM device/vendor/kernel trees and validated partition/boot metadata.

## Validation

```sh
python3 os/tools/generate.py --check
python3 -m unittest discover -s os/tests -v
python3 os/tools/check_keyboard_resources.py --sdk-root "$ANDROID_HOME"
python3 os/tools/fetch_upstream.py --dest work/grapefruit-os/upstream
python3 os/tests/verify_aosp.py --upstream work/grapefruit-os/upstream
./gradlew :app:assembleDebug :tv:assembleDebug :launcher:assembleDebug \
  :os-wallpaper:assembleDebug :blue-victory:assembleOsFoss \
  :app:lintDebug :tv:lintDebug :launcher:lintDebug :os-wallpaper:lintDebug \
  :blue-victory:lintOsFoss :launcher:testDebugUnitTest
```

Patch validation checks exact upstream inputs, clean application, repeated
application, XML parsing, and rejection of drift before any project is modified.
This is separate from compiling and booting a full AOSP image. See
[STATUS.md](STATUS.md) for completed checks and the remaining release gates.

## FOSS and OEM distribution

New `os/` platform code is Apache-2.0; existing Grapefruit apps remain
GPL-3.0-or-later, AOSP code retains its upstream licenses, and Geist remains SIL
OFL 1.1. Keep the corresponding source, notices and any required installation
information available for the exact shipped builds. Italics and language/emoji
fallbacks retain their AOSP fonts; apps can still supply their own fonts.

The shipping `osVictory` flavor includes the owner's five recordings as
**Bonus Bundled Content**. Track titles, album tags, source labels and error text
use that wording. The software remains FOSS; the recordings and artwork retain
the owner's separate copyright and distribution terms. Keep the audio and bundle
inputs outside the public branches. The `osFoss` flavor is a code-only CI/test
alternative; it is not the configured shipping Victory preinstall.

To reuse the approved release recordings, without transcoding:

```sh
python3 os/tools/import_victory_music.py --apk /private/Blue-Grapefruit-1.0.apk \
  --sha256 492a57c2cc0d3177e1ea23f0a545e783a0203c242f8c36163573516a9a8c83f8 \
  --output /private/grapefruit-os-bonus
BLUE_VICTORY_BUNDLE=/private/grapefruit-os-bonus/assets \
  ./gradlew :blue-victory:assembleOsVictory
```

The importer authenticates and verifies the existing music, preserves its cover,
retags the private copies and verifies that all five compressed audio streams are
unchanged. Review files stay beside the private bundle. The source remains
buildable independently; packaging the shipping music edition requires that
separate owner-supplied input. Future official-master updates can use the regular
preparer with the same Bonus Bundled Content metadata.

The OS is distributed through source, signed factory images and signed OTAs,
not as an F-Droid OS package. Its independently distributable FOSS apps may
continue to use F-Droid under their normal policies.

Google Apps are an OEM-supplied, separately licensed addition. They are not
downloaded, redistributed, or impersonated here. An already certified device
does not certify this replacement OS or a new SKU. The final signed hardware
and software combination must meet the applicable CDD/CTS/VTS/CTS Verifier and
Google's partner requirements. The OEM owns GMS approval, final provisioning,
production AVB/OTA keys, rollback protection, recovery and factory flashing.
