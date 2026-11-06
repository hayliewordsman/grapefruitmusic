# Development status

This branch contains an initial Android 17 source integration. No flashable N-XR
ROM, boot result, hardware certification, or completed visual-parity test is
claimed.

## Local validation — 2026-11-06

- Audio, TV, the standalone launcher and theme wallpaper debug APKs build; their
  requested lint checks pass. Blue's code-only `osFoss` build/lint also pass.
- The music-enabled `osVictory` APK builds and passes lint. Its packaged catalog
  contains five Bonus Bundled Content tracks. Eight bundle playback/library
  checks pass across Robolectric API 23, 28, 35 and 37.
- The approved source APK checksum is verified. Retagging preserves the cover,
  and FFmpeg verifies unchanged compressed audio streams for all five recordings.
- 58 standalone-launcher/theme-bridge tests and five host palette/font checks
  pass. These are not Quickstep gesture tests.
- Generated keyboard resources compile/link with Android SDK 36. The launcher
  appearance helper compiles against that SDK as well.
- All four platform patches pass pinned-source checks, apply cleanly, tolerate
  repeat application, parse their XML, and reject unrelated source drift before
  writing another project.

The full Android 17 platform, Quickstep, SystemUI, Settings and LatinIME binaries
have not been compiled or booted in this small workspace.

The source implementation includes both edition profiles, a stock Settings
extension and read-only theme provider, public dynamic-color integration, Geist
font integration, a theme wallpaper, a private-input shipping Victory build with Bonus Bundled Content,
an appearance port onto Android 17 Quickstep,
pill Quick Settings geometry and notification corner resources, and a
teardrop-key LatinIME appearance port that follows the system palette.

The appearance port is the first modern-launcher pass. The older launcher's
precise spacing, three-line chips, middle All Apps dock position and optional
half-cell layout still need deliberate porting and visual regression work on
the modern grid. Its old workspace database is not migrated to Launcher3's
database. Gesture implementation comes from AOSP; end-to-end operation has not
been established by a successful platform build or device test here.

Release gates:

1. Resolve the original N-XR/MT6580 Android 17 BSP, maintained kernel, vendor HALs,
   graphics/composer, modem/IMS, camera, codecs and device-tree prerequisites.
   The hardware profile is disabled for shipping until those inputs exist.
2. Build and boot the complete pinned Cuttlefish product; compile the stock
   Settings/SystemUI/Quickstep/LatinIME changes with the Android 17 build system. App
   Gradle builds and patch checks do not replace this step.
3. Test all four themes, cold boot and pre-unlock defaults, multiple users,
   work/private profiles, live theme changes, accessibility contrast and font
   size. Test a lock-screen photo across theme changes, reboot and OTA.
4. Test back, swipe Home, swipe-hold overview, quick switching, predictive back,
   rotation, keyguard transitions, split screen, widgets/folders, Home role and
   three-button fallback. Compare the modern launcher against the original
   Grapefruit visual design before accepting appearance parity.
5. Test pill QS tiles with toggled, unavailable, dual-target and edit states;
   test grouped/expanded notifications, inline replies, media controls, heads-up
   alerts, keyboard/TalkBack focus and large text. Shape changes need device
   screenshots and interaction evidence before release.
   Check keyboard letters, emoji, action/space keys, popup accents, long presses,
   suggestion selection, password fields, focus, RTL and language/layout changes.
6. Freeze the full source manifest, finish app/update signing, notices and source
   distribution, and test signed A/B or OEM-supported OTA/recovery/rollback flows.
   Run final compatibility and OEM/Google certification on the shipping build.

The CI workflow checks the foundation and app builds. It does not compile a
complete AOSP tree or produce certified device images.
