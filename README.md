# Grapefruit Launcher downloads

## 1.0 beta 5 — drag-and-drop Home

Download `Grapefruit-Launcher-1.0-beta5.apk` and install it over your existing
preview/beta. Version code 5 uses the same application ID and signing certificate;
your Home shortcuts, dock choices, icon cache and theme are preserved. No uninstall
is needed. Android 4.2.2 (API 17) and later. This is a debug-signed test build.

- Hold and drag Home icons into free cells or onto one occupied cell to swap them.
  Gaps remain empty. Drag from All Apps to place a shortcut on Home.
- Move shortcuts between Home and the dock; drag dock icons to swap positions.
  All four app positions remain configurable; All Apps stays in the middle.
- Drop on the top strip to remove a shortcut or open app options. Tap **Edit** on
  Home, then an icon, for options and directional movement controls. Drag near
  Home's top/bottom edge to scroll. Cancelled drags preserve the original layout.
- **Settings → Icon placement → Half-cell subgrid** enables finer snapping without
  overlapping icons. Portrait and landscape arrangements are saved separately.
- Landscape places the tall navigation pill on the physical right side, adapting
  its height and icon size to the screen.
- Settings follows Audio/TV's grouped design. **About Grapefruit Launcher** has its
  own Geist Pixel Triangle wordmark, GPL-3.0-or-later/no-warranty declarations,
  public source/support links, acknowledgements and complete GPL/OFL texts offline.

The all-installed-icon disk cache remains intact. Home creates cells only near the
visible area, with a small recycling pool. The low-RAM bitmap cache remains 256 KiB;
background cleanup releases visible icons without deleting prepared PNGs.

Debug/release builds, all 40 automated tests and lint passed. Tests cover native
view drag-event dispatch, saved gaps/restart, drawer-to-Home drops, Home/dock
transfers and dock swaps, separate orientation coordinates, grid/subgrid settings,
right-side landscape/RTL layout, offline licences and Home virtualization, alongside
the existing icon-cache and compatibility checks. Android's window-manager drag
handshake is simulated in tests. Physical JP5S/Android 4.2.2 gesture and performance
testing is still needed; no physical-device test is implied.

Source for this exact beta (GPL-3.0-or-later, build instructions included):
https://github.com/hayliewordsman/grapefruitmusic/tree/35b10c9f85ab9c2b63597a184d1a88fbf5e8854d

Geist fonts are SIL OFL 1.1; notices are bundled in the APK and source.
Checksums are in `SHA256SUMS.txt`. Previous preview/beta APKs remain available.
