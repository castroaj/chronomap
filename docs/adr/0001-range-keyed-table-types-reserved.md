# Range-keyed table types are reserved, not built, in v0.1

v0.1 ships the exact-key core only. Range matching (for example the phone-routing origin: NPA=200, NXX 200-210 as one row) is a reserved extension point in front of that core. The semantics below are binding; the lookup structure is a default that may change.

## Binding semantics

- **Search, don't expand.** A range row stays a range record and is searched at lookup time. It is never expanded into one exact key per value, which would blow the ~5M-key budget.
- **Range key shape.** A range applies to the last component of a declared composite key only: one inclusive integer interval, with every earlier component (the prefix) matched exactly.
- **Table type kind.** A table type is either exact-keyed or range-keyed, declared at build time. Never both, so there is no precedence rule. A range lookup with no live matching range returns absent.
- **Identity.** A range row is identified by (prefix, `lo`). The upper bound `hi` and the payload are the row's value, so a bounds change is an ordinary dated change (an update), and a split is an update on the first half plus a create for the second.
- **No overlaps.** Two range rows with the same prefix that overlap within one version make that version malformed. It follows the `onMalformed` policy: dropped whole and logged by default, `THROW` opt-in.
- **Timelines.** Each range has its own timeline of full-row dated changes. No per-version index is materialized, so memory stays proportional to the dated changes, not versions × ranges.
- **Inherited rules.** Lenient clamping to the oldest and newest version, tombstones for removed ranges, and re-adding after a tombstone all behave as in the exact-key core. Provenance for ranges follows the provenance decision (#5).

## Default lookup structure (non-binding)

Candidate-then-confirm, per prefix:

1. Index each range by its widest extent over all time, from `lo` to the largest `hi` it ever had. A stabbing query on the value N returns the candidates.
2. For each candidate, binary-search its timeline on the date D. Accept it only if the row exists on D (not a tombstone) and `lo` <= N <= `hi` in that version. Because overlap is malformed, at most one candidate passes.

A date-blind lookup is wrong: if 200-210 splits into 200-204 and 205-210 on June 1, a lookup for 207 on May 1 must hit the range starting at 200. That is why the index returns candidates and the timeline confirms them.

## Considered alternatives

- **Expand ranges to exact keys at load.** Rejected: size scales with range width.
- **Materialized interval index per version date.** Rejected: memory grows with versions × ranges, which undermines 1-2 years of history. Structural sharing would reduce it but adds complexity for no lookup benefit over the chosen default.
- **Boundary decomposition.** Cut the number line at every boundary ever seen, with a timeline per elementary segment: two binary searches per lookup, no candidates. Revisit if candidate sets prove large. Cost: a payload change to a wide range appends a dated change to every segment it spans.
- **Validity rectangles.** Each row version becomes [`lo`, `hi`] x [start, end) in a 2D interval index, queried by point (N, D). Revisit when range support is built. Cost: a more complex index, and an incremental refresh must close the previous period.
