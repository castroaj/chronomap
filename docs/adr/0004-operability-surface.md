# Operability goes through Micrometer meters and SLF4J logs

Operators watch a Chronomap instance through Micrometer meters and SLF4J log events. The surface below is binding for v0.1.

## Interface and dependencies

- **Micrometer.** The builder takes `.meterRegistry(MeterRegistry)`. With no registry passed, nothing is registered.
- **`micrometer-core` is a hard dependency of core.** Making it optional would need an isolated binder class so core never loads Micrometer types without it; that was judged easy to get wrong and hard to test across classloader setups.
- **`slf4j-api` is the logging facade**, with no binding bundled. Users route it to their own backend.
- Core therefore has two runtime dependencies, `micrometer-core` and `slf4j-api`. #12 (JDBC) and #15 (build tooling) inherit this, and the JDBC driver still stays out of core.

## Meters

All meters carry a `table` tag with the table type's name.

**Freshness**
- `chronomap.version.newest`: gauge, the date of the newest loaded version.
- `chronomap.version.lag`: gauge in days, today minus the newest version date. It reveals a datastore that stopped publishing, which an `AFTER_NEWEST` clamp (ADR 0002) would otherwise hide.
- `chronomap.refresh.age`: gauge in seconds since the last successful poll.

**Size**
- `chronomap.keys`: gauge, the number of keys.
- `chronomap.dated.changes`: gauge, the total dated changes across all timelines.
- `chronomap.heap.estimate`: gauge in bytes. See below.

**Health**
- `chronomap.versions.dropped`: counter of malformed versions dropped whole (#2).
- `chronomap.refreshes`: counter tagged `result=success|failure`.
- `chronomap.rebuilds`: counter tagged `cause=late|edited|removed|manual` and `result=success|failure` (ADR 0003).
- `chronomap.rebuild.duration` and `chronomap.build.duration`: timers, so the readiness budget in ADR 0003 can be watched.

## Heap estimate

The estimate is the timeline structure (keys and dated changes times fixed per-entry constants, calibrated against the JMH suite from #7) plus an optional `valueSizer(ToLongFunction<V>)` applied per value. Without a sizer it covers structure only. It is recomputed at build, refresh and rebuild time, never on a scrape, and the docs call it an estimate.

## Logging

Core logs through SLF4J. The events the docs list as meaningful are: a dropped malformed version, a change to history naming each affected version (ADR 0003), and a rebuild starting, finishing or failing.

## Identity and lifecycle

- The `table` tag is the name passed to `Chronomap.table(..)`. `build()` rejects a duplicate name on the same registry.
- `Chronomap` implements `AutoCloseable`. `close()` stops polling and the parse-ahead pool and removes its meters from the registry.
- Gauges hold weak references to the cache, so a forgotten `close()` does not leak it.

## What operators page on

The README publishes this as advice. The library ships no alert rules, and the thresholds are examples the user tunes.

- **Page:** `chronomap.refresh.age` above about 3x `pollEvery`; `chronomap.version.lag` beyond the publishing cadence plus slack; `chronomap.rebuilds{result=failure}` rising across consecutive attempts.
- **Ticket:** `chronomap.versions.dropped` rising; `chronomap.rebuilds{cause=edited|removed}` rising; `chronomap.heap.estimate` approaching the heap budget.
- **Do not alert on** a single failed refresh, `chronomap.keys` changes, or a `late` rebuild alone, since a backfill is legitimate.

## Considered alternatives

- **A plain `stats()` snapshot API.** Rejected: it keeps core dependency-free, but every user would write their own adapter to their metrics system. A Micrometer binding is what most JVM operators already run.
- **A listener interface.** Rejected: users would translate events into metrics themselves, and gauges such as refresh age do not fit an event model.
- **An optional `micrometer-core` dependency.** Rejected: see above.
- **`System.Logger` or no logging.** Rejected: SLF4J is what operators expect, and the dependency adds little next to Micrometer. Surfacing everything through metrics would leave the version named in a warning with nowhere to go.
- **Measuring real retained heap size.** Rejected: accurate, but slow and heavy, and it needs a dependency. The user-supplied sizer gives a full number when it matters.
- **Shipping alert rules in the library.** Rejected: thresholds depend on the publishing cadence and poll interval, which only the user knows.
