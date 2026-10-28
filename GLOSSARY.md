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
