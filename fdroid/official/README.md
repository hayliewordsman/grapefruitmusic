# Getting Grapefruit Music into f-droid.org

f-droid.org builds every app itself from its source, following a build recipe kept in its
[fdroiddata](https://gitlab.com/fdroid/fdroiddata) repository on GitLab. The recipe for Grapefruit
Music is [`io.github.hayliewordsman.grapefruitmusic.yml`](io.github.hayliewordsman.grapefruitmusic.yml)
here; submitting means proposing that file to fdroiddata.

## What's ready

- **Free licence:** GPL-3.0-or-later (`LICENSE`), shown in Settings › About with a link to the source.
- **Own app ID:** `io.github.hayliewordsman.grapefruitmusic`, from the GitHub Pages address.
- **Store listing** in `fastlane/metadata/android/en-US`: name, summary, description, icon and
  changelogs.
- **The recipe builds with F-Droid's own tool.** `fdroid build` (fdroidserver 2.4.5, Gradle 8.14.3,
  JDK 21) builds version 3.2.2 from commit `7c1cfe27`, and `fdroid lint` and `fdroid rewritemeta`
  are clean. F-Droid's scanner finds no non-free code, trackers or extra signing blocks. The one
  binary it flags, Karaoke Mode's model `app/src/main/assets/karaoke.bin`, is let through with
  `scanignore` and explained under `MaintainerNotes`, as other apps that ship model data do. The
  unit-test fixtures are removed before the build (`scandelete`).
- **Reproducible builds.** The same commit builds byte-for-byte the same APK on GitHub Actions as in
  an F-Droid-style build. `fdroid build` of the recipe downloaded the APK of GitHub release v3.2.2,
  compared its own build with it and accepted it ("compared built binary to supplied reference
  binary successfully", checked on 2026-10-29). So f-droid.org will ship the APK signed with Grapefruit's own key: copies from
  f-droid.org, GitHub releases and Grapefruit's own F-Droid repository all update each other.
  F-Droid only allows this at submission time; it can't be added later.

## Before submitting

1. **Release v3.2.2 is published** (done 2026-10-29, tagged on commit `7c1cfe27`, the commit the
   recipe names). f-droid.org downloads its signed APK
   (`https://github.com/hayliewordsman/grapefruitmusic/releases/download/v3.2.2/grapefruit-music-3.2.2.apk`,
   the recipe's `Binaries:` line) to compare with its own build. Later versions are released the same
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
   Commit it as `New app: Grapefruit Music`.
4. Push. The fork's pipeline runs F-Droid's checks and a real build; wait for it to pass. If GitLab
   asks for a phone number or card to run it, don't give one: open the merge request anyway and
   say so, and F-Droid's team runs it.
5. Open a merge request into `fdroid/fdroiddata` `master` titled **New app: Grapefruit Music**.
   Its template asks for an introduction and a checklist; a starting point for the introduction is
   below. Tick the reproducible-builds box: it's enabled.
6. Answer the reviewers' questions in the merge request. Once it's merged, the app appears on
   f-droid.org with the next index update, usually within a few days.

After that, each version you tag `v<versionName>` (the F-Droid repo workflow does it when the
version in `app/build.gradle` goes up) is picked up automatically. Tags with a hyphen, like
`v3.3-beta-1`, are skipped.

## Introduction for the merge request

Edit this as you like:

> Grapefruit Music is a small, native music player for Android 4.2.2 and later, made for older
> phones and tablets that most current players no longer support. It plays files on the device and
> from your own Navidrome, Subsonic and Jellyfin servers, with offline downloads, time-synced lyrics
> (.lrc files, the server, or LRCLIB), internet radio from Radio Browser, and FM radio on MediaTek
> and Rockchip tablets whose built-in radio it can drive. It has Volume Match, Volume Boost, a
> 10-band equalizer, Mono Audio (an energy-preserving mix for one earbud or hearing in one ear),
> and Karaoke Mode, which turns down the lead vocal with a 61 KB on-device model trained on freely
> licensed music (see MaintainerNotes). It brings its own TLS (Conscrypt) so it can still reach
> today's HTTPS servers on Android 4.2.
>
> Notes for reviewers:
> - The code was written with an AI coding assistant (Claude), directed and tested by the
>   developer.
> - FM radio is optional: on tablets that have one, Grapefruit uses the radio chip's driver or the
>   manufacturer's radio service already on the device; it bundles none of their code.
> - Reproducible builds are enabled (`Binaries`, `AllowedAPKSigningKeys`). The certificate's
>   name is "Android Debug" because it's the key the app has always been signed with; it's a
>   private release key.
