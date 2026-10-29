# Chronomap

A cache that knows what day it is. Chronomap answers "what was true for this key on this date?" from the dated versions of your reference data, so each event is validated against the data as it was when it happened.

> **Draft.** Written before the implementation as the test of whether the API is simple enough. Slice 12 of the implementation breakdown finalizes it. Names and signatures follow the quickstart prototype and ADR 0002.

## The problem

Orders arrive all day. Prices are published once a day as a full file. When an order is validated, replayed or audited, you need the price that applied on the order date, not today's price, and a re-run next month must give the same answer.

## Quickstart

Point Chronomap at a directory of dated files, one full copy per day:

```
prices/
  prices-2026-01-01.csv
  prices-2026-01-02.csv
  prices-2026-01-03.csv
```

```java
Chronomap<String, Price> prices = Chronomap.<String, Price>table("prices")
    .key(Price::sku)
    .source(DirectorySource.csv(Path.of("prices"), "prices-%tF.csv", Price::fromCsv))
    .pollEvery(Duration.ofMinutes(15))
    .build();                       // replays every version, oldest to newest

Optional<Price> price = prices.get(order.sku(), order.date());
```

`build()` blocks until the history is loaded. After that, lookups never wait for a refresh: new versions are appended and swapped in atomically.

## Asking why

`get` returns the value. `lookup` also says what asserted it:

```java
Lookup<Price> result = prices.lookup(order.sku(), order.date());

result.value();                      // Optional<Price>
result.provenance().outcome();       // PRESENT, DELETED or NOT_FOUND
result.provenance().changeDate();    // date of the version that introduced this price
result.provenance().versionRef();    // opaque reference to that version
result.provenance().clamp();         // NONE, BEFORE_OLDEST or AFTER_NEWEST
```

A date before the oldest version or after the newest is moved to that version (a clamp), and provenance tells you which side and by how many days. A deleted or never-seen key returns an absent value.

## Testing and replay

The `chronomap-test` artifact builds a cache from in-memory versions, so tests don't need files:

```java
Replay.of(versions)
    .assertAsOf("A1", date("2026-01-02"), new Price("A1", "10.00"))
    .assertAbsent("B2", date("2026-01-05"));
```

It can also replay an event log and compare two builds, which is how you show that a change to history alters only the answers you expect.

## Bad data

A malformed version is dropped whole and logged. Call `.onMalformed(Malformed.THROW)` to fail instead. A late, edited or removed version triggers one background rebuild; failed rebuilds keep serving the old state.

## Operations

Chronomap publishes Micrometer meters (data freshness above all) and SLF4J logs. Pass your `MeterRegistry` to the builder. Page on stale data; open a ticket for growth and slow rebuilds. *(Alert advice to be written in slice 8.)*

## Using it with Spring Boot

*(Stub. Slice 12 documents wiring a `Chronomap` bean by hand: build it in a `@Bean` method with the app's `MeterRegistry` and `DataSource`, and close it on shutdown. A starter is not part of v0.1.)*

## When not to use it

- The data doesn't fit in one process's memory (the target is up to about 5M keys and 1-2 years of history).
- You need ad hoc temporal queries or analytics. Use a temporal store or warehouse.
- You need "what we knew when", with corrections as first-class data. Chronomap has one time axis.
- You need finer than daily precision.
- You already run a stream processor with temporal joins.

## Not in v0.1

Range-keyed table types are reserved (ADR 0001) and not built. There is no change-stream ingestion, no persisted cache, and no shared-service mode.
