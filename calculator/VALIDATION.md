# LegacyHolo validation — 2026-11-05

- `assembleDebug` and `assembleRelease` build successfully.
- `testDebugUnitTest`: 12 checks passed across Robolectric API 17 and 19 framework models.
- `lintDebug` and release vital lint pass. The AOSP resource/translation/style warnings remain visible.
- Debug APK signature verified with `apksigner verify --verbose --min-sdk-version 17`;
  v1/JAR and v2 signatures verify.
- APK manifest reports minimum SDK 17, target SDK 19 and the LegacyHolo application name.

Physical Android 4.2.2/4.4.4 installation, touch input and TalkBack validation remain
pending. These are debug-signed beta test builds; release builds require the
repository's release signing environment variables.
