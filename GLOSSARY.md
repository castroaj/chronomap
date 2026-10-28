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
