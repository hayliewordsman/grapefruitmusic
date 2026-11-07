# JP5S Graphics and Video Supporting Libraries — Grapefruit OS

**Source:** User-provided ZIP `jp5slibfiles.zip`, described as copied from `/system/lib/` on the unlocked JP5S Android 4.2.2 installation.
**Analysis:** ZIP CRC integrity test and static ELF inspection with `readelf`; no APKs or native binaries executed, loaded, modified or installed.

## Summary

- ZIP integrity: **passed**.
- Archive SHA-256: `d48c552a17da2034672553ed8aea44a13336e894848a294233190f0422805e29`.
- Four ELF shared libraries, **all 32-bit little-endian ARM**, size combined 1,037,428 bytes uncompressed.
- All four expected files are present: `libMali.so`, `libUMP.so`, `libvpu.so`, and `libion.so`.
- These are **binary reference inputs**, not driver source code or proof of Android 4.4.2 ABI compatibility.

## File inventory

| Library | Bytes | SHA-256 | Notable ELF dependencies |
|---|---:|---|---|
| `libMali.so` | 963,880 | `db4a9cc735b44e3f8e3a211f2e0963e1e363c1adf970dc8b83d645537e7ad755` | `libUMP.so`, `libui.so`, `libhardware.so`, `libbinder.so` |
| `libUMP.so` | 21,664 | `1faa829394afb908d1fed8a3794117b6bef2efa851b9fd0aeb2417aaf078f903` | `libbinder.so`, `libutils.so` |
| `libion.so` | 9,664 | `9304820ebb3948af2f25dc91aef9886fa061682a59cc8736e3a593868b0e7002` | `libutils.so`, `liblog.so` |
| `libvpu.so` | 42,220 | `d42619b9c4b5905a81f278bd03ae46e7b1cd02b294de248e337b0e301dc4814a` | `libion.so`, `libcorkscrew.so` |

## Cross-check against existing HAL archive

- `hw/gralloc.rk30board.so` directly imports both **`libMali.so`** and **`libUMP.so`**, so the new archive fills those two dependencies from the HAL report.
- `hw/camera.rk30board.so` imports both **`libvpu.so`** and **`libion.so`**, also supplied in this archive. That camera HAL can be a source of VPU integration information even if the JP5S has no camera.
- `hw/hwcomposer.rk30board.so` imports **`libEGL.so`**; this archive does not supply it. In Android, `libEGL.so` often acts as the framework EGL loader; inspect the existing **`/system/lib/egl/`** folder for vendor EGL/GLES drivers rather than blindly replacing framework EGL.
- The HAL report also lists `libGLESv1_CM.so`, `libjpeghwenc.so`, `libasound.so`, `libmedia.so` and other dependencies. The Android framework/core libraries should come from the matching target build, **not** be replaced wholesale with Jelly Bean libraries.
- `libvpu.so` also needs `libcorkscrew.so`, normally supplied by the Android platform; verify availability and symbols in the eventual KitKat build.
- `libion.so` contains an ELF `TEXTREL` dynamic tag, a legacy Android binary characteristic. This warrants linker compatibility testing; it is not in itself evidence that it fails on KitKat.

## Cautions for reuse

1. **First 4.2.2 release:** preserve this working chipset userspace stack as a matched set with the current 4.2.2 platform and 3.0.36+ kernel, subject to provenance and installation testing.
2. **KitKat upgrade:** rebuild or adapt the relevant graphics/media integration against the selected RK3188 4.4.2 BSP, then test the binary interfaces. Never assume the Jelly Bean `libui.so`, Binder or hardware HAL ABI is identical to the KitKat ABI.
3. **Kernel side:** the earlier module archive includes mixed `3.0.36+` and `3.0.8+` variants. Match `mali.ko` / `ump.ko` to the actual kernel and userspace Mali/UMP pair.
4. **Boot/dual boot:** these libraries do not reveal the existing bootloader commands, expanded partition geometry, init mounts or dual-boot routing. Do not flash from library inventory alone.
5. **Privacy/legal:** proprietary binaries may be subject to redistribution restrictions. Keep the originals in approved private storage; do not commit them to the public Grapefruit repository. Avoid disclosing institutional configuration, credentials or device-identifying data.

## Recommended next non-invasive collection

Inspect **`/system/lib/egl/`** with the existing file manager and provide a screenshot of its filenames; if it exists and is appropriate to share, copy it rather than move it and archive the copy. This would reveal vendor EGL/OpenGL ES library naming and help trace the Mali driver stack. No terminal or privileged filesystem modification is needed just to list it.

## Related materials

- `JP5S-Grapefruit-KitKat-Driver-Report.md` and `jp5s_module_inventory.json` describe the kernel modules.
- `JP5S-Grapefruit-KitKat-HAL-Report.md` and `jp5s_hal_inventory.json` describe `/system/lib/hw/`.
- `jp5s_graphics_video_inventory.json` provides the complete ELF dependency lists and checksums for this ZIP.
