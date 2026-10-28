// PROTOTYPE (throwaway): shared types for the quickstart API variants. Not production code.
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;

record Price(String sku, BigDecimal price, String currency) {
    static Price fromCsv(String[] c) { return new Price(c[0], new BigDecimal(c[1]), c[2]); }
}

record Order(String orderId, String sku, LocalDate date) {}

class Dirs { static final Path PRICES = Path.of("/data/prices"); } // prices-2026-10-01.csv, ...
