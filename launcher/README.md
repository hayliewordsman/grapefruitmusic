# Grapefruit Launcher

A separate native Android Home app in the Grapefruit repository. Application ID:
`io.github.hayliewordsman.grapefruitlauncher`. Minimum Android 4.2.2 (API 17), compile
and target SDK 36 (Android 16). It installs alongside Audio and TV.

## 1.0 beta 3

- Fixes icon flashing/reloading when All Apps or Home rebinds cells while scrolling.
  An icon already displayed for the same app stays in its cell even after its
  entry leaves the small LRU cache. Recycled cells can also immediately reuse a
  bitmap that another cell still displays.
- The 256 KiB low-memory cache remains unchanged. A bounded registry of weak
  references locates existing live bitmaps without retaining additional bitmap
  allocations. Background cleanup and idle-worker shutdown remain in effect.
- Package changes invalidate old image bindings, and theme switches still rebuild
  icons with the selected palette. Previously unseen or garbage-collected icons
  still load asynchronously; rebinding an already displayed icon no longer clears it.
- Version code 3; the published APK uses the same debug certificate and app ID as
  beta 2, so it installs over the existing launcher and keeps saved settings.

## 1.0 beta 2

- Version code 2 updates over the first preview (code 1) when signed with its same
  certificate. The published beta 2 APK keeps that preview's debug certificate.
- Labels wrap naturally to **up to three lines**. Short labels keep a compact chip;
  the app grid reserves a consistent row height so longer chips do not overlap.
- The default dock is **Phone, Messages, All Apps, Chrome, Grapefruit Audio**.
  Phone and Messages use available Android handlers; Chrome and Audio use their
  app packages. Missing apps leave their own slots blank. Every app position is
  still replaceable or removable with a long press, and a blank slot opens a picker.
- On upgrade, beta 2 replaces the first preview's arbitrary initial dock once.
  Later user changes, including deliberately empty slots, survive catalog refreshes
  and restarts. An untouched blank slot can fill if its suggested app is installed
  later. Settings offers **Reset dock to suggested apps**. Home and theme are kept.

### Memory work for older tablets such as the JP5S

- Icon-cache budget reduced from **4 MiB to 256 KiB** on Android 4.2/4.3, for
  a heap class of 64 MiB or less, or on a device Android reports as low RAM;
  other devices use 1 MiB. The JP5S gets the small budget even though Android
  4.2 predates `isLowRamDevice()`.
- Icons render at their displayed size, capped at 96 px on low-memory devices
  (144 px otherwise), retaining transparency and the themed plates.
- A single worker drain shares duplicate icon requests, skips recycled views,
  limits pending icon jobs to 64, and keeps weak references to destination views.
  Old screen/theme requests are discarded. Theme changes evict the previous pack.
- While another app is open, the launcher releases its icon cache, grid cells and
  dock views; these restore on return. Memory-pressure callbacks also clear the
  cache. App catalog queries coalesce and run on startup/package changes rather
  than each opening of All Apps. Worker threads exit after two idle seconds.
- Screen references are cleared when switching pages; fonts load once per process.
  The launcher compiles the shared `Fonts.java` and uses the same shared font assets
  directly, without the unused Conscrypt/TLS code or native libraries.

These are bounded allocations and lifecycle checks, **not a measurement of total
JP5S process RAM**. Device profiling is still needed to quantify total PSS and the
system/GPU memory retained by Android.

## Appearance

- The exact bundled **Geist Regular, Medium, SemiBold and Bold** fonts used by
  Audio and TV, from `grapefruit-ui/`. The shared asset includes its SIL OFL license,
  also available in Settings.
- **Grapefruit Light**, **Grapefruit Dark** (default), **Pitch Black** and
  **Victory Blue**. The first three use Audio's palette values. Victory Blue uses
  the Blue Grapefruit Victory palette preserved in repository commit `f066424`.
  The original apps' themes are unaffected.
- A solid theme background; the launcher never displays or sets system wallpaper.
  The Android wallpaper setting itself is left alone.
- App labels use each theme's filled, hairline-outlined pill chips and Geist Medium.
- The dock uses the Grapefruit apps' surface-colored floating navigation pill,
  outline, shadow on Android 5+, and background fade. It has no visible text labels.
  TalkBack and keyboard users still get app names and focus feedback.

## Exclusive icon packs

The four packs are built into this launcher and follow the theme automatically.
Every launchable app visible to the current Android profile gets a plate, including
newly installed apps, without an app-by-app icon mapping. Original app artwork is
fitted inside the same teardrop: a circle extended into a gently rounded bottom-right
corner. Wide artwork is fitted rather than cropped. Each plate uses its theme's
surface/control gradient and accent outline; All Apps uses its accent gradient.

This is implemented with ordinary Canvas paths and drawables on **API 17 and up**.
It does not depend on the system's `AdaptiveIconDrawable`, which only exists on
Android 8+. It accepts modern adaptive source icons through PackageManager as well
as legacy PNGs. It does not replace icons outside this launcher or export an icon
pack to other launchers. Icons are loaded in a worker and held in a bounded cache.

## Use

1. Install the debug APK to try it, and open **Grapefruit Launcher**.
2. Choose **Settings → Set as default launcher**, or press Home and choose it.
   On Android 4.2, choose **Always** in the Home resolver. If another Home app is
   already the default, clear that app's defaults in Android settings first.
3. The initial Home page contains up to twelve apps. Touch and hold an app to
   add/remove it from Home, move it earlier/later, place it in the dock, open app
   info, or invoke Android's uninstall screen.
4. The dock has **four app positions plus All Apps fixed in the center**. Touch an
   empty position to pick an app. Hold an occupied position to replace or remove
   it. Placing an app in another dock position moves it rather than duplicating it.
5. Open **All Apps** for a searchable alphabetical list. Search matches labels and
   package names. Home and Back return to Home and close the keyboard/search.
6. Settings changes all surfaces, labels and icon plates immediately. Home order,
   dock positions and theme survive restarts. Removed/unavailable apps are omitted
   from Home and dock, but their saved positions can return after reinstalling.

No network permission, account, advertising, storage permission or broad
`QUERY_ALL_PACKAGES` permission is requested. App discovery uses a scoped launcher
intent query. The application does not require Audio or TV to be installed. Widgets,
folders and cross-profile administration are outside this first version.

## Build and check

Use the repository's Gradle wrapper, JDK 21, SDK platform 36 and build tools 35.0.0:

```sh
./gradlew :launcher:assembleDebug :launcher:assembleRelease :launcher:testDebugUnitTest :launcher:lintDebug
```

The installable test APK is `launcher/build/outputs/apk/debug/launcher-debug.apk`.
The release APK is unsigned unless `GRAPEFRUIT_KEYSTORE` and
`GRAPEFRUIT_KEYSTORE_PASSWORD` are supplied, following the other apps' release-key
convention. Debug uses Android's normal debug certificate. Release publication is
not configured; CI uploads build artifacts only.

Tests cover catalog search, launch intents, installation refresh, Home removal and
ordering, default dock holes and migration, user replacements/cleared slots,
shared icon work, low-memory cache limits, background cleanup/restoration,
multiline chips, theme persistence, teardrop rendering, portrait/landscape layout
and startup on API 21 and API 35. Native rendering tests
write previews under `work/launcher/previews/`. API 17 compatibility is checked by
lint and the APK's minimum-SDK metadata; Robolectric 4.14 cannot run API 17. Device
testing on Android 4.2.2 and Android 16 remains necessary before a production release,
including Home-role setup, system gestures, keyboard and large text. No emulator or
physical-device test is implied by the unit tests.

Regenerate the launcher app's own raster/adaptive brand assets with:

```sh
python tools/launcher/brand.py
```

This script requires Pillow. The procedural icon packs do not need generated assets.
