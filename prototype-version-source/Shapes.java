// PROTOTYPE (throwaway): version-source interface shapes. Not production code.
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

/** One stored version of a table type: a date plus an opaque id the source understands. */
record VersionRef(String id, LocalDate date) {}

class MalformedVersionException extends RuntimeException {
    MalformedVersionException(VersionRef ref, String why, Throwable cause) {
        super("malformed version " + ref.id() + " (" + ref.date() + "): " + why, cause);
    }
}

/**
 * SHAPE X (two methods): cheap listing, then read one version at a time.
 * The build lists once; a refresh lists with after = lastAppliedDate.
 */
interface VersionSource<V> {
    /** Versions dated strictly after `after` (null = all), oldest first. Names and dates only; no row IO. */
    List<VersionRef> list(LocalDate after);

    /** All rows of one version. Caller must close the stream. Unparseable version -> MalformedVersionException (whole version, never row-by-row). */
    Stream<V> read(VersionRef ref);
}
