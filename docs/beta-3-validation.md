# 4.0 beta 3 (versionCode 35)

## Reproduced issue

A live The Daily enclosure crossed six redirects: dts.podtrac.com → pdst.fm → pfx.vpixl.com → pscrb.fm → tracking.swap.fm → nyt.simplecastaudio.com → final MP3. Android's built-in downloader follows at most five. Automatic redirect behavior also varies by Android version and does not cover all HTTP-to-HTTPS cases.

A separate compatibility defect affected offline downloads on Jelly Bean: system DownloadManager runs in a different process and does not use Grapefruit's Conscrypt provider or bundled root certificates. Fetching RSS successfully in the app therefore did not establish that the system could fetch its audio.

## Change

Podcast playback and new podcast downloads use `PodcastAudio` for up to 20 explicit redirects, with timeouts, Range preservation, identity encoding and TLS verification. HTTP-to-HTTPS upgrades and relative locations are supported; loops, non-web destinations, embedded credentials and HTTPS downgrades are rejected. Sensitive request headers are not forwarded across origins. The original enclosure remains in the database, so expiring signed CDN destinations are resolved afresh. Other music-server and live-radio transport paths are unchanged.

New downloads run in a foreground service using the app's TLS stack. Progress, byte totals, Wi-Fi waiting, cancellation and retry remain available in the app and notifications. Notifications open Episode Downloads. Connectivity changes while the process is running and reopening the app resume paused work; modern Android may defer background service starts. Interrupted partial transfers restart from the original enclosure rather than appending bytes from potentially changed ad-inserted audio.

Partial files use a per-attempt generation and are never presented as complete. Empty/truncated transfers fail. Cancellation/retry invalidates the prior generation before a previous worker can publish its file. A partial transfer is renamed to the completed destination only after integrity checks. Pending downloads are durable; process restarts recover unfinished transfers. Modern data-sync foreground-service timeouts stop the service and leave the work recoverable.

## Data preservation

Database version 5 adds transfer-state columns to episodes. Existing subscriptions, episode identity, legacy system-download IDs and completed files remain intact. Legacy completed downloads still use their original provider records. Failed legacy downloads switch to the new app-managed path when retried. There is no automatic redownload of the existing catalog or deletion of completed episodes.

The actual CREATE/ALTER SQL was executed with SQLite for a fresh table and a version-4 episode containing an existing download ID; the metadata and ID survived with correct defaults for the new columns. This verifies SQL/data preservation, not an Android framework migration smoke test.

## Live audio verification

The shared production helper successfully fetched a 32,768-byte range (HTTP 206) from The Daily and downloaded the complete MP3 (HTTP 200, 35,748,619 bytes, matching Content-Length). ffprobe identifies MP3, 44.1 kHz stereo, approximately 37m14s; ffmpeg decoded the first three seconds without errors. These were host-side requests using the same redirect/copy implementation, not Android 4.2.2 device playback.

Unit tests cover six redirects, HTTP-to-HTTPS and relative redirects, cross-origin credential stripping, redirect loops/bounds, byte-range preservation, known/unknown content lengths, truncated/empty bodies and cancellation. Build, unit-test and Android lint results are recorded with the delivered artifact. API 17, target API 36, application ID and signing identity are retained. Publishing workflows are unchanged; no merge or release publication is performed.

On-device Jelly Bean MediaPlayer, notification, background-service and process-death checks still need confirmation on the affected device. Source review and host-side tests must not be represented as that confirmation.

Local results: 135 unit tests passed; both build variants and Android lint passed with zero lint errors.
