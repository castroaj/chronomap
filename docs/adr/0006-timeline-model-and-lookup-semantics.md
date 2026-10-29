# Versions become dated changes on one timeline per key

The cache stores changes, not copies. The semantics below are binding for v0.1.

## Model

- **Diff:** replay a table type's versions oldest to newest and append a dated change (create, update, or delete as a tombstone) only when a row changes, by whole-row equality. There is no field-level projection.
- **Timeline:** one sorted sequence of dated changes per key. Single time axis, day precision.
- **As-of lookup:** a floor search on the timeline.
- **Clamping:** an as-of date outside the oldest and newest version is moved to that bound (ADR 0002 reports it).
- **Absence:** never-seen keys, keys not yet created, and deleted keys return absent. Re-adding a deleted key is fine.
- **Leniency:** the library trusts the datastore to be complete. It does not detect missing days.
- **Determinism:** a full rebuild, a rebuild from shuffled input, and incremental append give identical answers.
- **Append order:** append requires strictly newer versions; late or out-of-order versions need a rebuild (ADR 0003).
- **Keys:** exact keys only, a primary or composite key declared per table type. Range keys are reserved (ADR 0001).

## Evidence

The timeline prototype measured about 17.8M lookups/s single-threaded (300k keys, 730 versions, ~0.1% churn) against a ~13k/s target. Replaying full versions, not lookup, is the slow part (ADR 0003).

## Considered alternatives

- **Keep every full copy.** Rejected: storage grows with every publication.
- **Field-level change projection.** Rejected for v0.1: whole-row equality is simpler to explain and test.
- **Strict mode** (detect missing days and malformed versions). Deferred to a future version.
- **A second "what we knew" timeline.** Out of scope: it makes the product much harder to use.
