# Grapefruit Launcher downloads

## 1.0 beta 6 — release icons on drop

Download `Grapefruit-Launcher-1.0-beta6.apk` and install it over your existing
preview/beta. Version code 6 uses the same application ID and signing certificate;
Home shortcuts, dock choices, prepared icon cache and settings are preserved.
No uninstall is needed. Android 4.2.2 (API 17) and later. Debug-signed test build.

- Adds an 8 dp gap between Home's Edit/Done and Settings buttons.
- Finishes the drag UI on drop, after saving the placement, without waiting for
  Android's final drag-ended notification. The icon returns to full opacity;
  temporary drop controls/placement views close, and Home drag guides and edge
  scrolling stop. The normal header returns.
- Rejected drops restore the prior screen, including the All Apps search. Successful
  drawer drops show the placed shortcut on Home. Cancellation still restores the
  original UI, and a delayed callback from an old drag cannot finish the next drag.

The beta 5 features remain: sparse Home placement, optional half-cell subgrid,
configurable dock, separate portrait/landscape arrangements and a tall right-side
landscape dock. Settings includes the own Geist Pixel Triangle wordmark, public
source links, GPL-3.0-or-later declarations and complete offline GPL/OFL notices.
The persistent icon cache, visible-area Home cells and low-RAM bitmap budget remain.

Debug/release builds, all 43 automated tests and lint passed. New regression checks
first reproduced stuck drag UI, then verified drops without a final drag-ended
event: Home placement, drawer placement, rejected drawer drops, shortcut removal,
header/search restoration and consecutive/cancelled drags. Android's window-manager
handshake is simulated; actual view dispatch and drop handlers run in the tests.
Physical JP5S/Android 4.2.2 gesture testing is still needed.

Source for this exact beta (GPL-3.0-or-later, build instructions included):
https://github.com/hayliewordsman/grapefruitmusic/tree/7e3ce2dcfdf693652f212cf5970ed9f1845c613f

Geist fonts are SIL OFL 1.1; notices are bundled in the APK and source.
Checksums are in `SHA256SUMS.txt`. Previous preview/beta APKs remain available.
