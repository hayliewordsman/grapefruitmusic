<p align="center"><img src="brand/app_launcher.svg" width="128" alt=""></p>

# Grapefruitgram Messenger

A free-software Telegram client for **Android 4.2.2 (API 17) and later**, in the Grapefruit themes and
following the Grapefruit universal theme. It is built from [Telegram X](https://github.com/TGX-Android/Telegram-X)
(GPL-3.0-or-later), the official alternative Android client, which is built on
[TDLib](https://github.com/tdlib/td), so it has Telegram X's whole feature set: private chats,
groups, supergroups, forums, channels, secret chats, voice and video calls, voice and video messages,
stickers, animated emoji and GIFs, polls, bots and inline bots, chat folders, archived chats,
scheduled and silent messages, live locations, contacts, multiple accounts, passcode lock, proxies,
Instant View, privacy and session settings, custom themes, and the rest.

What it adds or changes, and nothing else:

| | Telegram X | Grapefruitgram Messenger |
| --- | --- | --- |
| Themes | Classic, Blue, Night Blue, Night Black, ... | **Grapefruit Dark** (default), **Grapefruit Light** (default for daytime), **Pitch Black**, **Victory Blue**, first in Settings › Themes; Telegram X's themes are still there |
| Universal theme | – | Follows the Grapefruit Settings companion, like the other Grapefruit apps |
| Typeface | Roboto | Geist (Regular, Medium, SemiBold), with Android's fonts for other scripts |
| Push notifications | Firebase Cloud Messaging | [UnifiedPush](https://unifiedpush.org), or a background connection when there is no distributor |
| Maps | Google Maps | OpenStreetMap through osmdroid (map, dark, terrain) |
| Location | Play services' fused provider | Android's own location providers |
| QR codes | ML Kit, or ZXing | ZXing |
| Updates | Google Play, or Telegram X's channel | F-Droid or this repository's releases (no self-updating) |
| Proprietary code | Firebase, Play services, ML Kit, Play Integrity, reCAPTCHA, Play Billing, Huawei HMS | None |

## Android 4.2.2

Telegram X has a `legacy` build flavor for Android 4.1 to 4.4 (minSdk 16, NDK r23). Grapefruitgram is that
flavor: build `:app:assembleLegacyRelease` with `-PuseLegacyNdk=true`, for ARMv7 and x86, the two ABIs Telegram
X's Android 4.x build supports. Telegram itself is reached through TDLib, which brings its own OpenSSL, so
messaging doesn't depend on Android 4.2's old TLS (the other Grapefruit apps bundle Conscrypt for that). (Newer phones are better served by Telegram X's other
flavors; Grapefruitgram builds only `legacy`, which also installs on them.)

## Themes and the universal theme

`tools/themes.py` makes the four themes from the Grapefruit apps' palette (`maps/.../Brand.java`): each
starts from the closest Telegram X theme, its neutral and accent colours move onto the Grapefruit ramp, and
the roles that make the look (page, header, text, accent, switches, bubbles, unread badges) are pinned to the
exact palette. Colours that mean something (people's avatar and name colours, file kinds, online green,
delete red) are left as Telegram X has them. `tools/test_themes.py` checks the result.

| Universal id | Theme |
| --- | --- |
| 0 Dark | Grapefruit Dark |
| 1 Light | Grapefruit Light |
| 2 Black | Pitch Black |
| 3 Blue | Victory Blue |

On launch and on every return to the app (`BaseActivity.onResume`), `UniversalTheme` reads
`content://io.github.hayliewordsman.grapefruitsettings.theme/theme` (the general `theme` and `revision`
columns, the same contract as Grapefruit Maps). A new revision switches the app to the matching theme, and
turns off automatic night mode if it changed between light and dark, as choosing a theme in Settings does. A
revision is applied once, so a theme picked in the app stays until the next universal selection. Without the
companion nothing changes. The provider is only read; the manifest declares it for Android 11+ package visibility.

## Notifications without Firebase

With a UnifiedPush distributor installed (ntfy, NextPush, Sunup, ...), Grapefruitgram registers with it and
gives Telegram the endpoint as a *Simple Push* token. Telegram's servers then wake the app through the
distributor with an empty message, and the app fetches what's new itself, so no message content goes through
the distributor. (`extension/unifiedpush`, connector 2.5.0: Apache-2.0, minSdk 16, no other dependencies.)

Most UnifiedPush distributors need Android 5 or later. Without one, Grapefruitgram runs a low-priority
foreground service, "Connected to Telegram", which keeps the connection to Telegram open so that new messages
arrive as they do with the app open (`GrapefruitBackgroundService`). It stops by itself once a push service
works. On Android 4.x its notification has no status-bar icon.

## Build

The repository holds a small layer over Telegram X, not a copy of it:

| Path | What it is |
| --- | --- |
| `upstream.properties` | The Telegram X commit Grapefruitgram is built from |
| `patches/*.patch` | Changes to Telegram X's files, one patch per topic, applied in order |
| `overlay/` | New files: the themes, `UniversalTheme`, `OsmMaps`, `GrapefruitBackgroundService`, the FOSS maps and location (`app/src/foss`), the UnifiedPush extension, icons |
| `removed-paths.txt` | Telegram X's Google, reCAPTCHA and Huawei code, deleted |
| `prepare.sh` | Makes a source tree from those (and copies Geist from `grapefruit-ui`) |
| `tools/` | Theme and icon generators, the theme tests, `export-patches.sh` |
| `brand/` | Icon sources (SVG) |
| `fdroid/` | A draft F-Droid recipe |

Requirements: JDK 25, the Android SDK with platform `android-37.2`, build-tools 37.0.0, NDK 23.2.8568313
and CMake 3.22.1, rustup, git-lfs, and about 25 GB of disk. Get Telegram API credentials for Grapefruitgram at
https://my.telegram.org/apps (each client needs its own; Telegram X's must not be used for releases).

```sh
export ANDROID_HOME=~/Android/Sdk TELEGRAM_API_ID=... TELEGRAM_API_HASH=...
messenger/prepare.sh work/messenger
(cd work/messenger && scripts/setup-rust.sh)
(cd work/messenger && ./gradlew :app:assembleLegacyRelease -PuseLegacyNdk=true -Pabi=arm32,x86)
```

`work/` is ignored by git. `prepare.sh --tdlib source` rebuilds TDLib and OpenSSL from their sources instead
of using Telegram X's prebuilt libraries (slow; what F-Droid needs). `--sample-api-id` builds with Telegram
X's sample credentials, marked as a test build, for compile checks only. With `GRAPEFRUIT_KEYSTORE` and
`GRAPEFRUIT_KEYSTORE_PASSWORD` set, release APKs are signed with the permanent Grapefruit key (alias
`grapefruit`); without them they are left unsigned, as F-Droid builds them. Unsigned builds are not
"experimental": notifications and everything else work.

`.github/workflows/messenger.yml` runs the theme tests, checks the patches still apply to the pinned commit and
that no proprietary library is referenced, then builds the APKs and uploads them as the
`grapefruitgram-messenger` artifact. Add repository secrets `GRAPEFRUITGRAM_TELEGRAM_API_ID` and
`GRAPEFRUITGRAM_TELEGRAM_API_HASH` for real builds; without them CI builds a test APK with the sample
credentials and says so.

### Updating Telegram X

1. Check out the current `commit` of `upstream.properties` and run `prepare.sh --no-fetch --tdlib none` on it,
   then commit the result on a branch, one commit per patch (or apply the patches with `git am`-style commits).
2. Rebase that branch onto the new Telegram X commit and fix conflicts.
3. Rerun `tools/themes.py <tree>` (new colours get Grapefruit values too) and copy changed overlay files back.
4. `tools/export-patches.sh <tree>` rewrites `patches/`; set the new `commit` and `version` in
   `upstream.properties`.
5. Check: a fresh `prepare.sh` on the new commit must reproduce the branch exactly.

## Limits

- **Not yet built.** This branch was prepared and checked (the patches reproduce the working tree exactly from
  pristine Telegram X; every osmdroid and UnifiedPush call was checked against the libraries' class files;
  the theme tests pass), but the Gradle build has not been run here. The first build is the CI run of this
  workflow. Nothing has been tried on an Android 4.2.2 device yet.
- **Telegram API credentials** must be registered for Grapefruitgram before any release.
- **Sign-up and login verification.** Telegram sometimes asks apps to prove themselves with Play Integrity,
  SafetyNet or reCAPTCHA before sending a login code by SMS. Grapefruitgram reports these as unavailable, and
  Telegram then offers another way to get the code (usually in another Telegram app, or a later SMS or call).
  Creating a brand-new account may need the official app.
- **Telegram Premium** can't be bought in-app (as in Telegram X); an existing subscription works.
- **Maps:** no satellite view (there is no free worldwide satellite layer for osmdroid), and map tiles come from
  OpenStreetMap's and OpenTopoMap's public servers. Static location previews in chats use Telegram's own map
  images, as in Telegram X.
- **Language detection** for the "translate" button needs ML Kit, so (as in Telegram X on Android 4.x) it isn't
  offered automatically; messages can still be translated.
- **Other languages.** The English text says Grapefruitgram Messenger; translations come from Telegram X's
  translation platform at build time and still say Telegram X.
- **Features Telegram X doesn't have** (it is not a copy of the official Telegram app) aren't here either.

## Licence

GPL-3.0-or-later, like Telegram X and the rest of this repository. Telegram X is © its authors
(tgx-android@pm.me); TDLib is BSL-1.0; osmdroid and the UnifiedPush connector are Apache-2.0; Geist is
SIL OFL 1.1 (`grapefruit-ui/src/main/assets/fonts/Geist-OFL.txt`). Map data © OpenStreetMap contributors (ODbL).
The icon is original artwork (`tools/icon.py`). Telegram is a trademark of Telegram FZ-LLC;
Grapefruitgram Messenger is an unofficial client and is not affiliated with Telegram.
