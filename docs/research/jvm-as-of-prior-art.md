# JVM prior art for per-event as-of lookup over periodically published reference data

Research for GitHub issue #4. Tests the gap claimed in `docs/design.md` section 8. Vocabulary follows `GLOSSARY.md` (table type, version, dated change, timeline, as-of lookup).

## Short answer

The gap is **partially confirmed**. The semantics of as-of lookup are well covered. Flink temporal joins, Kafka Streams versioned stores, SQL:2011 system-versioned tables, XTDB, Datomic, Delta, Iceberg and Hudi time travel all implement them. The primitive itself is a one-liner on `NavigableMap.floorEntry`. But none of the primary-source candidates found does all of the following in one place: run in-process in an ordinary service, ingest full periodic versions of a table type, derive the dated changes (including tombstones) automatically, and serve as-of lookups with no datastore. The nearest matches are BarbelHisto (a bitemporal in-process Java library, which does not diff versions) and the Kafka Streams `VersionedKeyValueStore` (the closest semantic match, but tied to Kafka Streams). The design.md table is accurate in outline but too broad in two rows: "not usable from ordinary services" holds only loosely for Kafka Streams (see caveats), and "too slow for per-event lookups" is asserted without evidence. The "derive changes by diffing consecutive full versions" claim is the strongest part of the gap, and no source found offers it as a library feature. The absence was not exhaustively proven (see caveats).

## Candidate table

| Candidate | What it gives | In-process in an ordinary service? | Per-event as-of lookup? | Diffs full versions into dated changes? | Fills the gap? |
| --- | --- | --- | --- | --- | --- |
| Flink versioned tables / temporal join | Event-time join to the build-side version valid at the probe row's time | No, runs inside a Flink job | Yes, inside the pipeline | No (needs a changelog, or a dedup query on an append-only stream) | No |
| Kafka Streams `VersionedKeyValueStore` (KIP-889) and KIP-914 | `get(key, asOfTimestamp)`, bounded history retention | Only as a Streams `StateStore` | Yes | No (you `put` timestamped records) | Closest semantics, wrong packaging |
| XTDB | Bitemporal SQL database with valid time and system time | Not confirmed from docs read (runs as a database service) | Yes, via query | No (you write the changes) | No (heavyweight store; possible source) |
| Datomic `as-of` | `as-of` database filter by t, tx or instant | Peer library is in-process but is a database client | Yes | No | No |
| SQL:2011 system-versioned tables (and jOOQ DSL) | `FOR SYSTEM_TIME AS OF` in the database | No, needs an RDBMS | Yes, with a DB round trip | No (the DB records its own transaction time, not your publish date) | No |
| Delta / Iceberg / Hudi time travel | Query an older table snapshot | No, needs a Spark or engine runtime | Not designed for per-event | No | No (candidate source) |
| Reladomo | Bitemporal chaining ORM, with optional object cache | JVM library, but built around a relational database | Yes, via finders | No | No |
| Hibernate Envers | Per-revision auditing of ORM entities | In-process with Hibernate, needs a DB | Entity at revision, not a general table type | No | No |
| BarbelHisto | In-process bitemporal Java library, MIT | Yes (heap, off-heap, disk options) | Bitemporal retrieval (API not confirmed here) | No evidence | Partial; user must compute and feed changes |
| Ervacon/bitemporal | Domain-model bitemporal helper | Yes, but depends on an ORM for persistence | Not documented | No | No, and unmaintained |
| Chronicle Map | Fast off-heap key-value store | Yes | No temporal feature | No | No |
| Caffeine | Local cache with size and time expiration | Yes | No temporal feature | No | No |
| `TreeMap.floorEntry` pattern | Greatest key at or below the query | Yes | Yes, as a primitive | No | The building block, not the product |

## Per-candidate notes

### Flink versioned tables and temporal joins
A versioned table "remembers the past values for each key". It needs a primary key and an event-time attribute, and it is defined implicitly for changelog sources (upsert Kafka, Debezium, Canal) or via a `ROW_NUMBER()` deduplication view over an append-only source ([versioned tables](https://nightlies.apache.org/flink/flink-docs-release-2.3/docs/concepts/sql-table-concepts/versioned_tables/)). The temporal join `FOR SYSTEM_TIME AS OF table1.rowtime` joins each probe row with the build row valid at its event time. Both sides need watermarks, the build side keeps versions only back to the last watermark, and the build table's primary key must be in the equality condition ([joins](https://nightlies.apache.org/flink/flink-docs-release-2.3/docs/sql/reference/queries/joins/)). It is as-of semantics exactly, but it lives inside a Flink job. It does not offer a lookup call for a request handler. This confirms the design.md "stream processors" row.

### Kafka Streams versioned state stores (KIP-889, KIP-914)
`VersionedKeyValueStore<K,V> extends StateStore`. `get(key, asOfTimestamp)` returns "the existing record with the largest timestamp not exceeding the provided timestamp bound" (inclusive), or null if none exists or the bound is older than history retention. A `null` put is a delete, and writes older than stream time minus the grace period are rejected ([source](https://raw.githubusercontent.com/apache/kafka/trunk/streams/src/main/java/org/apache/kafka/streams/state/VersionedKeyValueStore.java)). KIP-889 states that the motivating use is stream-table joins returning the table value current at each record's timestamp, with a RocksDB implementation first and an in-memory one deferred ([KIP-889](https://cwiki.apache.org/confluence/display/KAFKA/KIP-889%3A+Versioned+State+Stores)). KIP-914 changes the DSL join to call `get(key, streamRecordTimestamp)` ([KIP-914](https://cwiki.apache.org/confluence/display/KAFKA/KIP-914%3A+DSL+Processor+Semantics+for+Versioned+Stores)). This is the most on-point prior art for the semantics: inclusive floor lookup, tombstones as null puts, bounded retention. Gaps for Chronomap: it is a Streams component, it expects timestamped per-key records and not full-file versions, and nothing in the sources diffs consecutive versions. Whether it can be instantiated outside a Streams topology was not established (KIP-889 does not claim it can).

### XTDB
Open-source immutable SQL database, bitemporal (system time and valid time) and based on SQL:2011 bitemporal features, MPL-2.0 ([XTDB intro](https://docs.xtdb.com/intro/what-is-xtdb.html), [repo](https://github.com/xtdb/xtdb)). The pages read describe a database (Postgres wire protocol, object storage, distributed log). They do not document an embedded in-process mode, so it was not confirmed. It would fit as a durable source and as the answer to the two-timeline case in 5.4, not as an embedded hot-path cache.

### Datomic as-of
An `as-of` filter takes a database value and returns a view at a past t, tx, or `java.util.Date`; later transactions are excluded ([filters](https://docs.datomic.com/reference/filters.html)). Peer binaries are Apache 2.0 licensed and free of fees, but the project is not open source ([Datomic is Free](https://blog.datomic.com/2023/04/datomic-is-free.html), [search summary of change log](https://docs.datomic.com/changes/pro.html)). It is a full database with its own transactor and data model. Adopting it to cache a daily file is a different category from a library.

### SQL:2011 system-versioned and application-time tables
The standard defines application-time, system-versioned and bitemporal tables, with `FOR SYSTEM_TIME AS OF` for time slices ([SQL:2011](https://en.wikipedia.org/wiki/SQL:2011); secondary source, the standard text itself is paywalled). Implemented by DB2, Oracle, SQL Server and MariaDB ([SQL Server docs](https://learn.microsoft.com/en-us/sql/relational-databases/tables/querying-data-in-a-system-versioned-temporal-table?view=sql-server-ver17), [MariaDB](https://mariadb.com/docs/server/reference/sql-structure/temporal-tables/system-versioned-tables)). jOOQ exposes it in a Java DSL (`systemTime().asOf(...)`, `period(...).asOf(...)`), only on Express/Professional/Enterprise editions and only where the dialect supports it natively ([jOOQ](https://www.jooq.org/doc/latest/manual/sql-building/table-expressions/temporal-tables/)). System time records when the database saw the change, not the publication date of a version, and every lookup is a database call. This confirms the "SCD / historical database" rows on latency grounds in principle (not benchmarked here).

### Delta Lake, Iceberg, Hudi
Delta: `VERSION AS OF` / `TIMESTAMP AS OF`, default 30 days retention, governed by `VACUUM` and log retention ([Delta docs](https://docs.delta.io/latest/delta-batch.html)). Iceberg exposes time travel via SQL and DataFrame ([Iceberg Spark queries](https://iceberg.apache.org/docs/latest/spark-queries/); only headings were readable, details not verified). Hudi supports `as.of.instant` ([Hudi quick start](https://hudi.apache.org/docs/quick-start-guide)). These are snapshot reads through an engine, suited to analysis and as upstream sources (matching design.md 8.1), not to per-event in-service lookups.

### Reladomo
Goldman Sachs' Java ORM with bitemporal chaining: business-date columns (FROM_Z, THRU_Z) and processing-date columns (IN_Z, OUT_Z), `AsOfAttribute` declarations, finders taking `businessDate()` and `processingDate()`, and a per-object cache (`cacheType="partial"`) ([Reladomo tour](https://goldmansachs.github.io/reladomo-kata/reladomo-tour-docs/tour-guide.html)). All examples assume JDBC and a relational database. It models bitemporality but does not ingest full publications or diff them.

### Hibernate Envers
Audits ORM entities per transaction revision; `AuditReader` queries an entity at a revision and lists revisions, including by date range ([Envers](https://hibernate.org/orm/envers/)). It audits writes made through Hibernate, so it cannot derive changes from a published file and needs a database.

### BarbelHisto and Ervacon/bitemporal
BarbelHisto is an MIT-licensed Java library "to store data in bi-temporal format" (record time and effective time), based on Fowler's temporal pattern and CQEngine, with heap, off-heap, disk and custom persistence ([repo](https://github.com/projectbarbel/barbelhisto-core)). This is the only in-process, database-free bitemporal Java library found. Not confirmed from the pages read: its retrieval API, whether it can diff full snapshots, Java baseline, and release cadence (the repo shows no published releases and a Travis CI badge, a sign of age). Ervacon/bitemporal is a domain-model helper that persists through an ORM and states it is "not actively worked on" ([repo](https://github.com/Ervacon/bitemporal)). Both would need the diffing and ingestion that Chronomap is meant to supply. BarbelHisto is the one to evaluate hands-on, because it is the nearest competitor on packaging.

### Chronicle Map and Caffeine
Chronicle Map is a fast in-memory, optionally persisted key-value store with replication and no documented temporal feature ([repo](https://github.com/OpenHFT/Chronicle-Map)). Caffeine's design page lists size eviction, expireAfterAccess/Write/variable expiry and Window TinyLFU, with no as-of or version feature mentioned ([design wiki](https://github.com/ben-manes/caffeine/wiki/Design)). Both confirm design.md's "in-memory caches hold the current value" row. Note that expiry is wall-clock based, which is the wrong clock for event-time lookups.

### Java `NavigableMap.floorEntry` pattern
`floorEntry(key)` returns the entry with the greatest key at or below the given key, or null ([javadoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/NavigableMap.html)). A composite (key, validFrom) ordering with `floorEntry` is a common hand-rolled as-of map; one public example is [a gist TimeMap](https://gist.github.com/aadnk/0afd87045086454569d5f3b311c5f80c). That is a hobby-grade pattern, not a maintained library. This supports the design.md "hand-built code" row: the lookup is trivial, and diffing, refresh, atomic swap during load, retention and tombstones are what teams rebuild.

## Caveats and unknowns

- Absence of evidence is not proof. The searches found no maintained "versioned map" or "time-travel cache" library for the JVM, but only a handful of queries were run, and the GitHub topics for "bitemporal" were not walked.
- BarbelHisto's actual API and ability to ingest snapshots was not verified from primary docs. Check its README and wiki and try a spike before claiming novelty over it.
- Kafka Streams versioned stores: whether `VersionedKeyValueStore` (RocksDB implementation) can be used standalone outside a topology was not verified. Design.md's "not usable from ordinary services" is true of the supported API surface but untested as a hack.
- XTDB embedded mode, Datomic peer in-memory today, and Iceberg details were not confirmed from the pages read.
- Performance claims in design.md ("too slow", "same latency problem") are unsupported by anything read here. They need measurement (design.md 5.6) before the pitch repeats them.
- Not checked: Eclipse Collections, Time4J, Guava, Apache Spark's own as-of join handling, Hazelcast/Ignite/Coherence temporal features, and commercial temporal caches. Eclipse Collections and Time4J are not expected to offer temporal maps, but that was not verified.
- Wikipedia and a gist were used where no primary source was reachable (SQL:2011 standard text, TimeMap); they are flagged inline.

## Implications for design.md section 8

- Keep the gap statement, but narrow it: "an embeddable library that ingests full periodic versions, derives dated changes including tombstones, and serves as-of lookups in-process".
- Add Kafka Streams versioned stores as the semantic reference (inclusive floor, null-as-delete, bounded retention) and BarbelHisto as the nearest in-process library.
- Soften latency claims until benchmarked.

## Sources

- Flink versioned tables: https://nightlies.apache.org/flink/flink-docs-release-2.3/docs/concepts/sql-table-concepts/versioned_tables/
- Flink joins (temporal join): https://nightlies.apache.org/flink/flink-docs-release-2.3/docs/sql/reference/queries/joins/
- KIP-889: https://cwiki.apache.org/confluence/display/KAFKA/KIP-889%3A+Versioned+State+Stores
- KIP-914: https://cwiki.apache.org/confluence/display/KAFKA/KIP-914%3A+DSL+Processor+Semantics+for+Versioned+Stores
- Kafka `VersionedKeyValueStore` source: https://raw.githubusercontent.com/apache/kafka/trunk/streams/src/main/java/org/apache/kafka/streams/state/VersionedKeyValueStore.java
- XTDB: https://docs.xtdb.com/intro/what-is-xtdb.html and https://github.com/xtdb/xtdb
- Datomic as-of: https://docs.datomic.com/reference/filters.html
- Datomic licensing: https://blog.datomic.com/2023/04/datomic-is-free.html and https://docs.datomic.com/changes/pro.html
- SQL:2011 (secondary): https://en.wikipedia.org/wiki/SQL:2011
- SQL Server temporal queries: https://learn.microsoft.com/en-us/sql/relational-databases/tables/querying-data-in-a-system-versioned-temporal-table?view=sql-server-ver17
- MariaDB system-versioned tables: https://mariadb.com/docs/server/reference/sql-structure/temporal-tables/system-versioned-tables
- jOOQ temporal tables: https://www.jooq.org/doc/latest/manual/sql-building/table-expressions/temporal-tables/
- Delta time travel: https://docs.delta.io/latest/delta-batch.html
- Iceberg Spark queries: https://iceberg.apache.org/docs/latest/spark-queries/
- Hudi quick start: https://hudi.apache.org/docs/quick-start-guide
- Reladomo tour: https://goldmansachs.github.io/reladomo-kata/reladomo-tour-docs/tour-guide.html
- Hibernate Envers: https://hibernate.org/orm/envers/
- BarbelHisto: https://github.com/projectbarbel/barbelhisto-core
- Ervacon/bitemporal: https://github.com/Ervacon/bitemporal
- Chronicle Map: https://github.com/OpenHFT/Chronicle-Map
- Caffeine design: https://github.com/ben-manes/caffeine/wiki/Design
- `NavigableMap` javadoc: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/NavigableMap.html
- TimeMap gist (example only): https://gist.github.com/aadnk/0afd87045086454569d5f3b311c5f80c
