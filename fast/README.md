# Grapefruit FAST

**1.0 beta 2 (build 2)** — `io.github.hayliewordsman.grapefruitfast`

A second native Android app in the Grapefruit Music & Podcasts repository. Both can be installed at once. Music's application ID and version remain unchanged. Shared Geist fonts and Conscrypt TLS support live in `grapefruit-ui/`; Music's existing UI remains in `app/`.

FAST uses Grapefruit's Dark, Light and Pitch Black palettes, Geist UI type and the Geist Pixel Triangle wordmark. Like Music 5.x, everything is a pill and floats: the tabs ride over the page on a floating pill (along the bottom, or down the side when there's room across), the last channel waits on a Continue watching pill above them like Music's mini player, channel rows are pills with round logos, and lists scroll out from under the floating bars. The launcher icon places the grapefruit inside a widescreen television, with a smaller circle based on the first approved TV design.

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

`src/main/assets/channels.json` contains 28 source-reviewed channels: three distinct CityNews feeds (Toronto, Alberta/Calgary & Edmonton, Vancouver), fourteen Global News feeds (National, Toronto, Winnipeg, Edmonton, BC, Calgary, Halifax, Kingston, Lethbridge, Montreal, Okanagan, Peterborough, Regina, Saskatoon), NBC News NOW, ABC News Live, CBS News 24/7, and eight entertainment channels: The Carol Burnett Show, Baywatch, Unsolved Mysteries, Forensic Files, Deal or No Deal, The Martha Stewart Channel, America’s Test Kitchen and Family Affair Channel.

**CP24 is excluded:** no public video stream was obtained from cp24.com. Do not add third-party CP24 restreams.

Each entry has an official viewing page and first-party endpoint provenance. News sources come from the broadcasters' current player metadata; NBC and entertainment sources come from Xumo's official guide and HLS/SSAI metadata. Provider ads and regional restrictions remain. `docs/fast/catalog-sources.json` records the source URLs, ad-template handling and retired candidates. No community playlist is imported at runtime. A reachable public feed is not a grant of third-party redistribution rights. Review the source terms and probe from the intended region before distribution. Channel names belong to their owners; Grapefruit is not affiliated with them.

The beta has a channel guide, **not a programme schedule/EPG**. It does not invent programme titles or times. Stream links ship in the APK, so catalog corrections currently require an app update.

Source checks:

```sh
python3 tools/fast/check_streams.py --output work/stream-report.json
```

The checker follows every public HLS master through all video/audio renditions and bounded media samples, including initialization/key resources where present. It rejects DRM, insecure redirects, non-media responses and ended playlists without adding location workarounds or stripping ads. The refreshed catalog passed all 28 channels across the full run and a recorded follow-up; Global National and Winnipeg had intermittent runtime proxy denials in the full run. Bounded ffprobe samples identified H.264/AAC for all 28. See `docs/fast/validation.md` and the committed JSON reports for exact scope and failures. JP5S playback remains untested.

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

Debug APK: `fast/build/outputs/apk/debug/fast-debug.apk`. Debug signatures are for testing, separate from the permanent release key. PR Actions also supplies a `grapefruit-fast-1.0-beta-2-test-apk` artifact with an installable test APK, checksum and verification evidence.

`.github/workflows/fast.yml` builds/tests/lints FAST and uploads its own APK/report artifacts. To publish, run it manually on the repository's default branch with `publish` enabled. Set these repository secrets first:

- `GRAPEFRUIT_FAST_KEYSTORE_BASE64`: a PKCS12 key with alias `grapefruit-fast`.
- `GRAPEFRUIT_FAST_KEYSTORE_PASSWORD`: the key/store password.
- Repository variable `GRAPEFRUIT_FAST_CERT_SHA256`: the permanent key's lower-case SHA-256 certificate fingerprint without colons.

Local signed builds use `GRAPEFRUIT_FAST_KEYSTORE` and `GRAPEFRUIT_FAST_KEYSTORE_PASSWORD`. Keep signing files outside Git. The release workflow verifies package identity and the configured certificate before publishing a `fast-v1.0-beta-2` prerelease. Music retains its `v...` tags, signing settings and F-Droid publisher; its publisher now filters Music tags so FAST APKs cannot be mistaken for Music. Beta 2 is not submitted to f-droid.org or added to the stable F-Droid feed.

The shared library extraction changes Music's build inputs. A future Music release must regenerate and recheck its reproducible APK against the updated source tree.

## Licences

Grapefruit code: GPL-3.0-or-later, copyright 2026 MagicFlute LLC. Geist and Geist Pixel Triangle: Vercel, SIL OFL 1.1. ExoPlayer, AndroidX, Conscrypt, Guava and supporting Google annotations: Apache-2.0; Checker Framework annotations: MIT. Runtime licence texts ship in the assets and are accessible through Settings. Test tooling is not bundled in the APK.
