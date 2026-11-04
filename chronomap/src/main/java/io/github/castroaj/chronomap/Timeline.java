package io.github.castroaj.chronomap;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Many versions diffed into one sorted sequence of dated changes per key (ADR 0006). A change is
 * appended only when a row differs from the key's previous row by {@code equals}; a key missing
 * from a version is closed with a tombstone. Days are stored as epoch days, so precision is one
 * day.
 *
 * @param <KEY> the key type that identifies a row
 * @param <RECORD> the row type of the table type
 */
final class Timeline<KEY, RECORD> {
    /**
     * One version's rows by key, as of a date.
     *
     * @param date the date the version is as of
     * @param versionRef the source's opaque reference to the version
     * @param rows every row of the version by key
     * @param <KEY> the key type that identifies a row
     * @param <RECORD> the row type of the table type
     */
    record Snapshot<KEY, RECORD>(LocalDate date, String versionRef, Map<KEY, RECORD> rows) {
        Snapshot {
            Objects.requireNonNull(date, "date must not be null");
            Objects.requireNonNull(versionRef, "versionRef must not be null");
            Objects.requireNonNull(rows, "rows must not be null");
        }

        /** A snapshot whose version reference is its date, for callers that do not track one. */
        Snapshot(LocalDate date, Map<KEY, RECORD> rows) {
            this(date, String.valueOf(date), rows);
        }
    }

    /** The version that introduced a change, shared by every change it introduced. */
    private record Stamp(LocalDate date, String versionRef) {}

    /**
     * Parallel arrays of change days, rows and introducing versions, sorted by day; a null row is a
     * tombstone.
     */
    private static final class KeyTimeline<RECORD> {
        private int[] days = new int[1];
        private Object[] rows = new Object[1];
        private Stamp[] stamps = new Stamp[1];
        private int size;

        void add(int day, RECORD row, Stamp stamp) {
            if (size == days.length) {
                days = Arrays.copyOf(days, size * 2);
                rows = Arrays.copyOf(rows, size * 2);
                stamps = Arrays.copyOf(stamps, size * 2);
            }
            days[size] = day;
            rows[size] = row;
            stamps[size] = stamp;
            size++;
        }

        /** Index of the last change on or before {@code day}, or -1 when there is none. */
        int floor(int day) {
            int low = 0;
            int high = size - 1;
            int found = -1;
            while (low <= high) {
                int mid = (low + high) >>> 1;
                if (days[mid] <= day) {
                    found = mid;
                    low = mid + 1;
                } else {
                    high = mid - 1;
                }
            }
            return found;
        }
    }

    private final Map<KEY, KeyTimeline<RECORD>> timelines = new HashMap<>();

    /** The rows of the newest version, the baseline the next version is diffed against. */
    private Map<KEY, RECORD> newestRows = Map.of();

    private boolean loaded;
    private int oldest;
    private int newest;

    private Timeline() {}

    /**
     * Builds a timeline from versions given in any order.
     *
     * @param snapshots the versions to replay; sorted by date first
     * @param <KEY> the key type that identifies a row
     * @param <RECORD> the row type of the table type
     * @return the timeline
     * @throws IllegalArgumentException if two versions share a date
     */
    static <KEY, RECORD> Timeline<KEY, RECORD> of(Collection<Snapshot<KEY, RECORD>> snapshots) {
        List<Snapshot<KEY, RECORD>> sorted = new ArrayList<>(snapshots);
        sorted.sort(Comparator.comparing(Snapshot::date));
        Timeline<KEY, RECORD> timeline = new Timeline<>();
        sorted.forEach(timeline::append);
        return timeline;
    }

    /**
     * Appends one version newer than every version already held.
     *
     * @param snapshot the version to diff against the newest one
     * @throws IllegalArgumentException if the version is not strictly newer than the newest
     * @throws NullPointerException if any row is null
     */
    void append(Snapshot<KEY, RECORD> snapshot) {
        int day = Math.toIntExact(snapshot.date().toEpochDay());
        if (loaded && day <= newest) {
            throw new IllegalArgumentException(
                    "Version "
                            + snapshot.date()
                            + " is not newer than the newest version "
                            + LocalDate.ofEpochDay(newest));
        }
        snapshot.rows()
                .values()
                .forEach(row -> Objects.requireNonNull(row, "row must not be null"));
        Stamp stamp = new Stamp(snapshot.date(), snapshot.versionRef());

        snapshot.rows()
                .forEach(
                        (key, row) -> {
                            RECORD previous = newestRows.get(key);
                            if (previous == null || !previous.equals(row)) {
                                timeline(key).add(day, row, stamp);
                            }
                        });
        newestRows.keySet().stream()
                .filter(key -> !snapshot.rows().containsKey(key))
                .forEach(key -> timeline(key).add(day, null, stamp));

        newestRows = new HashMap<>(snapshot.rows());
        if (!loaded) {
            oldest = day;
            loaded = true;
        }
        newest = day;
    }

    /**
     * Answers the row for {@code key} as of {@code asOf}, clamping dates outside the oldest and
     * newest version to that bound.
     *
     * @param key the key to look up
     * @param asOf the date to answer for
     * @return the row, or empty when the key was never seen, not yet created or deleted
     */
    Optional<RECORD> get(KEY key, LocalDate asOf) {
        KeyTimeline<RECORD> timeline = timelines.get(key);
        if (timeline == null) {
            return Optional.empty();
        }
        int index = timeline.floor(clampedDay(asOf.toEpochDay()));
        if (index < 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(row(timeline, index));
    }

    /**
     * Answers like {@link #get} and reports what asserted the answer (ADR 0002).
     *
     * @param key the key to look up
     * @param asOf the date to answer for
     * @return the row and its provenance
     */
    Lookup<RECORD> lookup(KEY key, LocalDate asOf) {
        long asOfDay = asOf.toEpochDay();
        Clamp clamp = Clamp.NONE;
        Duration distance = Duration.ZERO;
        LocalDate effective = asOf;
        if (loaded && asOfDay < oldest) {
            clamp = Clamp.BEFORE_OLDEST;
            distance = Duration.ofDays(oldest - asOfDay);
            effective = LocalDate.ofEpochDay(oldest);
        } else if (loaded && asOfDay > newest) {
            clamp = Clamp.AFTER_NEWEST;
            distance = Duration.ofDays(asOfDay - newest);
            effective = LocalDate.ofEpochDay(newest);
        }

        KeyTimeline<RECORD> timeline = timelines.get(key);
        int index = timeline == null ? -1 : timeline.floor((int) effective.toEpochDay());
        if (index < 0) {
            return new Lookup<>(
                    Optional.empty(),
                    new Provenance(
                            Outcome.NOT_FOUND,
                            Optional.empty(),
                            Optional.empty(),
                            clamp,
                            distance,
                            effective));
        }
        RECORD row = row(timeline, index);
        Stamp stamp = timeline.stamps[index];
        return new Lookup<>(
                Optional.ofNullable(row),
                new Provenance(
                        row == null ? Outcome.DELETED : Outcome.PRESENT,
                        Optional.of(stamp.date()),
                        Optional.of(stamp.versionRef()),
                        clamp,
                        distance,
                        effective));
    }

    private int clampedDay(long day) {
        return (int) Math.max(oldest, Math.min(newest, day));
    }

    @SuppressWarnings("unchecked")
    private static <RECORD> RECORD row(KeyTimeline<RECORD> timeline, int index) {
        return (RECORD) timeline.rows[index];
    }

    /** The number of dated changes held for {@code key}, tombstones included. */
    int changeCount(KEY key) {
        KeyTimeline<RECORD> timeline = timelines.get(key);
        return timeline == null ? 0 : timeline.size;
    }

    private KeyTimeline<RECORD> timeline(KEY key) {
        return timelines.computeIfAbsent(key, k -> new KeyTimeline<>());
    }
}
