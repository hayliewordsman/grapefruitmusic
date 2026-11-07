# Provisional 2026 acknowledgements snapshot

## Independently refreshed feed

Blue 1.1 checks `https://raw.githubusercontent.com/hayliewordsman/grapefruitmusic/blue-roll-call-data/roster.json`
whenever Victory Roll Call opens. It displays a validated private cached copy (or the included
offline snapshot) while refreshing, saves valid results atomically, and keeps the last good copy
if the service is unavailable or the response is invalid. Older responses cannot replace newer
cached data. The closing paragraph remains app copy and appears only after list data has loaded.

`.github/workflows/blue-roll-call.yml` checks the public result sources hourly and on manual
dispatch. It publishes only `roster.json` on the separate `blue-roll-call-data` branch; no APK,
app version, release tag, store listing or F-Droid repository is changed. The source manifest and
minimal public identity references are under `feed-seed/`. Current-roster CSVs are reduced to the
fields used for office/party/term matching. Raw research pages, contact fields and signing or music
inputs are not published in the feed. The runtime payload excludes research-only fields.

Run `refresh_feed.py --cache <private scratch> --output <roster.json>` with Beautiful Soup and
PyYAML installed. A broad source outage or unexpected coverage loss refuses publication, leaving
the existing public feed in place. Public identity evidence remains independently sourced and is
applied only to a verified roster member. Its review date is carried separately from result checks.
This feed is a provisional projected roster, not certification or an exact count of uncalled seats.

`blue-victory/src/main/assets/acknowledgements/roster.json` is an offline, incomplete snapshot
as of **November 5, 2026** (America/Toronto). It covers Democrats projected to serve after the
2026 election: continuing and elected federal/state legislators, plus Democratic governors-elect
(including incumbents winning another term). It is not certification and does not resolve a
seat by assuming that an incumbent or vote leader will win. Unresolved contests include seats
that may ultimately elect Republicans; they are coverage gaps, not a count of missing Democrats.
The author-supplied closing paragraph is app editorial copy, separate from the roster evidence.

## Evidence rules

- Ballotpedia's 2026 **general-election** green check, general-election winner row, or exact
  `Won General` status qualifies a projected winner. Old-year and primary results are excluded.
- NBC New York's public Decision Desk embed is also used. Both the race and the candidate must
  explicitly be `Projected Winner`, with the candidate winner marker set. Leads are excluded.
  Source: https://www.nbcnewyork.com/elections/live-results-2026-ny-nj-ct-midterm-elections/6563336/
- The ABC generic state-result widgets checked for this build contained a **2020** configuration.
  They are excluded. A current page/JavaScript build date does not establish an election year.
- Continuing federal terms come from `unitedstates/congress-legislators` term-end dates.
  State continuing terms use Open States current rosters only for off-cycle or explicitly
  non-contested staggered seats. Full-chamber elections never get incumbent-based holdovers.
  Known winners moving to Congress or governor are excluded from their previous continuing seat.
- Hawaii primary winners are included as elected outright only when the 2026 general election
  is explicitly canceled and the primary table has exactly one advancing winner for that seat.
- Nebraska has nonpartisan ballots. Continuing-term dates and registered party are checked
  separately; new called winners need their own party evidence. Unverified party is a gap.
- Multi-member districts keep separate people and mark partially resolved contests when needed.
  Democratic-Farmer-Labor and Democratic-NPL affiliations retain their party codes.
- District result pages supplement incomplete statewide indexes. A result must match the exact
  office, district and November 3, 2026 general election. Explicit winning-round markers qualify
  ranked-choice winners. Special Senate general elections are included; primaries are excluded.
- The public Movement Voter Project [state legislative tracker](https://movement.vote/election-results/2026/races/state-legislatures/)
  and [House tracker](https://movement.vote/election-results/2026/races/us-house/) supply additional
  AP-backed projections. A candidate must have both an explicit winner marker and a `Called by AP`
  statement, and independently match the name, office, district and party on the 2026 general
  ballot. Its [methodology](https://movement.vote/election-results/2026/how-we-count/) identifies AP
  as the results provider. Uncalled leads and partially called multi-member contests stay open.

Sources are attached to each person and unresolved contest. `researchSources` includes URLs and
SHA-256 digests of cached source pages or public data extracts. Only names, public offices,
party/state, election/term status and verified pride labels are shipped. Raw CSVs and other
research caches stay under ignored `work/`; unused contact fields are never bundled.

## Identity flags

The Out for America public self-identification map supplies documented labels. Exact normalized
name/nickname, state and office level must match; namesakes in Congress and a state chamber do
not inherit each other's flags. Candidate-authored Victory Fund biographies and official/campaign
self-descriptions supplement newly elected members and governors. Source URLs accompany every
flag. The Dina Nina campaign source was browser-verified and cached as a three-word excerpt.

Explicit gay/lesbian = rainbow; bisexual = bisexual; transgender = transgender. Multiple flags
are allowed. Queer, pansexual, nonbinary, pronouns, partners and appearance alone do not imply any
of these labels. An absent flag means no verified matching label in this snapshot. Flag stripes
are compiled Canvas drawings and require no device emoji font or network connection.

## Rebuilding and validation

The builder accepts a local public-source cache and `source-manifest.json` with `states` and
`sources` entries (`state`, `kind`, `file`, `url`, `ok`). Kinds are `current`, `state_senate`,
`state_house`, `us_house`, and election-research pages. Supplemental file names and URLs are
listed in the builder. Updating an election snapshot requires fresh source review, not a date change.

```
python3 -m pip install beautifulsoup4 PyYAML
python3 tools/acknowledgements/build_snapshot.py \
  --snapshot-dir work/blue-grapefruit/acknowledgements \
  --output blue-victory/src/main/assets/acknowledgements/roster.json
python3 -m unittest discover -s tools/acknowledgements -p 'test_*.py'
```

Parser regression tests check uncalled leaders, primary/old-year exclusion, at-large results,
continuing-term scope, district labels and identity namesakes. Android tests load the shipped
snapshot, reject unsourced flags/candidate statuses, render flag stripes and verify that the
closing paragraph is the final list item, absent during loading and above the results, and
appears after scrolling to the end. No real-device playback claim is implied.

## November 5 refresh

All 242 result/term/reference pages used by the previous snapshot were checked again, along with
the public identity directory and new candidate-authored biographies. The congressional YAML is
fetched from its raw content URL; its GitHub viewing page remains the human-readable citation.
`source-manifest.json` records `snapshotDate`, retrieval times and content digests. The builder
uses that explicit date, not the machine clock or an unverified date-only edit.

`identity-evidence.json` supplies the seven new direct self-identification matches. Each match
requires name, state, chamber and district, a cached explicit public statement, and a public URL.
Documented aliases require name sources. Supplements label only independently verified roster
members; they cannot promote a primary winner or an uncalled candidate. Short browser-verified
excerpts are used where a normal page download is unavailable, and are marked as excerpts.

The refreshed roster has 18 documented transgender members across the existing legislative scope,
including reelected and continuing-term members. Broader nonbinary labels alone are not treated
as a transgender label. Local boards, treasurers and other offices outside the requested roster
scope are not added merely because a transgender candidate won one of those races.

## Beta 7 projected-roster review

The November 5 source batch was checked again (246 successful page/data fetches), followed by
1,258 district-level result pages covering 1,292 of the previous snapshot's gap records. A
successful source fetch does not establish a winner: the majority of these pages still lacked
an explicit call. Public AP-backed state and federal trackers supplement the result tables.
The snapshot remains provisional and incomplete. Gaps include results awaiting our verification;
they are not an exact count of genuinely uncalled seats or of missing Democratic members.

The same-page closing paragraph is created lazily as the roster's final adapter row. It is not
an always-present ListView footer: it cannot appear while the background loader is still empty,
and a filtered name/state search shows only the search results. Clearing the search restores it
at the end of the full roster.

## 1.1 independent results feed

`refresh_feed.py` publishes a compact runtime snapshot independently of all APKs. The
`blue-roll-call.yml` workflow checks sources hourly and updates only `roster.json` on the
`blue-roll-call-data` branch. It cannot build, sign, tag or release an app. Blue requests the
raw HTTPS feed whenever Victory Roll Call opens, validates it off the UI thread, and saves it
atomically for offline use; the APK includes a first-use fallback snapshot.

Install Beautiful Soup and PyYAML, then run:

```sh
python -m unittest discover -s tools/acknowledgements -p 'test_*.py'
python tools/acknowledgements/refresh_feed.py --cache work/roll-call-sources \
  --previous /path/to/published-roster.json --output work/roster.json
```

Empty HTTP 200 responses, HTTP 202 challenge responses, malformed pages and failed requests
are not fresh evidence. The updater first attempts the full source batch. If most result sites
are unavailable, all four public AP-backed trackers (state legislatures, House, Senate and
governors) must still be freshly accessible and expose matching 2026 contests. Their explicit
calls are checked against `feed-seed/general-ballots.json`; candidate identity, party, state,
office and district must match. Complete replacement calls remove earlier projections,
including when a Republican now wins. A withdrawn call removes projections that depended on
that tracker and restores an unresolved notice. Partial multi-member calls cannot close a gap.

Previously verified results outside refreshed coverage remain. The displayed note states that
some sources are unavailable; a new check date does not imply a complete or certified roster.
Missing tracker coverage or a large unexplained roster loss prevents publication, leaving the
last good feed available. Pride labels remain limited to source-backed public self-description;
the preserved identity evidence follows exact member IDs and never establishes an election win.

For 1.1 the revised closing paragraph is the last loaded row in both full and filtered results.
Raw research and unused contact fields are excluded from the public runtime JSON. Publisher
diagnostics retain only public result-source metadata for seven days after a failed run.
