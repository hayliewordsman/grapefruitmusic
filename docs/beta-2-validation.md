# 4.0 beta 2 rebuild (versionCode 34)

The navigation bar now contains Library, Radio, Podcasts, Search and Settings. Playlists is still available in Library. Existing numeric tab identities are retained; restored state for the removed tab falls back to Library.

## Button spacing audit

Reviewed shared controls and their callers across Library, artist/album/song/playlist browsing, album and playlist details, Radio (FM and internet), station search, Podcasts and its subpages, unified Search, Settings and its grouped subpages, Add Server, Add Songs, tag editing, dialog cards/sheets, and Now Playing.

Changes:

- Shared text/icon pills allow labels to wrap, retain centered content and have a minimum 48dp height. Header actions retain a 48dp target and allow wrapping.
- Library, Radio, album and podcast action rows use consistent 8dp gaps and equal-height backgrounds. Replaced raw-pixel margins in the podcast row with dp.
- Podcast show actions and offline buttons have explicit vertical gaps. Search controls have consistent full-width layout and spacing.
- Dialog actions wrap, grow with text, and stack vertically if either label will not fit its share of the row. Menu Cancel actions grow rather than clipping larger text.
- Settings uses inset rounded rows with 8dp spacing; removed the obsolete section divider lines.
- The Add Server selector keeps equal-height selection backgrounds and wrapped labels. Podcast evidence has its own full-width action.
- Now Playing retains the icon-only 48dp bottom row and left-aligned metadata padding.

## Podcast download progress

The app uses DownloadManager's ongoing and completed system notifications. System notification settings still control whether they appear. It does not duplicate the download with a second transfer service.

Episode Downloads and podcast show pages refresh provider snapshots every 1.5 seconds while visible. Downloads are queried in bounded batches, rather than one provider request per episode. A refresh reads local state without fetching RSS again. Percentages and byte totals appear when the server supplies a size; unknown sizes use an indeterminate bar. Queued, downloading, waiting for Wi-Fi/network/retry, completed, failed and missing-system-record states are distinguished. Active downloads appear first on Episode Downloads. Menus offer cancel, remove and retry as appropriate; successful downloads whose files are missing can be downloaded again.

Download IDs remain in the existing database and are reconciled with DownloadManager after process restart. No schema migration or change to music download behavior is required.

## Verification and limits

Debug/release builds, unit tests and Android lint are run before signing through the existing Build APK workflow. Minimum API 17, target API 36, application ID and signing identity are preserved. Production publishing workflows are unchanged; the development branch does not publish an F-Droid repository or GitHub release.

The spacing audit is based on source/layout review and builds. It is not a visual smoke test on every screen or device; the cloud emulator was previously unable to complete a reliable runtime session. Download progress formatting has tests for known and unknown sizes, overflow, waiting states, completion, missing records and failure reasons. Real notification/provider behavior and rotation during active downloads still need device testing.

Local validation for build 34: 127 unit tests passed, zero lint errors, and both debug and release APKs assembled successfully. The release manifest reports versionName `4.0-beta-2`, versionCode `34`, minSdk `17` and targetSdk `36`.
