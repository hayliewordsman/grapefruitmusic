# Grapefruit FAST 1.0 beta 1 — validation

Validated with JDK 21, Android SDK 36 and Gradle 8.14.3:

- FAST: assembleDebug, assembleRelease, testDebugUnitTest, lintDebug passed.
- Music: assembleDebug, testDebugUnitTest, lintDebug passed after extracting shared fonts and TLS support.
- Eight FAST tests: channel parser boundaries and source list coverage; UI navigation/search/empty favourites; theme rendering; portrait and landscape layouts. UI tests use Robolectric API 28, not a JP5S emulator.
- Rendered actual layouts at 360×640 and 1024×600, with Dark and Light themes. Checked wrapping, chip scrolling, channel rows and navigation; tightened portrait navigation padding after inspection.
- FAST manifest minimum SDK remains 17; new-platform style attributes are in version-qualified resources. No NewApi lint errors remain.
- FAST debug APK is signed with a test key. Release APK is unsigned until the separate permanent FAST key is configured.
- Workflow YAML parses; Music's publisher filters `v...` tags, FAST uses `fast-v...` tags and its own artifacts/signing configuration.

## Still required

- Every one of the 28 stream probes was blocked by this runtime's outbound proxy (CONNECT 403), including playlist access. These results do **not** establish whether a provider feed itself is live or dead. The saved environment configuration was updated by the user, but this running instance still reports revision 1 with restricted networking. Re-run `tools/fast/check_streams.py` in a runtime using the updated configuration, correct stale endpoints and confirm manifests/segments from the target region.
- CP24 remains excluded unless a public stream URL can be obtained from cp24.com itself.
- Test H.264/AAC video and audio on the actual jailbroken JP5S, including adaptive bitrate changes under throttled Wi-Fi, TLS, rotation, reconnect, audio focus, remote keys, app switching and low memory. Build success and an API-17 minimum do not prove hardware playback compatibility.
- Check provider terms/region eligibility before distributing the channel catalog. The public CDN candidates are not a claim of redistribution permission.
- Configure and verify the permanent FAST signing key before a GitHub prerelease. No release, F-Droid publication or production deployment has been performed.
