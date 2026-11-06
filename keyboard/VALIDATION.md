# LegacyHolo beta 3 validation — 2026-11-06

- Debug and release builds, debug lint and release vital lint pass.
- 60 unit checks pass across Robolectric API 17 and 19 framework models.
- Debug APK v1/JAR signing verifies with minimum SDK 17; its certificate matches beta 1.
- Minimum SDK remains 17 and target SDK remains 19; version code is 3.
- All five launcher bitmap densities use the new app-specific artwork.
- Universal-theme tests cover all four canonical IDs, revision-based local overrides,
  malformed or unavailable providers, and preservation of unrelated settings.
- In an Android 4.2.2 x86 emulator, both apps received Light and Victory Blue from
  the actual Grapefruit Settings UI preview/provider built from commit `df11694`.

The beta 1 crash was reproduced with the IME enabled: `Resources.NotFoundException:
Resource ID #0x0` in `InitializeBinaryDictionary` / `BinaryDictionaryGetter`.
The installed application ID differs from the Java namespace. Dictionary and
subtype-name lookups now use the resource package; the main fallback uses `R.raw.main`.
Regression tests failed before the fix and pass afterward. An in-place upgrade
from beta 1 opened successfully with Victory Blue preserved and no AndroidRuntime
fatal exception. The launcher and activity labels both read **Grapefruit Keyboard**.

Reference-layout checks cover phone, 600dp tablet and 768dp tablet configurations,
with the number row on and off. They verify key positions, both Shift keys,
all keys within keyboard bounds, centered spacebars, non-touchable gutters,
primary/secondary symbols, numeric-pane accessibility descriptions, and unchanged
numeric editors. Messaging fields keep Enter or the editor’s Send action beside
the home row, with the dedicated smiley key on the bottom row.
Number-row tests cover persistence, invalid preference types, height scaling,
shifted letters and maximum-height landscape bounds.

Input-view regression checks exercise all four themes with a 270px preview
backing: its background remains transparent, the suggestion strip measures to
its resource-defined height, and the keyboard begins immediately below the strip.
This catches the opaque-root background that made the preview area look like a
large suggestion bar. Preview/expanded-suggestion backing remains available.

In the Android 4.2.2 x86 emulator, an in-place beta 2 → beta 3 update preserved
settings. Real key taps typed `q -'12q`: the gutters produced no input, the pill
produced one space, symbols produced dash/apostrophe, and `1234` opened the numeric
pad and returned through ABC. Turning on **Number row** through Settings while
the IME was running added working 1–0 keys; further taps appended `12q`. No
AndroidRuntime fatal exception occurred. The legacy emulator image returns blank
screen captures, so visual regression checks use view/background/layout geometry;
physical JP5s appearance testing remains pending.

Appearance tests cover all saved themes, activity reopening, invalid preference
types/values and switching Victory Blue without recreating Settings.
The APK includes ARMv7/x86 JNI libraries and eight uncompressed dictionaries.
JVM layout tests stub native proximity allocation; the emulator uses the real
packaged x86 engine. With this IME active in the 4.2.2 emulator, tapping a letter,
switching to symbols, and tapping apostrophe/dash entered `q'-` in the settings
test field without a fatal exception. Selecting Victory Blue through the settings
dialog, stopping the app, and reopening it preserved the theme without a fatal
exception. Physical JP5s/ARM and KitKat device testing is still needed.

Download APKs are debug-signed beta tests. Release signing uses the repository’s
existing keystore environment variables. AOSP lint warnings remain visible.
