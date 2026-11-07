# JP5S Grapefruit OS — EGL and Xposed compatibility assessment

**Input:** `JP5S-EGL-Drivers.zip` (provided by user, 2026-11-06). **Method:** static ZIP validation, SHA-256, ELF headers and ELF dynamic dependency tables only. No uploaded binaries executed.

## EGL inventory

- ZIP integrity passed (no bad members).
- All four shared libraries are ELF32 for ARM.
- `egl.cfg` contents are exactly `0 0 android\n0 1 mali\n`, indicating Android software renderer and Mali driver options.

| Path | Bytes | ELF direct dependencies |
|---|---:|---|
| `egl/egl.cfg` | 21 | N/A (text config) |
| `egl/libGLESv1_CM_mali.so` | 25,624 | `libMali.so`, `libc.so`, `libstdc++.so`, `libm.so` |
| `egl/libGLES_android.so` | 83,196 | `liblog.so`, `libcutils.so`, `libhardware.so`, `libutils.so`, `libpixelflinger.so`, `libETC1.so`, `libui.so`, `libc.so`, `libstdc++.so`, `libm.so` |
| `egl/libEGL_mali.so` | 13,328 | `libMali.so`, `libc.so`, `libstdc++.so`, `libm.so` |
| `egl/libGLESv2_mali.so` | 21,524 | `libMali.so`, `libc.so`, `libstdc++.so`, `libm.so` |

The three vendor Mali EGL/GLES drivers link directly to `libMali.so`. The AOSP software renderer `libGLES_android.so` links to the legacy Android graphics/system libraries listed above, so KitKat reuse requires checking these ABI expectations. These user-space libraries do not themselves prove kernel/user-space compatibility or that the correct hardware modules load successfully.

## Preserve Xposed / GravityBox in Grapefruit OS

### Android 4.2.2 — first release

- Target the original Dalvik-based Xposed for Android 4.2.2; identify the existing installer/framework version and enabled modules from **non-sensitive screenshots or APK manifests** before packaging.
- Existing Xposed installation may use modified `/system/bin/app_process` and/or framework files. Confirm exact integration before rebuilding; do not assume simply copying the installer APK preserves Xposed.
- Keep GravityBox **[JB]** compatible with API 17. Preserve the app's package/signing identity and user configuration where safe and compatible.
- Customize AOSP Settings and SystemUI natively for four Grapefruit palettes, then check individual GravityBox hooks (notification shade, navigation, lock screen, power menu, buttons, status bar). Avoid overlapping customizations; disable conflicting features individually.
- Provide a tested way to disable Xposed modules and recover from crashes. No device changes until a full restorable backup exists.

### Android 4.4.2 — future release

- Use a KitKat-compatible original Xposed build on **Dalvik**, not a Jelly Bean-specific GravityBox binary. GravityBox **[KK]** is a separate edition; settings migration isn't guaranteed.
- Retest all hooks in Android 4.4.2 SystemUI/framework. A data-preserving Android OS update is a goal, not a guarantee; protected backups and schema-migration testing are necessary.

## Device/ROM limitations

- These uploaded files are **compiled binaries**, not hardware driver source.
- No check of touchscreen, Wi-Fi chipset currently in use, kernel configuration, bootloader, mount points, or full graphics runtime was performed here.
- Preserve the independent normal-boot system and do not change institutional synchronization or device-management components.
- Source/references: https://github.com/rovo89/XposedBridge/wiki/Using-the-Xposed-Framework-API ; https://github.com/GravityBox/GravityBox ; https://github.com/ElderDrivers/EdXposed/wiki/Available-Android-versions .

## Recommended next research (no terminal required)

1. Open Xposed Installer and record the **framework version** and **enabled modules** as screenshots; redact anything personal.
2. Open GravityBox and record the edition and version; no need to enable/disable anything.
3. Use collected module/HAL/EGL inventories to identify remaining firmware components. Inspect a complete ROM only with the user's authorization and a separate backup.
