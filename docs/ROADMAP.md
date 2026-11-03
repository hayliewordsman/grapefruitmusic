# Grapefruit roadmap

Ideas and plans for Grapefruit TV and Grapefruit Music & Podcasts, written down so they aren't lost.
Nothing here is being built until it's picked up for a release. Add to it any time ("add X to the
roadmap for 2.1"); move an item into a release's section when it's planned, and out of this file
when it ships (its release notes say what shipped).

## Grapefruit TV

### Next release (unplanned)

_Nothing planned yet._

### 2.5: picture-in-picture, mini player, background play

Feasible on every supported Android version, with picture-in-picture where Android has it.

- **In-app mini player** (all versions, Android 4.2+): leaving the watch page keeps the channel
  playing in a floating pill above the tabs, as Music's mini player, with the picture in a small
  rounded window; tap to go back to full size, swipe or ✕ to stop. Needs the player to belong to
  the activity rather than the watch page, and the picture drawn on a TextureView (a SurfaceView
  can't move or round its corners smoothly on older Android). ExoPlayer 2.18.7 switches surfaces
  without restarting the stream.
- **Picture-in-picture** (Android 8.0+, and Android TVs that support it): Home or the PiP button
  shrinks the channel into Android's floating window over other apps, with play/pause and a
  "next channel" action; auto-enter on Home from Android 12. Manifest:
  `supportsPictureInPicture`, `resizeableActivity`. Android 4.2–7 phones get the in-app mini player
  and background play instead.
- **Background play** (all versions): keep the sound going with the screen off or in another app, as
  Music does: a foreground media-playback service, a notification with stop and next/previous
  channel, and lock-screen/headset controls (MediaSession on 5.0+, RemoteControlClient on 4.x, the
  same split as Music's `compat/`). The video track is switched off while nothing shows it, to save
  data. Needs the FOREGROUND_SERVICE and FOREGROUND_SERVICE_MEDIA_PLAYBACK permissions and, on
  Android 13+, the notification permission prompt. Settings: Background Play on/off.
- Watch-outs: IPTV services often limit simultaneous connections, so the mini player, PiP and
  background play must hand the one stream over, never open a second; Keep Screen On and captions
  carry across; Android TV has no background play (TVs stop media when leaving the app), only PiP.

### 3.0: On Demand tab (for IPTV sources that offer it)

Feasible for server logins; partly for playlists.

- **Where it comes from:** the Xtream Codes API that Your Sources' TV logins already use also lists
  films and series: `get_vod_categories`, `get_vod_streams`, `get_series_categories`, `get_series`
  and `get_series_info` (seasons and episodes), with titles, posters, plots and durations. Streams are
  `/movie/<user>/<pass>/<id>.<ext>` and `/series/<user>/<pass>/<id>.<ext>`.
- **The tab reveals itself** only when a connected source returns films or series; it stays hidden
  with built-in channels only or with playlists that have none. M3U playlists sometimes mix films
  in with channels (by group or file extension); those can be sorted into On Demand on a best-effort
  basis, but there's no reliable marker, so logins are the main path.
- **Browsing:** Films and Series chips, categories, posters on cards (Music's album-card layout),
  search, Continue Watching with resume points, and a series page with seasons and episodes.
  Still no live programme guide.
- **Playback:** films are files (MP4, MKV, TS) with seeking, so the player gains a scrubber, pause and
  ±10 s skips; ExoPlayer plays MP4/MKV/TS already. Subtitles in the file show through the existing
  captions.
- Watch-outs: much IPTV video is HEVC with AC3/DTS sound, which older phones (Jelly Bean especially)
  can't decode; Grapefruit TV would show "This device can't play this video's format" rather than fail
  silently. ExoPlayer's FFmpeg audio extension could add AC3/DTS, but it's a native build with codec
  patent questions, so it would be its own decision. Grapefruit TV still provides no content: this
  only shows what the viewer's own service offers.

### 3.5: downloads for offline playback

Feasible for On Demand films and episodes (not live channels).

- **Downloading:** a film or episode downloads as its file, resuming after a dropped connection, in
  a foreground download service with a progress notification, as Music's DownloadService does.
  Wi-Fi only by default; a Downloads view in On Demand; delete, and space used, in Settings. Saved
  to the app's storage, or an SD card where Android offers one (4.4+).
- **Playing offline:** downloaded titles play with no connection, resume where they stopped, and
  show their subtitles.
- Watch-outs: only where the viewer's service allows downloads (many limit simultaneous
  connections, so a download may count as one, and some forbid it in their terms); IPTV
  providers' HLS (`.m3u8`) films would use ExoPlayer's DownloadManager instead of a plain file
  download; storage is small on old devices, so Grapefruit TV would check free space first. Live channels
  aren't downloaded or recorded.

### Ideas

- **Regional FAST lineups through official partnerships.** Pluto TV, Samsung TV Plus, Plex, Roku,
  LG and Xumo license channels by country; ask them, or channel owners (Blue Ant, Gusto TV, BBC
  Studios, Cineflix) and distributors (Amagi, Wurl), for official access. Drafts:
  `docs/tv/partner-requests.md`. Pluto refuses honestly identified third-party clients, so no
  Pluto without an agreement.
- **CBC local news channels**, if CBC offers them somewhere without a CBC Gem sign-in.
- **Australian entertainment channels** from an official, public source (none found so far).
- **Mayday: Air Disaster worldwide** by asking Cineflix directly.
- **A real-device check of Your TV Apps and Your Sources** on Jelly Bean, a Pixel and Android TV.

### Decided against (and why)

- Signing in to Crave, Sling, Hulu Live, Fibe TV, Ignite TV, Xfinity and similar services inside
  Grapefruit TV: their live TV is DRM-protected and only plays in their own apps; getting around
  that is illegal and against their terms. Your TV Apps opens those apps instead.
- Posing as another service's own app (Pluto's web player) to get past its refusal.
- A programme guide (EPG) for now: channel names only.

## Grapefruit Music & Podcasts

### Next release (unplanned)

_Nothing planned yet._

### Ideas

_None yet._
