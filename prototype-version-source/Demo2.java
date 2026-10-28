// PROTOTYPE (throwaway): the same build/refresh loop, now over the versions-table source.
import java.time.LocalDate;

public class Demo2 {
    public static void main(String[] a) {
        FakeDb db = new FakeDb();
        db.ingest("prices", LocalDate.of(2026,10,1), java.util.List.of("A1,10.00,USD","B2,5.50,USD"));
        db.ingest("prices", LocalDate.of(2026,10,2), java.util.List.of("A1,10.00,USD","B2,5.75,USD"));
        db.ingest("taxes",  LocalDate.of(2026,10,2), java.util.List.of("x"));          // other table type, same versions table
        VersionSource<Price> src = new VersionsTableSource<>(db, "prices", Price::fromCsv);

        LocalDate cursor = null;
        for (VersionRef ref : src.list(null)) {
            try (var rows = src.read(ref)) { System.out.println("applied " + ref + " rows=" + rows.count()); }
            cursor = ref.date();
        }
        db.ingest("prices", LocalDate.of(2026,10,3), java.util.List.of("A1,11.00,USD"));
        System.out.println("refresh after " + cursor + ": " + src.list(cursor));
    }
}
