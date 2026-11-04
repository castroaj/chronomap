package io.github.castroaj.chronomap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

class TimelineTest {
    private static final LocalDate START = LocalDate.parse("2026-01-01");

    private static Timeline.Snapshot<String, String> day(int offset, Map<String, String> rows) {
        return new Timeline.Snapshot<>(START.plusDays(offset), rows);
    }

    /** Versions with creates, an update, a no-op, a delete and a re-add. */
    private static List<Timeline.Snapshot<String, String>> history() {
        return List.of(
                day(0, Map.of("a", "1", "b", "1")),
                day(3, Map.of("a", "1", "b", "2")),
                day(5, Map.of("b", "2", "c", "1")),
                day(9, Map.of("a", "2", "b", "2", "c", "1")),
                day(12, Map.of("a", "2", "c", "3")));
    }

    private static void assertSameAnswers(
            Timeline<String, String> expected, Timeline<String, String> actual) {
        for (String key : List.of("a", "b", "c", "never")) {
            for (int offset = -3; offset <= 16; offset++) {
                LocalDate asOf = START.plusDays(offset);
                assertEquals(
                        expected.get(key, asOf), actual.get(key, asOf), key + " as of " + asOf);
            }
        }
    }

    @Test
    void fullRebuildShuffledRebuildAndIncrementalAppendGiveIdenticalAnswers() {
        Timeline<String, String> full = Timeline.of(history());

        for (long seed = 0; seed < 25; seed++) {
            List<Timeline.Snapshot<String, String>> shuffled = new ArrayList<>(history());
            Collections.shuffle(shuffled, new Random(seed));
            assertSameAnswers(full, Timeline.of(shuffled));
        }

        Timeline<String, String> incremental = Timeline.of(history().subList(0, 2));
        for (Timeline.Snapshot<String, String> next : history().subList(2, 5)) {
            incremental.append(next);
        }
        assertSameAnswers(full, incremental);
    }

    @Test
    void anUnchangedRowAddsNoDatedChange() {
        Timeline<String, String> timeline =
                Timeline.of(List.of(day(0, Map.of("a", "1")), day(4, Map.of("a", "1"))));

        assertEquals(1, timeline.changeCount("a"));
    }

    @Test
    void appendRejectsAVersionNotNewerThanTheNewest() {
        Timeline<String, String> timeline = Timeline.of(List.of(day(5, Map.of("a", "1"))));

        assertThrows(IllegalArgumentException.class, () -> timeline.append(day(5, Map.of())));
        assertThrows(IllegalArgumentException.class, () -> timeline.append(day(2, Map.of())));
    }

    @Test
    void aFullBuildRejectsTwoVersionsOnTheSameDate() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Timeline.of(List.of(day(1, Map.of("a", "1")), day(1, Map.of("a", "2")))));
    }

    @Test
    void anEmptyTimelineAnswersAbsent() {
        assertEquals(Optional.empty(), Timeline.<String, String>of(List.of()).get("a", START));
    }
}
