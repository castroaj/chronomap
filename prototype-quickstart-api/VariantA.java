// PROTOTYPE (throwaway). Variant A: explicit builder. Everything is spelled out; nothing is magic.
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Function;

public class VariantA {
    // ---- stub API ----
    enum Malformed { THROW, SKIP_AND_LOG }
    interface VersionSource<V> {}
    static class DirectorySource {
        static <V> VersionSource<V> csv(Path dir, String filePattern, Function<String[], V> parser) { return null; }
    }
    interface ChronoCache<K, V> { Optional<V> get(K key, LocalDate asOf); void close(); }
    static class Chronomap {
        static <K, V> Builder<K, V> table(String name) { return new Builder<>(); }
    }
    static class Builder<K, V> {
        Builder<K, V> key(Function<V, K> f) { return this; }
        Builder<K, V> source(VersionSource<V> s) { return this; }
        Builder<K, V> onMalformed(Malformed m) { return this; }
        Builder<K, V> pollEvery(Duration d) { return this; }
        ChronoCache<K, V> build() { return null; }   // replays all versions oldest -> newest
    }

    // ---- the quickstart ----
    static void quickstart(Order order) {
        ChronoCache<String, Price> prices = Chronomap.<String, Price>table("prices")
            .key(Price::sku)
            .source(DirectorySource.csv(Dirs.PRICES, "prices-%tF.csv", Price::fromCsv))
            .onMalformed(Malformed.THROW)
            .pollEvery(Duration.ofMinutes(15))
            .build();

        Optional<Price> price = prices.get(order.sku(), order.date());
    }
}
