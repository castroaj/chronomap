// PROTOTYPE (throwaway), compile-only. SHAPE Y: single method returning lazy versions.
import java.time.LocalDate;
import java.util.stream.Stream;

interface LazyVersion<V> { LocalDate date(); Stream<V> rows(); }

interface VersionSourceY<V> {
    /** Oldest first, rows opened lazily. One call does listing and reading. */
    Stream<LazyVersion<V>> versionsAfter(LocalDate after);
}
