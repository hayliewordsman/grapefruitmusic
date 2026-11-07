# JP5S Kernel Module Inventory — Grapefruit OS KitKat Planning

**Source:** User-provided `JP5S-Kernel-Modules.zip`, copied from `/system/lib/modules/` on a JP5S running an unlocked Android 4.2.2 installation.  
**Method:** Read-only ZIP CRC verification, byte inspection of ELF headers, extraction of embedded `.modinfo` strings and SHA-256 hashing. No code or kernel modules were executed, loaded, or installed.  
**Observed Android kernel:** User's About Tablet screen showed `3.0.36+` (November 9, 2017 build). 

## Executive summary

- ZIP contains **36 files** (34 32-bit ARM/ARMv7 ELF relocatable kernel modules plus 2 configuration/data files).
- **21** compiled modules embed `vermagic=3.0.36+ SMP preempt mod_unload ARMv7`.
- **13** compiled modules embed `vermagic=3.0.8+ SMP preempt mod_unload ARMv7`.
- Complete archive CRC verification **passed**.
- Reported compressed content bytes: **8,022,171**; uncompressed: **19,637,533**.
- The **3.0.36+** modules are the most relevant *candidates* for the current unlocked kernel; matching vermagic does **not** prove they are loadable, currently loaded, or compatible with a newly built kernel.
- `mali.ko` and `ump.ko` (unsuffixed) target **3.0.8+**; the files suffixed `.ko.3.0.36+` target **3.0.36+**. Blindly choosing unsuffixed files would be an error.
- `mali` declares a dependency on `ump`; `mt7601sta` declares a dependency on `mtprealloc7601Usta`.
- No evidence in this archive identifies which Wi-Fi driver is actually in use or whether any listed optional GPS/camera radio hardware is physically present.
- **Do not use these 4.2.2 kernel modules to flash or install a 4.4.2 ROM without verified kernel/source, configuration, symbols, and matching hardware.**
## Build/porting implications

1. Treat the module filenames as a **hardware and kernel ABI inventory**, not as drop-in replacements for a fresh KitKat kernel. Compatibility is tied to compiler, `CONFIG_MODVERSIONS`, kernel symbols, config, and driver implementation; the same `uname -r` is not sufficient.
2. Use original Rockchip RK3188 KitKat BSP/kernel source and, where legally authorized, the device-specific vendor adaptations. Rebuild drivers against the target kernel when source is available.
3. Preserve the existing dual-boot boot entry and normal-boot environment as non-modification requirements. This inventory does not expose partition layout, boot image, shared bootloader settings, or recovery ramdisk. No inferences about those should be made from filenames.
4. Collect the **hardware abstraction layer** (`/system/lib/hw/`) separately, together with non-sensitive firmware/version metadata. This can help identify dependencies for GPU, VPU, audio and display paths. Do not post firmware or private system dumps in a public repository.
5. KitKat release gates should include verified graphics acceleration, UI composition, video decode, audio, Wi-Fi, power/charging, touchscreen, clean reboots, and restoration plans. Tests on a real authorized device remain necessary.

## Selected files and dependencies

| File | Embedded kernel | Role / observation | Module dependency |
|---|---|---|---|
| `mali.ko` | `3.0.8+` | Mali GPU, older version | `ump` |
| `mali.ko.3.0.36+` | `3.0.36+` | Mali GPU, current-kernel candidate | `ump` |
| `ump.ko` | `3.0.8+` | GPU shared-memory driver, older version | `—` |
| `ump.ko.3.0.36+` | `3.0.36+` | GPU shared-memory driver, current-kernel candidate | `—` |
| `vpu_service.ko` | `3.0.8+` | Rockchip video processor, older version | `—` |
| `vpu_service.ko.3.0.36+` | `3.0.36+` | Rockchip video processor, current-kernel candidate | `—` |
| `rk29-ipp.ko.3.0.36+` | `3.0.36+` | Rockchip image processor | `—` |
| `rkwifi.ko` | `3.0.36+` | Rockchip Wi-Fi integration | `—` |
| `esp8089.ko` | `3.0.36+` | ESP8089 wireless driver | `—` |
| `8189es.ko` | `3.0.36+` | Realtek Wi-Fi driver | `—` |
| `tun.ko` | `3.0.36+` | TUN/TAP network driver | `—` |
| `mt7601sta.ko` | `3.0.36+` | MediaTek wireless station driver | `mtprealloc7601Usta` |
| `mtprealloc7601Usta.ko` | `3.0.36+` | Preallocation support for MediaTek driver | `—` |

## Complete module inventory

The table below is based on **embedded ELF metadata**, not filename suffixes.

| Filename | Kernel `vermagic` release | Size (KiB) | Declared dependencies |
|---|---|---:|---|
| `8188eu.ko` | `3.0.36+` | 833.0 | — |
| `8188eu.ko.3.0.8+` | `3.0.8+` | 591.9 | — |
| `8189es.ko` | `3.0.36+` | 909.9 | — |
| `8192cu.ko` | `3.0.36+` | 609.9 | — |
| `8192cu.ko.3.0.8+` | `3.0.8+` | 536.0 | — |
| `8723as.ko` | `3.0.36+` | 1195.8 | — |
| `8723au.ko` | `3.0.36+` | 1024.5 | — |
| `8723bu.ko` | `3.0.36+` | 1490.0 | — |
| `esp8089.ko` | `3.0.36+` | 189.6 | — |
| `esp_init_data.bin` | `not ELF` | 0.1 | — |
| `gps_rk30xx.ko.3.0.36+` | `3.0.36+` | 277.2 | — |
| `init_data.conf` | `not ELF` | 0.2 | — |
| `mali.ko` | `3.0.8+` | 148.4 | ump |
| `mali.ko.3.0.36+` | `3.0.36+` | 160.1 | ump |
| `mt5931.ko` | `3.0.36+` | 396.7 | — |
| `mt7601ap.ko` | `3.0.36+` | 952.9 | — |
| `mt7601sta.ko` | `3.0.36+` | 1971.0 | mtprealloc7601Usta |
| `mtprealloc7601Usta.ko` | `3.0.36+` | 4.6 | — |
| `rk29-ipp.ko` | `3.0.8+` | 16.5 | — |
| `rk29-ipp.ko.3.0.36+` | `3.0.36+` | 17.0 | — |
| `rk30_mirroring.ko.3.0.36+` | `3.0.36+` | 18.4 | — |
| `rk30_mirroring.ko.3.0.8+` | `3.0.8+` | 19.0 | — |
| `rkwifi.ko` | `3.0.36+` | 563.9 | — |
| `rkwifi.ko.3.0.8+` | `3.0.8+` | 523.2 | — |
| `rkwifi.oob.ko` | `3.0.36+` | 582.0 | — |
| `rt5370ap.ko` | `3.0.8+` | 1081.6 | — |
| `rt5370ap.ko.3.0.8+` | `3.0.8+` | 1081.6 | — |
| `rt5370sta.ko` | `3.0.8+` | 1914.4 | — |
| `rt5370sta.ko.3.0.8+` | `3.0.8+` | 1914.4 | — |
| `tun.ko` | `3.0.36+` | 21.5 | — |
| `ump.ko` | `3.0.8+` | 41.8 | — |
| `ump.ko.3.0.36+` | `3.0.36+` | 43.6 | — |
| `vpu_service.ko` | `3.0.8+` | 20.3 | — |
| `vpu_service.ko.3.0.36+` | `3.0.36+` | 21.1 | — |
| `wlan.ko` | `3.0.36+` | 2.7 | — |
| `wlan.ko.3.0.8+` | `3.0.8+` | 2.6 | — |

## Reproducibility and file integrity

- The accompanying JSON file `jp5s_module_inventory.json` records each member's SHA-256, uncompressed size, ELF machine ID, embedded `vermagic`, declared dependencies and selected `.modinfo` fields.
- Two identical-file pairs were observed: `rt5370sta.ko` = `rt5370sta.ko.3.0.8+`, and `rt5370ap.ko` = `rt5370ap.ko.3.0.8+` (identical SHA-256 within each pair).
- `init_data.conf` is an ESP-related configuration text file. `esp_init_data.bin` is a small binary data file. Neither is an ELF kernel module.
- ELF `e_machine=40` is ARM; these objects were not executed during inspection.

## Recommended next inputs for Codex

- **First:** the existing Android 4.2.2 **HAL directory**, `/system/lib/hw/`, as a private copy-only ZIP. A screenshot has already shown `gpu.rk30board.so`, `gralloc.rk30board.so`, `hwcomposer.rk30board.so`, `audio.primary.rk30board.so`, and `power.rk3188.so`. Inspect actual binaries and link dependencies before making compatibility claims.
- **Later:** authorized kernel source/config and the vendor-provided board-support files; structured boot/partition information gathered by a qualified technician, if appropriate. Avoid changing the locked installation or any sync/security mechanisms.
- **Do not** commit full system images, private keys, user data, or institutional configuration to the public Grapefruit repository.
