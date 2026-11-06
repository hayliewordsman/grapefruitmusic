# Grapefruit Keyboard LegacyHolo

[Download the 1.0 beta 3 APK](https://raw.githubusercontent.com/hayliewordsman/grapefruitmusic/codex/grapefruit-keyboard-legacyholo/downloads/Grapefruit-Keyboard-LegacyHolo-1.0-beta3.apk) — debug-signed test build for Android 4.2.2–4.4.4. [SHA-256 checksums](../downloads/SHA256SUMS.txt).

A standalone Grapefruit redesign of **AOSP LatinIME from Android 4.2.2**, for
**Android 4.2.2–4.4.4 (API 17–19)**. Application ID:
`io.github.hayliewordsman.grapefruitkeyboard.legacyholo`.

Install the APK, open Grapefruit Keyboard, then use **Enable Grapefruit
Keyboard** and **Choose Grapefruit Keyboard**. A text field at the bottom of
Settings lets you try it. The original language selector and AOSP typing settings
remain available.

## Beta 3

The oversized colored area above the keys is fixed. AOSP reserves transparent
space there for key previews and expanded suggestions; only the actual suggestion
strip and keyboard now receive the Grapefruit background.

English and no-language QWERTY use the supplied reference arrangement on phones
and tablets: backspace beside Q–P, Enter beside A–L, Shift at both ends of the
Z–M / ! / ? row, and mode keys at both ends of the bottom row. The symbols page
places digits and backspace on its first row; apostrophe, dash and the other
symbols follow the reference. Comma and period are on the bottom row of both pages.
The `1234` button opens a numeric pad with an **ABC** return key.

**Number row**, in the Grapefruit section of keyboard Settings, defaults off.
Enable it for a dedicated 1–0 row above QWERTY. It adds a row while retaining the
chosen key height, bounded by the available screen space. Changes take effect
when the keyboard next appears.

The spacebar is a centered pill in every Grapefruit theme, with non-touchable
empty gutters on both sides. The language-switch key uses its reserved slot
without shifting the pill. Other keys retain the Grapefruit teardrop design.

Version code 3 installs over beta 1 or beta 2 with the same signing key and keeps
existing settings. Other language arrangements retain their AOSP geometry.

## Earlier fixes retained

The app now has its own Grapefruit keyboard icon. Its launcher, recent-apps and
keyboard-picker label is **Grapefruit Keyboard**; the project remains LegacyHolo.
Theme selection updates the settings screen in place rather than recreating the
activity from the preference dialog. Appearance preferences are checked before
platform preferences load, so invalid values/types cannot prevent settings from
opening; a valid saved Victory Blue choice is kept. The reproduced API 17
crash in `InitializeBinaryDictionary` is fixed: dictionary and subtype resource
lookups now use the installed resource package instead of the old AOSP Java
namespace, and the fallback resource is a real `R.raw` ID. Preference change listeners
start only after initialization.

## Appearance

Settings offers Grapefruit Dark (default), Grapefruit Light, Pitch Black and
Victory Blue. Colors and the lower-right teardrop path match Grapefruit Launcher
commit `70302e6759e2a7e0fdb6fc511a6f91b22e13d4ed`. Letter, number, symbol and
long-press popup keys use the same geometry. Wide keys stretch it; the spacebar
uses a rounded pill with the same palette, gradient, border and pressed colors.
The Enter key uses the accent gradient. Key previews, hints, icons, spacebar
language labels and suggestions follow the chosen palette, with static Geist fonts.

**Key height** offers 70%–150% in 10% steps, with 100% as the default. It scales
the AOSP layout before row positions, key bounds and proximity grids are built,
so touch targets match the keys. Height is bounded on short screens to leave room
for the app and suggestion strip. Theme, height and the number-row choice persist; the IME rebuilds its
view and clears cached layouts when one changes, on its next appearance.

## Universal Grapefruit Apps Theme

This build reads the existing Grapefruit Settings contract from
`content://io.github.hayliewordsman.grapefruitsettings.theme/theme`, using the
canonical `theme` and `revision` columns (Dark 0, Light 1, Black 2, Blue 3).
It checks when settings opens/resumes and when the keyboard starts an input view.
All four universal themes, including Victory Blue, are supported. A local choice
is kept until a **new** universal selection/revision. Without a compatible Settings
provider, the local theme continues to work. No provider writes or new permission
are required. Contract source: `codex/jp5s-grapefruit-settings`, commit `df11694`.

## AOSP behavior

The AOSP language layouts, alternate characters, suggestions, autocorrection,
personal dictionary, password/numeric/phone layouts, shift/symbol states,
sound/vibration controls and spell-check service remain. The original bundled
dictionaries and native dictionary/proximity engine are included for ARMv7 and
x86, the 32-bit Android architectures in the requested version range.

AOSP's open-source 4.2.2 tree does **not** include Google's proprietary gesture
decoder. Gesture typing stays disabled, matching upstream. Voice shortcuts depend
on a separate installed voice IME. This keyboard itself has no Internet permission.

## Build

Use JDK 17 or 21, Android SDK platform 35 and **NDK r17c (`17.2.4988734`)**:

```sh
sdkmanager "platforms;android-35" "ndk;17.2.4988734"
./gradlew :keyboard:assembleDebug :keyboard:assembleRelease \
  :keyboard:testDebugUnitTest :keyboard:lintDebug
```

The JNI build deliberately selects NDK r17c's GCC 4.9 and static GNU STL, which
retain Android 17 support and compile the original native engine. No native
binary is downloaded during the build; it is compiled from `src/main/jni`.
Dictionary files remain uncompressed so AOSP can memory-map them.

Install `keyboard/build/outputs/apk/debug/keyboard-debug.apk` for testing.
Release output is unsigned unless `GRAPEFRUIT_KEYSTORE` and
`GRAPEFRUIT_KEYSTORE_PASSWORD` are provided, with PKCS12 alias `grapefruit`.
Minimum SDK 17 preserves v1/JAR signing. Target SDK 19 is deliberate; compile SDK
35 does not raise the minimum. The application installs alongside the stock IME.

## Source and verification

Upstream is AOSP LatinIME tag `android-4.2.2_r1`, commit
`a610aa2bc5e1a79c405f5166865cfe54eb8d74b5`:
<https://android.googlesource.com/platform/packages/inputmethods/LatinIME/+/android-4.2.2_r1/>.
The matching AOSP `frameworks/opt/inputmethodcommon` sources are included to
support the language settings. Original Apache 2.0 headers and `NOTICE` remain.
Grapefruit additions follow the repository license; shared font licenses remain
with the assets.

Adaptations include Gradle/NDK packaging, resource namespaces, an independent app
ID, branded settings, theme/height controls, palette-aware drawing, saved Canvas
clipping, preference-fragment validation for patched KitKat, fallback resources
and lifecycle fixes needed by the current tools.

Robolectric tests use API 17 and 19 framework models. They check actual row/key
geometry and hit neighbors after height changes, screen bounds in landscape,
numeric and password layouts, English/French/German/Russian/Arabic layouts,
long-press alternatives, theme preferences and the launcher settings activity.
Only the native proximity allocation/release is stubbed in those JVM tests;
the APK's native ARMv7/x86 libraries are built and packaged separately. Lint keeps
upstream's incomplete translations as warnings with English fallback. New
appearance labels are currently English.

Physical-device testing of typing, native suggestions, TalkBack, language changes
and height changes on Jelly Bean and KitKat remains necessary before a stable
release. Modern Android is outside this LegacyHolo build's release target.
