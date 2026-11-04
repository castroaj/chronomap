package io.github.castroaj.chronomap;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * What asserted a lookup's answer (ADR 0002).
 *
 * @param outcome whether the key was present, deleted or not found
 * @param changeDate the date of the version that introduced the answer; empty when {@code outcome}
 *     is {@link Outcome#NOT_FOUND}
 * @param versionRef the source's opaque reference to that version; empty when {@code outcome} is
 *     {@link Outcome#NOT_FOUND}
 * @param clamp which bound the as-of date was moved to, if any
 * @param clampDistance the whole days between the as-of date and the bound; zero when {@code clamp}
 *     is {@link Clamp#NONE}
 * @param effectiveDate the as-of date after clamping
 */
public record Provenance(
        Outcome outcome,
        Optional<LocalDate> changeDate,
        Optional<String> versionRef,
        Clamp clamp,
        Duration clampDistance,
        LocalDate effectiveDate) {
    public Provenance {
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(changeDate, "changeDate must not be null");
        Objects.requireNonNull(versionRef, "versionRef must not be null");
        Objects.requireNonNull(clamp, "clamp must not be null");
        Objects.requireNonNull(clampDistance, "clampDistance must not be null");
        Objects.requireNonNull(effectiveDate, "effectiveDate must not be null");
    }
}
