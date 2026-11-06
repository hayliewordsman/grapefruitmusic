# Grapefruit Launcher

A separate native Android Home app in the Grapefruit repository. Application ID:
`io.github.hayliewordsman.grapefruitlauncher`. Minimum Android 4.2.2 (API 17), compile
and target SDK 36 (Android 16). It installs alongside Audio and TV.

## 1.0 beta 7

Beta 7 rebuilds the workspace on **AOSP Launcher2**, the launcher shipped with
Android 4.2.2. The integrated core is pinned to upstream commit
`002c25eede2a33a5378c16c5bb9165179783e729`. The original files, Apache 2.0
notices and a precise list of adaptations are in [third_party/launcher2](third_party/launcher2/README.md).
This uses the Launcher2 workspace/drag core with a Grapefruit shell; it does not
ship the entire stock launcher or its private platform dependencies.

- Home is a horizontal paged workspace with a compact header and page indicators.
  Existing shortcuts, saved gaps, half-cell coordinates, separate orientation
  layouts, dock customizations and icon cache migrate in place. Hold near either
  side while dragging to change pages; a spare page appears while arranging.
- Launcher2 owns the whole in-window pointer lifecycle: press, move, drop on
  finger-up, cancel, source completion and drag-image removal. It no longer uses
  Android's cross-window `startDrag`/`DragEvent` handshake. Home, the dock and the
  top action strip share the same target search and completion logic.
- Keeps all four themes, wallpaper-free backgrounds, Geist label chips wrapping
  to three lines, themed teardrop plates for every app, the persistent all-theme
  icon cache, suggested dock apps, fixed middle All Apps, and the same floating
  pill dock that becomes vertical at the physical right in landscape.
- Settings now follows Audio/TV's scrolling grouped pages, round back controls,
  56 dp rounded rows, accent section titles, gray values, footers, custom choice
  dialogs and the same rolling grapefruit-slice switches. Includes optional
  half-cell placement and layout locking. About keeps the own Triangle wordmark
  and adds the AOSP source, licence and complete offline Apache/GPL/OFL notices.
- Nearby pages retain icon views; distant pages release their images into a
  bounded recycling pool. A drag allocates one small icon bitmap and releases it
  on completion. The existing 256 KiB low-RAM cache/background cleanup remain.
- Version code 7; the published APK retains the earlier beta signing certificate
  and package ID. Install over beta 6, with no uninstall or settings reset.

### Universal Grapefruit theme

Uses the exact optional, read-only `UniversalTheme` contract introduced by
[Grapefruit Settings](https://github.com/hayliewordsman/grapefruitmusic/tree/df116946820c4f47accc9803419ab36650d8306e/settings).
The shared helper is included in `grapefruit-ui` without adding runtime libraries.

- Reads `theme` and `revision` from
  `content://io.github.hayliewordsman.grapefruitsettings.theme/theme`.
  Canonical IDs map directly: Dark 0, Light 1, Pitch Black 2, Victory Blue 3.
- Adopts a new universal selection on launch/resume and observes changes while
  visible. The observer is removed in the background. Icons already prepared in
  each theme are reused immediately.
- A manual launcher theme remains until a **new revision** is selected universally;
  rereading the same revision does not undo it. The launcher uses the universal
  revision, not the separate media revision used by standard Audio/TV for Blue.
- Missing/inaccessible/uninitialized or invalid providers preserve the local
  choice. The launcher works independently on older and newer Android phones.
- Settings → Universal theme opens Grapefruit Settings if available. This client
  does not write into that provider or Android's system settings. Queries are
  scoped to the companion's authority/package; no new permission is required.

## 1.0 beta 6

- Adds an 8 dp gap between Home's Edit/Done and Settings buttons.
- Finishes drag UI when the drop reaches the window, after the destination saves
  its placement, instead of waiting for Android's final drag-ended notification.
  Home/dock icons return to full opacity, temporary drop controls and placement
  overlays close, and Home's drag guides and edge scrolling stop. Rejected drops
  restore the previous screen, including All Apps search; successful drawer drops
  show the placed shortcut on Home. Cancelled drags still restore the original UI.
- Completion callbacks belong to one drag session; a late callback cannot end the
  next drag. Regression tests deliver drops without a final drag-ended event.
- Version code 6; the published APK uses the existing certificate and application
  ID, installs over beta 5 and preserves shortcuts, icon cache and settings.

## 1.0 beta 5

- Hold and drag icons on Home, from All Apps to Home, between Home and the dock,
  or between the four configurable dock positions. All Apps stays fixed in the
  middle. Drop on the top strip to remove a shortcut or open app options. Cancelled
  drags keep the original layout. Dropping onto one occupied Home cell swaps the
  two existing Home shortcuts; overlapping multiple icons is rejected.
- Home stores sparse cell coordinates: moving an icon leaves its space empty.
  **Settings → Icon placement → Half-cell subgrid** enables finer snapping without
  overlaps. Turning it off projects positions onto a normal grid; saved fine
  positions return when re-enabled. Portrait and landscape use independent layouts.
- Landscape moves the navigation pill to the physical right edge and adapts its
  height/icon size to the screen. Home cells are kept only near the visible area,
  with a small recycling pool; the persistent icon cache and low-RAM budget remain.
- Tap **Edit** on Home, then an icon, for replacement/removal and directional
  movement controls. Drag near the top or bottom of Home to scroll into more space.
- Settings follows Audio/TV's grouped rounded rows. About includes an original
  **Grapefruit Launcher** wordmark in Geist Pixel Triangle, GPL-3.0-or-later and
  no-warranty declarations, public source/support links, privacy information,
  acknowledgements and the complete GPL/OFL texts available offline.
- Version code 5; the published APK uses the same application ID and debug signing
  certificate as previous betas. Install over the existing app to retain settings.

## 1.0 beta 4

- On first launch after installation or upgrading from a version without a disk
  cache, the launcher prepares **every launchable installed app's icon in all four
  themes**. It shows progress while preparing the app list. Icon PNGs are stored
  in private internal app files, so they survive restarts and memory cleanup.
- All Apps and Home read prepared icons before changing a cell, so a RAM-cache
  miss no longer replaces a saved icon with an asynchronous loading plate. There
  is no repeated PackageManager lookup or plate rendering during normal scrolling.
- App-added/changed/replaced broadcasts rebuild only the affected app's icon set,
  including when the launcher is in the background. Uninstalls remove obsolete
  files. On startup, package version/update timestamps also catch changes that
  happened while the launcher process was stopped.
- Theme switches use their prepared files immediately. The small RAM cache,
  weak image reuse, background memory cleanup and idle-thread shutdown remain.
  Disk metadata holds no bitmaps; preparation renders and saves one small image
  at a time. Storage scales with installed icons rather than retaining all of them
  in the JP5S's RAM.
- Files are written atomically; incomplete/corrupt images can be rebuilt. If app
  storage is unavailable, the launcher still lists/opens apps and reports the
  failed preparation, with individual icons falling back to background loading.
- Version code 4; the APK uses the existing debug certificate and application ID,
  installs over beta 3, and preserves Home, dock and theme settings.

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
- While another app is open, the launcher releases its RAM icon cache, grid cells and
  dock views; these restore on return. Memory-pressure callbacks also clear the
  RAM cache (persistent files remain). App catalog queries coalesce and run on startup/package changes rather
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
3. Home initially contains up to twelve apps. Swipe left/right between pages. Hold and drag an icon to a
   free cell, swap it with another Home icon, or move it to the dock. Drag from
   All Apps to add a shortcut. Drop on the top strip for removal or app options.
   Hold near the left or right edge to change page while dragging. Tap **Edit**,
   then an icon, for options including directional movement controls.
4. The dock has **four app positions plus All Apps fixed in the center**. Touch an
   empty position to pick an app. Drag occupied positions to swap them; use Edit
   then tap an occupied position to replace/remove it. All four positions remain
   configurable. The dock is horizontal in portrait and vertical at right in landscape.
5. Open **All Apps** for a searchable alphabetical list. Search matches labels and
   package names. Home and Back return to Home and close the keyboard/search.
6. Settings changes all surfaces, labels and icon plates immediately. Choose
   **Half-cell subgrid** for finer placement, or **Lock layout** to prevent dragging. Home coordinates,
   dock choices and theme survive restarts. Unavailable apps are omitted; saved
   shortcuts can return after reinstalling. Empty Home spaces stay empty.

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
all-app/all-theme disk preparation, reuse across restart, background app installs,
incremental updates and uninstalls, corruption recovery, synchronous icons while
scrolling the actual app grid, multiline chips, theme persistence, teardrop rendering, sparse drag/drop placement, dock transfers, grid/subgrid
persistence, separate orientation coordinates, right-edge landscape/RTL layout,
offline licence declarations, virtualized Home cells, and startup on API 21 and API 35. Native rendering tests
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

Beta 7 validation: **54 automated checks** pass across API 21, 28 and 35, including
real MotionEvent long-press/move/release/cancel routing, drops without a preceding
move, Home/dock transfers, rejected drawer drops, consecutive drags, edge paging,
page swipes, sparse/fine layouts, background image release and universal-theme
read/observe/resume/revision behavior. The earlier icon-cache tests remain. No
system drag service handshake is mocked: Launcher2 processes the pointer stream
inside the app. Debug/release builds and API-compatibility lint pass (no errors).
Physical Android 4.2.2/JP5S and modern-phone testing is still required; automated
checks do not establish device-specific gesture performance or process RAM.

## Free and open-source software

Original Grapefruit launcher code, branding and icon plates are **GPL-3.0-or-later**.
Adapted AOSP Launcher2 files retain their **Apache-2.0** headers and notices; the
combined app is distributed under GPL-3.0-or-later with those notices retained. See the
repository [LICENSE](../LICENSE) and the bundled [notice](src/main/assets/NOTICE.txt).
The full GPL, source link, no-warranty declaration and acknowledgements are in
**Settings → About Grapefruit Launcher** and work without a network connection.
The Android framework is the only runtime API dependency; there is no proprietary
runtime SDK. Geist fonts, including the Triangle wordmark subset, retain their
**SIL OFL 1.1** licence and copyright notice in the shared font assets.

Regenerate the wordmark from the pinned `geist@1.9.0` npm package (unpack its tarball):

```sh
python -m pip install fonttools brotli
python tools/launcher/wordmark.py path/to/unpacked/geist/package
```

The script creates a static TTF with all glyphs in “Grapefruit Launcher” for old
Android versions. It preserves the upstream font names and licence metadata.
