import java.util.*;

class Proc {
    String name; int arrival, burst, priority;
    int remaining, start = -1, finish, tier = 1, tier2Since = -1;

    Proc(String n, int a, int b, int p) { name = n; arrival = a; burst = b; priority = p; remaining = b; }

    Proc copy() { return new Proc(name, arrival, burst, priority); }
    int tat() { return finish - arrival; }   
    int wt()  { return tat() - burst; }     
    int rt()  { return start - arrival; }    

    static List<Proc> sortedCopies(List<Proc> in) {
        List<Proc> ps = new ArrayList<>();
        for (Proc p : in) ps.add(p.copy());
        ps.sort(Comparator.comparingInt(p -> p.arrival));
        return ps;
    }
}
