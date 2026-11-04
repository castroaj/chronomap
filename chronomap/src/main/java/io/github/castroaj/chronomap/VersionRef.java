package io.github.castroaj.chronomap;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Names one version of a table type. The {@code id} is an opaque pointer only the {@link
 * VersionSource} understands; the {@code checksum} lets a source report edits to a loaded version
 * (ADR 0003). It is not a security check. Every component must be non-null.
 *
 * @param id opaque pointer to the version, meaningful only to its source
 * @param date the date the version is as of
 * @param checksum changes when the version's rows are edited
 */
public record VersionRef(String id, LocalDate date, String checksum) {
    public VersionRef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(checksum, "checksum");
    }
}
