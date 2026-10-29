// PROTOTYPE (throwaway): replay/test helper shapes. Answers ticket #14. Run: cd prototype-replay && javac *.java && java Demo
import java.util.*;
import java.util.function.*;

public class Demo {
    record Row(String sku, String price) {}
    static Timeline.Row r(String s, String p) { return new Timeline.Row(s, p); }

    // ---- Shape A: in-core `Replay` helper (ships in main artifact) ----
    /** Build from dated versions, assert as-of answers, replay an event log, diff two builds. */
    static final class Replay {
        final Timeline tl = new Timeline();
        record Event(String key, int day) {}
        record Answer(Event e, Optional<Timeline.Row> row) {}
        static Replay of(List<Timeline.Version> versions) { var x = new Replay(); x.tl.append(versions); return x; }
        Replay assertAsOf(String key, int day, Timeline.Row expected) {
            var got = tl.get(key, day).orElse(null);
            if (!Objects.equals(got, expected)) throw new AssertionError(key + " @" + day + ": expected " + expected + " got " + got);
            return this;
        }
        Replay assertAbsent(String key, int day) { return assertAsOf(key, day, null); }
        List<Answer> run(List<Event> events) { return events.stream().map(e -> new Answer(e, tl.get(e.key, e.day))).toList(); }
        /** Every (key, day) over the union of keys and [from,to] where two builds disagree. */
        static List<String> diff(Replay a, Replay b, int from, int to) {
            var keys = new TreeSet<>(a.tl.timelines.keySet()); keys.addAll(b.tl.timelines.keySet());
            var out = new ArrayList<String>();
            for (var k : keys) for (int d = from; d <= to; d++) {
                var x = a.tl.get(k, d); var y = b.tl.get(k, d);
                if (!x.equals(y)) out.add(k + " @" + d + ": " + x.orElse(null) + " vs " + y.orElse(null));
            }
            return out;
        }
    }

    // ---- Shape C: README-only. No helper; users hand-roll with the public API. ----
    static void shapeC(List<Timeline.Version> vs) {
        var t = new Timeline(); t.append(vs);                       // stands in for Chronomap.table(..).source(listOf(vs)).build()
        if (!t.get("A1", 3).equals(Optional.of(r("A1", "12.00")))) throw new AssertionError();
        // comparing two builds and replaying an event log: ~10 lines each, copy-pasted per project
    }

    public static void main(String[] a) {
        var v1 = new Timeline.Version(1, Map.of("A1", r("A1", "10.00"), "B2", r("B2", "5.00")));
        var v3 = new Timeline.Version(3, Map.of("A1", r("A1", "12.00"), "B2", r("B2", "5.00")));
        var v5 = new Timeline.Version(5, Map.of("A1", r("A1", "12.00")));            // B2 deleted
        var versions = List.of(v1, v3, v5);

        System.out.println("== Shape A: in-core helper, fluent asserts ==");
        Replay.of(versions).assertAsOf("A1", 2, r("A1", "10.00")).assertAsOf("A1", 4, r("A1", "12.00"))
              .assertAbsent("B2", 5).assertAsOf("A1", 99, r("A1", "12.00"));          // clamp after newest
        System.out.println("asserts passed");

        System.out.println("== Replay a recorded event log twice: identical? ==");
        var events = List.of(new Replay.Event("A1", 1), new Replay.Event("A1", 4), new Replay.Event("B2", 6));
        var first = Replay.of(versions).run(events);
        var second = Replay.of(new ArrayList<>(List.of(v5, v1, v3))).run(events);     // versions handed in shuffled
        System.out.println("deterministic across input order: " + first.equals(second));
        first.forEach(x -> System.out.println("  " + x));

        System.out.println("== Compare two builds: original vs one with v3 price edited (a 'change to history') ==");
        var v3x = new Timeline.Version(3, Map.of("A1", r("A1", "11.00"), "B2", r("B2", "5.00")));
        Replay.diff(Replay.of(versions), Replay.of(List.of(v1, v3x, v5)), 1, 6).forEach(s -> System.out.println("  " + s));

        System.out.println("== Shape C: README-only ==");
        shapeC(versions); System.out.println("passed, but diff/replay are hand-rolled each time");
    }
}
