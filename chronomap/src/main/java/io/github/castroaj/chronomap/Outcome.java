package io.github.castroaj.chronomap;

/** What a lookup found for a key on its effective date (ADR 0002). */
public enum Outcome {
    /** The key had a row on the effective date. */
    PRESENT,
    /** The key's latest dated change on the effective date is a tombstone. */
    DELETED,
    /** No dated change exists for the key at or before the effective date. */
    NOT_FOUND
}
