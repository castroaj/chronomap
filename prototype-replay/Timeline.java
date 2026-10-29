// PROTOTYPE (throwaway): timeline model + as-of lookup. Answers ticket "Prototype the timeline model and as-of lookup".
import java.util.*;

public class Timeline {
    record Row(String sku, String price) {}
    enum Kind { CREATE, UPDATE, DELETE }
    record Change(int day, Kind kind, Row row) {}              // row null for DELETE (tombstone)
    record Version(int day, Map<String, Row> rows) {}

    /** Per-key timeline: parallel arrays sorted by day. */
    static final class KeyTimeline {
        int[] days = new int[1]; Row[] rows = new Row[1]; Kind[] kinds = new Kind[1]; int n;
        void append(Change c) {
            if (n == days.length) { days = Arrays.copyOf(days, n * 2); rows = Arrays.copyOf(rows, n * 2); kinds = Arrays.copyOf(kinds, n * 2); }
            days[n] = c.day; rows[n] = c.row; kinds[n] = c.kind; n++;
        }
        /** index of last change with day <= d, or -1 */
        int floor(int d) {
            int lo = 0, hi = n - 1, r = -1;
            while (lo <= hi) { int m = (lo + hi) >>> 1; if (days[m] <= d) { r = m; lo = m + 1; } else hi = m - 1; }
            return r;
        }
    }

    final Map<String, KeyTimeline> timelines = new HashMap<>();
    Map<String, Row> last = new HashMap<>();   // state as of newest version, used for incremental append
    int oldest = Integer.MAX_VALUE, newest = Integer.MIN_VALUE;

    /** Replay versions oldest->newest (sorts defensively); append a dated change only when the row changes. */
    void append(List<Version> versions) {
        var sorted = new ArrayList<>(versions); sorted.sort(Comparator.comparingInt(Version::day));
        for (Version v : sorted) {
            if (v.day <= newest) throw new IllegalStateException("version " + v.day + " not newer than " + newest);
            oldest = Math.min(oldest, v.day); newest = v.day;
            for (var e : v.rows.entrySet()) {
                Row prev = last.get(e.getKey());
                if (prev == null) add(e.getKey(), new Change(v.day, Kind.CREATE, e.getValue()));
                else if (!prev.equals(e.getValue())) add(e.getKey(), new Change(v.day, Kind.UPDATE, e.getValue()));
            }
            for (var k : last.keySet()) if (!v.rows.containsKey(k)) add(k, new Change(v.day, Kind.DELETE, null));
            last = new HashMap<>(v.rows);
        }
    }
    private void add(String k, Change c) { timelines.computeIfAbsent(k, x -> new KeyTimeline()).append(c); }

    /** As-of lookup. Clamps asOf into [oldest, newest]; absent if deleted or not yet created at that date. */
    Optional<Row> get(String key, int asOf) {
        var t = timelines.get(key);
        if (t == null) return Optional.empty();
        int d = Math.max(oldest, Math.min(newest, asOf));
        int i = t.floor(d);
        if (i < 0 || t.kinds[i] == Kind.DELETE) return Optional.empty();
        return Optional.of(t.rows[i]);
    }
    List<Change> changes(String key) {
        var t = timelines.get(key); var out = new ArrayList<Change>();
        if (t != null) for (int i = 0; i < t.n; i++) out.add(new Change(t.days[i], t.kinds[i], t.rows[i]));
        return out;
    }
}
