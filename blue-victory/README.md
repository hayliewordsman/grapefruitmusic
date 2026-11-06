# Blue Grapefruit: Victory Edition

Version **1.0 stable**, application ID `io.github.hayliewordsman.bluegrapefruit.victory`.
This separate app module uses Grapefruit Audio's original layouts and unmodified shared classes,
with Blue-only Java overrides for edition changes. Build-type
resource overlays supply the blue palette and branding. The approved blue grapefruit icon has a donkey silhouette over the Capitol and no headphones. The two-line wordmark reads **Blue Grapefruit** / **Victory Edition**,
using the original Geist Pixel Triangle letterforms and a smaller second line. The launcher
label and all of the app's own copy use **Blue Grapefruit**. There is one theme.

Minimum Android 4.2.2 (API 17); target API 36, with no maximum Android version. Forward compatibility
with Android 17 must be checked on that OS: minSdk 17 means Android 4.2.2, not Android 17.
All four original native ABIs are retained. Stable releases are published on GitHub and in the
developer's F-Droid repository. Test downloads remain GitHub prereleases and stay out of F-Droid.
The Blue download page is https://hayliewordsman.github.io/grapefruitmusic/blue/.

## Grapefruit OS preinstall

On the Grapefruit OS development branches, the shipping `osVictory` build
preloads the owner's five tracks as **Bonus Bundled Content**. The album and
track titles use that suffix; the old internal source IDs remain stable for
updates. See [the OS build guide](../os/README.md) for the private asset import,
unchanged-audio verification and OEM signing workflow. `osFoss` is a music-free
CI alternative. Audio, artwork and bundle inputs are not committed to source.

## Private music inputs

The public repository contains code and the owner-approved tag plan in
`tools/blue_victory/metadata.json`. **Never commit the EP audio, its cover, the tagged copies, or
the bundle key.** Keep all of them outside the repository or under the ignored `work/` directory.
Music is separately copyrighted; the app's GPL does not change the music's distribution terms.

After the owner has approved the metadata and cover:

```sh
python tools/blue_victory/prepare.py \
  --music-dir /private/uploads --cover /private/cover.jpg \
  --output work/blue-grapefruit/assets --tagged work/blue-grapefruit/tagged --approved
BLUE_VICTORY_BUNDLE="$PWD/work/blue-grapefruit/assets" \
  ./gradlew :blue-victory:assembleBeta :blue-victory:testDebugUnitTest :blue-victory:lintBeta
```

The preparer requires `mutagen`, `Pillow`, `cryptography`, and FFmpeg. It replaces all ID3 tags,
embeds one front cover, removes lyrics and old metadata, uses ID3v2.3, and verifies that the
compressed audio stream is byte-identical. A 1000px JPEG display copy of the approved photo reduces repeated cover overhead; `--preserve-cover` keeps the original encoding when required. The build refuses to package missing or draft assets.
The test APK is `blue-victory/build/outputs/apk/beta/blue-victory-beta.apk`. It is non-debuggable.
Without the permanent signing key it uses the persistent local beta key in ignored `work/blue-grapefruit/debug.keystore`. Beta 2 reuses Beta 1's certificate, so it installs as an update. Keep that key for future test updates.

## Private playback

The five MP3s are authenticated AES-CBC bundles in the APK, unpacked atomically to internal app
storage. They are never registered in MediaStore, copied to shared storage, offered as downloads,
exported through a content provider, or queried on lyrics services. Their source and album IDs
are separate from purchased copies, and the included tracks are read-only. The EP appears alongside local music in **On This Device**, without a separate source-picker entry; normal phone, server, radio, podcast and audiobook features remain.

This prevents ordinary access through other players. It is not DRM: the APK carries its key,
and device owners can reverse engineer or record playback. Do not promise unextractable audio.

## Branding

Run `python tools/brand/blue_victory.py path/to/geist/package` with the original logo generator's
Python dependencies and Geist 1.9.0. It embeds `brand/approved-icon.png` and uses the verified Geist Pixel Triangle font instance, renders Android 4.2-compatible PNGs
in the existing sizes/densities, and subsets the font for the Blue edition's two-line wordmark.
It never changes the standard apps' artwork, fonts or store listings. The shared Java copy uses
edition-aware literal strings; user-supplied media metadata is never passed through renaming.

## Beta 2 TV integration

Blue-only Java and all audio overrides live under `blue-victory/src/main/java`. A build-only
source copy excludes files overridden by Blue, so `app/` and `tv/` stay byte-for-byte identical
to the pre-Blue baseline (`3455b41`). Standard Audio and TV builds receive no Blue features,
branding, radio palette changes, or podcast fix. The Blue audio shell loads its TV extension. The 59 included channels,
M3U/Xtream parsers, guide, transport and video controls are adapted from TV & Movies 3.0
(commit `e8fdfd1da211fe42629f77776c23d0aa72eae104`).

The **TV** tab has Channels and Guide chips, mirrored in its tap/long-press popup. On Demand
appears only when linked provider content exists. Downloads appear with that capability or
while previously saved downloads remain. Series open seasons/episodes; only films and episodes
can be downloaded. Progressive downloads resume with verified HTTP Range offsets; finite,
unencrypted HLS with muxed audio is supported. Live capture, encrypted HLS downloads and
separate-audio HLS downloads are unavailable. No DVR, recording scheduler or Record control
is installed. There is no free Movies catalog or external-app directory in Blue.

Beta 3 uses a draggable 16:9 TV picture in a content corner above the tabs, with vector
play/pause and close controls. TV moves between that picture and Now Playing on one
ExoPlayer/connection. Music and TV remain mutually exclusive. Background playback disables the video track and surface, retaining audio
and notification/media controls. Choosing music or radio releases TV; choosing TV pauses audio.
TV provider, playback, guide and download controls are rows in the main Settings screen.

MinSdk remains 17 with ExoPlayer 2.18.7. Lint checks old-API calls; Robolectric exercises API 23,
28, 35 and 37. Device testing is still required for actual hardware decoding and provider streams.

## Beta 3 interface fixes

TV filter and playback buttons use equal 8dp gaps and wrap onto additional rows when needed.
Text buttons have at least 48dp touch height and grow for larger text. The Now Playing channel
lineup has 14dp side gutters and 8dp between buttons. Full-screen controls wrap too.

The TV picture starts in the bottom-right corner, clear of the bottom tabs or side rail, and
remembers its side and vertical position after dragging. Its close and play/pause controls are
44dp vector icons with accessibility labels; the former Roman-numeral pause glyph is removed.
The existing application-owned playback session and audio-only background behavior are retained.
Android 4.x uses the TV app's painted corner mask; Android 5+ clips to a rounded outline.

## Beta 4 Victory Roll Call and radio colours

Settings has a Blue-only **Victory Roll Call** button. The offline, dated provisional snapshot
lists verified Democratic projected winners and continuing terms: federal Senate, federal House, Democratic governors-elect,
then state Senate and state House/Assembly by state. Search and a state jump menu keep the long
list usable. Missing results have tappable unresolved-contest details; neither prior incumbency
nor a vote lead is treated as a re-election. The owner's exact closing paragraph follows a blank
line as the final item in this same list. It loads with the roster, stays hidden during loading,
and is omitted from filtered search results.

Pride flags are compiled Canvas drawings, including rainbow, bisexual and transgender stripes.
They work without system emoji support. A flag requires a public self-identification source;
an absent flag means no matching verified label, rather than a claim about someone's identity.
Names open their election/term and identity sources. People with multiple requested labels can
have multiple flags. Publicly queer, pansexual or nonbinary labels alone do not imply one of the
three requested flags. Exact name, state and office-level matching prevents namesake errors.

The source snapshot and methodology are documented in `tools/acknowledgements/README.md`.
This is a provisional list with unresolved results, not a complete or certified 2027 roster.

Internet Radio genre tiles and saved Internet/FM station backgrounds use seven coordinated blue
gradients. Station logos keep their colours on a white card over the blue background. The
standard audio app keeps its existing multicolour palette. The separate TV app is unchanged.

Podcast source setup and MediaPlayer cleanup run on a serial background worker. Android's
`setDataSource(url)` may block for HTTP response headers before `prepareAsync`; it no longer runs
inside the episode tap handler. Cancelled/replaced loads cannot revive playback, cached volume
is applied when ready, and opening the next episode does not wait for a stalled previous load.
Regression checks deliberately stall setup and verify responsive UI work and cancellation.

## Original-app isolation

Every Blue-specific Java change from all four betas is confined to this module, including
branding, private EP access, TV integration hooks, radio artwork, toggles and podcast loading.
`prepareBlueAudioSources` copies the remaining unchanged standard Java classes to an ignored
build directory and excludes any class with a Blue override. The standard `app` and separate
`tv` source trees, resources, manifests, tests and build files match commit `3455b41` exactly.
Only the Blue APK is published. Original Audio and TV release/version processes are untouched.

## Beta 5 Android 4.x text editing

Blue's Android 4.x fields use an app-owned selection/paste menu and draggable blue selection
handles while keeping EditText's normal keyboard/input connection. The system selection toolbar
and insertion popup are bypassed on API 17–20. API 21+ retains the normal platform editor.
Password fields cannot copy/cut, filters constrain pasted text, and menu cleanup follows the
field's lifecycle. All field constructors, including TV and radio diagnostics, are covered.
See `docs/legacy-text-editing.md` for scope, test limits and a later porting guide. Standard Audio
and the separate TV app remain byte-for-byte unchanged from the pre-Blue baseline.

## Beta 6 Victory Roll Call refresh

The provisional snapshot is checked as of November 5, 2026. It contains 3,411 verified entries,
127 more than Beta 5, with 1,313 contests still unresolved or not yet verified. Eighteen publicly
documented transgender members in the covered federal/state legislative offices have bundled
flags. New public identity supplements cover Allison Montgomery, Jaelynn Scott, Alexis Hoffkling,
Abi Boatman, Liish Kozlowski, SJ Howell and Alissandra Murray. Newly verified reelections also
bring Wick Thomas and Alice Wade into the roster. Public name aliases are source-backed and
matched with the exact state, chamber and district; Liish's current public name is displayed.
The identity supplement cannot add a candidate without independent election or term evidence.

The complete tag/artwork-approved EP, Beta 5's Android 4.x compatibility editor, original app
isolation and other Blue features are retained. The list is provisional; neither a lead nor a
primary victory is treated as a general-election call.

## Stable 1.0 publication

Version code 8 promotes the approved Beta 7 without changing its features, music or November 5
projected roster. The release variant uses Blue's existing persistent certificate so it updates
all seven betas. The standard apps' signing key is not used for Blue.

The stable APK and checksum are distributed through the `blue-v1.0` APK-only GitHub tag and
release. The complete app source is on the default branch; private raw music and its bundle key
remain uncommitted. F-Droid's existing publisher recognizes stable `blue-v*` releases, verifies
Blue's package ID and certificate separately, and imports its listing from `blue-victory/fastlane`.
The separately copyrighted EP is identified as NonFreeAssets in this developer-owned repository.
This is not a submission to the independent f-droid.org catalog.

Use the F-Droid workflow's `repository_only=true` input to deploy Blue without building or
publishing either original app. This mode compares the complete existing Audio and TV catalog
entries against the rebuilt index and refuses deployment if either changes. The original app
source trees, store listings, site front page, versions, release tags and APKs remain unchanged.
The existing repository certificate is pinned; Blue has a separate `/blue/` download page.
