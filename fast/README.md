# Grapefruit FAST

**1.0 beta 1 (build 1)** — `io.github.hayliewordsman.grapefruitfast`

A second native Android app in the Grapefruit Music & Podcasts repository. Both can be installed at once. Music's application ID and version remain unchanged. Shared Geist fonts and Conscrypt TLS support live in `grapefruit-ui/`; Music's existing UI remains in `app/`.

FAST uses Grapefruit's Dark, Light and Pitch Black palettes, Geist UI type, Geist Pixel Triangle wordmark, rounded cards, pill controls and responsive side/bottom navigation. The launcher icon places the grapefruit inside a widescreen television, with a smaller circle based on the first approved TV design.

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

`src/main/assets/channels.json` contains 28 initial channel candidates: three CityNews feeds (Toronto, West/Calgary & Edmonton, Vancouver), fourteen Global News feeds (National, Toronto, Winnipeg, Edmonton, BC, Calgary, Halifax, Kingston, Lethbridge, Montreal, Okanagan, Peterborough, Regina, Saskatoon), NBC News NOW, ABC News Live, CBS News 24/7, and eight series/reality/lifestyle channels.

**CP24 is excluded:** no public video stream was obtained from cp24.com. Do not add third-party CP24 restreams.

Each entry has an official viewing page and endpoint provenance. Only public broadcaster/distributor CDN candidates are included; an IPTV directory listing is not proof of permission for third-party redistribution or current availability. No broad community playlist is imported at runtime. Channel names belong to their owners; Grapefruit is not affiliated with them. Before distribution, review the source terms and run the probes from the intended region. The catalog currently contains unverified candidates, including older Global origin paths; replace failed paths with current official ones after live verification. Fourteen listed Global regions do not imply that every stream is currently reachable.

The beta has a channel guide, **not a programme schedule/EPG**. It does not invent programme titles or times. Stream links ship in the APK, so catalog corrections currently require an app update.

Source checks:

```sh
python3 tools/fast/check_streams.py --output work/stream-report.json
```

The checker follows the public HLS master to a media playlist and checks a media segment sample. It records variants, failures and hosts without adding location workarounds or stripping ads. A successful check establishes reachability, not distribution rights or JP5S compatibility. During implementation the environment policy blocked the stream hosts, so successful playback is **not yet verified**.

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

Debug APK: `fast/build/outputs/apk/debug/fast-debug.apk`. Debug signatures are for testing, separate from the permanent release key.

`.github/workflows/fast.yml` builds/tests/lints FAST and uploads its own APK/report artifacts. To publish, run it manually on the repository's default branch with `publish` enabled. Set these repository secrets first:

- `GRAPEFRUIT_FAST_KEYSTORE_BASE64`: a PKCS12 key with alias `grapefruit-fast`.
- `GRAPEFRUIT_FAST_KEYSTORE_PASSWORD`: the key/store password.
- Repository variable `GRAPEFRUIT_FAST_CERT_SHA256`: the permanent key's lower-case SHA-256 certificate fingerprint without colons.

Local signed builds use `GRAPEFRUIT_FAST_KEYSTORE` and `GRAPEFRUIT_FAST_KEYSTORE_PASSWORD`. Keep signing files outside Git. The release workflow verifies package identity and the configured certificate before publishing a `fast-v1.0-beta-1` prerelease. Music retains its `v...` tags, signing settings and F-Droid publisher; its publisher now filters Music tags so FAST APKs cannot be mistaken for Music. Beta 1 is not submitted to f-droid.org or added to the stable F-Droid feed.

The shared library extraction changes Music's build inputs. A future Music release must regenerate and recheck its reproducible APK against the updated source tree.

## Licences

Grapefruit code: GPL-3.0-or-later, copyright 2026 MagicFlute LLC. Geist and Geist Pixel Triangle: Vercel, SIL OFL 1.1. ExoPlayer, AndroidX, Conscrypt, Guava and supporting Google annotations: Apache-2.0; Checker Framework annotations: MIT. Runtime licence texts ship in the assets and are accessible through Settings. Test tooling is not bundled in the APK.
