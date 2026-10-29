# Versions reach the cache through a two-method `VersionSource`

A `VersionSource` lists versions cheaply, then reads one whole version at a time. The contract below is binding for v0.1.

## Contract

```java
interface VersionSource<V> {
    List<VersionRef> list(LocalDate after);   // names and dates only, oldest first, no row IO
    Stream<V> read(VersionRef ref);           // one whole version; any bad row fails the whole version
}
record VersionRef(String id, LocalDate date)  // id is an opaque pointer the source understands
```

- **Build:** `list(null)`, then read oldest to newest. **Refresh:** `list(cursor)` and read only the new versions.
- **Cursor:** the newest version *attempted*, not the newest applied. A malformed version stays skipped until the next full rebuild; otherwise it is retried on every poll and could land out of order if fixed later.
- **Late versions:** a version dated at or before the cursor is ignored by refresh and handled by a rebuild (ADR 0003).
- **Checksum:** every version carries one, required (ADR 0003). The in-memory source computes it from content.
- **Stable refs:** a source returns the same `versionRef` for the same version across restarts and rebuilds (ADR 0002).
- **Malformed versions:** any bad row fails the whole version, which is dropped and logged by default (`THROW` is opt-in).

## Core sources

Three sources ship in the core artifact:

- **Directory:** dated CSV files, date from the file name; writers use a temp name then rename.
- **Versions table (JDBC):** a `DataSource` and a `RowMapper<V>`, portable SQL only, configurable and validated (never quoted) identifiers. A NULL `version_date` is skipped and logged; a bad checksum, pointer or duplicate date makes a malformed version. No driver or pool is bundled. Splitting it into its own artifact stays open as a future change.
- **In-memory:** used by the test helper and the quickstart.

The ingestion service that writes the versions table is out of scope; the README documents its contract.

## Considered alternatives

- **A single lazy-stream method** that yields versions in one call. Rejected: refresh could not skip already-loaded versions without reading them, and listing could not be separated from row IO.
- **Cursor at the newest applied version.** Rejected: see above.
- **Separate artifacts per source.** Deferred: costs the quickstart a dependency now, and splitting later is possible.
