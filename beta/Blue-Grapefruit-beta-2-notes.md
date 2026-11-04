# Blue Grapefruit: Victory Edition — 1.0 beta 2

APK-only test build, version code 2. Installs over Beta 1 using the same local beta signing certificate.

- Advance EP merged into **On This Device**, alongside local music; advance-copy titles and private track identities remain separate from purchased copies.
- Long album names scroll in the Now Playing header.
- Blue grapefruit toggles in both states, including the rolling slice knob.
- Approved blue grapefruit / Capitol / donkey icon, without headphones. Geist Pixel Triangle two-line wordmark retained.
- Built-in **TV** tab: 59 included FAST channels, Channels and Guide chips, matching tap/long-press tab menu, M3U and Xtream providers.
- Provider On Demand appears when films or series are supplied. Series open their episodes. Downloads support progressive files (resumable) and finite unencrypted HLS with muxed audio.
- TV Now Playing, captions, quality choices, full-screen viewing, and a picture mini-player sharing the music mini-player slot. Changing media source pauses the previous source.
- Background TV audio disables video selection and its surface while retaining audio playback.
- Provider, playback, guide and download settings are rows on the main Settings page.
- No DVR, live recording, Record button, standalone Movies library or external TV-app directory. Separate TV & Movies app unchanged.

Size: 39,497,586 bytes (39.50 MB / 37.67 MiB), down from Beta 1's 47,501,961 bytes. All five compressed audio hashes match Beta 1. Repeated album artwork uses a compact 1000×1000 JPEG display copy of the approved Capitol photo; uploaded originals remain intact.

Metadata: Poppy Peppers (artist and album artist); lyricist Haylie Wordsman; copyright and publisher 2026 Bumper-to-Bumper Records; year 2026; disc 1/1; tracks 1/5–5/5. Album and song titles end in (Advance Copy); embedded lyrics removed. See the JSON review for exact filenames and tags.

Compatibility: minSdk 17 / Android 4.2.2, target API 36, no upper SDK restriction; four native ABIs retained. Automated UI/runtime checks on API 23, 28, 35 and 37 (Android 17); 48 Blue tests plus 202 original audio tests pass. Both lint checks pass. APK is non-debuggable with v1/v2 signatures and the same certificate as Beta 1. No emulator or physical device was available: actual playback, codec support and provider endpoints still need device testing.

Download limitations: live streams, encrypted HLS downloads and HLS with separately delivered audio are unsupported. Channel availability and programme listings depend on their providers and region. Finished downloads stay available if their provider is removed. This is a GitHub pre-release, with no F-Droid publication.

APK SHA-256: d9ba9344a2c40c9e0a6189f22a2c45cb29f0703804dc0c6561900bc4a97b765d
