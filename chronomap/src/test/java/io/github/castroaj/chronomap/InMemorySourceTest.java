package io.github.castroaj.chronomap;

import java.time.LocalDate;
import java.util.List;

class InMemorySourceTest extends VersionSourceContract {
    @Override
    VersionSource<?> sourceWithOneVersion() {
        return InMemorySource.of(
                new Version<>("v1", LocalDate.parse("2026-01-01"), List.of("a", "b")));
    }
}
