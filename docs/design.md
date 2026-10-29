# Chronomap — a date-aware reference-data cache

> **Working name:** *Chronomap*. Alternatives: *AsOf*, *Retrospect*, *Chronex*, *TimeVault*.
> Naming is an open decision (see [Open questions](#6-open-questions)).

**Status:** concept / project direction. No architectural or technology decisions have
been made yet. This document records the problem, the promise, and the business-level
choices to resolve before design begins.

**Nature of the project:** open-source, with no commercial aims. Success is measured by
how quickly a new user gets a correct answer, the quality of the documentation, and the
rigor of the correctness guarantees, not by revenue or market share.

---

## 1. Summary

Chronomap answers one question, very quickly: **"What was true for this thing at this
moment?"**

Organizations receive reference data that is republished on a cadence (for example, a new
full file each day). Chronomap takes those publications, works out what changed and when,
and lets applications look up the correct answer for any point in time, including the
past, without the cost or latency of a dedicated temporal database.

### 1.1 Positioning

> **The missing layer between "the reference file arrived" and "the business rule ran
> correctly for the date it should."**

Think of it as *a cache that knows what day it is*. It is a small, embeddable piece that
makes point-in-time correctness the default for services that do not have a temporal
platform. It does not compete with databases or stream processors.

### 1.2 Flagship scenario

**Validate each event against the reference data as it was when the event happened.**

The lead example is an order or transaction stream checked against a daily-published
reference file (prices, tax rates, or a product catalog). Everyone understands "what was
the price at order time?" without domain knowledge, and it has the same shape as the
origin system: a new full file each day, a fast event stream, and replay that must match
the original answers. Phone-number routing is the origin story and a case study, not the
pitch.

**Adoption target:** a team points the library at a daily file and gets a correct
point-in-time answer in a handful of lines. The quickstart is written before the
implementation and acts as the test of whether the design is simple enough.

Generality should emerge from this one scenario being done well, not from trying to cover
every use case up front.

---

## 2. Origin

This generalizes a real system. A service validated a fast stream of events against
complex reference data. One case was phone-number validation, including whether a number
was *routable at the time of the event*. Reference data arrived as a new full file each
day, each representing that day's truth. The original solution loaded each file, compared
consecutive days, recorded what changed per key, and offered a lookup by key and date.
Chronomap is that idea turned into a reusable product.

---

## 3. The problem

- **Correctness depends on time.** The right question is "what was true when the event
  happened?", not "what is true now?".
- **Reference data changes on a schedule.** Each publication is a full picture of current
  state; what changed is implicit and must be derived.
- **Reprocessing must give the same answer every time.** Late events, backfills and
  re-runs must produce identical results regardless of when they run.
- **Speed matters.** Event volumes are high, so answers must be near-instant and must not
  stall while new data is being loaded.
- **Common workarounds fall short.** Keeping only the latest data loses history. Keeping
  every daily copy is wasteful. Querying a historical database per event is too slow.

---

## 4. Product promise

1. **Time-correct answers.** Any lookup can be pinned to a business date and time.
2. **Deterministic replay.** The same question about the same moment always gets the same
   answer, so past results can be reproduced and audited.
3. **Very fast.** Lookups are fast enough for high-volume event processing and are never
   blocked by data refreshes.
4. **Efficient with history.** Long history is affordable because only changes are
   remembered, not repeated copies of unchanged data.
5. **Easy to adopt.** A team describes where its data comes from and what counts as a
   meaningful change. Everything else is handled for them.
6. **Flexible within a narrow core.** Not tied to telecom or to any one industry, but
   flexibility comes from a few well-chosen extension points, not from supporting
   everything.

**Guiding principle:** remember *changes over time*, not repeated full copies.

**Honest limits to communicate to users:**
- Precision follows how often data is published. With daily files, a change is known only
  to the day it first appeared.
- Removals count as changes. Once something is removed, later lookups must report it as
  gone rather than keep returning its last value.

---

## 5. Business-level decisions

Each decision lists the realistic options and, where one exists, the current leaning.
None are final.

### 5.1 Target audience and positioning
- **Decided:** an open-source project with no commercial aims, aimed at teams that have
  file- or table-based reference data and no temporal platform.
- **Consequences:** the project must stay small enough for one or two maintainers.
  Clarity of the pitch, quality of documentation and test rigor matter more than breadth
  of features. Licensing and contribution model are still to be chosen.

### 5.2 How data arrives
- **Options:** (a) full periodic publications only; (b) streams of already-computed
  changes only; (c) both.
- **Leaning:** support both, since real organizations have both. Treat full publications
  as the primary case because that is the proven origin.

### 5.3 How precise "as of" needs to be
- **Options:** (a) day-level; (b) finer where the source provides exact timestamps.
- **Leaning:** start with whatever precision the source can defend, and state that limit
  plainly. Revisit once real sub-daily needs appear.

### 5.4 One timeline or two ("what was true" vs. "what we knew")
- **Options:** (a) a single timeline of when things were true; (b) a second timeline of
  when we *learned* it, which supports corrections to past data and audit questions.
- **Tension:** (b) answers more questions but makes the product harder to understand and
  to use.
- **Leaning:** start with (a), and keep the door open to (b) later.

### 5.5 Handling corrections to past data
- **Options:** (a) not supported initially; (b) supported by reprocessing the affected
  period; (c) supported as a first-class concept (tied to 5.4).
- **Open:** how common corrections are in the target use cases.

### 5.6 Scale and cost profile
- **Question:** what sizes of data and history must be supported (thousands of records,
  or hundreds of millions)? What lookup speed is "fast enough"?
- **Why it matters:** this largely determines how simple or how sophisticated the
  solution has to be, and should be answered with real numbers before design.

### 5.7 Deployment shape
- **Options:** (a) a library embedded in each application; (b) a shared service that many
  applications call; (c) both.
- **Trade-off:** embedding gives the fastest answers and the simplest operation. A shared
  service gives one place to manage data but adds a network hop.
- **Leaning:** embedded only. A shared service adds operational scope that a small
  open-source project should not take on.

### 5.8 Restarts and keeping data fresh
- **Questions:** how quickly must new publications become visible? Is a fast restart
  important, or is rebuilding from source acceptable?

### 5.9 Platform and language
- **Options:** start on one platform, or be platform-neutral from day one.
- **Leaning:** the concept is platform-neutral. The first implementation choice is
  deferred until the decisions above are settled.

### 5.10 Operability expectations
- **Questions:** what visibility do operators need (freshness of data, size, speed, size
  of each change set)? What are the failure expectations (what if a publication is late,
  malformed, or missing)?

---

## 6. Open questions

- Final product name.
- Real-world volume, history length and speed targets (5.6).
- How often past data is corrected, and how much that matters (5.4, 5.5).
- Whether any target use case truly needs sub-daily precision (5.3).
- Embedded versus shared service (5.7).
- First platform and language, once the above are known (5.9).
- Licensing and contribution model (5.1).
- The supported size range, stated honestly in the documentation (5.6).

---

## 7. Use cases

Anywhere a fast stream of timestamped events must be checked against slowly changing
reference data:

- **Telecom routing:** was this number routable when the call happened?
- **Finance:** which exchange rate or security details applied on the trade date?
- **Access and entitlements:** was this person authorized when they acted?
- **Retail and catalogs:** what price or description was live at order time?
- **Network and security:** who owned this address when the log entry was written?
- **Configuration history:** which settings governed this request when it ran?

---

## 8. Alternatives and why a new product

| Alternative | Strength | Why it falls short here |
| --- | --- | --- |
| Keep only the latest data | Simple | Cannot answer historical questions or replay |
| Keep every full copy | Simple, complete | Wasteful, and storage grows with every publication |
| Historical or time-travel database | Durable, ad hoc queries, unlimited history | Too slow and heavyweight for per-event lookups |
| Slowly-changing-dimension tables | Familiar to data teams | Same latency problem on the hot path |
| Stream processors with temporal joins | Solve the same problem inside a streaming pipeline | Heavy to adopt for teams that do not already run one, and not usable from ordinary services |
| As-of joins in analytics tools | Mature point-in-time semantics | Built for analysis and batch work, not per-event lookups inside a service |
| Hand-built code (database table plus local cache) | Quick to start | Each team rebuilds the diffing, refresh and edge cases, usually inconsistently |

**The gap:** a lightweight, fast, easy-to-adopt way to ask "what was true then?" over
periodically published data, with changes worked out automatically and no temporal
database required. Existing historical databases could also act as *sources* of data for
Chronomap rather than competitors.

### 8.1 Where it fits in the ecosystem

It is an **in-process library** inside the consuming service, at the seam between
"reference data arrives" and "business rule runs."

**Upstream (what feeds it).** It consumes what already exists and replaces none of it:
file drops from object storage or SFTP, relational or warehouse tables, change streams
from CDC tools, and time-travel tables in lakehouse formats.

**Downstream (where it is called).** Anywhere an event handler needs a reference lookup:
stream consumers, web or microservice request handlers, batch and replay jobs, and
serverless functions (with cold-start caveats).

| Neighbor | Relationship |
| --- | --- |
| In-memory caches | Closest cousin. They hold the *current* value; this is a cache aware of *time*. |
| Stream processors | An alternative for teams already running one. This is for teams that do not, and for services that are not streaming pipelines. |
| Temporal and lakehouse stores | Complementary. They are the durable system of record and can act as sources; this is the fast read path. |
| Warehouse history tables | Same logic, different purpose. They serve analytics; this serves the hot path. |
| Change-data-capture tools | Upstream. They produce the change streams this can consume. |
| Master data management | Upstream governance. It decides what the truth is; this serves it by time. |
| Batch engines | Used for backfills and replay. How the timeline is shared with workers needs design work. |

**Integration surface to plan for.** Adoption comes from working with what teams already
have, so the connectors matter as much as the core: file and table sources, a first
application-framework integration, operational metrics (above all data freshness, which is
what operators actually page on), and a test and replay helper that makes deterministic
replay easy to demonstrate.

### 8.2 When not to use it

- The dataset is too large for one process's memory.
- You need ad hoc temporal queries or analytics. Use a temporal store or warehouse.
- You must track corrections as "what we knew when" (the two-timeline case, 5.4).
- You already run a stream processor with temporal joins and have no need for another
  moving part.

The documentation should state this plainly. It builds trust and saves adopters time.

---

## 9. Non-goals (for now)

- No technology, architecture or implementation choices are made in this document.
- No commitment to the "what we knew" timeline or to corrections handling in the first
  release.
- Not a general-purpose database, an analytics engine, or an audit system of record.
- No shared-service mode.
- No multi-language ports unless the community contributes them.
- No attempt to support every source, storage option or timeline model. Features are
  added only when real adopters ask, to keep the project maintainable by a small team.

---

## 10. Next steps

1. Write the flagship-scenario quickstart (1.2) as a README draft before any code.
2. Resolve the highest-impact decisions: scale and speed targets (5.6) and data arrival
   (5.2). If real volumes exceed what one process can hold, stop and reconsider.
3. Find two or three outside teams with the same pain to act as design partners, and
   confirm the quickstart makes sense to them.
4. Choose the first language and licensing deliberately (5.9, 5.1).
5. Only then, begin architectural design and record decisions as ADRs.
