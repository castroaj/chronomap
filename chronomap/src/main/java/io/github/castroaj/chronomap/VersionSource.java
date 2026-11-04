package io.github.castroaj.chronomap;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

/**
 * Supplies the dated versions of one table type (ADR 0005).
 *
 * @param <RECORD> the row type of the table type
 */
public interface VersionSource<RECORD> {
    /**
     * Lists versions dated after {@code after} (all of them when null), oldest first. Names, dates
     * and checksums only, no row IO. A source returns the same {@link VersionRef} for the same
     * version across restarts and rebuilds.
     *
     * @param after only versions dated strictly after this date are listed; null lists all
     * @return the matching versions, oldest first
     */
    List<VersionRef> list(LocalDate after);

    /**
     * Reads one whole version. Any bad row fails the whole version.
     *
     * @param ref the version to read, as returned by {@link #list(LocalDate)}
     * @return the rows of the version
     */
    Stream<RECORD> read(VersionRef ref);
}
