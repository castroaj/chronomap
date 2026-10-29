# Rebuilds replay full history, detect changes to history by checksum, and never snapshot

A build replays a table type's versions oldest to newest. The strategy below is binding for v0.1.

## Readiness budget

A cold start reaches readiness in about 2 minutes at ~5M keys and 2 years of history. Instances sit behind a readiness probe and restart in a rolling fashion. The timeline prototype replayed 300k keys x 730 versions in ~10 s, so the benchmark suite must confirm the budget at 5M keys.

## Startup

- **`build()` blocks** until the first full replay finishes and throws if the source fails. A `buildAsync()` can be added later without breaking callers.
- **Parse-ahead pipeline.** A thread pool reads and parses versions ahead of time. One thread diffs and appends them in strict oldest-to-newest order, so replay stays deterministic. Key-partitioned replay is deferred until profiling shows the diff step is the bottleneck.
- **Optional retention window**, e.g. `retain(Duration)`, off by default. A build loads only versions inside the window. The oldest retained version is the baseline, and earlier as-of dates clamp to it (`BEFORE_OLDEST`, see ADR 0002). The window is applied at build and rebuild time only; a running instance never trims. Versions outside the window are neither loaded nor checksummed.

## Changes to history

A **change to history** is any of: a version dated before the refresh cursor (late), a loaded version whose checksum differs (edited), or a loaded version missing from the listing (removed).

- Each poll compares the listing against the loaded set. If anything differs it logs a warning naming each affected version and runs **one** rebuild, whatever the mix.
- The rebuild runs in the background and swaps atomically. Readers keep the old state until the swap. Peak heap is ~2x during a rebuild, within the target from #7.
- `onLateVersion(REBUILD | IGNORE | THROW)` sets the policy for late versions. `REBUILD` is the default. Edits and removals always rebuild.
- A public `rebuild()` method uses the same code path.
- **If a rebuild fails** the old state keeps serving, the error is logged, and a failure counter increments. Retries back off exponentially, capped, so a persistent failure does not hit the datastore on every poll.

## Checksum in the `VersionSource` contract

This amends the two-method `VersionSource` from #8: every entry returned by `list(after)` carries a **required** per-version checksum, next to its `versionRef`.

- **JDBC source:** a configurable checksum column in the versions table, written by the user's ingestion service.
- **CSV directory source:** SHA-256 of the file contents, cached against file size and modified time so a poll re-hashes only new or changed files.
- `list` must report loaded versions as well as new ones, so the comparison above is possible. How the listing is bounded is a detail for the implementation breakdown.

The datastore is always trusted. The checksum detects accidental or deliberate edits and is not a security check. Protecting the data is the user's job, and the docs say so.

## Snapshots are a future feature

Persisting built timelines to disk to speed restarts is out of v0.1 as a whole concept. The snapshot format would become a public contract, with versioning, staleness and corruption to handle. The `VersionSource` contract and the timeline model must not rule snapshots out later.

## Considered alternatives

- **Snapshot-based rebuild.** Rejected for v0.1: the biggest startup win, but the hardest to reverse (see above).
- **Key-partitioned parallel replay.** Deferred: more complex, and parsing is the likelier bottleneck.
- **Treat versions as immutable and only offer a manual `rebuild()`.** Rejected: silent stale data after an edit is worse than a logged rebuild.
- **One checksum for the whole table type.** Rejected: it changes on every legitimate append and cannot name the edited version.
- **Optional checksum.** Rejected: edit detection would silently turn off for some sources.
- **Ignore or silently skip late versions.** Rejected as the default: timelines stay consistent but the data is silently incomplete. Still available as `IGNORE`.
- **Fail lookups after a failed rebuild.** Rejected: the old state is complete, only possibly stale, and the freshness metrics (#11) expose staleness.
- **Trim the retention window continuously.** Rejected: it mutates live timelines and fights the wait-free readers from #7, for little gain at low daily churn.
- **Asynchronous `build()`.** Rejected for v0.1: it gives `get` an unclear not-ready meaning.
