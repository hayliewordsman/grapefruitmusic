# LegacyHolo beta 2 validation — 2026-11-06

- Debug and release builds, debug lint and release vital lint pass.
- 20 unit checks pass across Robolectric API 17 and 19 framework models.
- Debug APK v1/JAR signing verifies with minimum SDK 17; its certificate matches beta 1.
- Minimum SDK remains 17 and target SDK remains 19; version code is 2.
- All five launcher bitmap densities use the new app-specific artwork.
- Universal-theme tests cover all four canonical IDs, revision-based local overrides,
  malformed or unavailable providers, and preservation of unrelated settings.
- In an Android 4.2.2 x86 emulator, both apps received Light and Victory Blue from
  the actual Grapefruit Settings UI preview/provider built from commit `df11694`.

Calculator checks cover arithmetic, deletion, history restoration, both panels,
four themes, portrait/landscape/tablet layouts and theme selection. Emulator
installation and universal theme updates completed without AndroidRuntime fatal
exceptions. Physical-device touch/TalkBack testing remains pending.

Download APKs are debug-signed beta tests. Release signing uses the repository’s
existing keystore environment variables. AOSP lint warnings remain visible.
