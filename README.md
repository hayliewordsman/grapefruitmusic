<p align="center"><img src="docs/icon.png" width="120" alt="Grapefruit Music icon"></p>

# Grapefruit Music

A small, native music player for **Android 4.2.2 (Jelly Bean) and later**, styled after the iOS 10 Music app.
It plays music you own: files on the phone, and your own **Navidrome / Subsonic** or **Jellyfin** server,
with downloads for offline listening and time-synced lyrics.

## Features

- **Sources:** music files on the phone, any number of Navidrome/Subsonic and Jellyfin servers, and *Downloaded Music*.
- **Tabs:** Library, Playlists, Radio, Search and Settings. Library and Playlists show the source picked in Library.
- **Excluded folders:** Settings › Excluded Folders hides folders (ringtones, voice messages, …) from
  *On This Device*. Grapefruit's own download folder is always kept out of it.
- **Offline listening:** download albums, playlists or single songs from a server. Downloads keep their cover art
  and lyrics, play with no connection, and can be limited to Wi‑Fi. Interrupted downloads resume.
- **Time-synced lyrics:** lyrics scroll with the song and highlight the current line; tap a line to jump to it.
  Lyrics come from, in order:
  1. a `.lrc` file next to the song (`Song.mp3` + `Song.lrc`)
  2. your server (Navidrome's OpenSubsonic lyrics, Jellyfin 10.9+)
  3. [LRCLIB](https://lrclib.net), a free community database of synced lyrics. You can turn this off in Settings.

  Lyrics are saved after the first lookup, so they work offline too.
- **iOS 10-style design:** large bold titles, album-art grids, a mini player above the tab bar, and a Now Playing
  card that slides up over a blurred copy of the artwork. The artwork shrinks when paused. Pull the card down to close it.
  Lyrics and Up Next show a condensed header (small artwork and title) above the list.
- **Themes:** Settings › Theme: *Grapefruit Light* and *Grapefruit Dark* (in the app icon's peach, cream, ruby and
  coral), *Starlight* (white) or *Pitch Black* (black). Until you pick one, Grapefruit Music uses Grapefruit Light, or
  Pitch Black while the phone is in dark mode (Android 10+).
- **Library grids:** every library page (Playlists, Artists, Albums, Songs, Recently Added) shows cards, two
  across, scrolling down. Turned sideways, the pages scroll sideways instead: the Library's menu is the first
  column, then Recently Added two cards high; swipe right to left and the menu slides away as more albums come
  in. Now Playing pops in as a panel on the right, beside the library (swipe it right or tap outside to close it).
- **Looks the same on every Android version:** Grapefruit draws its own switches, dialogs, menus, text fields,
  messages and fast-scroll thumb instead of using Android's (Holo on 4.x, Material later). Settings switches are
  little grapefruit slices that roll across a grapefruit-gradient track.
- **Playback:** background playback with notification, lock-screen and headset controls, shuffle, repeat (all/one),
  Up Next (drag the ≡ handle to reorder: the other songs slide aside), Play Next / Add to Up Next. The
  notification shows the whole cover (fitted, not cropped) and has an ✕ at the top right that stops playback,
  closes Grapefruit and clears the notification.
- **FM Radio (beta):** the Radio tab shows your presets as station tiles, finds stations (Find Stations) and
  starts the radio; choosing a station opens Now Playing on it, with a dial to drag or tap, seek buttons for the
  next station down or up, 0.1 MHz steps, the station's name, programme type and text from RDS, + to save it as
  a preset, and a Headphones / Speaker switch. Wired headphones are the antenna, as on any phone radio, and its
  sound can still go to the speaker. Not every tablet tells Android when headphones are in, so the radio
  doesn't insist; it stops only for an unplug Android reports. It keeps playing in the background and with the
  screen off. Android has no public radio API: Grapefruit drives the radio chip the way MediaTek's own FM Radio
  app does (the system's `libfmjni.so` and MediaTek's radio MediaPlayer), which is how most budget tablets'
  radios work. Radio › Radio Details shows what Grapefruit found on a tablet whose radio it can't use yet, to
  copy or save to a file.
- **Volume Match:** Settings › Volume Match plays every song at about the same loudness, like Apple's Sound Check.
  Grapefruit measures songs on the phone and downloads in the background (EBU R128, the measure ReplayGain 2 uses),
  uses the loudness your server reports for streamed songs (Navidrome ReplayGain tags, Jellyfin 10.9+
  normalization), and otherwise measures a streamed song after its first play. Loud songs are turned down to
  -16 LUFS; Android's player can't turn quiet songs up, so they play as they are.
- **Volume Boost and Equalizer:** Settings › Sound. Volume Boost (Mild, Medium, Strong, Extreme: +4 to +14 dB)
  makes Grapefruit louder than the phone's maximum, like Viper4Android's eXtra-Loud, with a look-ahead limiter so
  it doesn't crackle. The Equalizer page has ten bands (31 Hz to 16 kHz, ±12 dB) to drag, saved as Custom, and
  presets: Acoustic, Bass Booster / Reducer, Classical, Dance, Deep, Electronic, Hip-Hop, Jazz, Latin, Loudness,
  Lounge, Piano, Pop, R&B, Rock, Small Speakers, Spoken Word, Treble Booster / Reducer and Vocal Booster /
  Reducer. Each band is a peaking filter; because neighbouring bands overlap, their gains are solved together so
  every band's centre lands exactly where its slider is.
- **Karaoke Mode:** Settings › Sound › Karaoke Mode, or the microphone in Now Playing, turns down the lead vocal
  of any song, by as much as the Vocal Removal slider says (in Settings, or the upright slider beside the
  artwork in Now Playing while Karaoke Mode is on: slide up to take more away). A small neural network (about 220,000 weights,
  4-bit, 0.11 MB) runs on the phone, offline, inside Grapefruit's own audio engine: for each 46 ms window of
  sound (a new one every 12 ms) it looks at 96 frequency bands of the middle and sides of the mix, from 46 ms
  back to 23 ms ahead, and estimates how much of each band is lead vocal; that part of the middle is
  subtracted from both channels, so instruments and the stereo picture stay. The sound starts 70 ms later.
  The model is trained on the MUSDB18 7-second excerpts (CC BY 4.0) by `tools/karaoke`. It's read and run
  once in the background before it plays (Android 4's JIT compiles code only after it has run a while), so
  it's fast from the first note.
- **Mono Audio:** Settings › Accessibility. Left and right are mixed (averaged at full precision, rounded once)
  and played in both ears, at the song's own sample rate. Mono alone never needs limiting, so apart from the mix
  the sound is untouched.
- **Grapefruit's own audio engine:** Android 4.2 has no loudness or per-app mono effect, so with any of these on
  Grapefruit decodes and plays the music itself (MediaCodec + AudioTrack); with all of them off, Android's player
  plays it. A song Grapefruit's engine can't play falls back to Android's player.
- **Recently Played:** scroll up in Up Next to see the songs you've played (newest just above Now Playing).
  Tap one to play it again, or Clear the list.
- **Playlist editing:** Edit on a playlist changes its name, description and cover photo (a photo on the phone,
  album art from the playlist, or a picture from another app), and removes songs. Done saves; Cancel or Back
  leaves it as it was. Names, removed songs and (on Navidrome/Subsonic) descriptions are saved on the server;
  cover photos are kept on the phone.
- **Song menu:** the ⋯ button on any song (or a long press) offers Play Next, Add to Up Next, Add to Playlist
  (an existing playlist or a new one, on your server or the phone), Go to Album, Go to Artist and Download.
- **Streaming quality:** stream original files when the phone can play them (MP3, AAC/M4A, FLAC, Ogg Vorbis, WAV),
  otherwise the server converts to MP3. You can also pick a fixed MP3 bitrate.
- **Modern HTTPS on old phones:** bundles [Conscrypt](https://github.com/google/conscrypt) for TLS 1.2/1.3 and a few
  newer root certificates (Let's Encrypt and others) that Android 4.2.2 doesn't have.

## What it doesn't do

Grapefruit Music doesn't play Apple Music, Spotify, Amazon Music or YouTube Music. None of those services let
third-party apps play full songs or store them offline: their downloads are encrypted and tied to their own apps.
To keep music on the phone, use files you own (for example DRM-free purchases) or your own server.

## Install

1. Open the latest successful run of the **Build APK** workflow under the repository's *Actions* tab and download
   the `grapefruit-music-apk` artifact.
2. On the phone, allow *Unknown sources* (Settings › Security) and install `app-release.apk`
   (or `app-debug.apk`).

The app targets Android 4.2.2 and uses install-time permissions (`targetSdk 22`). It installs on Android 4.2–13.
Android 14 and later block apps built for old Android versions; to install there anyway, use
`adb install --bypass-low-target-sdk-block app-release.apk`.

## Connect a server

Settings › **Add a Server…** (or the source picker under *Library*):

- **Navidrome / Subsonic:** address such as `http://192.168.1.10:4533`, plus your username and password.
- **Jellyfin:** address such as `http://192.168.1.10:8096`, plus your username and password.

Plain `http://` addresses work on your home network, and `https://` works with normal certificates.
Self-signed certificates aren't supported.

## Build

Requirements: JDK 17 and the Android SDK (platform 35).

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
```

The APK ends up in `app/build/outputs/apk/debug/`. GitHub Actions runs the same build on every push
(`.github/workflows/build.yml`).

The code is plain Java with no AndroidX or support libraries. Everything uses framework APIs available on API 17.

| Package | What's in it |
| --- | --- |
| `source` | `MusicSource` and its implementations: `LocalSource` (MediaStore), `SubsonicSource`, `JellyfinSource`, `DownloadedSource` |
| `download` | Download queue (SQLite), background `DownloadService`, Wi‑Fi resume |
| `lyrics` | LRC parser and the lyrics lookup and cache |
| `playback` | `Player` (queue + MediaPlayer), `PlaybackService` (notification, lock screen, headset) |
| `net` | HTTP helper, Conscrypt/TLS setup, and a loopback relay that lets MediaPlayer stream over modern HTTPS |
| `ui` | Single activity, pages as fragments, Now Playing card, Grapefruit's own dialogs and widgets (toggle, slider, fast scroll), code-drawn icons |

### Release signing

Release APKs are signed with a permanent key so each version installs over the last. The key is never stored in
the repository. Builds use it when these are set:

- `GRAPEFRUIT_KEYSTORE`: path to the PKCS12 keystore (`.p12`, key alias `grapefruit`)
- `GRAPEFRUIT_KEYSTORE_PASSWORD`: its password

On GitHub, add two repository secrets (Settings › Secrets and variables › Actions): `GRAPEFRUIT_KEYSTORE_BASE64`
(the keystore, base64-encoded) and `GRAPEFRUIT_KEYSTORE_PASSWORD`. Without them, the release APK is signed with
a debug key and won't install over a release-signed copy.

## About

Copyright © 2026 MagicFlute LLC. Developer: Haylie (Telegram [@afishinauto](https://t.me/afishinauto)).
Donate: [paypal.me/fishglub](https://paypal.me/fishglub).
