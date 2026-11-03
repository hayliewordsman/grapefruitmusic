# Grapefruit FAST 1.0 beta 1 — verification report

Checked November 2, 2026 (America/Toronto; machine reports use November 3 UTC).

## Result

All **28 final channels** reached media through their official broadcaster/distributor sources. Across the full probe and the recorded follow-up, **112 video renditions**, CBS's additional audio-only rendition and CityNews Toronto's separate audio playlist passed playlist/media checks. Every channel offers multiple video bitrates. Bounded ffprobe samples identified **H.264 video and AAC audio for all 28 channels**. This establishes network/media reachability and codec metadata, not successful JP5S decoding or runtime bandwidth adaptation.

The refreshed catalog and evidence are committed in `fast/src/main/assets/channels.json`, `docs/fast/catalog-sources.json`, `docs/fast/stream-report.json` and `docs/fast/codec-report.json`.

## Network and source corrections

The updated environment reported unrestricted networking with an enforced policy. An actual HTTPS request to github.com returned 200, Git fetched the requested branch, and the official CityNews, Global, ABC, CBS, NBC and Xumo pages/APIs were accessible. The previous run's 28 proxy CONNECT failures were not used to classify streams.

The original catalog's 14 `siloh.pluto.tv` Global paths returned provider HTTP 403. ABC's old Hudson endpoint returned HTTP 404. They were replaced from current first-party player metadata. Global's official encoded `video-entry` responses supply distinct Google DAI asset keys for all fourteen regions; ad-supported DAI masters are used instead of bare origin/backup streams. Toronto and Alberta use CityNews's public Brightcove DAI keys. Vancouver retains its exact current Brightcove CDN source; that record supplies no DAI key. ABC uses its official itemfeed's Apple News HLS master. CBS uses the DAI master in its current live page.

Toronto, Alberta (Calgary/Edmonton share one video ID) and Vancouver are the three distinct 24/7 CityNews feeds found. Montreal, Winnipeg, Ottawa and Halifax pages were also inspected: their current players showed on-demand clips, not additional distinct 24/7 streams. No duplicate regional entry or on-demand clip was added as live TV.

NBC News NOW and all eight entertainment entries now use clear HLS/SSAI URLs obtained directly from current Xumo metadata and its viewing guide. Every provider query key and SSAI/ad tag is retained. Optional ad macros use the app identity, limited-ad-tracking, a zero advertising ID and blank/unknown targeting values; no advertising identifier or precise coordinates are collected. No country override was introduced, and no VPN, geo proxy, DRM workaround, ad filtering or community playlist is used by the app.

The older 21 Jump Street, Bob Ross and FilmRise Westerns CDN candidates were reachable, but a direct first-party endpoint link was not established. They were replaced with **The Carol Burnett Show**, **The Martha Stewart Channel** and **Family Affair Channel**. CINEVAULT Westerns was considered and excluded because its official HLS metadata declares FairPlay DRM. **CP24 remains excluded:** no public stream URL was obtained from cp24.com.

The probes ran through US egress. Provider ads and regional restrictions remain; Canadian/device-specific availability and third-party distribution rights are not established by a public CDN response. No currently included channel had a persistent failed probe. Regional or later unavailability must still be reported by the player rather than worked around.

## Probe evidence and intermittent failures

`tools/fast/check_streams.py` now checks every configured master and fallback, every video rendition (including those above the API-17 selection cap), all separate audio playlists, initialization resources and ordinary AES-128 keys when present. It checks bounded 64 KiB segment samples for TS/fMP4/AAC framing, rejects HTML error bodies, ended/empty playlists, DRM and HTTPS downgrades, and distinguishes runtime proxy CONNECT denials from provider HTTP errors. Samples are taken before the publishing edge. This does not decode entire programmes or establish continuous uptime.

The full final-catalog run passed 26/28 channels. Global National and Winnipeg had intermittent **runtime proxy CONNECT 403** during rendition/media sampling. A targeted follow-up using the exact same masters passed all three renditions for both channels. Both failed and successful observations are retained in `stream-report.json`; they were not replaced with a claim that the provider streams were dead. The preceding full-rendition run had also passed those same Global sources. Separate 512 KiB ffprobe samples passed for all 28 final channels.

| Channel | Video renditions | Offered heights | Data saver candidate | Compatibility candidate | Media check |
|---|---:|---|---|---|---|
| CityNews 24/7 · Toronto | 3 | 360–720p | Yes | None | Passed |
| CityNews 24/7 · Alberta / Calgary & Edmonton | 3 | 360–720p | Yes | None | Passed |
| CityNews 24/7 · Vancouver | 3 | 360–720p | Yes | None | Passed |
| Global News · National | 3 | 480–720p | None | None | Passed after follow-up |
| Global News · Toronto | 3 | 480–720p | None | None | Passed |
| Global News · Winnipeg | 3 | 480–720p | None | None | Passed after follow-up |
| Global News · Edmonton | 3 | 480–720p | None | None | Passed |
| Global News · BC | 3 | 480–720p | None | None | Passed |
| Global News · Calgary | 3 | 480–720p | None | None | Passed |
| Global News · Halifax | 3 | 480–720p | None | None | Passed |
| Global News · Kingston | 3 | 480–720p | None | None | Passed |
| Global News · Lethbridge | 3 | 480–720p | None | None | Passed |
| Global News · Montreal | 3 | 480–720p | None | None | Passed |
| Global News · Okanagan | 3 | 480–720p | None | None | Passed |
| Global News · Peterborough | 3 | 480–720p | None | None | Passed |
| Global News · Regina | 3 | 480–720p | None | None | Passed |
| Global News · Saskatoon | 3 | 480–720p | None | None | Passed |
| NBC News NOW | 6 | 240–1080p | Yes | Yes | Passed |
| ABC News Live | 5 | 180–720p | Yes | Yes | Passed |
| CBS News 24/7 | 7 | 224–1080p | Yes | Yes | Passed |
| The Carol Burnett Show | 5 | 240–1080p | Yes | Yes | Passed |
| Baywatch | 5 | 240–1080p | Yes | Yes | Passed |
| Unsolved Mysteries | 6 | 144–720p | Yes | Yes | Passed |
| Forensic Files | 6 | 144–720p | Yes | Yes | Passed |
| Deal or No Deal | 6 | 144–720p | Yes | Yes | Passed |
| The Martha Stewart Channel | 5 | 240–1080p | Yes | Yes | Passed |
| America’s Test Kitchen | 4 | 360–1080p | Yes | None | Passed |
| Family Affair Channel | 6 | 144–720p | Yes | Yes | Passed |

"Candidate" means a manifest track fits the existing size/bitrate/frame-rate limits, not that a JP5S decoder supports its H.264 profile/level. Auto remains ExoPlayer 2.18.7's measured-bandwidth adaptive selection, with an API-17 maximum of 720p/30 fps. For CityNews that excludes the 720p/59.94 rendition, leaving 360p/540p. Global's lowest offered track is 480p at about 1.57 Mbps, so **Data saver and Compatibility cannot select Global video** under the preserved 1.4 Mbps/800 Kbps caps. Compatibility also cannot select CityNews or America's Test Kitchen video. The existing player reports no compatible video and suggests Auto; it does not exceed the chosen caps.

## Builds, tests and compatibility

JDK 21, Android SDK 36, build tools 35.0.0 and Gradle 8.14.3:

```sh
./gradlew :fast:assembleDebug :fast:assembleRelease :fast:testDebugUnitTest :fast:lintDebug \
          :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
python3 -m unittest discover -s tools/fast -p 'test_*.py' -v
python3 tools/fast/check_streams.py --output work/stream-report.json
```

- FAST debug and unsigned release builds passed; **8 FAST tests passed**, zero test errors/failures; lint has zero errors (5 warnings).
- Music debug build passed; **183 Music tests passed**, zero test errors/failures; lint has zero errors (existing warnings remain).
- **11 offline stream-checker regression tests passed**: broken high renditions, separate-audio failures, HTTP-200 HTML, fMP4 initialization, DRM, live boundaries, audio-only masters, fallbacks, HTTPS, byte ranges and proxy/provider error classification. FAST CI runs these tests too.
- Both APK manifests retain **minimum SDK 17**. FAST's ID is `io.github.hayliewordsman.grapefruitfast`, version `1.0-beta-1` (1); Music remains `io.github.hayliewordsman.grapefruitmusic`, `5.1-beta-2` (63). They install independently.
- `apksigner verify --verbose --print-certs --min-sdk-version 17` passed for the local test APK, including **v1/JAR signing**, which Android 4.2.2 needs. Conscrypt's four ABI libraries remain present. No dependency, minimum SDK, theme, font, branding image or icon was changed in this continuation.
- The managed runtime required a writable `ANDROID_USER_HOME`, Java HTTP proxy settings and the runtime CA truststore for Robolectric's artifact downloads. Those were supplied through an uncommitted Gradle init script. No TLS verification was disabled or app build policy changed.

Local test APK SHA-256: `bcc89f3c5dd5fe83c5c5e55d163cb81e19cb3143ff4b3669f2002b07432bfe9f`.
Local debug signing certificate SHA-256: `b7a28c2d50e429b1e09f2210d7cabb5505f05ffb9859d03a4c14f04fd53bbb2d`.

## Installable test build

The FAST Actions run attached to PR #1 uploads **`grapefruit-fast-1.0-beta-1-test-apk`**. Download and extract that artifact, then sideload **`grapefruit-fast-1.0-beta-1-test.apk`**. It includes `SHA256SUMS`, signature/package metadata and this verification report. Use that artifact's own checksum and certificate: a CI runner's temporary debug key and APK hash can differ from the local build above. This is an installable debug test build, not the unsigned release APK. No release has been published.

## Still required

Test playback on the actual jailbroken **JP5S**: H.264 profile/level support, TLS, audio, throttled Wi-Fi bitrate changes, decoder recovery, rotation, reconnect, audio focus, remote keys, app switching and low memory. API-17 manifests, successful builds and network samples do not substitute for those tests. Existing UI tests use Robolectric API 28.

Review provider terms and target-region eligibility before distribution. Configure and verify the separate permanent FAST signing key before a production/prerelease build. No release, F-Droid publication or production deployment was performed.

## Beta 5 catalog (November 3, 2026)

`tools/fast/sources.py` added 29 channels from official first-party metadata only: Xumo's guide (live SIMULCAST sources; DRM and episodic channels refused), DW's own live page and the ABC's own News Channel page. `tools/fast/check_streams.py` then probed the full 57-channel catalog from US egress. All 57 reached media, all with multiple renditions, and every rendition declares H.264/AAC (`docs/fast/stream-report-beta-5.json`). Xumo is a US service and CityNews/Global are Canadian broadcasters; availability elsewhere was not established by these probes. DW News and ABC News Australia are served by the broadcasters' public players and answered from outside Germany and Australia. CBC's local news channels were not added: CBC Gem offers them to signed-in members, and Grapefruit does not get around sign-in. No ffprobe decode pass was made for the new channels; codecs are from the HLS masters. JP5S hardware playback remains untested.
