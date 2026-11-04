package io.github.castroaj.chronomap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ChronomapGetTest {
    record Price(String sku, String amount) {}

    private static final LocalDate JAN_1 = LocalDate.parse("2026-01-01");

    private static final LocalDate JAN_10 = LocalDate.parse("2026-01-10");
    private static final LocalDate JAN_20 = LocalDate.parse("2026-01-20");
    private static final LocalDate FEB_1 = LocalDate.parse("2026-02-01");

    @SafeVarargs
    @SuppressWarnings("varargs")
    private static Chronomap<String, Price> pricesFrom(Version<Price>... versions) {
        return Chronomap.<String, Price>table("prices")
                .key(Price::sku)
                .source(InMemorySource.of(versions))
                .build();
    }

    private static Version<Price> version(LocalDate date, Price... rows) {
        return new Version<>("prices-" + date, date, List.of(rows));
    }

    @Test
    void getFollowsCreateUpdateAndKeepsTheAnswerBetweenChanges() {
        Price created = new Price("A1", "10.00");
        Price updated = new Price("A1", "12.00");
        Chronomap<String, Price> prices =
                pricesFrom(
                        version(JAN_1, created),
                        version(JAN_10, created),
                        version(JAN_20, updated));

        assertEquals(Optional.of(created), prices.get("A1", JAN_1));
        assertEquals(Optional.of(created), prices.get("A1", LocalDate.parse("2026-01-19")));
        assertEquals(Optional.of(updated), prices.get("A1", JAN_20));
    }

    @Test
    void getIsAbsentBeforeAKeyIsCreated() {
        Price b2 = new Price("B2", "5.00");
        Chronomap<String, Price> prices =
                pricesFrom(version(JAN_1, new Price("A1", "10.00")), version(JAN_10, b2));

        assertEquals(Optional.empty(), prices.get("B2", LocalDate.parse("2026-01-09")));
        assertEquals(Optional.of(b2), prices.get("B2", JAN_10));
    }

    @Test
    void getIsAbsentFromTheVersionThatDeletesAKey() {
        Price a1 = new Price("A1", "10.00");
        Chronomap<String, Price> prices =
                pricesFrom(version(JAN_1, a1), version(JAN_10, new Price("B2", "5.00")));

        assertEquals(Optional.of(a1), prices.get("A1", LocalDate.parse("2026-01-09")));
        assertEquals(Optional.empty(), prices.get("A1", JAN_10));
    }

    @Test
    void getReturnsTheNewRowWhenADeletedKeyIsReAdded() {
        Price first = new Price("A1", "10.00");
        Price readded = new Price("A1", "11.00");
        Price b2 = new Price("B2", "5.00");
        Chronomap<String, Price> prices =
                pricesFrom(
                        version(JAN_1, first), version(JAN_10, b2), version(JAN_20, readded, b2));

        assertEquals(Optional.of(first), prices.get("A1", JAN_1));
        assertEquals(Optional.empty(), prices.get("A1", JAN_10));
        assertEquals(Optional.of(readded), prices.get("A1", JAN_20));
    }

    @Test
    void getClampsDatesOutsideTheVersionRange() {
        Price oldest = new Price("A1", "10.00");
        Price newest = new Price("A1", "12.00");
        Chronomap<String, Price> prices =
                pricesFrom(version(JAN_10, oldest), version(JAN_20, newest));

        assertEquals(Optional.of(oldest), prices.get("A1", JAN_1));
        assertEquals(Optional.of(newest), prices.get("A1", FEB_1));
    }

    @Test
    void clampingBeforeTheOldestVersionDoesNotInventAKeyCreatedLater() {
        Price a1 = new Price("A1", "10.00");
        Chronomap<String, Price> prices =
                pricesFrom(version(JAN_10, a1), version(JAN_20, a1, new Price("B2", "5.00")));

        assertEquals(Optional.empty(), prices.get("B2", JAN_1));
    }

    @Test
    void getIsAbsentEverywhereForAnEmptySource() {
        Chronomap<String, Price> prices = pricesFrom();

        assertEquals(Optional.empty(), prices.get("A1", JAN_1));
    }

    record Route(String region, String sku) {}

    record RouteRow(Route route, String carrier) {}

    @Test
    void getSupportsCompositeKeys() {
        RouteRow eu = new RouteRow(new Route("EU", "A1"), "dhl");
        RouteRow euLater = new RouteRow(eu.route(), "fedex");
        RouteRow us = new RouteRow(new Route("US", "A1"), "ups");
        Chronomap<Route, RouteRow> routes =
                Chronomap.<Route, RouteRow>table("routes")
                        .key(RouteRow::route)
                        .source(
                                InMemorySource.of(
                                        new Version<>("r1", JAN_1, List.of(eu, us)),
                                        new Version<>("r2", JAN_10, List.of(euLater, us))))
                        .build();

        assertEquals(Optional.of(eu), routes.get(eu.route(), JAN_1));
        assertEquals(Optional.of(euLater), routes.get(eu.route(), JAN_10));
        assertEquals(Optional.of(us), routes.get(us.route(), JAN_10));
        assertEquals(Optional.empty(), routes.get(new Route("EU", "Z9"), JAN_1));
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
