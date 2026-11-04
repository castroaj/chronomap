package io.github.castroaj.chronomap;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * One dated, full copy of a table type, held in memory. Every component must be non-null.
 *
 * @param id names the version within its source
 * @param date the date the version is as of
 * @param rows every row of the version; copied defensively
 * @param <RECORD> the row type of the table type
 */
public record Version<RECORD>(String id, LocalDate date, List<RECORD> rows) {
    public Version {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(date, "date must not be null");
        rows = List.copyOf(rows);
    }
}
