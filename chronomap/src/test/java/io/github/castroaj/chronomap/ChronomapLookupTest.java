package io.github.castroaj.chronomap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChronomapLookupTest {
    record Price(String sku, String amount) {}

    private static final LocalDate JAN_1 = LocalDate.parse("2026-01-01");
    private static final LocalDate JAN_10 = LocalDate.parse("2026-01-10");
    private static final LocalDate JAN_15 = LocalDate.parse("2026-01-15");
    private static final LocalDate JAN_20 = LocalDate.parse("2026-01-20");
    private static final LocalDate FEB_1 = LocalDate.parse("2026-02-01");

    private static final Price A1 = new Price("A1", "10.00");
    private static final Price A1_UPDATED = new Price("A1", "12.00");
    private static final Price B2 = new Price("B2", "5.00");

    private static Chronomap<String, Price> pricesFrom(VersionSource<Price> source) {
        return Chronomap.<String, Price>table("prices").key(Price::sku).source(source).build();
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    private static Chronomap<String, Price> pricesFrom(Version<Price>... versions) {
        return pricesFrom(InMemorySource.of(versions));
    }

    private static Version<Price> version(LocalDate date, Price... rows) {
        return new Version<>("prices-" + date, date, List.of(rows));
    }

    @Test
    void presentReportsTheVersionThatIntroducedTheRow() {
        Chronomap<String, Price> prices =
                pricesFrom(version(JAN_1, A1), version(JAN_10, A1), version(JAN_20, A1_UPDATED));

        Lookup<Price> result = prices.lookup("A1", JAN_15);

        assertEquals(Optional.of(A1), result.value());
        assertEquals(
                new Provenance(
                        Outcome.PRESENT,
                        Optional.of(JAN_1),
                        Optional.of("prices-" + JAN_1),
                        Clamp.NONE,
                        Duration.ZERO,
                        JAN_15),
                result.provenance());
    }

    @Test
    void presentOnTheDayOfAnUpdateReportsTheUpdatingVersion() {
        Chronomap<String, Price> prices =
                pricesFrom(version(JAN_1, A1), version(JAN_20, A1_UPDATED));

        Provenance provenance = prices.lookup("A1", JAN_20).provenance();

        assertEquals(Outcome.PRESENT, provenance.outcome());
        assertEquals(Optional.of(JAN_20), provenance.changeDate());
        assertEquals(Optional.of("prices-" + JAN_20), provenance.versionRef());
    }

    @Test
    void deletedReportsTheTombstoneVersion() {
        Chronomap<String, Price> prices = pricesFrom(version(JAN_1, A1), version(JAN_10, B2));

        Lookup<Price> result = prices.lookup("A1", JAN_15);

        assertEquals(Optional.empty(), result.value());
        assertEquals(Outcome.DELETED, result.provenance().outcome());
        assertEquals(Optional.of(JAN_10), result.provenance().changeDate());
        assertEquals(Optional.of("prices-" + JAN_10), result.provenance().versionRef());
    }

    @Test
    void notFoundForAnUnknownKeyHasNoChangeFields() {
        Chronomap<String, Price> prices = pricesFrom(version(JAN_1, A1));

        Lookup<Price> result = prices.lookup("Z9", JAN_1);

        assertEquals(Optional.empty(), result.value());
        assertEquals(
                new Provenance(
                        Outcome.NOT_FOUND,
                        Optional.empty(),
                        Optional.empty(),
                        Clamp.NONE,
                        Duration.ZERO,
                        JAN_1),
                result.provenance());
    }

    @Test
    void notFoundBeforeAKeyIsCreated() {
        Chronomap<String, Price> prices = pricesFrom(version(JAN_1, A1), version(JAN_10, A1, B2));

        Provenance provenance = prices.lookup("B2", LocalDate.parse("2026-01-09")).provenance();

        assertEquals(Outcome.NOT_FOUND, provenance.outcome());
        assertEquals(Optional.empty(), provenance.changeDate());
        assertEquals(Optional.empty(), provenance.versionRef());
    }

    @Test
    void aDateBeforeTheOldestVersionClampsToItAndReportsTheDistance() {
        Chronomap<String, Price> prices =
                pricesFrom(version(JAN_10, A1), version(JAN_20, A1_UPDATED));

        Lookup<Price> result = prices.lookup("A1", JAN_1);

        assertEquals(Optional.of(A1), result.value());
        assertEquals(Clamp.BEFORE_OLDEST, result.provenance().clamp());
        assertEquals(Duration.ofDays(9), result.provenance().clampDistance());
        assertEquals(JAN_10, result.provenance().effectiveDate());
        assertEquals(Optional.of(JAN_10), result.provenance().changeDate());
    }

    @Test
    void aDateAfterTheNewestVersionClampsToItAndReportsTheDistance() {
        Chronomap<String, Price> prices =
                pricesFrom(version(JAN_10, A1), version(JAN_20, A1_UPDATED));

        Lookup<Price> result = prices.lookup("A1", FEB_1);

        assertEquals(Optional.of(A1_UPDATED), result.value());
        assertEquals(Clamp.AFTER_NEWEST, result.provenance().clamp());
        assertEquals(Duration.ofDays(12), result.provenance().clampDistance());
        assertEquals(JAN_20, result.provenance().effectiveDate());
    }

    @Test
    void theBoundsThemselvesAreNotClamped() {
        Chronomap<String, Price> prices = pricesFrom(version(JAN_10, A1), version(JAN_20, A1));

        for (LocalDate bound : List.of(JAN_10, JAN_20)) {
            Provenance provenance = prices.lookup("A1", bound).provenance();
            assertEquals(Clamp.NONE, provenance.clamp());
            assertEquals(Duration.ZERO, provenance.clampDistance());
            assertEquals(bound, provenance.effectiveDate());
        }
    }

    @Test
    void clampIsReportedWhateverTheOutcome() {
        Chronomap<String, Price> prices = pricesFrom(version(JAN_10, A1), version(JAN_20, B2));

        Provenance notFound = prices.lookup("B2", JAN_1).provenance();
        assertEquals(Outcome.NOT_FOUND, notFound.outcome());
        assertEquals(Clamp.BEFORE_OLDEST, notFound.clamp());
        assertEquals(Duration.ofDays(9), notFound.clampDistance());

        Provenance deleted = prices.lookup("A1", FEB_1).provenance();
        assertEquals(Outcome.DELETED, deleted.outcome());
        assertEquals(Clamp.AFTER_NEWEST, deleted.clamp());
        assertEquals(Optional.of(JAN_20), deleted.changeDate());
    }

    @Test
    void anEmptySourceIsNotFoundWithoutAClamp() {
        Chronomap<String, Price> prices = pricesFrom();

        assertEquals(
                new Provenance(
                        Outcome.NOT_FOUND,
                        Optional.empty(),
                        Optional.empty(),
                        Clamp.NONE,
                        Duration.ZERO,
                        JAN_1),
                prices.lookup("A1", JAN_1).provenance());
    }

    @Test
    void versionRefIsStableAcrossRebuilds() {
        InMemorySource<Price> source =
                InMemorySource.of(
                        version(JAN_1, A1), version(JAN_10, B2), version(JAN_20, A1_UPDATED, B2));

        Chronomap<String, Price> first = pricesFrom(source);
        Chronomap<String, Price> rebuilt = pricesFrom(source);

        for (String key : List.of("A1", "B2", "Z9")) {
            for (LocalDate asOf : List.of(JAN_1, JAN_10, JAN_15, JAN_20, FEB_1)) {
                assertEquals(first.lookup(key, asOf), rebuilt.lookup(key, asOf), key + " " + asOf);
            }
        }
    }

    @Test
    void versionRefIsTheSourcesOpaqueId() {
        Chronomap<String, Price> prices =
                pricesFrom(new Version<>("opaque/ref#7", JAN_1, List.of(A1)));

        assertEquals(
                Optional.of("opaque/ref#7"), prices.lookup("A1", JAN_1).provenance().versionRef());
    }

    @Test
    void lookupAgreesWithGet() {
        Chronomap<String, Price> prices =
                pricesFrom(version(JAN_1, A1), version(JAN_10, B2), version(JAN_20, A1_UPDATED));

        for (String key : List.of("A1", "B2", "Z9")) {
            for (LocalDate asOf : List.of(JAN_1, JAN_10, JAN_15, JAN_20, FEB_1)) {
                assertEquals(prices.get(key, asOf), prices.lookup(key, asOf).value());
            }
        }
    }

    @Test
    void lookupRejectsNullArguments() {
        Chronomap<String, Price> prices = pricesFrom(version(JAN_1, A1));

        assertThrows(NullPointerException.class, () -> prices.lookup(null, JAN_1));
        assertThrows(NullPointerException.class, () -> prices.lookup("A1", null));
    }
}
