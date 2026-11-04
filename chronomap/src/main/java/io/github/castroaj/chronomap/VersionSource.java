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
     * <p>Lifetime (ADR 0005): the caller owns the returned stream and closes it, so a source that
     * holds a file handle or cursor releases it from {@link Stream#onClose}, and only there. Each
     * call returns a fresh, single-use stream; the caller may close it without consuming it all,
     * and a later {@code read} of the same ref works. A {@link RuntimeException} while opening,
     * consuming or closing the stream fails the version, and the caller discards any rows it
     * already took from it. Reads are sequential; timeouts are the source's own concern.
     *
     * @param ref the version to read, as returned by {@link #list(LocalDate)}
     * @return the rows of the version
     */
    Stream<RECORD> read(VersionRef ref);
}
