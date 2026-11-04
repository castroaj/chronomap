package io.github.castroaj.chronomap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** What every {@link VersionSource} must honour (ADR 0005); each source's test extends it. */
abstract class VersionSourceContract {
    /** A source holding one version with at least one row. */
    abstract VersionSource<?> sourceWithOneVersion();

    @Test
    void aVersionCanBeReadAgainAfterTheFirstStreamIsClosed() {
        VersionSource<?> source = sourceWithOneVersion();
        VersionRef ref = source.list(null).get(0);

        List<?> first = readAll(source.read(ref));
        List<?> second = readAll(source.read(ref));

        assertFalse(first.isEmpty());
        assertEquals(first, second);
    }

    @Test
    void aStreamCanBeClosedWithoutConsumingIt() {
        VersionSource<?> source = sourceWithOneVersion();
        VersionRef ref = source.list(null).get(0);

        source.read(ref).close();

        assertFalse(readAll(source.read(ref)).isEmpty());
    }

    private static List<?> readAll(Stream<?> stream) {
        try (stream) {
            return stream.collect(Collectors.toList());
        }
    }
}
