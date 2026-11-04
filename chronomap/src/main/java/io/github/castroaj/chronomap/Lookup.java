package io.github.castroaj.chronomap;

import java.util.Objects;
import java.util.Optional;

/**
 * The answer to an as-of lookup plus what asserted it (ADR 0002).
 *
 * @param value the row, or empty when the key was never seen, not yet created, or deleted
 * @param provenance what asserted the answer
 * @param <RECORD> the row type of the table type
 */
public record Lookup<RECORD>(Optional<RECORD> value, Provenance provenance) {
    public Lookup {
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(provenance, "provenance must not be null");
    }
}
