# Lookups report provenance through a separate `lookup` call

`get` returns `Optional<V>` and computes nothing else. `lookup` returns a non-null `Lookup<V>`: an `Optional<V>` value plus a `Provenance` record saying what asserted the answer. The shape below is binding.

## Provenance fields

- **`outcome`**: `PRESENT`, `DELETED` or `NOT_FOUND`. `DELETED` means the key's latest dated change on the effective date is a tombstone, and `changeDate` and `versionRef` are the tombstone's. `NOT_FOUND` means no dated change exists at or before the effective date, so the other change fields are empty.
- **`changeDate`**: the date of the version that introduced the answer. The timeline stores dated changes only, so a later unchanged version that merely confirmed the row is not reported. Operators who need the newest version date read it once per table type, not per lookup.
- **`versionRef`**: an opaque `String` produced by the `VersionSource`. Chronomap never parses it. A source must return the same ref for the same version across restarts and rebuilds, so replayed lookups give identical provenance. This guarantee belongs in the `VersionSource` contract.
- **`clamp`**: `NONE`, `BEFORE_OLDEST` or `AFTER_NEWEST`, from the as-of date against the table type's oldest and newest version dates.
- **Clamp distance**: a non-negative `Duration` of whole days between the as-of date and the bound it was clamped to. `Duration.ZERO` when `clamp` is `NONE`. The direction comes from `clamp`.
- **Effective date**: the as-of date after clamping.
- **`matchedRange`**: optional, the inclusive `lo` and `hi` of the range that answered. Empty for exact-keyed table types. Reserved and unused in v0.1. For a range-keyed table type the change fields come from the matched range's own timeline, `DELETED` means its latest change is a tombstone, and `NOT_FOUND` means no range contained the value (see ADR 0001).

## Cost

- `get` never computes provenance.
- `lookup` allocates freely: one `Lookup` and one `Provenance` per call, no pooling. The timeline prototype measured ~17.8M lookups/s single-thread against a ~13k/s target.
- Each stored dated change holds one reference to a shared per-version object (date plus `versionRef`), not its own copy, so memory grows by one reference per dated change.
- The clamp, its distance and the effective date are computed per call. None are stored per key.
- Re-measure `lookup` against the target when the timeline is built.

## Considered alternatives

- **Report the confirming version too.** Rejected: it needs a separate list of version dates per table type and answers a different question than what asserted the value.
- **Boolean `clamped`.** Rejected: it hides which side was clamped. Clamping past the newest version can mean the datastore stopped publishing.
- **Generic `versionRef` type.** Rejected: it adds a type parameter to every public type, and callers only log or display the ref.
- **Provenance on `get`.** Rejected: it would allocate on the lean path and break the `Optional<V>` contract settled in the quickstart API.
