package io.github.castroaj.chronomap;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * A cache that answers "what was true for this key on this date?" (ADR 0006).
 *
 * <p>Build one per table type with {@link #table(String)}.
 *
 * @param <KEY> the key type that identifies a row
 * @param <RECORD> the row type of the table type
 */
public final class Chronomap<KEY, RECORD> {
    private final Map<KEY, RECORD> rows;

    private Chronomap(Map<KEY, RECORD> rows) {
        this.rows = rows;
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
     * Looks up the row for {@code key} as of {@code asOf}.
     *
     * @param key the key to look up
     * @param asOf the date to answer for
     * @return the row, or empty when the key has no row then
     * @throws NullPointerException if {@code key} or {@code asOf} is null
     */
    public Optional<RECORD> get(KEY key, LocalDate asOf) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(asOf, "asOf must not be null");
        return Optional.ofNullable(rows.get(key));
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
         * @throws UnsupportedOperationException if the source lists more than one version
         */
        public Chronomap<KEY, RECORD> build() {
            List<VersionRef> refs = source.list(null);
            if (refs.size() > 1) {
                throw new UnsupportedOperationException(
                        "Replaying several versions needs the timeline");
            }
            Map<KEY, RECORD> rows = new HashMap<>();
            for (VersionRef ref : refs) {
                source.read(ref).forEach(row -> rows.put(keyExtractor.apply(row), row));
            }
            return new Chronomap<>(rows);
        }
    }
}
