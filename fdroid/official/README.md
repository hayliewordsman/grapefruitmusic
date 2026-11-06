# Grapefruit Maps Legacy: official f-droid.org submission

The signed developer repository already publishes Grapefruit apps at
<https://hayliewordsman.github.io/grapefruitmusic/repo>. This is distinct from
inclusion in f-droid.org, which requires an fdroiddata merge request and review.

Maps metadata: [`io.github.hayliewordsman.grapefruitmaps.yml`](io.github.hayliewordsman.grapefruitmaps.yml).
Copy it to `metadata/` in a public fork of <https://gitlab.com/fdroid/fdroiddata>,
run its checks, and open **New app: Grapefruit Maps Legacy** against `master`.
The account used for this repository has no connected GitLab submission
credential in this workspace, so this file is a prepared submission, not an
accepted upstream listing.

The recipe uses JDK 21 and builds only Maps and grapefruit-ui. It removes
unrelated Audio, TV and Blue modules before scanning/building. Listing text,
icon, four screenshots and changelog are in `maps/fastlane/metadata/android/`.
`Binaries` and `AllowedAPKSigningKeys` request reproducible builds so users
can update between GitHub, the developer repo and a future official listing.
Validation on November 6, 2026: `fdroid lint` and `fdroid rewritemeta --list`
pass. `fdroid build --test --no-tarball` with fdroidserver 2.4.5, JDK 21,
Gradle 8.14.3 and the real `maps-v1.0` tag passes the source scanner, builds
version 1.0 (7), and reports "compared built binary to supplied reference binary
successfully". It also verifies the allowed permanent signer. This confirms
reproducibility against the published GitHub APK. The local test uses the
installed JDK instead of running the build-server-only sudo setup commands.

Suggested introduction:

> Grapefruit Maps Legacy is a small GPL-3.0-or-later native map app for Android
> 4.2.2 and later, tested by its developer and beta users on older hardware.
> It offers OpenStreetMap/Mapsforge 2D maps, NASA Blue Marble satellite imagery,
> country and regional offline maps, place search and written driving directions.
> It uses no proprietary SDK, API key, advertising, account or telemetry.
> Location is optional. Satellite detail is regional and directions use an online
> OSRM service; offline routing and turn-by-turn navigation are not included.
> Its four themes include an optional, hidden political-satire minigame.
> The code and caricature were made with AI assistance under developer direction;
> source, editable artwork, prompt, asset recipes and licenses are included.
>
> Please review the `Binaries` recipe for reproducibility and preservation of the
> existing developer signing key. The certificate subject is Android Debug,
> but the pinned certificate is the project's permanent private release key.

F-Droid's inclusion policy normally asks for three months of development,
unless an app already has users or its author maintains other F-Droid apps.
This repository began October 25, 2026. Explain the existing beta users when
submitting; the reviewers determine whether that exception applies. Acceptance
and publication dates are controlled by F-Droid's reviewers and build servers.

---

# Getting Grapefruit Music & Podcasts into f-droid.org

f-droid.org builds every app itself from its source, following a build recipe kept in its
[fdroiddata](https://gitlab.com/fdroid/fdroiddata) repository on GitLab. The recipe for Grapefruit
Music & Podcasts is [`io.github.hayliewordsman.grapefruitmusic.yml`](io.github.hayliewordsman.grapefruitmusic.yml)
here; submitting means proposing that file to fdroiddata.

## What's ready

- **Free licence:** GPL-3.0-or-later (`LICENSE`), shown in Settings › About with a link to the source.
- **Own app ID:** `io.github.hayliewordsman.grapefruitmusic`, from the GitHub Pages address.
- **Store listing** in `fastlane/metadata/android/en-US`: name, summary, description, icon and
  changelogs.
- **The recipe builds with F-Droid's own tool.** `fdroid build` (fdroidserver 2.4.5, Gradle 8.14.3,
  JDK 21) builds version 5.1 from commit `769bfa9a`, and `fdroid lint` and `fdroid rewritemeta`
  are clean. F-Droid's scanner finds no non-free code, trackers or extra signing blocks. The two
  binaries it flags are data, let through with `scanignore` and explained under `MaintainerNotes`,
  as other apps that ship model data do: Karaoke Mode's model `app/src/main/assets/karaoke.bin` and
  Live Mode's synthesized crowd `app/src/main/assets/live_crowd.bin`. The unit-test fixtures are
  removed before the build (`scandelete`). The fonts (Geist, OFL) aren't flagged.
- **Anti-feature: NonFreeNet.** Podcast search and the podcast charts use Apple's podcast directory
  (iTunes Search API). The recipe declares it, with that reason; playing and subscribing use each
  podcast's own RSS feed, and a feed can be added by its address.
- **Reproducible builds.** The same commit builds byte-for-byte the same APK on GitHub Actions as in
  an F-Droid-style build. `fdroid build` of the recipe downloaded the APK of GitHub release v5.1,
  compared its own build with it and accepted it ("compared built binary to supplied reference
  binary successfully", checked on 2026-11-03, with Grapefruit FAST's `fast/` (now Grapefruit TV's `tv/`) in the repository, not
  built by this recipe, and the shared `grapefruit-ui` library built from source; 5.0, 4.1, 4.1.1 and
  4.1.2 passed the same check on 2026-11-02, and 3.2.2 on 2026-10-29). So f-droid.org will ship the
  APK signed with Grapefruit's own key: copies from f-droid.org, GitHub releases and Grapefruit's own
  F-Droid repository all update each other.
  F-Droid only allows this at submission time; it can't be added later.

## Before submitting

1. **Release v5.1 is published** (done 2026-11-03, tagged on commit `769bfa9a`, the commit the
   recipe names). f-droid.org downloads its signed APK
   (`https://github.com/hayliewordsman/grapefruitmusic/releases/download/v5.1/grapefruit-5.1.apk`,
   the recipe's `Binaries:` line) to compare with its own build. If a newer version is out when you
   submit, move the recipe's build to it (versionName, versionCode, commit, CurrentVersion) and
   check it the same way. Later versions are released the same
   way: the **F-Droid repo** workflow builds, signs and tags the commit it runs on when the version
   in `app/build.gradle` goes up.
2. **Wait for the three-month mark.** F-Droid's inclusion policy (quality point 6, which the
   merge-request template points out) asks for at least three months of development before
   inclusion, unless the app already has users or the author maintains other F-Droid apps. This
   repository started on 25 October 2026, so submit from about **25 January 2027**, or say in the
   merge request who already uses it.
3. **Add screenshots** (recommended): put PNGs from a phone in
   `fastlane/metadata/android/en-US/images/phoneScreenshots/` and from a tablet in
   `.../images/tenInchScreenshots/` (named `1.png`, `2.png`, …), and attach a few to the merge request.
4. **Optional:** rename the default branch to `main` on GitHub (Settings › General). F-Droid checks
   for updates by tags, so it doesn't need this, but it reads better to reviewers.

## Submitting

1. Make an account on [gitlab.com](https://gitlab.com) (F-Droid lives there, not on GitHub).
2. Fork <https://gitlab.com/fdroid/fdroiddata> and keep your fork public.
3. In the fork, make a branch (for example `grapefruit-music`) and add this folder's
   `io.github.hayliewordsman.grapefruitmusic.yml` as `metadata/io.github.hayliewordsman.grapefruitmusic.yml`.
   Commit it as `New app: Grapefruit Music & Podcasts`.
4. Push. The fork's pipeline runs F-Droid's checks and a real build; wait for it to pass. If GitLab
   asks for a phone number or card to run it, don't give one: open the merge request anyway and
   say so, and F-Droid's team runs it.
5. Open a merge request into `fdroid/fdroiddata` `master` titled **New app: Grapefruit Music & Podcasts**.
   Its template asks for an introduction and a checklist; a starting point for the introduction is
   below. Tick the reproducible-builds box: it's enabled.
6. Answer the reviewers' questions in the merge request. Once it's merged, the app appears on
   f-droid.org with the next index update, usually within a few days.

After that, each version you tag `v<versionName>` (the F-Droid repo workflow does it when the
version in `app/build.gradle` goes up) is picked up automatically. Tags with a hyphen, like
`v3.3-beta-1`, are skipped.

## Introduction for the merge request

Edit this as you like:

> Grapefruit Music & Podcasts is a small, native music and podcast player for Android 4.2.2 and
> later, made for older phones and tablets that most current players no longer support. It plays
> files on the device and from your own Navidrome, Subsonic and Jellyfin servers, with offline
> downloads, time-synced lyrics (.lrc files, the server, or LRCLIB), podcasts (subscribe by feed or
> find them in Apple's directory, download episodes), internet radio from Radio Browser, and FM radio
> on MediaTek and Rockchip tablets whose built-in radio it can drive. It has Volume Match, Volume
> Boost, a 10-band equalizer, Mono Audio (an energy-preserving mix for one earbud or hearing in one
> ear), Karaoke Mode, which turns down the lead vocal with a 63 KB on-device model trained on CC BY
> 4.0 stems, and Live Mode, which adds a synthesized stadium crowd (see MaintainerNotes for both). It
> brings its own TLS (Conscrypt) so it can still reach today's HTTPS servers on Android 4.2.
>
> Notes for reviewers:
> - The code was written with an AI coding assistant (Claude), directed and tested by the
>   developer.
> - FM radio is optional: on tablets that have one, Grapefruit uses the radio chip's driver or the
>   manufacturer's radio service already on the device; it bundles none of their code.
> - NonFreeNet is declared for podcast search and charts, which use Apple's podcast directory.
> - Reproducible builds are enabled (`Binaries`, `AllowedAPKSigningKeys`). The certificate's
>   name is "Android Debug" because it's the key the app has always been signed with; it's a
>   private release key.
