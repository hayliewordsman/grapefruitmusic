# Grapefruit Calculator LegacyHolo

[Download the 1.0 beta 1 APK](https://raw.githubusercontent.com/hayliewordsman/grapefruitmusic/codex/grapefruit-calculator-legacyholo/downloads/Grapefruit-Calculator-LegacyHolo-1.0-beta1.apk) — debug-signed test build for Android 4.2.2–4.4.4. [SHA-256 checksums](../downloads/SHA256SUMS.txt).

A standalone Grapefruit redesign of the AOSP **Android 4.2.2** calculator for
**Android 4.2.2–4.4.4 (API 17–19)**. Application ID:
`io.github.hayliewordsman.grapefruitcalculator.legacyholo`.

The basic and scientific panels, expression editing, long-press clear, hardware
keyboard input, math accessibility descriptions, clipboard support and private
calculation history come from AOSP. Portrait, landscape and tablet layouts remain.
The options button is available on phones and tablets, including devices with a
hardware Menu key.

Choose **Options › Theme** for Grapefruit Dark (default), Grapefruit Light,
Pitch Black or Victory Blue. The exact palette and lower-right teardrop path come
from Grapefruit Launcher commit `70302e6759e2a7e0fdb6fc511a6f91b22e13d4ed`.
All calculator keys use that path; wider keys stretch it. The equals key uses the
theme accent gradient. Display, keys and scientific panel update together, and
the choice survives restarts. Geist is reused from `grapefruit-ui` as static fonts
without adding its TLS dependency.

## Build

From the repository root, with JDK 17 or 21 and Android SDK platform 35 installed:

```sh
./gradlew :calculator:assembleDebug :calculator:assembleRelease \
  :calculator:testDebugUnitTest :calculator:lintDebug
```

Install `calculator/build/outputs/apk/debug/calculator-debug.apk` for testing.
The debug build is signed; the release build is unsigned unless
`GRAPEFRUIT_KEYSTORE` and `GRAPEFRUIT_KEYSTORE_PASSWORD` are supplied, using the
repository's PKCS12 key alias `grapefruit`. Minimum SDK 17 preserves v1/JAR signing
for Jelly Bean. The application targets SDK 19 deliberately; the compile SDK does
not raise its minimum version. It installs alongside the stock calculator.

## Source and verification

The starting point is AOSP Calculator tag `android-4.2.2_r1`, commit
`6f8c058716866aecbd29b89064d3b1b76be47207`:
<https://android.googlesource.com/platform/packages/apps/Calculator/+/android-4.2.2_r1/>.
Original Apache 2.0 headers and `NOTICE` are retained. AOSP's bundled
`arity-2.1.2.jar` is retained byte-for-byte. Grapefruit additions follow the
repository license; font licensing remains in the shared font assets.

Changes include standalone Gradle packaging, an independent application ID,
Grapefruit appearance and preferences, public layout classes, a resource-aware
digit-limit lookup, Java collection replacement for the platform's Guava subset,
resource fallbacks, focus-independent touchscreen deletion and lifecycle fixes
needed by the current tools.

The unit suite runs on Robolectric's API 17 and 19 framework models. It checks
arithmetic precedence, deletion, restored history, all four palettes on both
panels, theme selection, landscape operation and tablet controls. Its ViewSwitcher
shadow corrects Robolectric 4.8's separate current/next child bookkeeping; it does
not replace calculator arithmetic. Lint treats AOSP's incomplete translations as
warnings and retains English fallback; new appearance labels are currently English.

These checks do not replace installation, TalkBack and touch testing on physical
Jelly Bean and KitKat devices. Modern Android is outside this LegacyHolo build's
release target.
