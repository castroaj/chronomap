// PROTOTYPE (throwaway). Run: java prototype-timeline/Timeline.java prototype-timeline/Demo.java  (or: cd prototype-timeline && javac *.java && java Demo)
import java.util.*;

public class Demo {
    static Timeline.Version v(int day, String... kv) {
        var m = new LinkedHashMap<String, Timeline.Row>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], new Timeline.Row(kv[i], kv[i + 1]));
        return new Timeline.Version(day, m);
    }
    public static void main(String[] a) {
        // days 1..6. A: stable. B: price change day3. C: deleted day4, re-added day6 at new price. D: created day3. E: unchanged row, absent never.
        var versions = List.of(
            v(1, "A", "10", "B", "5", "C", "7"),
            v(2, "A", "10", "B", "5", "C", "7"),
            v(3, "A", "10", "B", "6", "C", "7", "D", "1"),
            v(4, "A", "10", "B", "6", "D", "1"),
            v(5, "A", "10", "B", "6", "D", "1"),
            v(6, "A", "10", "B", "6", "C", "9", "D", "1"));
        var t = new Timeline(); t.append(versions);
        System.out.println("== timelines (dated changes; unchanged days store nothing)");
        for (var k : List.of("A", "B", "C", "D", "Z")) System.out.println(k + " " + t.changes(k));
        System.out.println("\n== as-of grid (day -1 and 99 exercise clamping)");
        int[] days = {-1, 1, 2, 3, 4, 5, 6, 99};
        System.out.print("key  "); for (int d : days) System.out.printf("%6d", d); System.out.println();
        for (var k : List.of("A", "B", "C", "D", "Z")) {
            System.out.printf("%-5s", k);
            for (int d : days) System.out.printf("%6s", t.get(k, d).map(Timeline.Row::price).orElse("-"));
            System.out.println();
        }
        // replay: full rebuild twice, and incremental (first 3 then rest) must all give identical answers
        var t2 = new Timeline(); t2.append(versions);
        var shuffled = new ArrayList<>(versions); Collections.shuffle(shuffled, new Random(1));
        var t3 = new Timeline(); t3.append(shuffled);
        var t4 = new Timeline(); t4.append(versions.subList(0, 3)); t4.append(versions.subList(3, 6));
        boolean same = true;
        for (var k : List.of("A", "B", "C", "D", "Z")) for (int d = -2; d <= 100; d++) {
            var x = t.get(k, d);
            same &= x.equals(t2.get(k, d)) && x.equals(t3.get(k, d)) && x.equals(t4.get(k, d));
        }
        System.out.println("\n== replay identical (rebuild, shuffled input, incremental append): " + same);

        // throughput: 300k keys, 730 daily versions generated lazily, ~0.1% churn/day
        int keys = 300_000, days2 = 730; var rnd = new Random(42);
        String[] ks = new String[keys]; String[] price = new String[keys];
        for (int i = 0; i < keys; i++) { ks[i] = "SKU" + i; price[i] = "p" + i; }
        var big = new Timeline(); long b0 = System.nanoTime();
        Map<String, Timeline.Row> cur = new HashMap<>();
        for (int i = 0; i < keys; i++) cur.put(ks[i], new Timeline.Row(ks[i], price[i]));
        for (int d = 0; d < days2; d++) {
            for (int c = 0; c < keys / 1000; c++) { int i = rnd.nextInt(keys); price[i] = "p" + rnd.nextInt(); cur.put(ks[i], new Timeline.Row(ks[i], price[i])); }
            if (d % 97 == 50) cur.remove(ks[rnd.nextInt(keys)]);
            big.append(List.of(new Timeline.Version(d, new HashMap<>(cur))));
        }
        long changes = big.timelines.values().stream().mapToLong(x -> x.n).sum();
        System.out.printf("%n== build: %d keys x %d versions -> %d dated changes (%.1f/key) in %.1fs%n", keys, days2, changes, changes / (double) keys, (System.nanoTime() - b0) / 1e9);
        Runtime rt = Runtime.getRuntime(); System.gc();
        System.out.printf("heap used ~%d MB%n", (rt.totalMemory() - rt.freeMemory()) >> 20);
        int N = 5_000_000; int[] qk = new int[N], qd = new int[N];
        for (int i = 0; i < N; i++) { qk[i] = rnd.nextInt(keys); qd[i] = rnd.nextInt(days2 + 20) - 10; }
        long hit = 0;
        for (int i = 0; i < N; i++) if (big.get(ks[qk[i]], qd[i]).isPresent()) hit++;   // warmup
        long t0 = System.nanoTime();
        for (int i = 0; i < N; i++) if (big.get(ks[qk[i]], qd[i]).isPresent()) hit++;
        double s = (System.nanoTime() - t0) / 1e9;
        System.out.printf("single-thread: %,.0f lookups/s (target ~13,000) hits=%d%n", N / s, hit);
    }
}
