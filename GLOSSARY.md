# Chronomap

A date-aware reference-data cache: it answers "what was true for this key on this date?" from dated versions of reference data, so events can be validated against the data as it was when they happened. (Working name; final name is undecided.)

## Language

**Table type**:
A kind of reference data that is published repeatedly, such as a price list or a phone-routing table. It is the unit a cache is built for.
_Avoid_: Dataset, table, feed

**Version**:
One dated, full copy of a table type, as stored in the datastore.
_Avoid_: Publication, snapshot, file, dump

**Dated change**:
A record that a key was created, updated, or deleted as of a given version's date. A deletion is a tombstone.
_Avoid_: Delta, diff, event

**Timeline**:
The ordered dated changes for one key.
_Avoid_: History, log

**As-of lookup**:
Asking for a key's state on a given date.
_Avoid_: Point-in-time query, time travel

**Range row**:
A row whose last key component is an inclusive integer interval (`lo` to `hi`) rather than a single value, identified by its prefix and `lo`. Only range-keyed table types have them.
_Avoid_: Range record, interval

**Prefix**:
The key components before the range in a range-keyed table type, matched exactly.
_Avoid_: Partition, bucket

**Range-keyed table type**:
A table type whose key ends in a range. It is declared as such at build time and never mixes with exact keys. Reserved, not built, in v0.1.
_Avoid_: Interval table

**Provenance**:
What a lookup reports about what asserted its answer: the outcome, the date and reference of the version that introduced the answer, and any clamp. Returned by `lookup`, never by `get`.
_Avoid_: Metadata, audit trail, lineage

**Clamp**:
Moving an as-of date to the oldest or newest version when it falls outside them. Provenance reports which side (`BEFORE_OLDEST` or `AFTER_NEWEST`) and how many days the date was moved.
_Avoid_: Fallback, extrapolation

**Rebuild**:
Replaying a table type's versions from the datastore into a fresh cache and swapping it in atomically. It runs at startup, and again whenever a poll finds a change to history or the user calls `rebuild()`.
_Avoid_: Reload, refresh (a refresh appends new versions incrementally)

**Change to history**:
A late version (dated before the refresh cursor), an edited version (its checksum differs from the loaded one), or a removed version (loaded but no longer listed). Any of them triggers one rebuild per poll.
_Avoid_: Correction, backfill

**Retention window**:
An opt-in limit on how much history a build loads. It applies at build and rebuild time only.
_Avoid_: TTL, expiry

**Baseline version**:
The oldest version a build loads. With a retention window it is the oldest version inside the window; as-of dates before it clamp to it.
_Avoid_: Base snapshot, initial load

**Checksum**:
A per-version value supplied by the `VersionSource` to detect edits to a loaded version. It is not a security check; the datastore is trusted.
_Avoid_: Hash, signature

**Freshness lag**:
How far the data is behind, reported two ways: the days between today and the newest loaded version's date, and the seconds since the last successful poll. The first reveals a datastore that stopped publishing; the second reveals a stuck poller.
_Avoid_: Staleness, delay

**Heap estimate**:
Chronomap's own estimate of the memory a table type's cache holds: the timeline structure, plus value sizes when the user supplies a sizer. Never a measured retained size.
_Avoid_: Memory usage, footprint
