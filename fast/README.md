# Grapefruit FAST

**1.0 beta 5 (build 5)** — `io.github.hayliewordsman.grapefruitfast`

A second native Android app in the Grapefruit Music & Podcasts repository. Both can be installed at once. Music's application ID and version remain unchanged. Shared Geist fonts and Conscrypt TLS support live in `grapefruit-ui/`; Music's existing UI remains in `app/`.

FAST uses Grapefruit's Dark, Light and Pitch Black palettes and Geist UI type. The wordmark is Geist Pixel in its Triangle variant (`assets/fonts/GeistPixel-Triangle-FAST.ttf`, cut to the letters of the name), "Grapefruit" in the accent and "FAST" in the text colour, as in Music: on the splash screen, the About page and the side rail. As in Music, the splash is the stacked logo baked into one picture (the TV grapefruit over the Triangle wordmark, light and dark), which the window shows from the moment the app starts and the app keeps up for a moment (Android 12+ draws its own icon splash first, in the chosen theme's colour from Android 13); `tools/fast/splash.py` makes its artwork. Settings follows Music's grouped layout: accent section titles, rounded rows with grey values, Music's grapefruit switches and a note under each section, with Settings › About Grapefruit FAST, a copy of Music's About page (round back button, 88dp icon, one-line wordmark, version, copyright, License, Developer and Acknowledgements). On a phone, turning sideways while watching goes full screen and turning upright again comes out; Full screen turns the picture sideways. Like Music 5.x, everything is a pill and floats: the tabs (icons, with the chosen tab's name beside its icon) ride over the page on a floating pill (along the bottom, or down the side when there's room across), the last channel waits on a Continue watching pill above them like Music's mini player, channel rows are pills with round logos, and lists scroll out from under the floating bars. The launcher icon places the grapefruit inside a widescreen television, with a smaller circle based on the first approved TV design.

## Watching

- Browse US and Canadian channels by genre and country, search by city/name/description, keep favourites, or reopen your last channel.
- Touch a channel to watch. Hold it (or press Menu on a remote) for favourites and its official website. D-pad/Enter navigates; channel up/down changes channels; Back returns to browsing.
- Auto playback uses ExoPlayer's measured-bandwidth adaptive HLS selection. The displayed resolution updates when the video format changes. Adaptation requires multiple renditions in the provider's master playlist: a single-rendition channel cannot change resolution.
- Android 4.x Auto caps selection at 720p/30 fps to reduce old-decoder pressure, while still adapting below that. Data saver caps at 480p/1.4 Mbps; Compatibility caps at 360p/800 Kbps. Constraints are not exceeded when a provider offers no suitable track; try Auto instead.
- Full screen expands the viewing area. Playback stops when the app is left and reconnects on return; this is live TV, so no pause-behind-live or downloads.
- No account, Play Services, WebView, tracking SDK, extra ads or paid subscription is required by Grapefruit. Provider advertisements and region restrictions remain.

## Compatibility

Minimum Android **4.2.2 / API 17**, target API 36, Java 8 bytecode desugared by AGP. Four ABIs: armeabi-v7a, x86, arm64-v8a, x86_64. ExoPlayer **2.18.7** retains Jelly Bean support; do not upgrade to Media3 or ExoPlayer 2.19 without explicitly reviewing the minimum Android requirement. Conscrypt and Music's additional root certificates provide modern HTTPS with normal certificate/hostname verification.

A jailbroken JP5S needs no additional root permissions for this app. Actual device decoding depends on the firmware's codecs, RAM, working Wi-Fi and each feed. Prefer H.264/AAC. The app does not emulate missing codecs, decrypt DRM, bypass captive portals, or alter device management. API-17 manifest/lint checks are not a substitute for a real JP5S playback test.

## Channel catalog

`src/main/assets/channels.json` contains 57 source-reviewed channels, filtered by genre and by region (Canada, United States, UK, Europe, Australia):

- **Canada:** three CityNews feeds, fourteen Global News feeds and CBC News; entertainment: Heartland, Murdoch Mysteries, Mayday: Air Disaster, Property Brothers, Love Nature and GustoTV.
- **United States:** NBC News NOW, ABC News Live, CBS News 24/7, Cheddar News and Bloomberg TV+; entertainment: The Carol Burnett Show, Baywatch, Unsolved Mysteries, Forensic Files, Deal or No Deal, The Martha Stewart Channel, America's Test Kitchen, Family Affair Channel, MythBusters, Family Feud Classic, PBS Nature, Little House on the Prairie and Lassie.
- **UK:** BBC News, Sky News and Reuters 60; entertainment: Top Gear, Midsomer Murders, Classic Doctor Who, BBC Drama, The Great British Baking Channel, The Graham Norton Show, The Repair Shop, Come Dine With Me, BBC Earth and The Jamie Oliver Channel.
- **Europe:** DW News. **Australia:** ABC News Australia.

**News policy.** News is non-partisan: no right-wing or partisan channels, nothing News Corp or Fox owned, and no Piers Morgan. Each news source record carries its Media Bias/Fact Check (and, where rated, Ad Fontes Media) rating. Every news channel included is rated Least Biased or Left-Center with High factual reporting by Media Bias/Fact Check, except Bloomberg (Left-Center; Ad Fontes Media: Middle). Excluded after review: Newsmax2, OAN Plus, Salem News, LiveNOW from FOX, Fox Weather, Sky News Australia, TalkTV, GB News, TYT and CNN Headlines. `CatalogTest` enforces the exclusions.

**Availability.** DW News and ABC News Australia come from the broadcasters' own public players and stream worldwide. Most UK, Canadian and new US entertainment and news come from Xumo's official guide. Xumo is a US service, so outside the US those providers may refuse the stream. Each channel's details show where it can be watched. France 24 English (the official site plays YouTube only) and Euronews English (unavailable in the US) were not added. CBC's local news channels on CBC Gem require a CBC sign-in, so they are not included. Xumo channels assembled from on-demand episodes (McLeod's Daughters, Highway Thru Hell, Just for Laughs Gags and others) have no single live stream and were not added.

**CP24 is excluded:** no public video stream was obtained from cp24.com. Do not add third-party CP24 restreams.

Each entry has an official viewing page and first-party endpoint provenance. `tools/fast/sources.py` rebuilds beta 5's additions from Xumo's guide and DW's and the ABC's own pages. `docs/fast/catalog-sources.json` records the source URLs, ad-template handling, bias reviews and excluded candidates. No community playlist is imported at runtime. A reachable public feed is not a grant of third-party redistribution rights. Review the source terms and probe from the intended region before distribution. Channel names belong to their owners; Grapefruit is not affiliated with them.

The beta has a channel guide, **not a programme schedule/EPG**. It does not invent programme titles or times. Stream links ship in the APK, so catalog corrections currently require an app update.

Source checks:

```sh
python3 tools/fast/check_streams.py --output work/stream-report.json
```

The checker follows every public HLS master through all video/audio renditions and bounded media samples, including initialization/key resources where present. It rejects DRM, insecure redirects, non-media responses and ended playlists without adding location workarounds or stripping ads. Beta 5's full run (`docs/fast/stream-report-beta-5.json`, November 3, 2026, US egress) reached media for all 57 channels, every one adaptive, all renditions declaring H.264/AAC. Beta 1's 28 channels also passed its full run and a recorded follow-up; Global National and Winnipeg had intermittent runtime proxy denials in the full run. Bounded ffprobe samples identified H.264/AAC for all 28. See `docs/fast/validation.md` and the committed JSON reports for exact scope and failures. JP5S playback remains untested.

The quality caps are strict: Global's lowest track exceeds Data saver's bitrate cap, so Global video requires Auto. Compatibility has no matching track for CityNews, Global or America's Test Kitchen. The player reports this and suggests Auto. Auto adaptation still depends on measured bandwidth and the available provider tracks.

Offline probe regression tests (also run in FAST CI):

```sh
python3 -m unittest discover -s tools/fast -p 'test_*.py' -v
```

Official availability references:

- CityNews: https://toronto.citynews.ca/video/ and https://vancouver.citynews.ca/video/
- Global: https://globalnews.ca/pages/watch-global-news-anytime/
- NBC: https://www.nbcnews.com/now
- ABC: https://abcnews.com/Live
- CBS: https://www.cbsnews.com/live/

## Build and release

JDK 21, Android SDK 36, build tools 35:

```sh
./gradlew :fast:assembleDebug :fast:assembleRelease :fast:testDebugUnitTest :fast:lintDebug
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Debug APK: `fast/build/outputs/apk/debug/fast-debug.apk`. Debug signatures are for testing, separate from the permanent release key. PR Actions also supplies a `grapefruit-fast-1.0-beta-5-test-apk` artifact with an installable test APK, checksum and verification evidence.

`.github/workflows/fast.yml` builds/tests/lints FAST and uploads its own APK/report artifacts. To publish, run it manually on the repository's default branch with `publish` enabled.

**Signing.** From beta 3, FAST is signed with the permanent Grapefruit key, the same key and repository secrets as Music: `GRAPEFRUIT_KEYSTORE_BASE64` (the PKCS12 file, alias `grapefruit`) and `GRAPEFRUIT_KEYSTORE_PASSWORD`. Every build that has the key (the CI test APK, releases, debug and release, beta and stable) is signed with it, so each installs over the one before. The CI test APK is the release build on that key, checked against the certificate SHA-256 `85cac0ef72777211d99e2bbe4ad09a3815c6cf313e5fa18f401ec8da0ae0e795` (also in `fdroid-repo.yml`). Without the key (a fork's pull request, a local build) the test APK is debug-signed and won't install over signed builds. Keep `versionCode` rising with every build (beta 5 is 5), since Android won't install a lower one over a higher one. Beta 1 and beta 2 test APKs were debug-signed: uninstall them once before installing beta 3.

Local signed builds use `GRAPEFRUIT_KEYSTORE` (path to the .p12) and `GRAPEFRUIT_KEYSTORE_PASSWORD`. Keep signing files outside Git. The release workflow verifies package identity and the certificate before publishing a `fast-v1.0-beta-5` prerelease. Music keeps its `v...` tags and F-Droid publisher; the publisher filters Music tags so FAST APKs cannot be mistaken for Music. Beta 5 is not submitted to f-droid.org or added to the stable F-Droid feed.

**Modern Android.** Minimum stays Android 4.2.2 (API 17); target is API 36 (Android 16). Behaviour is version-gated: on Android 11+ the page is padded for the system bars, the camera cutout and the keyboard (Android 15+ draws edge to edge and no longer resizes for the keyboard), full screen hides the bars through the insets controller, and the bars' icon colours follow the theme. On Android 13+ Back uses predictive back callbacks (Android 16 never calls `onBackPressed` for apps targeting it), so Back steps out of full screen, the watch page and tabs before leaving the app. Conscrypt's native libraries are 16 KB page aligned.

The shared library extraction changes Music's build inputs. A future Music release must regenerate and recheck its reproducible APK against the updated source tree.

## Licences

Grapefruit code: GPL-3.0-or-later, copyright 2026 MagicFlute LLC. Geist and Geist Pixel Triangle: Vercel, SIL OFL 1.1. ExoPlayer, AndroidX, Conscrypt, Guava and supporting Google annotations: Apache-2.0; Checker Framework annotations: MIT. Runtime licence texts ship in the assets and are accessible through Settings. Test tooling is not bundled in the APK.
