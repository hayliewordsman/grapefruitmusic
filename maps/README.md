# Grapefruit Maps 1.0 beta 1

A separate, native GPL-3.0-or-later Android app in the Grapefruit repository.
Android **4.2.2 (API 17) and later**, targeting API 36. No Google Maps SDK,
Play services, WebView, advertising or analytics. The universal APK includes
ARMv7, ARM64, x86 and x86_64 TLS support and is approximately 4.1 MB.

## Beta 1

- Pan, pinch/zoom, location on request, place search and long-press pins.
- Standard 2D OpenStreetMap tiles and **NASA Blue Marble satellite composite**.
  Satellite is free NASA imagery with regional detail, maximum zoom 8, and is
  a static composite rather than current high-resolution aerial photography.
- App-owned labels use **Gulf of Mexico** and **Lake Ontario** in both views.
  Local lookup and identified search results preserve these names. The local
  vector renderer normalizes the identified water bodies without changing
  downloaded source files. Provider-baked text in online raster tiles cannot
  be rewritten by the app. The default search provider returned the traditional
  names when checked on November 5, 2026.
- **418 downloadable 2D maps under 200 country/territory groups**. Larger
  countries have smaller regional downloads. Alphabetical list with filtering.
  Country extracts are rendered locally using Mapsforge, at street detail.
- Downloads stream to app-specific storage, with free-space checks, HTTP
  Range/If-Range resume, size checking and Mapsforge header validation before
  installation. One transfer at a time. Tap progress to pause. Keep the app
  open; if Android stops it, tap the same map to resume. Removal is available
  on each installed map. No shared-storage permission is needed.
- Select an installed map to open it. Settings can switch back to the online
  map. Offline mode prevents new map/search/route requests; coordinate lookup
  and the last saved driving route still work. Country maps include 2D data;
  satellite downloads and offline route calculation are outside beta 1.
- Driving directions through OSRM: distance, estimated duration, route line
  and a written route overview. The last route stays on-device and can be
  cleared. **No turn-by-turn navigation**, voice guidance or background GPS.
  Walking, cycling, transit and real-time traffic are not offered in beta 1.
- Grapefruit Dark, Grapefruit Light, Pitch Black and **Victory Blue**, using
  the media apps' palettes, Geist fonts, grouped pill rows, custom dialogs,
  tabs and grapefruit-slice switches. Settings uses the media apps' design.
- Victory Blue only: Settings → About → tap **Developer / Haylie five times**.
  Tap the red baseball cap above the map of the contiguous United States to
  drop poop faces. Back clears all drops. Art is original Canvas paths and
  shapes bundled in source; no system emoji support or internet is required.

## Build

Use JDK 21 and Android SDK platform 36. The existing Gradle wrapper is used:

```sh
./gradlew :maps:assembleBeta
```

APK: `maps/build/outputs/apk/beta/maps-beta.apk`. Beta builds are non-debuggable.
With `GRAPEFRUIT_KEYSTORE` and `GRAPEFRUIT_KEYSTORE_PASSWORD`, they use the
existing permanent Grapefruit certificate. Otherwise they use a local test
key in ignored `work/maps-beta/debug.keystore`. Never distribute that test
key or try to update a permanent-key installation with a test-key APK.
Unsigned FOSS release build: `./gradlew :maps:assembleRelease` without those
variables. All source and build dependencies are publicly available.

```sh
mkdir -p work/maps-beta
curl --fail --location https://download.mapsforge.org/maps/v5/europe/andorra.map -o work/maps-beta/andorra.map
./gradlew :maps:testBetaUnitTest :maps:lintBeta
```

The sample map is test data only, not shipped in the APK. Tests cover download
validation/resume/failures, coordinates, country catalogue, theme-exclusive
unlocking, game reset, native screenshots and offline Mapsforge rendering.
Functional tests run on API 23, 28 and 35; graphics tests run on API 28.
API 17 compatibility is checked by minSdk/API lint and APK v1 signature
verification. An actual Android 4.2.2 device has **not** been tested here.

`.github/workflows/maps.yml` builds, tests and lints Maps separately and
uploads a verified APK, signature, checksum and reports. It does not publish
other apps or modify the F-Droid repository. Refresh the bundled country
catalogue with `python3 tools/maps/catalogue.py`; it reads provider directory
indexes only, without downloading map files or raster tiles.

## Licenses, privacy and providers

App and original artwork: GPL-3.0-or-later; root `LICENSE` applies.
Runtime libraries: Mapsforge 0.21.0 (LGPL-3.0-or-later with upstream waiver),
AndroidSVG 1.4 (Apache-2.0), Conscrypt 2.5.3 (Apache-2.0 and its BoringSSL,
Harmony and Netty notices), Geist (SIL OFL 1.1). License texts, credits and
source links are shipped in `src/main/assets/licenses` and the shared font
assets, accessible through About → Open-source licenses.

OpenStreetMap contributors supply the open ODbL data; Mapsforge supplies
unmodified downloadable extracts. Raster cartography is credited to OSM.
NASA EOSDIS GIBS supplies the public-domain Blue Marble satellite composite.
Attribution remains visible over both map views. No proprietary imagery or
API keys are used. These public services have independent availability and
usage limits; none are a guaranteed production hosting service.

Online tiles send their visible area to OSM/NASA. Submitted queries go to
Nominatim, rate-limited to one per 1.1 seconds; route endpoints go to OSRM.
Only explicit submissions trigger geocoding. Search and routing endpoints
are configurable HTTPS addresses in Settings. Location permissions are
optional, requested at Locate, and location is not tracked in the background.
No account or telemetry is used. Maps, view position and the last route stay
in app storage; uninstalling removes them. Backup is disabled.
