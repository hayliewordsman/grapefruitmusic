# JP5S compatibility and signing audit

All supplied binaries are treated as static compatibility references. Original archives and
their copyright-bearing bytes are preserved in private staging. Redistribution restrictions
are respected even where limited noncommercial development use is authorized.

| Component | Source/use | Remaining dependency |
|---|---|---|
| Settings / SystemUI | Rebuild original AOSP 4.2.2 source with this branch's patches | OEM changes, full platform compile and physical service/UI tests |
| framework-res | Rebuild AOSP with product overlays | Vendor resource IDs/config and coherent platform signing |
| framework.jar / services.jar | DEX compatibility reference only | RK3188 BSP framework deltas; never wholesale replacement |
| Mali / EGL / UMP | 4 vendor EGL/config files, libMali/libUMP, gralloc/gpu/hwcomposer candidates | Matched 3.0.36+ kernel, UMP/Mali modules, exported libui/Binder symbols, compositor testing |
| VPU / ION | libvpu/libion and 3.0.36+ vpu_service candidate | Rockchip codec integration, media XML, linker/text relocations and kernel symbols |
| Audio | Board primary audio/audio-policy candidates | Original audio policy, mixer routing, libmedia/BSP ABI and physical output testing |
| Power / lights | RK3188 power and rk30board lights candidates | Working init permissions, sysfs nodes, charging/suspend/backlight tests |
| Wi-Fi | Inventory only; no driver auto-selected | Identify actual chipset and loaded module, firmware, supplicant and board init |
| Camera / GPS / sensors / Bluetooth | Inventory only; no hardware assumed | Physical presence and runtime selection; camera also needs libjpeghwenc, ALSA variants need libasound |

Selected vendor ELF files still import `libEGL.so`, `libGLESv1_CM.so`, `libbinder.so`,
`libc.so`, `libcorkscrew.so`, `libcutils.so`, `libdl.so`, `libhardware.so`,
`libhardware_legacy.so`, `liblog.so`, `libm.so`, `libmedia.so`, `libstdc++.so`,
`libui.so` and `libutils.so`. These must come from the target platform/BSP with matching
symbols. Copying old framework libraries wholesale is not implemented. DT_NEEDED names
alone do not prove symbol or object-layout compatibility.

## Observed signing results

Full APK signature checks were run with Android build-tools 35 `apksigner verify
--min-sdk-version 17 --print-certs` on the exact supplied files:

- Settings.apk verifies. Certificate SHA-256:
  `a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc`.
  Its manifest declares `android.uid.system` and target/minimum API 17. The manifest also
  records build-tools platform version 27; that metadata alone does not change its API 17 target.
- SystemUI.apk fails verification: signed manifest SHA-1 digests differ from actual resources,
  including `res/drawable/capture.png` and `res/drawable-sw480dp/...`. The earlier certificate
  inventory is certificate metadata, not proof that this modified APK is validly signed.
  It remains a useful static reference but is never included as a prebuilt.
- framework-res.apk verifies with a different JPay `jp5platform` certificate:
  `ed09abf7186b79c5d7b450024c02dd6b4d221e729172e5b5e970ab5385ca3071`.

Public AOSP test certificates do not provide the JPay private platform key. Original shared
UID/signature relationships and userdata package state need reconciliation. The source uses
the normal AOSP `platform` signing declaration for the rebuilt core packages; a production
key plan has not been supplied. No attempt is made to bypass package signature checks.

Grapefruit apps remain individually signed under their original IDs. Media uses Blue's ID,
requires Blue's matching certificate and must meet the installed version floor. Debug APKs
are validation artifacts and are not accepted as production identities merely because they
build. Presigned app staging requires explicit expected certificate fingerprints; it does
not automatically sign or substitute keys.

## Firmware boundaries

The module archive includes both 3.0.8+ and 3.0.36+ builds. Only the three explicitly
selected GPU/UMP/VPU files with embedded 3.0.36+ vermagic are staged; originals keep their
suffixes. No module is automatically loaded. Matching vermagic is necessary and insufficient;
kernel config, symbol CRCs, compiler, userspace versions and actual hardware remain gates.

There is no kernel image, boot/recovery ramdisk, verified mount/partition map or analyzed
boot-entry mechanism in these archives. Consequently there is no verified target for a
system image and no implementation for installing one. Every current build/staging command
operates on host files. The normal-boot JPay partitions, data and boot mechanism remain
untouched.
