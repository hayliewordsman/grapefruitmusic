# LegacyHolo beta 2 validation — 2026-11-06

- Debug and release builds, debug lint and release vital lint pass.
- 42 unit checks pass across Robolectric API 17 and 19 framework models.
- Debug APK v1/JAR signing verifies with minimum SDK 17; its certificate matches beta 1.
- Minimum SDK remains 17 and target SDK remains 19; version code is 2.
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

Tablet tests at 600dp and 768dp verify apostrophe/dash on symbols, comma/period
on letters, expanded spacebar bounds, and both language-switch-key states.
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
