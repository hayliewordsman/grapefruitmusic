# Grapefruit Launcher downloads

## 1.0 beta 2

Download `Grapefruit-Launcher-1.0-beta2.apk`. It installs over the first preview:
the package ID and signing certificate are unchanged, and version code is now 2.
No uninstall is required. Home and theme settings are retained.

- Low-memory icon cache: 256 KiB, down from 4 MiB. Android 4.2/4.3 always get this budget.
- Shared/bounded icon jobs, recycled-view cancellation, background icon cleanup, and idle thread shutdown.
- Multiline app-label chips, up to three lines.
- Phone, Messages, All Apps, Chrome, Grapefruit Audio dock. Missing apps leave blank slots.
- The new default dock is applied once on upgrade. All four app slots remain editable;
  later choices and deliberately blank slots stay saved.
- Unused TLS/native dependencies removed from this offline launcher.

This is a **debug-signed testing build**, supporting Android 4.2.2 (API 17) and later.
Both builds, 15 automated tests, and lint passed. Total JP5S process RAM has not been
measured on a physical device.

Source for this exact beta:
https://github.com/hayliewordsman/grapefruitmusic/tree/2058cca938522ad396f66e37c6576edc752d5e13

SHA-256 checksums are in `SHA256SUMS.txt`. The first preview APK is retained below
for reference; beta 2 is the update to install.
