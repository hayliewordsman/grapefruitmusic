# Provisional 2026 acknowledgements snapshot

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
