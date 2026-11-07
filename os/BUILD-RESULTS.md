# Development build and validation results

Branch: `grapefruit-os-jp5s-4.2.2`; base application commit: `f271bde`.

## Completed checks

| Check | Result |
|---|---|
| Native source additions | All 15 classes compile with ECJ Java 6 settings against official Android API 17 |
| Native behavior models | Passed: 4,090 display widths, centered Home, complete slot coverage, at most one pixel width rounding, tile add/order/empty/default handling and four atomic universal/media theme states |
| Static source checks | Five checks passed; Python scripts compile; build shell parses; Git whitespace check passes |
| Original AOSP patches | Both patches applied successfully to 22 SHA-256-locked AOSP 4.2.2_r1 reference files; verified outside Git and inside an ignored work directory |
| Patched navigation XML | Both rotation containers in base and sw600dp layouts contain the exact seven ordered slots and inert spacer |
| Vendor inputs | All six original ZIPs and 74 members verified; 18 ARM/kernel-filtered candidates staged privately with DT_NEEDED report |
| APK signing audit | Settings and framework-res verify; supplied SystemUI fails resource digest verification |
| App debug APK builds | Keyboard, Calculator, Launcher, Maps and Grapefruit Media succeeded |
| Audio / TV Java builds | Both succeeded with Victory Blue changes |
| App behavioral tests | 85 tests passed, zero failures/errors: Media 2, Keyboard 8, Calculator 20, Launcher 54, selected Maps theme test 1 |
| Media theme simulation | Four actual palette backgrounds and universal revisions tested with Robolectric API 17 and API 19 |
| Media APK metadata | Existing Blue ID, version code 9, minimum API 17, Grapefruit Media label, ARMv7 native code |
| Media branding source | First Music icon copied from initial commit in all five densities; wordmark font contains every character in “Grapefruit Media” |
| Public source audit | No tracked source file matches any of the 74 supplied binary member hashes; no proprietary payload committed |
| Original checkout | Original `work` branch remains at `f271bde`, clean |

The app test count is from current JUnit XML, not an estimate. The initial broad test run
passed all app builds and all 84 Media/Keyboard/Calculator/Launcher tests. The final run
rebuilt changed Launcher/TV/Media sources, re-ran Media tests and passed the selected Maps
universal-theme test. Other Maps tests were not run as part of this task.

The local legacy Keyboard JNI build succeeded using NDK 17.2.4988734, API 17 and GCC 4.9
with gnustl_static. Android SDK 35/36 and Java 21 build the modern Gradle applications;
their minimum runtime remains API 17. The native platform additions use Java 6/API 17.
The complete original AOSP platform packages were **not** compiled with Java 21.

All app APKs are development builds. Maps required a newly generated test-only keystore in
ignored `work/`; it is not the existing release certificate. No key is committed. Production
preinstallation remains blocked until the supplied signed APKs match explicit expected
release fingerprints and installed version floors. No release has been published.

## Commands exercised

```sh
python3 os/scripts/validate.py --android-jar /path/to/api17/android.jar --ecj /path/to/ecj-4.6.1.jar
python3 os/scripts/fetch_aosp_references.py --output /external/reference-tree
python3 os/scripts/apply_sources.py --aosp-root /external/reference-tree
python3 os/scripts/stage_vendor.py --inputs /private/original-archives --vendor-out /private/vendor-staging
./gradlew :keyboard:assembleDebug :calculator:assembleDebug :launcher:assembleDebug :maps:assembleDebug :grapefruit-media:assembleDebug :app:compileDebugJavaWithJavac :tv:compileDebugJavaWithJavac
./gradlew :grapefruit-media:testDebugUnitTest :calculator:testDebugUnitTest :launcher:testDebugUnitTest :keyboard:testDebugUnitTest --tests '*UniversalThemeTest'
./gradlew :maps:testDebugUnitTest --tests '*UniversalThemeAndVictoryTest.mapsFollowsAllFourGeneralThemesIncludingBlueInsteadOfTheMediaFallback'
```

Media's separate full test task was executed to include `MediaThemeTest`; the test filter
shown on the combined command applies to the final Keyboard test task. The full build and
test logs remain in the worktree's ignored `work/jp5s/` directory. CI is configured for public
source and app checks without signing secrets or vendor binaries; remote CI results are
separate from these local results. Existing app CI jobs skip this development branch and
retain their normal behavior on other branches.

The first remote source-validation job passed, including original AOSP patch application.
Its app job stopped in SDK setup because the setup action requested the retired `tools`
package by default. The workflow now explicitly selects `platform-tools` before installing
the app compilation SDKs; the local app results above are independent of that setup failure.

## Not built or tested

- No complete AOSP checkout or supported JDK 6 host is present. New native classes and
  patches were validated; the complete rebuilt Settings/SystemUI/framework packages were
  not built or executed.
- No system/boot/recovery image, OTA or installer was generated. The required kernel,
  BSP/ramdisk, partition map, signing plan and board runtime configuration remain missing.
- Driver ABI, kernel symbol compatibility, hardware acceleration, audio routing, Wi-Fi,
  power behavior and OEM Settings extensions remain unverified.
- The exact seven-slot views and service actions have not been exercised on physical JP5S
  hardware. Java/Robolectric/model checks cannot establish hardware functionality.
- No ROM boot or data-preserving upgrade is claimed. No device or normal-boot environment
  was accessed, flashed or modified.

Detailed remaining inputs are in `requirements.json`; signing findings and hardware
dependencies are in `COMPATIBILITY.md` and `reference/vendor-dependencies.json`.
