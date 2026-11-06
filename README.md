# Grapefruit Launcher downloads

## 1.0 beta 7 — Launcher2 base and universal themes

Download `Grapefruit-Launcher-1.0-beta7.apk` and install it over beta 6 (or an earlier
launcher beta). Version code 7 retains the same package and signing certificate;
your Home shortcuts, dock choices, icon cache and settings remain. No uninstall
is needed. Android 4.2.2 (API 17) and newer; target SDK 36 (Android 16).
This is a debug-signed test build. The same APK works on older tablets and newer
phones, with ordinary local themes even without Grapefruit Settings installed.

Beta 7 replaces the old custom/native-system drag path with an adapted **AOSP
Launcher2** workspace and drag core, pinned to the Android 4.2.2 release:

- Horizontal Home pages, a compact header and page indicators. Saved gaps and
  optional half-cell placement remain; hold at a side edge to change page while
  dragging. A spare page appears while arranging.
- Launcher2 handles the finger's down/move/up/cancel stream inside the app. Home,
  dock and action-strip targets use the same controller. Release completes the
  drop and removes the drag image without waiting for an Android drag-service
  callback. Includes layout locking to prevent accidental movement.
- Retains wallpaper-free theme backgrounds, all four palettes, Geist fonts,
  three-line chip labels, themed teardrop plates for every app and the persistent
  all-theme icon cache. The same floating pill dock has four configurable apps
  around fixed All Apps, and stands at the physical right in landscape.
- Grapefruit Settings design: scrolling grouped pages, round back controls,
  rounded rows, accent sections, themed grapefruit-slice switches and custom
  dialogs. About retains its own Geist Pixel Triangle wordmark and now includes
  the AOSP source, Apache licence and complete offline GPL/Apache/OFL notices.

Universal themes use the exact read-only contract established by Grapefruit
Settings. New universal Dark/Light/Pitch Black/Victory Blue selections apply on
resume and while the launcher is visible. A local choice remains until the next
universal revision. Missing or incompatible Settings preserves the local choice;
no system-setting write permission is required. Settings → Universal theme opens
Grapefruit Settings when installed.

Only nearby pages retain icon views. The low-RAM bitmap cache remains 256 KiB,
background cleanup releases images, and each drag owns one small temporary bitmap.
No new runtime library, native library, tracking SDK or Internet permission.

Both builds, all **54 automated tests** and lint (no errors) passed. Checks run on
API 21/28/35 and include actual MotionEvent long press/move/up/cancel, release
without moving, Home/dock transfers, rejected drops, consecutive drags, edge
paging, normal swipes, separate orientation layouts, fine-cell occupancy, icon
caching and universal-theme revision/observer/resume behavior. Physical JP5S and
modern-phone testing is still needed; automated checks do not establish total
process RAM or device-specific performance.

Source for this exact beta, with build instructions and original upstream snapshots:
https://github.com/hayliewordsman/grapefruitmusic/tree/61dba8530aefa0d5055d0ce731f416389487b9e3

Launcher2 upstream (Apache 2.0):
https://android.googlesource.com/platform/packages/apps/Launcher2/+/002c25eede2a33a5378c16c5bb9165179783e729

Original Grapefruit code is GPL-3.0-or-later; adapted AOSP files retain Apache 2.0;
Geist fonts are SIL OFL 1.1. The combined app retains all notices. Checksums are in
`SHA256SUMS.txt`; previous beta APKs remain available.
