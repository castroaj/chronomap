# One repo, three modules, and the sources live in core

The layout below is binding for v0.1.

- `chronomap`: core, published. It holds the builder, the timeline, the three sources (directory, JDBC, in-memory) and the Micrometer and SLF4J integration. Its only runtime dependencies are `micrometer-core` and `slf4j-api` (ADR 0004).
- `chronomap-test`: the replay and assertion helper, published, depends on core. It keeps assertion and diff APIs off every user's production classpath.
- `chronomap-benchmarks`: the JMH suite, not published, depends on core.
- One shared version, no BOM. No `module-info.java`; `Automatic-Module-Name: io.github.castroaj.chronomap` only.
- Coordinates `io.github.castroaj:chronomap`; v0.1 is distributed through GitHub Releases, not Maven Central.
- Core never depends on a framework. A later Spring Boot starter would be a separate module over the public builder.

## Considered alternatives

- **The helper in core or README-only.** Rejected: it pollutes production classpaths, or leaves replay and diff hand-rolled per project.
- **Sources as separate artifacts.** Deferred: a split can come later.
- **JPMS from the start.** Rejected: it adds friction for little gain at v0.1.
