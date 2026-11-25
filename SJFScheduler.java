import java.util.*;

/** Preemptive Shortest-Job-First (shortest remaining time first), with context-switch cost. */
class SJFScheduler {
    static Result run(List<Proc> in, int cs) {
        List<Proc> ps = Proc.sortedCopies(in); Result r = new Result(ps);
        List<Proc> ready = new ArrayList<>();
        Proc cur = null, last = null, target = null;
        int idx = 0, done = 0, csLeft = 0; boolean skip = false;
        for (int t = 0; done < ps.size(); t++) {
            while (idx < ps.size() && ps.get(idx).arrival <= t) ready.add(ps.get(idx++));
            if (csLeft == 0 && !skip) {                       
                Proc best = null;
                for (Proc p : ready)
                    if (best == null || p.remaining < best.remaining || (p.remaining == best.remaining && p == cur)) best = p;
                if (best == null) cur = null;
                else if (best != cur) {
                    if (last != null && last != best && cs > 0) { csLeft = cs; target = best; cur = null; }
                    else { cur = best; last = best; }
                }
            }
            skip = false;
            String cpu = csLeft > 0 ? "CS -> " + target.name : cur != null ? cur.name : "Idle";
            StringJoiner sj = new StringJoiner(", ", "Ready: [", "]");
            for (Proc p : ready) sj.add(p.name + " (remaining: " + p.remaining + ")");
            r.rows.add(new String[]{"" + t, cpu, ready.isEmpty() ? "Ready: [Empty]" : sj.toString(), "-"});
            if (csLeft > 0) { if (--csLeft == 0) { cur = target; last = target; skip = true; } }
            else if (cur != null) {
                if (cur.start < 0) cur.start = t;
                if (--cur.remaining == 0) { cur.finish = t + 1; ready.remove(cur); cur = null; done++; }
            }
        }
        r.rows.add(new String[]{"" + r.rows.size(), "Done", "Ready: [Empty]", "-"});
        return r;
    }
}
