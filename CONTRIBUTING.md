# Contributing

## Issue first

Every pull request starts from a GitHub issue. Open one (or comment on an existing one)
and wait for it to be triaged before writing code. Pull requests without a linked issue
are closed.

- Branches: `feature/{issue-number}-{issue-name}`, for example
  `feature/12-version-source-contract`.
- Read `GLOSSARY.md` and the ADRs in `docs/adr/` first. They are binding for v0.1.

## Building

You need JDK 17 or newer. The Gradle wrapper does the rest.

```
./gradlew build
```

This compiles with `--release 17`, `-Xlint:all -Werror`, runs the tests, checks
formatting and writes a JaCoCo coverage report. Coverage is reported, never enforced.

Fix formatting with `./gradlew spotlessApply` (google-java-format).

## Dependencies

Dependency locking and checksum verification are on. After changing a dependency:

```
./gradlew build --write-locks --write-verification-metadata sha256
```

Commit the updated `gradle.lockfile` and `gradle/verification-metadata.xml`. CI fails on
a stale lockfile or a checksum mismatch.

Core (`chronomap`) must never depend on a framework. The build fails if it imports or
depends on `org.springframework.*`.
