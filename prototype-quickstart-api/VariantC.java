// PROTOTYPE (throwaway). Variant C: date-bound view + provenance. Built for replay and audit.
// Pin the date once, then do many lookups; every answer says what it was based on.
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.function.Function;

public class VariantC {
    // ---- stub API ----
    record Provenance(LocalDate versionDate, LocalDate changeDate, boolean clamped) {}
    record Lookup<V>(V valueOrNull, Provenance provenance) {
        boolean present() { return valueOrNull != null; }
    }
    interface View<K, V> { Lookup<V> get(K key); }
    interface ChronoCache<K, V> { View<K, V> asOf(LocalDate date); }
    interface VersionSource<V> {}
    static class DirectorySource {
        static <V> VersionSource<V> csv(Path dir, Function<String[], V> parser) { return null; }
    }
    static class Chronomap {
        static <K, V> ChronoCache<K, V> load(String table, Function<V, K> key, VersionSource<V> src) { return null; }
    }

    // ---- the quickstart ----
    static void quickstart(Order order) {
        var prices = Chronomap.load("prices", Price::sku, DirectorySource.csv(Dirs.PRICES, Price::fromCsv));

        var result = prices.asOf(order.date()).get(order.sku());
        if (result.present()) {
            Price p = result.valueOrNull();
            System.out.println(p + " via " + result.provenance());   // version used, change date, clamped?
        }
    }
}
