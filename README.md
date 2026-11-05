# Grapefruit Launcher downloads

## 1.0 beta 4 — persistent icon cache

Download `Grapefruit-Launcher-1.0-beta4.apk` and install it over your current
preview/beta. The application ID and signing certificate match; version code is 4.
Home, dock and theme settings are preserved. No uninstall is needed.

On first launch after this update, let **Preparing your apps** finish. The launcher
saves every launchable installed app's icon in all four themes to private internal
storage. Prepared icons are read before cells change, so an icon leaving the small
RAM cache does not require an asynchronous PackageManager reload while scrolling.

New or changed apps refresh only their own cached set; uninstalled apps' obsolete
files are removed. This also works while the launcher is in the background. Startup
reconciles changes missed while its process was stopped. Cache files survive restarts
and memory cleanup; the low-memory bitmap cache remains 256 KiB. Small disk metadata
contains no bitmap artwork, and preparation saves one image at a time.

Both builds, 25 automated tests and lint passed, including real-grid scrolling
through 80 apps, background app installation, all-theme preparation, cache reuse
after restart, incremental updates/uninstalls, and corrupted-file recovery.
Physical JP5S performance profiling is still needed for device-specific results.

Source for this exact beta:
https://github.com/hayliewordsman/grapefruitmusic/tree/70302e6759e2a7e0fdb6fc511a6f91b22e13d4ed

Android 4.2.2 (API 17) and later. This is a debug-signed test build. Checksums are in
`SHA256SUMS.txt`. Previous preview/beta APKs remain available for reference.
