# Grapefruit OS 1.0 — JP5S / Android 4.2.2 source development

Development branch: `grapefruit-os-jp5s-4.2.2`, based on application commit `f271bde` in a
separate worktree. This is an initial source implementation, **not a bootable or flashable
ROM release**. The normal-boot JPay system and existing recovery-boot entry are outside
the implementation's write scope. No device, partition, mount, bootloader or recovery
commands are included.

## Implemented source

- SHA-256-locked original `android-4.2.2_r1` Settings, SystemUI and framework policy patches.
  The complete OS is to be built from original AOSP sources, with JP5S BSP reconciliation.
- Native Settings retains its package, preferences, activities and service controllers.
  Grapefruit styling uses Geist, pill backgrounds and custom native switch drawables;
  a new appearance page hosts universal theme selection and tile editing.
- The existing read-only universal-theme authority is hosted inside native Settings.
  Canonical IDs stay Dark=0, Light=1, Black=2, Blue=3. All four also update the media columns
  atomically. No standalone Grapefruit Settings app is included.
- The seven navigation slots are Screenshot, Volume Down, Back, Home, Recents, Volume Up,
  inert Spacer. Mirrored pixel boundaries keep Home centered, with at most one pixel of
  slot-width rounding. Back/Home/Recents retain AOSP handlers; screenshot uses the original
  SystemUI Messenger service and volume uses AudioManager. IME/disabled-key states retain
  slot geometry. Portrait and landscape stay at the bottom through a product-gated policy
  resource. Native menu/search/dead-zone references are retained.
- JP5S uses AOSP 4.2's unified `PhoneStatusBar` implementation at all tablet widths, with
  separate status and navigation bars. Both base and sw600dp navigation resources are
  patched. The old combined `TabletStatusBar` is intentionally not selected for this
  product; OEM sw480dp resources are references, not blindly copied over these layouts.
- Native Quick Settings editing adds/removes/reorders/resets existing AOSP permanent
  tiles; settings are persisted in `Settings.System.grapefruit_qs_tiles_v1`. Hardware-gated
  tiles remain gated. Alarm/IME/location and other transient tiles keep AOSP callbacks.
  An intentionally empty permanent list is supported. Appearance remains an editor entry;
  the status panel's Settings button also opens it on long press.
- Imported source modules for Launcher beta 7, Keyboard beta 5 and Calculator beta 2 keep
  their existing IDs and notices. Maps remains the existing module. Audio and TV now add
  Victory Blue; visible supported activities and the keyboard observe universal changes.
- [Grapefruit Media](../grapefruit-media/README.md) builds from Blue's playback/TV code,
  restores all four themes, the first Music fruit icon and Geist Pixel Triangle branding.
- RK3188/ARMv7 product configuration, source-only build wrapper, signed-app staging,
  private vendor staging, dependency manifests and offline/CI validation.

No GMS, Xposed, GravityBox or standalone Grapefruit Settings APK is selected.

## Reproduce source checks

Use Python 3.10+, Git, Java and a separate work directory. These commands only prepare and
validate source; the reference subset is not a full AOSP checkout.

```sh
python3 os/scripts/validate.py
python3 os/scripts/fetch_aosp_references.py --output work/os-reference
python3 os/scripts/apply_sources.py --aosp-root work/os-reference
```

For API/language checks, supply the official API 17 `android.jar` and ECJ 4.6.1:

```sh
python3 os/scripts/validate.py --android-jar /path/to/api17/android.jar --ecj /path/to/ecj-4.6.1.jar
```

CI downloads those tools over HTTPS, checks `validation-tools.sha256`, compiles every
native source addition with Java 6 language/bytecode settings against API 17, executes
the navigation/tile/theme models, and checks both patches against locked upstream files.
This is distinct from compiling the complete AOSP Settings/SystemUI packages.

## Full source and app builds

Use a full original AOSP `android-4.2.2_r1` checkout with a supported legacy JDK 6/Linux
host. Apply the source additions with `apply_sources.py --aosp-root /path/to/aosp`.
It verifies baseline hashes and refuses to overwrite existing additions. `build_framework.sh`
accepts only that full checkout and builds `framework-res Settings SystemUI`; it has no
image or OTA target. This host's Java 21 is suitable for the modern app Gradle build,
but not the original AOSP platform build.

The existing Gradle modules remain separate from AOSP's old Make toolchain. Build approved,
signed app releases separately. `stage_apps.py` accepts a private specification based on
`app-signing.example.json`, verifies each package/SDK/certificate/version floor, preserves
the APK and stages `PRESIGNED` prebuilt modules under an external AOSP tree's
`vendor/grapefruit/apps`. ARMv7 JNI libraries are staged into JB's system library directory;
different bytes under the same filename are rejected. The example certificate fields are
deliberately null. No production key or certificate identity is invented.

Keyboard uses NDK 17.2.4988734 with its existing GCC 4.9/gnustl API 17 build. The source was
imported with original licenses. `-PgrapefruitSkipKeyboard` permits independent app checks
on hosts without this legacy NDK; it does not produce or substitute a keyboard binary.

## Proprietary compatibility inputs

The six newly supplied binary archives remain outside this Git repository. All six archive
hashes and 74 member hashes are recorded in `reference/binary-manifest.json`. The earlier
reports in `reference/` are evidence, not instructions or redistributed firmware.

`stage_vendor.py --inputs /private/original-archives --vendor-out /external/aosp/vendor/jpay/jp5s`
verifies complete original archives before staging 18 candidate files. It preserves original
archives, filenames, embedded copyright notices and binary bytes, records ELF DT_NEEDED
dependencies, checks ARM32 and restricts selected modules to embedded 3.0.36+ vermagic.
It rejects destinations inside Git checkouts. The generated product fragment is opt-in via
the product file and contains no module-loading or init commands. These are **candidates**:
matching hashes, ELF architecture and vermagic do not establish runtime compatibility.

Reference APKs and DEX framework JARs are never copied into the new OS. Generic HALs,
3.0.8+ modules and undetermined Wi-Fi/camera/GPS drivers are not selected. The staged
Mali/EGL/UMP/VPU/ION stack must be tested with the working kernel and matching BSP.
`reference/vendor-dependencies.json` records the selected dependency closure. App and vendor
staging must happen outside every public checkout; no restricted payload is committed.

## Remaining gates

See `requirements.json` and [COMPATIBILITY.md](COMPATIBILITY.md). Kernel image/source,
ramdisk/init, partition map, BSP patches, Wi-Fi selection, board policies and signing/data
migration are still missing. No partition sizes or flash routing are guessed. Native AOSP
Settings preserves upstream behavior; OEM HDMI/screenshot extensions still need source
reconciliation before it can replace the working JP5S Settings.

The supplied SystemUI APK matches the inventory hash but **fails APK signature verification**
for modified resources. Settings and framework-res signatures verify; their certificates
differ. These facts prevent assumptions about an in-place system-package update.

Physical tests must cover accelerated graphics, touchscreen/rotation/IME/navigation,
screenshot protections, audio, Wi-Fi, charging/suspend, codecs and every system Settings
flow. No physical boot test has been performed.

## Future KitKat data preservation

Keep product identity, package IDs, app certificates, preference schemas, provider URI/column
IDs and the versioned tile key stable. The theme contract is append-only and media/general
revisions remain atomic. A 4.4.2 branch must rebuild against KitKat and use a matched BSP;
never transplant these Jelly Bean framework JARs. Before upgrade development, establish
userdata ownership/UIDs, shared UID and signing continuity, SettingsProvider database
migrations, Dalvik-cache handling and all recovery-boot mounts using offline copies. Test
the complete migration on a cloned data set. An existing standalone Settings provider in
userdata also requires a reviewed authority/state handover. No automatic removal or wipe
is implemented, and no data-preserving upgrade is currently claimed.
