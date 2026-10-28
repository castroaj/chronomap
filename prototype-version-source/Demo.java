// PROTOTYPE (throwaway): drives SHAPE X the way a build + refresh would. Prints, applies nothing real.
import java.nio.file.*;
import java.time.LocalDate;

public class Demo {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("PROTOTYPE-prices-wipe-me");
        w(dir, "prices-2026-10-01.csv", "sku,price,currency\nA1,10.00,USD\nB2,5.50,USD");
        w(dir, "prices-2026-10-02.csv", "sku,price,currency\nA1,10.00,USD\nB2,5.75,USD");
        w(dir, "prices-2026-10-03.csv", "sku,price,currency\nA1,ten,USD\nB2,5.75,USD");   // malformed
        w(dir, "notes.txt", "ignored");
        VersionSource<Price> src = new DirectorySource<>(dir, "prices-", Price::fromCsv);

        System.out.println("== build: list(null) then read oldest -> newest ==");
        LocalDate lastApplied = null;
        for (VersionRef ref : src.list(null)) {
            try (var rows = src.read(ref)) {
                System.out.println("applied " + ref.date() + " rows=" + rows.count());
                lastApplied = ref.date();
            } catch (MalformedVersionException e) {
                System.out.println("SKIP_AND_LOG: " + e.getMessage());   // whole version dropped
            }
        }
        System.out.println("lastApplied=" + lastApplied);

        w(dir, "prices-2026-10-04.csv", "sku,price,currency\nA1,11.00,USD\nB2,5.75,USD");
        System.out.println("== refresh: list(after=" + lastApplied + ") ==");
        System.out.println(src.list(lastApplied));
        System.out.println("(note: skipped 10-03 would be re-listed if we tracked lastListed instead of lastApplied)");
    }
    static void w(Path d, String n, String s) throws Exception { Files.writeString(d.resolve(n), s); }
}
