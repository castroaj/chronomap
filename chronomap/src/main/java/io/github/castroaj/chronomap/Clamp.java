package io.github.castroaj.chronomap;

/** Which bound, if any, a lookup's as-of date was moved to (ADR 0002). */
public enum Clamp {
    /** The as-of date was inside the oldest and newest versions. */
    NONE,
    /** The as-of date was before the oldest version and moved to it. */
    BEFORE_OLDEST,
    /** The as-of date was after the newest version and moved to it. */
    AFTER_NEWEST
}
