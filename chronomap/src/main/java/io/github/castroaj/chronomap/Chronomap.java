package io.github.castroaj.chronomap;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A cache that answers "what was true for this key on this date?" (ADR 0006).
 *
 * <p>Build one per table type with {@link #table(String)}. The cache stores a dated change only
 * when a row differs from the key's previous row by {@code equals}, so {@code RECORD} must be a
 * value-equal type (a record, for instance); a row type with identity {@code equals} records a
 * change for every row of every version.
 *
 * @param <KEY> the key type that identifies a row; a value-equal type, which may be composite
 * @param <RECORD> the row type of the table type; a value-equal type
 */
public final class Chronomap<KEY, RECORD> {
    private final Timeline<KEY, RECORD> timeline;

    private Chronomap(Timeline<KEY, RECORD> timeline) {
        this.timeline = timeline;
    }

    /**
     * Starts building a cache for one table type.
     *
     * @param name the name of the table type
     * @param <KEY> the key type that identifies a row
     * @param <RECORD> the row type of the table type
     * @return a builder for the table type
     * @throws NullPointerException if {@code name} is null
     */
    public static <KEY, RECORD> Builder<KEY, RECORD> table(String name) {
        return new Builder<>(name);
    }

    /**
     * Looks up the row for {@code key} as of {@code asOf}. A date before the oldest version or
     * after the newest is moved to that bound.
     *
     * @param key the key to look up
     * @param asOf the date to answer for
     * @return the row, or empty when the key was never seen, not yet created, or deleted then
     * @throws NullPointerException if {@code key} or {@code asOf} is null
     */
    public Optional<RECORD> get(KEY key, LocalDate asOf) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(asOf, "asOf must not be null");
        return timeline.get(key, asOf);
    }

    /**
     * Configures and builds a {@link Chronomap}.
     *
     * @param <KEY> the key type that identifies a row
     * @param <RECORD> the row type of the table type
     */
    public static final class Builder<KEY, RECORD> {
        private final String name;
        private Function<RECORD, KEY> keyExtractor;
        private VersionSource<RECORD> source;

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "name must not be null");
        }

        /**
         * Sets how a row's key is read from the row.
         *
         * @param keyExtractor returns the key of a row
         * @return this builder
         * @throws NullPointerException if {@code keyExtractor} is null
         */
        public Builder<KEY, RECORD> key(Function<RECORD, KEY> keyExtractor) {
            this.keyExtractor =
                    Objects.requireNonNull(keyExtractor, "keyExtractor must not be null");
            return this;
        }

        /**
         * Sets where the dated versions come from.
         *
         * @param source supplies the versions of the table type
         * @return this builder
         * @throws NullPointerException if {@code source} is null
         */
        public Builder<KEY, RECORD> source(VersionSource<RECORD> source) {
            this.source = Objects.requireNonNull(source, "source must not be null");
            return this;
        }

        /**
         * Loads the history from the source and returns the cache. Blocks until the history is
         * loaded.
         *
         * @return the built cache
         * @throws IllegalArgumentException if the source lists two versions with the same date
         * @throws IllegalStateException if reading a version fails; the cause is preserved and the
         *     version's stream is closed
         */
        public Chronomap<KEY, RECORD> build() {
            List<Timeline.Snapshot<KEY, RECORD>> snapshots = new ArrayList<>();
            for (VersionRef ref : source.list(null)) {
                snapshots.add(new Timeline.Snapshot<>(ref.date(), readVersion(ref)));
            }
            return new Chronomap<>(Timeline.of(snapshots));
        }

        /**
         * Reads one whole version into its own map, so a failed version contributes nothing. The
         * stream is closed on every path (ADR 0005), and a failure while opening, consuming or
         * closing it fails the version.
         */
        private Map<KEY, RECORD> readVersion(VersionRef ref) {
            Map<KEY, RECORD> versionRows = new HashMap<>();
            try (Stream<RECORD> stream = source.read(ref)) {
                stream.forEach(row -> versionRows.put(keyExtractor.apply(row), row));
            } catch (RuntimeException e) {
                throw new IllegalStateException("Failed to read version " + ref.id(), e);
            }
            return versionRows;
        }
    }
}
