# Grapefruit roadmap

Ideas and plans for Grapefruit TV and Grapefruit Music & Podcasts, written down so they aren't lost.
Nothing here is being built until it's picked up for a release. Add to it any time ("add X to the
roadmap for 2.1"); move an item into a release's section when it's planned, and out of this file
when it ships (its release notes say what shipped).

## Grapefruit TV

### Next release (unplanned)

_Nothing planned yet._

### 3.0: Grapefruit TV & Movies (in 3.0 beta 1)

Built in 3.0 beta 1 (see docs/tv/releases/3.0-beta-1.md):
- **Movies tab:** hand-picked films that are free and legal to watch (public domain in the United States, or
  Creative Commons), streamed and downloaded from the Internet Archive (tools/tv/movies.py). Nothing discriminatory.
- **On Demand tab** (shows only when an IPTV source has films or series): films, series with seasons and
  episodes, the source's groups, and **downloads for offline playback** (resuming, Wi-Fi only by default).
- **Guide (EPG) in Live TV:** programmes from the source's own guide (Xtream short EPG, a playlist's XMLTV);
  elsewhere, hour blocks on news channels and half hours on others, labelled "Program information currently
  unavailable".
- **DVR and the Recordings tab** (All, Finished, Scheduled): records free channels and IPTV channels on the
  device, from the guide, the channel page or a manual schedule, in a foreground service woken by exact alarms
  (and set again after a restart), so recordings happen with the app in the background or closed. Streams at a
  Time keeps watching, recording and downloading within an IPTV plan's limit.
- The in-app floating player from 2.5 beta 1 stays; background play is dropped.

Still to come in 3.0 betas:
- Watching a recording while it's still recording (time-shift), and a series recording rule ("record every
  episode").
- Recording streams whose sound is kept separately (HLS with separate audio renditions).
- Posters on a grid for Movies and On Demand, and more films for Movies.
- A real-device check of recordings with the screen off on Jelly Bean, a Pixel and Android TV.

### Later

- **Android picture-in-picture** (8.0+, and Android TVs that support it): Home shrinks the channel into
  Android's own floating window over other apps. Android 4.2–7 keep the in-app floating player.

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
- Background play (sound with the screen off or in another app): dropped for 3.0; the in-app floating
  player stays.
- Recording or saving streams that are encrypted or DRM-protected: not done.

## Grapefruit Music & Podcasts

### Next release (unplanned)

_Nothing planned yet._

### Ideas

_None yet._
