import java.util.*;

class ACSScheduler {
    static int eff(Proc p, int t, int aging) { return Math.max(0, p.priority - (t - p.tier2Since) / aging); }

    static String f(Proc p, int t, int aging) {
        return p.name + " (remaining: " + p.remaining + ", Priority: " + (p.tier == 2 ? eff(p, t, aging) : p.priority) + ")";
    }

    static String tierStr(int n, Collection<Proc> q, Proc shown, int t, int aging) {
        List<Proc> l = new ArrayList<>();
        if (shown != null && shown.tier == n) l.add(shown);
        l.addAll(q);
        if (l.isEmpty()) return "Tier " + n + ": [Empty]";
        StringJoiner sj = new StringJoiner(", ", "Tier " + n + ": [", "]");
        for (Proc p : l) sj.add(f(p, t, aging));
        return sj.toString();
    }

    static Result run(List<Proc> in, int q, int cs, int aging) {
        if (aging <= 0) throw new IllegalArgumentException("Aging interval must be above 0.");
        List<Proc> ps = Proc.sortedCopies(in); Result r = new Result(ps);
        Deque<Proc> t1 = new ArrayDeque<>(); List<Proc> t2 = new ArrayList<>();
        Proc run = null, last = null, target = null;
        int idx = 0, done = 0, slice = 0, csLeft = 0;
        for (int t = 0; done < ps.size(); t++) {
            while (idx < ps.size() && ps.get(idx).arrival <= t) { Proc p = ps.get(idx++); p.tier = 1; t1.add(p); }
            if (csLeft == 0 && run == null) {                
                Proc nxt = null;
                if (!t1.isEmpty()) nxt = t1.poll();           
                else if (!t2.isEmpty()) {                     
                    nxt = t2.get(0);
                    for (Proc p : t2) if (eff(p, t, aging) < eff(nxt, t, aging)) nxt = p;
                    t2.remove(nxt); nxt.tier = 2;
                }
                if (nxt != null) {
                    slice = 0;
                    if (last != null && last != nxt && cs > 0) { csLeft = cs; target = nxt; }
                    else { run = nxt; last = nxt; }
                }
            }
            Proc shown = run != null ? run : (csLeft > 0 ? target : null);
            String cpu = csLeft > 0 ? "CS -> " + target.name : run != null ? run.name : "Idle";
            r.rows.add(new String[]{"" + t, cpu, tierStr(1, t1, shown, t, aging), tierStr(2, t2, shown, t, aging)});
            if (csLeft > 0) { if (--csLeft == 0) { run = target; last = target; slice = 0; } }
            else if (run != null) {
                if (run.start < 0) run.start = t;
                run.remaining--; slice++;
                if (run.remaining == 0) { run.finish = t + 1; run = null; done++; }
                else if (run.tier == 1 && slice >= q) {       
                    run.tier = 2; run.tier2Since = t + 1; t2.add(run); run = null;
                }
            }
        }
        
        r.rows.add(new String[]{"" + r.rows.size(), "Done", "Tier 1: [Empty]", "Tier 2: [Empty]"});
        return r;
    }
}
