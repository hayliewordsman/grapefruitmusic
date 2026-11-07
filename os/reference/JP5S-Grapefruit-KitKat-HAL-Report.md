# JP5S Hardware Abstraction Library (HAL) Inventory — Grapefruit OS

**Source:** User-supplied copy of `/system/lib/hw/` from the unlocked Android 4.2.2 JP5S installation (`JP5S-Hardware-Libraries.zip`).
**Analysis:** Read-only ZIP verification and static ELF metadata inspection using `readelf`; no libraries were executed, loaded, or installed.

## Summary

- Archive SHA-256: `b96bbd894fac3d8640536027370a1da41c9c268e272a21b12933b431e7dd02a8`.
- ZIP integrity: **passed**; **24 shared libraries** (2,065,872 bytes uncompressed).
- **Every file is ELF32, little-endian ARM (`EM_ARM`)** and exports the Android HAL information symbol `HMI`.
- Most libraries report GCC 4.6.x-google 20120106 (prerelease) in the ELF `.comment` section; `gps.rk30xx.so` reports GCC 4.4.3. These are compiler clues, not secure proof of a build date or exact OS version.
- Most Rockchip-specific HAL names use `rk30board`; the power HAL uses `rk3188` and the GPS HAL uses `rk30xx`. File presence does **not** prove the corresponding hardware exists or that each HAL is selected at runtime.
- These files were collected from the running **Android 4.2.2** firmware. They are a useful base for a 4.2.2 customization, but **cannot be presumed ABI compatible** with Android 4.4.2 or with a different kernel/BSP.

## Hardware-relevant observations

| Area | Relevant files | Notes |
|---|---|---|
| GPU/display | `gralloc.rk30board.so`, `gpu.rk30board.so`, `hwcomposer.rk30board.so` | Gralloc imports `libMali.so`, `libGLESv1_CM.so`, and `libUMP.so`; hwcomposer imports `libEGL.so`. Supporting libraries are absent from this ZIP. |
| Audio | `audio.primary.rk30board.so`, `alsa.audio.primary.rk30board.so`, `audio_policy.rk30board.so`, `alsa.audio_policy.rk30board.so`, `alsa.default.so` | Audio uses `libmedia.so`, `libhardware_legacy.so`, and, for ALSA, `libasound.so`; KitKat audio HAL and policy compatibility needs review. |
| Video and camera | `camera.rk30board.so` | Imports `libvpu.so`, `libion.so`, `libjpeghwenc.so`, and framework camera libraries; camera HAL presence does not imply a fitted camera. Video decode needs separate investigation. |
| Power/backlight | `power.rk3188.so`, `lights.rk30board.so` | RK3188-specific power HAL plus board-specific lights. |
| Bluetooth | `bluetooth.default.so` | Imports `libbt-hci.so`, `libbt-utils.so`; hardware presence and actual runtime use unverified. |
| Sensors / GPS | `sensors.rk30board.so`, `gps.rk30xx.so` | Supplied binaries do not establish fitted sensors or GPS. |
| Defaults | `gralloc.default.so`, `audio.primary.default.so`, etc. | Generic fallback HALs coexist with vendor files. Do not replace the board-specific HALs indiscriminately. |

## Immediate follow-up: related vendor libraries in `/system/lib/`

The following referenced libraries are outside `/system/lib/hw/` and worth **checking for**, then copying only if present and authorized (no need to capture the entire `/system` partition):

| Priority | Library names | Why |
|---|---|---|
| Highest | `libMali.so`, `libUMP.so`, `libEGL.so`, `libGLESv1_CM.so` | Graphics stack dependencies |
| High | `libvpu.so`, `libion.so`, `libjpeghwenc.so` | Video and image-processing support |
| High | `libasound.so`, `libtinyalsa.so` | Audio device support |
| Later | `libbt-hci.so`, `libbt-utils.so` | Bluetooth support, if actually used |

When inspecting the running installation, these files may be absent, renamed, or reside elsewhere. **Do not create replacements or alter permissions**. Standard Android framework libraries such as `libc.so`, `libutils.so` and `libbinder.so` need to match the target KitKat build; copying old framework libraries wholesale is not a sensible compatibility strategy.

## KitKat porting caveats

1. Some HAL interfaces and framework library ABIs differ between Android 4.2.2 and 4.4.2. In particular, inspect graphics composition, audio device/policy interfaces, and the EGL/UMP/Mali userspace-driver and kernel-module versions as a matched set.
2. The module ZIP analyzed separately includes both Linux 3.0.36+ and 3.0.8+ builds. Kernel modules and these userspace HAL libraries are **not** independently interchangeable; compare the working kernel, vendor libraries, firmware, and Rockchip KitKat BSP before attempting integration.
3. The HAL archive contains **no device tree, kernel configuration, boot/recovery image, full Android framework or partition map**. It cannot establish an installable KitKat firmware image by itself.
4. Preserve the ordinary boot environment and treat any claims about the custom recovery-based dual boot as **unverified until examined using authorized read-only information**. Never use a candidate ROM without independent recovery and data backups.
5. Keep proprietary firmware and binaries in private storage; do not commit them to the public `grapefruitmusic` repo. Exclude credentials, tokens, personal information, and institution-specific app data from archives.

## Complete ELF inventory

| File | Bytes | Key DT_NEEDED libraries (excluding basic C runtime) |
|---|---:|---|
| `acoustics.default.so` | 5,292 | — |
| `alsa.audio.primary.rk30board.so` | 54,920 | `libasound.so`, `libmedia.so`, `libhardware.so`, `libhardware_legacy.so` |
| `alsa.audio_policy.rk30board.so` | 75,156 | `libmedia.so` |
| `alsa.default.so` | 13,700 | `libasound.so` |
| `audio.a2dp.default.so` | 9,492 | `libpower.so` |
| `audio.primary.default.so` | 5,348 | — |
| `audio.primary.rk30board.so` | 136,672 | `libmedia.so`, `libhardware_legacy.so` |
| `audio.r_submix.default.so` | 17,692 | `libnbaio.so` |
| `audio.usb.default.so` | 5,384 | `libtinyalsa.so` |
| `audio_policy.default.so` | 75,156 | — |
| `audio_policy.rk30board.so` | 75,200 | `libmedia.so` |
| `bluetooth.default.so` | 753,296 | `libpower.so`, `libbt-hci.so`, `libbt-utils.so` |
| `camera.rk30board.so` | 120,916 | `libui.so`, `libbinder.so`, `libcamera_client.so`, `libgui.so`, `libjpeg.so`, `libjpeghwenc.so`, `libion.so`, `libvpu.so`, `libdl.so` |
| `gps.rk30xx.so` | 570,392 | — |
| `gpu.rk30board.so` | 5,416 | — |
| `gralloc.default.so` | 9,720 | — |
| `gralloc.rk30board.so` | 13,548 | `libMali.so`, `libGLESv1_CM.so`, `libUMP.so` |
| `hwcomposer.rk30board.so` | 28,788 | `libhardware.so`, `libui.so`, `libEGL.so`, `libbinder.so`, `libhardware_legacy.so` |
| `keystore.default.so` | 9,408 | `libcrypto.so`, `libkeystore_client.so` |
| `lights.rk30board.so` | 5,336 | — |
| `local_time.default.so` | 5,292 | — |
| `power.default.so` | 5,292 | — |
| `power.rk3188.so` | 5,296 | — |
| `sensors.rk30board.so` | 59,160 | — |

## Machine-readable companion

The sibling file `jp5s_hal_inventory.json` lists complete SHA-256 checksums, ELF architecture, `HMI` symbol presence, compiler comments, and every ELF `DT_NEEDED` dependency for all 24 libraries.

The module-level companion files from the previous collection are `JP5S-Grapefruit-KitKat-Driver-Report.md` and `jp5s_module_inventory.json`.
