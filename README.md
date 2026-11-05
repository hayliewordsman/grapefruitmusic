# Grapefruit Launcher downloads

## 1.0 beta 3 — scrolling icon fix

Download `Grapefruit-Launcher-1.0-beta3.apk`. Install it over beta 2; the package ID
and signing certificate match, and the version code is now 3. No uninstall is needed.
Home, theme and dock settings are retained.

Beta 3 fixes icons clearing and reloading when cells are rebound while scrolling
in All Apps or Home. Already displayed images stay in place, and recycled cells
can reuse a bitmap that is still displayed elsewhere. The 256 KiB low-memory cache,
background cleanup and idle-worker shutdown remain. Weak references locate existing
images without keeping an additional bitmap cache alive. Package updates and theme
changes still refresh the artwork correctly.

Both builds, 19 automated tests and lint passed. Four new regressions cover cache
eviction, same-app rebinding, recycled-cell reuse, memory trim and content/theme refresh.
New or garbage-collected icons still load asynchronously when needed. No physical
JP5S performance measurement is implied by these tests.

Source for this exact beta:
https://github.com/hayliewordsman/grapefruitmusic/tree/1eafad74247360576f51830fe897c6713a0ca126

The launcher supports Android 4.2.2 (API 17) and later. This is a debug-signed test
build. SHA-256 checksums are in `SHA256SUMS.txt`. Earlier preview and beta 2 APKs
are kept for reference; beta 3 is the update to install.
