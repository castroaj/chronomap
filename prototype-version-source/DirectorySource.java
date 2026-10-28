// PROTOTYPE (throwaway): reference implementation candidate 1. Directory of dated CSV files.
// Date comes from the file name; "prices-2026-10-01.csv". Header row skipped.
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.regex.*;
import java.util.stream.Stream;

class DirectorySource<V> implements VersionSource<V> {
    private final Path dir;
    private final Pattern namePattern;          // one capture group: yyyy-MM-dd
    private final Function<String[], V> parser;

    DirectorySource(Path dir, String prefix, Function<String[], V> parser) {
        this.dir = dir;
        this.namePattern = Pattern.compile(Pattern.quote(prefix) + "(\\d{4}-\\d{2}-\\d{2})\\.csv");
        this.parser = parser;
    }

    @Override public List<VersionRef> list(LocalDate after) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString())
                .map(namePattern::matcher).filter(Matcher::matches)
                .map(m -> new VersionRef(m.group(0), LocalDate.parse(m.group(1))))
                .filter(r -> after == null || r.date().isAfter(after))
                .sorted(Comparator.comparing(VersionRef::date))
                .toList();
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    @Override public Stream<V> read(VersionRef ref) {
        try {
            // Parse eagerly so a bad row fails the WHOLE version before any row is handed out.
            List<V> rows = new ArrayList<>();
            try (Stream<String> lines = Files.lines(dir.resolve(ref.id())).skip(1)) {
                for (String line : (Iterable<String>) lines::iterator) {
                    try { rows.add(parser.apply(line.split(",", -1))); }
                    catch (RuntimeException e) { throw new MalformedVersionException(ref, "bad row: " + line, e); }
                }
            }
            return rows.stream();
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
}
