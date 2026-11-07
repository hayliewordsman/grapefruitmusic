# JP5S Grapefruit OS — Android framework JAR analysis

**Source:** user-provided `jp5sjars.zip`, collected from the working unlocked JP5S Android 4.2.2 system.  
**Method:** read-only archive validation, SHA-256 hashing, Dalvik DEX static header, class and method-table inspection. No executable code from the archives was run.  
**Archive:** ZIP CRC integrity check passed. Contains exactly `framework.jar` and `services.jar` (plus the enclosing directory). These are **compiled Dalvik runtime JARs**, not Android source code.

## File inventory

| File | JAR size | DEX size | DEX version | DEX-defined classes | Method references |
|---|---:|---:|---|---:|---:|
| `framework.jar` | 3,967,674 bytes | 9,359,716 bytes | `DEX 035` | 5,499 | 61,368 |
| `services.jar` | 1,121,444 bytes | 2,461,336 bytes | `DEX 035` | 891 | 11,910 |

**SHA-256 checksums:**

- `framework.jar`: `5f1bdb59a43c6397ffd7283a6128485d13c7710c7ff2700415222f29c91fea31`
- `framework.jar/classes.dex`: `f4758cf3b757c9a8b88055a9dfece07afe1610739e692f7f6563eb21087e1f86`
- `services.jar`: `79076f6562cc4de2d5642b4aa5a307b2ab6cb8756e7e4e2abe9ea49d23959cf6`
- `services.jar/classes.dex`: `b95273a8800f0c068ccf0c0361c9cd89766d41e7b0be6283f0a807ca3d6df195`

## Relevant runtime interfaces found

- `framework.jar` contains `android.app.StatusBarManager` methods including `expandSettingsPanel()`, `expandNotificationsPanel()`, `collapsePanels()`, and `disable()`.
- `framework.jar` contains the internal `IStatusBarService` interface, with `toggleRecentApps()` and status-bar icon/visibility methods.
- `framework.jar` contains `android.media.AudioManager`, including `adjustVolume()`, `adjustStreamVolume()`, and `adjustSuggestedStreamVolume()`. The proposed on-screen volume buttons can be designed around the existing Android audio services, with appropriate system privileges.
- `services.jar` contains the `com.android.server.StatusBarManagerService` implementation and `com.android.server.wm.WindowManagerService`. These are the device's existing system-service counterparts, not an invitation to transplant them into KitKat unchanged.
- `framework.jar` contains `android.os.RecoverySystem` (including `bootCommand` and OTA-related methods). **Do not use this as evidence for the current recovery-boot layout:** the tablet's custom dual-boot control has not been analyzed, and the locked-side Help app is not part of these JARs.

No separately named `JPay`, `Securus`, `Xposed`, or `GravityBox` class was identified in the DEX class definition tables of these two JARs. This **does not** prove the system has no vendor modifications or Xposed hooks. Vendor code can patch AOSP classes, and Xposed can integrate through the Zygote / app_process chain or separate archives.

## Grapefruit OS 4.2.2 implications

1. Use these as exact-device **compatibility references** alongside the previously collected `SystemUI.apk`, `Settings.apk`, `framework-res.apk`, HAL and kernel module inputs.
2. Start actual development from **matching Android 4.2.2 AOSP source**, then reconcile device/vendor differences. These archives alone cannot reconstruct the original source or fully characterize patches.
3. Most planned changes belong in the rebuilt **SystemUI** and **Settings** source: four palettes, seven-slot navigation bar, screenshot control, and editable Quick Settings. Avoid replacing `framework.jar` or `services.jar` unless a documented requirement demands a source-level framework patch.
4. The requested **seven equal-slot navigation row** is: Screenshot | Volume Down | Back | Home | Recents | Volume Up | Blank. Keep Home in center slot 4; the blank slot is noninteractive. Account for portrait/landscape layouts, 1024×600 screen geometry and accessible touch targets.
5. For the screenshot control, reuse the installed SystemUI screenshot service after verifying permissions and calling contracts in **SystemUI.apk**. For volume controls, use system AudioManager services and display volume changes appropriately. Native theme changes should include status and navigation bars and a configurable Quick Settings tile order.
6. The unlocked system currently uses Xposed/GravityBox only for Quick Settings and bar colors according to the user; replicate those functions natively. **Do not uninstall or disable existing modules on the working installation.**
7. Preserve the current normal-boot JPay partition and the existing recovery-boot entry arrangement. Do not assume these JARs describe bootloader partitioning or supply any verified flashing route.
8. For the later **Android 4.4.2** port, **rebuild against KitKat source**. Do not transplant Jelly Bean `framework.jar` / `services.jar` across OS versions: internal services and APIs can differ.

## Limitations and next development gate

- This inspection is static and did not decompile method bodies or verify exact differences from clean AOSP; it cannot assert whether a vendor altered framework methods in place.
- The JARs do not contain kernel source, boot images, or reliable partition mappings; they are insufficient for generating a flashable ROM.
- `services.jar` and `framework.jar` may rely on additional framework JARs, native libraries and security settings not included in this bundle.
- Before any ROM build or update, Codex should produce a component-by-component source mapping, explicitly state missing hardware inputs, and use non-destructive offline validation. Data preservation cannot be guaranteed without verified boot and userdata layouts.

## Inputs to give Codex

This report and `jp5s_framework_inventory.json`, plus original unmodified `jp5sjars.zip`, `JP5S-System-UI.zip`, kernel/HAL/EGL archives. Keep proprietary binaries private and separate from public application source. `JP5S-Grapefruit-Codex-Research-v4.zip` is **research-only** and does not include proprietary binaries.
