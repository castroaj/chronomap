# Timeline and Chronomap: data structure and algorithm choices

This note explains the data structures and algorithms behind the timeline and `Chronomap`. The binding decisions live in the ADRs; this note collects the reasoning in one place.

**Status:** the timeline, diffing, clamping, refresh and rebuild are design only. The code today (walking skeleton) implements the `VersionSource` contract, `InMemorySource` and a staged per-version read. `Chronomap.get` ignores `asOf`, and `build()` rejects sources with more than one version.

Sources: [ADR 0001](adr/0001-range-keyed-table-types-reserved.md), [0002](adr/0002-lookup-provenance-shape.md), [0003](adr/0003-startup-rebuild-strategy.md), [0005](adr/0005-version-source-contract.md), [0006](adr/0006-timeline-model-and-lookup-semantics.md).

## 1. Store changes, not copies

Reference data arrives as a full file per day, and most rows do not change between days. Keeping every copy grows storage with each publication. Keeping only the latest loses history. Chronomap keeps a **timeline per key**: a sorted sequence of dated changes (create, update, or delete as a tombstone). Memory grows with the number of changes, not with versions x rows.

## 2. Data structure

- **Layout:** a map from key to that key's timeline. Each timeline is a sorted sequence of `(date, row-or-tombstone)` entries, with day precision on a single time axis. ADR 0006 specifies a "sorted sequence" without fixing the concrete type, so a compact sorted array and a `NavigableMap` are both open; at hundreds of thousands to millions of keys with few changes each, an array is likely cheaper. This is a note, not a decision.
- **Exact keys only:** a primary or composite key is declared per table type. Range keys are reserved (ADR 0001). When added, each range gets its own timeline, and no per-version interval index is materialized, because that would grow with versions x ranges and undermine long history.
- **Shared provenance:** each dated change holds one reference to a shared per-version object (date plus `versionRef`), not its own copy (ADR 0002). Memory grows by one reference per change, and lookups can report which version produced an answer.
- **Tombstones:** a deletion is an entry, so a later lookup returns absent instead of the last value. Re-adding a deleted key is another entry.

## 3. Lookup: floor search

`get(key, asOf)`:

1. **Clamp** `asOf` into [oldest version, newest version]. ADR 0002 reports the clamp (e.g. `BEFORE_OLDEST`).
2. **Hash** to the key's timeline. A never-seen key is absent.
3. **Floor search:** binary search for the latest entry with `date <= asOf`. Cost is O(log n) in the changes for that key, and n is small. A key not yet created at `asOf` has no floor and is absent.
4. **Tombstone check:** a tombstone floor is absent; otherwise return the row.

The timeline prototype measured about 17.8M lookups/s single-threaded (300k keys, 730 versions, ~0.1% churn) against a target of about 13k/s. Lookup is not the bottleneck.

## 4. Build: replay and diff

A build replays a table type's versions oldest to newest, holding the previous state per key. For each row in the next version:

- New key: append a create.
- Existing key whose row differs by **whole-row equality**: append an update.
- Key present before and missing now: append a delete tombstone.
- Unchanged: append nothing.

Whole-row equality was chosen over field-level projection because it is simpler to explain and test. The diff step, not lookup, is the slow part (~10 s for 300k keys x 730 versions).

**Parse-ahead pipeline:** a thread pool reads and parses versions ahead of time, while one thread diffs and appends in strict date order. This keeps replay deterministic. Key-partitioned parallel replay is deferred until profiling shows the diff is the bottleneck.

**Staging:** the builder reads each version into its own map and merges only after a clean consume and close, so a failed version contributes nothing (ADR 0005).

**Determinism:** a full rebuild, a rebuild from shuffled input, and incremental append must give identical answers. `InMemorySource` sorts input by date for this reason.

## 5. Refresh and rebuild

- **Incremental append:** refresh lists versions after a cursor and reads only those. Append requires strictly newer versions.
- **Cursor:** the newest version *attempted*, not applied. A malformed version stays skipped until the next full rebuild; otherwise it would be retried every poll and could land out of order if fixed later.
- **Change to history** is a late version (dated before the cursor), an edited version (checksum differs) or a removed version (missing from the listing). Any mix triggers **one** rebuild, with a warning naming each affected version.
- **Per-version checksums**, not one per table: a table-wide checksum changes on every legitimate append and cannot name the edited version.
- **Atomic swap:** the rebuild runs in the background and swaps in atomically. Readers keep the old state until the swap, so lookups are never blocked. Peak heap is about 2x during a rebuild.
- **Failure:** if a rebuild fails, the old state keeps serving, the error is logged, a counter increments, and retries back off exponentially (capped).
- **Retention window:** optional and off by default, applied at build and rebuild only. The oldest retained version is the baseline and earlier dates clamp to it. A running instance never trims, since trimming would mutate live timelines and fight the wait-free readers.

## 6. Trade-offs

| Choice | Alternative | Why |
| --- | --- | --- |
| Timeline of changes | Keep every full copy | Storage grows with every publication |
| Whole-row equality | Field-level projection | Simpler to explain and test |
| Single time axis | Second "what we knew" timeline | Far harder to use; out of scope |
| Trust the datastore | Strict mode detecting missing days | Deferred |
| Full rebuild on history change | Manual `rebuild()` only | Silent stale data after an edit is worse |
| One diff thread | Key-partitioned replay | Parsing is the likelier bottleneck |
| No snapshots | Persist timelines to disk | Format would become a public contract |
| Blocking `build()` | Async build | Gives `get` an unclear not-ready meaning |
