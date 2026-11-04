package io.github.castroaj.chronomap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ChronomapBuildStreamTest {
    private static final VersionRef REF =
            new VersionRef("v1", LocalDate.parse("2026-01-01"), "sum");

    /** A source whose stream records close and can fail after the first row or on close. */
    private static final class ProbeSource implements VersionSource<String> {
        final AtomicBoolean closed = new AtomicBoolean();
        final boolean failMidStream;
        final boolean failOnClose;

        ProbeSource(boolean failMidStream, boolean failOnClose) {
            this.failMidStream = failMidStream;
            this.failOnClose = failOnClose;
        }

        @Override
        public List<VersionRef> list(LocalDate after) {
            return List.of(REF);
        }

        @Override
        public Stream<String> read(VersionRef ref) {
            return Stream.of("a", "b")
                    .peek(
                            row -> {
                                if (failMidStream && row.equals("b")) {
                                    throw new IllegalStateException("bad row");
                                }
                            })
                    .onClose(
                            () -> {
                                closed.set(true);
                                if (failOnClose) {
                                    throw new IllegalStateException("close failed");
                                }
                            });
        }
    }

    private static Chronomap.Builder<String, String> builderOver(ProbeSource source) {
        return Chronomap.<String, String>table("t").key(s -> s).source(source);
    }

    @Test
    void buildClosesTheStreamOnSuccess() {
        ProbeSource source = new ProbeSource(false, false);

        Chronomap<String, String> map = builderOver(source).build();

        assertTrue(source.closed.get());
        assertEquals("a", map.get("a", REF.date()).orElseThrow());
    }

    @Test
    void buildClosesTheStreamAndKeepsTheCauseWhenTheStreamFailsMidway() {
        ProbeSource source = new ProbeSource(true, false);

        IllegalStateException e =
                assertThrows(IllegalStateException.class, () -> builderOver(source).build());

        assertTrue(source.closed.get());
        assertEquals("bad row", e.getCause().getMessage());
    }

    @Test
    void aFailureWhileClosingFailsTheVersion() {
        ProbeSource source = new ProbeSource(false, true);

        IllegalStateException e =
                assertThrows(IllegalStateException.class, () -> builderOver(source).build());

        assertEquals("close failed", e.getCause().getMessage());
    }
}
