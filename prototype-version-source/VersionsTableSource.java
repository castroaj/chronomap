// PROTOTYPE (throwaway): reference implementation candidate 2, the ORIGIN design, modeled in memory.
//
//   versions(table_type, version_date, data_table)      <- written by an ingestion service, one row per arrived file,
//                                                          inserted only AFTER the data table is complete
//   PRICES_2026_10_01(sku, price, currency)             <- one data table per version; the versions row points at it
//
// list(after) = SELECT version_date, data_table FROM versions
//               WHERE table_type = ? AND version_date > ? ORDER BY version_date
// read(ref)   = SELECT * FROM <ref.id()>        (ref.id() IS the pointer; identifier must be validated, never concatenated raw)
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

class FakeDb {
    record VersionRow(String tableType, LocalDate versionDate, String dataTable) {}
    final List<VersionRow> versions = new ArrayList<>();
    final Map<String, List<String>> tables = new HashMap<>();      // data table name -> csv-ish rows
    void ingest(String type, LocalDate date, List<String> rows) {  // what the ingestion service does
        String name = type.toUpperCase() + "_" + date.toString().replace('-', '_');
        tables.put(name, rows);                                     // 1) data table first
        versions.add(new VersionRow(type, date, name));             // 2) then the discovery row
    }
}

class VersionsTableSource<V> implements VersionSource<V> {
    private static final Pattern SAFE_IDENT = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");
    private final FakeDb db; private final String tableType; private final Function<String[], V> parser;
    VersionsTableSource(FakeDb db, String tableType, Function<String[], V> parser) {
        this.db = db; this.tableType = tableType; this.parser = parser;
    }
    @Override public List<VersionRef> list(LocalDate after) {
        return db.versions.stream()
            .filter(v -> v.tableType().equals(tableType))
            .filter(v -> after == null || v.versionDate().isAfter(after))
            .sorted(Comparator.comparing(FakeDb.VersionRow::versionDate))
            .map(v -> new VersionRef(v.dataTable(), v.versionDate()))
            .toList();
    }
    @Override public Stream<V> read(VersionRef ref) {
        if (!SAFE_IDENT.matcher(ref.id()).matches())
            throw new MalformedVersionException(ref, "unsafe data table identifier", null);
        List<V> out = new ArrayList<>();
        for (String line : db.tables.get(ref.id())) {
            try { out.add(parser.apply(line.split(",", -1))); }
            catch (RuntimeException e) { throw new MalformedVersionException(ref, "bad row: " + line, e); }
        }
        return out.stream();
    }
}
