// PROTOTYPE (throwaway): shared types.
import java.math.BigDecimal;
record Price(String sku, BigDecimal price, String currency) {
    static Price fromCsv(String[] c) { return new Price(c[0], new BigDecimal(c[1]), c[2]); }
}
