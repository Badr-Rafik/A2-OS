import java.util.*;

/** Output of one simulation: one row per time unit + the finished processes. */
class Result {
    List<Proc> procs;
    List<String[]> rows = new ArrayList<>();   // {time, cpu, tier1, tier2}

    Result(List<Proc> p) { procs = p; }

    double avgWT()  { return procs.stream().mapToInt(Proc::wt).average().orElse(0); }
    double avgTAT() { return procs.stream().mapToInt(Proc::tat).average().orElse(0); }
    double avgRT()  { return procs.stream().mapToInt(Proc::rt).average().orElse(0); }

    /** Console output: step table, per-process metrics and averages. */
    static void print(Result r) {
        for (String[] w : r.rows) System.out.printf("%-4s %-8s %s | %s%n", w[0], w[1], w[2], w[3]);
        System.out.printf("%n%-8s %-8s %-8s %-11s %-8s %-9s%n", "Process", "Arrival", "Finish", "Turnaround", "Waiting", "Response");
        for (Proc p : r.procs)
            System.out.printf("%-8s %-8d %-8d %-11d %-8d %-9d%n", p.name, p.arrival, p.finish, p.tat(), p.wt(), p.rt());
        System.out.printf("Avg Turnaround = %.2f | Avg Waiting = %.2f | Avg Response = %.2f%n%n", r.avgTAT(), r.avgWT(), r.avgRT());
    }
}
