// PROTOTYPE (throwaway). Variant B: declarative. The record carries the table type; the quickstart is two lines.
import java.lang.annotation.*;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

public class VariantB {
    // ---- stub API ----
    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
    @interface TableType { String name(); String[] key(); }   // composite key = several fields
    interface VersionSource {}
    static class DirectorySource { static VersionSource csv(Path dir) { return null; } }
    interface ChronoCache<V> {
        Optional<V> get(LocalDate asOf, Object... keyParts);
    }
    static class Chronomap {
        static <V> ChronoCache<V> open(Class<V> type, VersionSource source) { return null; }
    }

    // ---- the quickstart ----
    // (Price would be annotated: @TableType(name = "prices", key = {"sku"}) record Price(...))
    static void quickstart(Order order) {
        ChronoCache<Price> prices = Chronomap.open(Price.class, DirectorySource.csv(Dirs.PRICES));

        Optional<Price> price = prices.get(order.date(), order.sku());
        // composite key: prices.get(date, order.sku(), order.region())
    }
}
