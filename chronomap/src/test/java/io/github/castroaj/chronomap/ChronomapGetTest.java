package io.github.castroaj.chronomap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChronomapGetTest {
    record Price(String sku, String amount) {}

    private static final LocalDate JAN_1 = LocalDate.parse("2026-01-01");

    private static Chronomap<String, Price> pricesFrom(Version<Price> version) {
        return Chronomap.<String, Price>table("prices")
                .key(Price::sku)
                .source(InMemorySource.of(version))
                .build();
    }

    @Test
    void getReturnsTheRowFromTheBuiltVersion() {
        Price a1 = new Price("A1", "10.00");
        Chronomap<String, Price> prices =
                pricesFrom(
                        new Version<>(
                                "prices-2026-01-01", JAN_1, List.of(a1, new Price("B2", "5.00"))));

        assertEquals(Optional.of(a1), prices.get("A1", JAN_1));
    }

    @Test
    void getReturnsEmptyForAnUnknownKey() {
        Chronomap<String, Price> prices =
                pricesFrom(
                        new Version<>(
                                "prices-2026-01-01", JAN_1, List.of(new Price("A1", "10.00"))));

        assertEquals(Optional.empty(), prices.get("Z9", JAN_1));
    }
}
