import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

/** Modern Swing front-end for the CPU scheduling simulator (scheduling logic lives in Proc, Result, SJFScheduler and ACSScheduler). */
public class SchedulerGUI extends JFrame {
    // ---------- look & feel ----------
    static final Color BG = new Color(0xF4F6FB), PRIMARY = new Color(0x4F46E5), PRIMARY_D = new Color(0x4338CA),
            TEXT = new Color(0x111827), MUTED = new Color(0x6B7280), LINE = new Color(0xE5E7EB), GOOD = new Color(0xD1FAE5);
    static final Font F = new Font("Segoe UI", Font.PLAIN, 13), FB = F.deriveFont(Font.BOLD), FS = F.deriveFont(12f),
            FT = F.deriveFont(Font.BOLD, 15f);
    static final Color[] PAL = {new Color(0x6366F1), new Color(0xF59E0B), new Color(0x10B981), new Color(0xEF4444),
            new Color(0x06B6D4), new Color(0xA855F7), new Color(0xEC4899), new Color(0x84CC16), new Color(0x3B82F6), new Color(0xF97316)};
    static final Map<String, Color> COLORS = new LinkedHashMap<>();   // process name -> colour

    // ---------- widgets ----------
    final DefaultTableModel procModel = new DefaultTableModel(new String[]{"Process", "Arrival", "Burst", "Priority"}, 0);
    final JTable procTable = new JTable(procModel);
    final JSpinner qSp = spin(3, 1, 100), csSp = spin(0, 0, 100);
    final JComboBox<String> algo = new JComboBox<>(new String[]{"ACS - Adaptive Container Scheduler", "Preemptive SJF"});
    final RO stepModel = new RO("Time", "CPU", "Tier 1  (new / interactive jobs)", "Tier 2  (long / batch jobs)");
    final RO metModel = new RO("Process", "Arrival", "Burst", "Finish", "Turnaround", "Waiting", "Response");
    final Stat sWT = new Stat("Average waiting time", new Color(0xF59E0B)),
            sTAT = new Stat("Average turnaround time", PRIMARY), sRT = new Stat("Average response time", new Color(0x10B981));
    final Gantt gantt = new Gantt();
    final JLabel status = lbl(" ", FS, MUTED);
    Analysis analysis;

    SchedulerGUI() {
        super("CPU Scheduler Simulator");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout());
        add(header(), BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout(14, 0));
        body.setOpaque(false); body.setBorder(new EmptyBorder(14, 14, 14, 14));
        body.add(sidebar(), BorderLayout.WEST);
        body.add(tabs(), BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
        algo.addActionListener(e -> qSp.setEnabled(algo.getSelectedIndex() == 0));
        setSize(1260, 820); setMinimumSize(new Dimension(1050, 700)); setLocationRelativeTo(null);
        load(new int[][]{{0, 4, 2}, {1, 7, 3}, {3, 2, 1}}, 3, 0);
    }

    // ================= layout =================
    JComponent header() {
        JPanel h = new JPanel(new GridLayout(2, 1));
        h.setBackground(PRIMARY); h.setBorder(new EmptyBorder(14, 22, 14, 22));
        h.add(lbl("CPU Scheduler Simulator", F.deriveFont(Font.BOLD, 22f), Color.WHITE));
        h.add(lbl("Compare Preemptive SJF with the Adaptive Container Scheduler (ACS)", F, new Color(0xE0E7FF)));
        return h;
    }

    JComponent sidebar() {
        // --- card 1: processes
        Card c1 = new Card(new BorderLayout(0, 8));
        JPanel top = new JPanel(new BorderLayout(0, 4)); top.setOpaque(false);
        top.add(lbl("1   Add your processes", FT, TEXT), BorderLayout.NORTH);
        top.add(hint("Double-click a cell to edit it. A lower priority number means a more important process."), BorderLayout.CENTER);
        c1.add(top, BorderLayout.NORTH);
        style(procTable);
        JScrollPane sp = new JScrollPane(procTable); sp.setBorder(new LineBorder(LINE)); sp.getViewport().setBackground(Color.WHITE);
        c1.add(sp, BorderLayout.CENTER);
        Btn add = new Btn("+ Add", false), del = new Btn("Remove selected", false),
                e1 = new Btn("Example 1", false), e2 = new Btn("Example 2", false);
        JPanel bt = new JPanel(new GridLayout(2, 2, 6, 6)); bt.setOpaque(false);
        bt.add(add); bt.add(del); bt.add(e1); bt.add(e2);
        c1.add(bt, BorderLayout.SOUTH);
        add.addActionListener(e -> procModel.addRow(new Object[]{"P" + (procModel.getRowCount() + 1), 0, 1, 1}));
        del.addActionListener(e -> { int r = procTable.getSelectedRow(); if (r >= 0) procModel.removeRow(r); });
        e1.addActionListener(e -> load(new int[][]{{0, 4, 2}, {1, 7, 3}, {3, 2, 1}}, 3, 0));
        e2.addActionListener(e -> load(new int[][]{{0, 5, 4}, {1, 6, 2}, {4, 4, 3}}, 3, 1));

        // --- card 2: settings
        Card c2 = new Card(new BorderLayout(0, 8));
        c2.add(lbl("2   Choose the settings", FT, TEXT), BorderLayout.NORTH);
        JPanel f = new JPanel(); f.setOpaque(false); f.setLayout(new BoxLayout(f, BoxLayout.Y_AXIS));
        algo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        f.add(field("Scheduler", "ACS gives new jobs quick turns and moves long jobs to a batch queue. SJF always runs the job with the least time left.", algo));
        f.add(Box.createVerticalStrut(8));
        f.add(field("Quantum (Q)", "How long a new job may run before it is treated as a long \"batch\" job (ACS only).", qSp));
        f.add(Box.createVerticalStrut(8));
        f.add(field("Context-switch time (CS)", "Time wasted each time the CPU switches to a different process.", csSp));
        f.add(Box.createVerticalStrut(12));
        Btn run = new Btn("Run simulation", true);
        run.setAlignmentX(0f); run.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        run.addActionListener(e -> run());
        f.add(run);
        status.setAlignmentX(0f); f.add(Box.createVerticalStrut(6)); f.add(status);
        c2.add(f, BorderLayout.CENTER);

        JPanel side = new JPanel(new BorderLayout(0, 14)); side.setOpaque(false);
        side.setPreferredSize(new Dimension(340, 0));
        side.add(c1, BorderLayout.CENTER); side.add(c2, BorderLayout.SOUTH);
        return side;
    }

    JComponent tabs() {
        JTabbedPane t = new JTabbedPane(); t.setFont(FB);
        // timeline + results
        JPanel p = new JPanel(new BorderLayout(0, 12)); p.setBackground(BG); p.setBorder(new EmptyBorder(12, 0, 0, 0));
        JPanel stats = new JPanel(new GridLayout(1, 3, 12, 0)); stats.setOpaque(false);
        stats.add(sWT); stats.add(sTAT); stats.add(sRT);
        p.add(stats, BorderLayout.NORTH);
        JPanel mid = new JPanel(new BorderLayout(0, 12)); mid.setOpaque(false);
        Card gc = new Card(new BorderLayout(0, 6));
        gc.add(lbl("Timeline - who is using the CPU at each moment", FT, TEXT), BorderLayout.NORTH);
        JScrollPane gs = new JScrollPane(gantt); gs.setBorder(null); gs.getHorizontalScrollBar().setUnitIncrement(30);
        gs.getViewport().setBackground(Color.WHITE);
        gc.add(gs, BorderLayout.CENTER); mid.add(gc, BorderLayout.NORTH);
        Card tc = new Card(new BorderLayout(0, 6));
        tc.add(lbl("Results for each process", FT, TEXT), BorderLayout.NORTH);
        JTable mt = new JTable(metModel); style(mt);
        mt.getColumnModel().getColumn(0).setCellRenderer(new Zebra() {
            public Component getTableCellRendererComponent(JTable tb, Object v, boolean s, boolean f, int r, int c) {
                super.getTableCellRendererComponent(tb, v, s, f, r, c);
                setFont(FB); Color col = COLORS.get(String.valueOf(v)); setForeground(col == null ? TEXT : col.darker()); return this; }
        });
        JScrollPane ms = new JScrollPane(mt); ms.setBorder(new LineBorder(LINE)); ms.getViewport().setBackground(Color.WHITE);
        tc.add(ms, BorderLayout.CENTER);
        tc.add(hint("Waiting = turnaround - burst.  Turnaround = finish - arrival.  Response = first time on the CPU - arrival."), BorderLayout.SOUTH);
        mid.add(tc, BorderLayout.CENTER);
        p.add(mid, BorderLayout.CENTER);
        t.addTab("Timeline & Results", p);

        // step-by-step
        JPanel sp = new JPanel(new BorderLayout()); sp.setBackground(BG); sp.setBorder(new EmptyBorder(12, 0, 0, 0));
        Card sc = new Card(new BorderLayout(0, 6));
        sc.add(lbl("Step-by-step: every time unit", FT, TEXT), BorderLayout.NORTH);
        JTable st = new JTable(stepModel); style(st);
        st.setDefaultRenderer(Object.class, new Zebra() {
            public Component getTableCellRendererComponent(JTable tb, Object v, boolean s, boolean f, int r, int c) {
                super.getTableCellRendererComponent(tb, v, s, f, r, c);
                if (c <= 1) setHorizontalAlignment(CENTER);
                if (c == 1) {
                    String x = String.valueOf(v);
                    if (x.startsWith("CS")) { setBackground(new Color(0xE5E7EB)); setForeground(MUTED); }
                    else if (x.equals("Idle") || x.equals("Done")) setForeground(MUTED);
                    else if (COLORS.containsKey(x)) { setBackground(COLORS.get(x)); setForeground(Color.WHITE); setFont(FB); }
                }
                return this; }
        });
        st.getColumnModel().getColumn(0).setMaxWidth(60);
        st.getColumnModel().getColumn(1).setMaxWidth(110);
        JScrollPane ss = new JScrollPane(st); ss.setBorder(new LineBorder(LINE)); ss.getViewport().setBackground(Color.WHITE);
        sc.add(ss, BorderLayout.CENTER);
        sc.add(hint("\"CS -> P2\" means the CPU is busy switching to P2. Hover a cell to read the full text."), BorderLayout.SOUTH);
        sp.add(sc); t.addTab("Step-by-step", sp);

        // analysis
        analysis = new Analysis(this::read);
        t.addTab("Q / CS Analysis", analysis);
        t.addTab("How it works", help());
        return t;
    }

    JComponent help() {
        JEditorPane e = new JEditorPane("text/html",
            "<html><body style='font-family:Segoe UI,Arial,sans-serif;font-size:13px;color:#111827;margin:14px'>"
          + "<h2>How to use</h2><ol><li>Add processes on the left (or click an example).</li>"
          + "<li>Pick a scheduler and set Q and CS.</li><li>Press <b>Run simulation</b>.</li>"
          + "<li>Open <b>Q / CS Analysis</b> to see how Q and CS change the averages.</li></ol>"
          + "<h2>Key words</h2><ul>"
          + "<li><b>Quantum (Q)</b> - the longest a new process may run in one go.</li>"
          + "<li><b>Context switch (CS)</b> - time the CPU loses when it changes from one process to another.</li>"
          + "<li><b>Burst</b> - total CPU time a process needs.  <b>Priority</b> - lower number = more important.</li></ul>"
          + "<h2>Preemptive SJF</h2><p>At every moment the process with the <b>shortest remaining time</b> runs. "
          + "A new, shorter process can interrupt the running one.</p>"
          + "<h2>ACS - Adaptive Container Scheduler</h2><ul>"
          + "<li><b>Tier 1:</b> every new process starts in a Round Robin queue and gets one quantum. Short, interactive jobs finish here quickly.</li>"
          + "<li><b>Tier 2:</b> a process that uses its whole quantum without finishing is a \"batch job\" and moves to a priority queue. "
          + "Once it starts running there, it cannot be interrupted.</li>"
          + "<li><b>Aging:</b> every 4 time units a process waits in Tier 2, its priority number drops by 1, so nobody starves.</li>"
          + "<li>Tier 1 is always served before Tier 2.</li></ul>"
          + "<h2>Results</h2><ul><li><b>Turnaround</b> = finish - arrival</li><li><b>Waiting</b> = turnaround - burst</li>"
          + "<li><b>Response</b> = first start - arrival</li></ul></body></html>");
        e.setEditable(false);
        JScrollPane s = new JScrollPane(e); s.setBorder(new LineBorder(LINE));
        JPanel p = new JPanel(new BorderLayout()); p.setBackground(BG); p.setBorder(new EmptyBorder(12, 0, 0, 0)); p.add(s);
        return p;
    }

    // ================= actions =================
    void load(int[][] d, int q, int cs) {
        procModel.setRowCount(0);
        for (int i = 0; i < d.length; i++) procModel.addRow(new Object[]{"P" + (i + 1), d[i][0], d[i][1], d[i][2]});
        qSp.setValue(q); csSp.setValue(cs);
        run();
    }

    List<Proc> read() {
        if (procTable.isEditing()) procTable.getCellEditor().stopCellEditing();
        List<Proc> l = new ArrayList<>();
        for (int i = 0; i < procModel.getRowCount(); i++) {
            Proc p = new Proc(procModel.getValueAt(i, 0).toString().trim(),
                    Integer.parseInt(procModel.getValueAt(i, 1).toString().trim()),
                    Integer.parseInt(procModel.getValueAt(i, 2).toString().trim()),
                    Integer.parseInt(procModel.getValueAt(i, 3).toString().trim()));
            if (p.burst <= 0 || p.arrival < 0) throw new IllegalArgumentException("Burst must be above 0 and arrival 0 or more.");
            l.add(p);
        }
        if (l.isEmpty()) throw new IllegalArgumentException("Add at least one process.");
        return l;
    }

    void run() {
        try {
            Result r = SchedulerGUI.simulate(read(), algo.getSelectedIndex() == 0, (int) qSp.getValue(), (int) csSp.getValue());
            COLORS.clear();
            int k = 0; for (Proc p : r.procs) COLORS.put(p.name, PAL[k++ % PAL.length]);
            stepModel.setRowCount(0); for (String[] w : r.rows) stepModel.addRow(w);
            metModel.setRowCount(0);
            for (Proc p : r.procs) metModel.addRow(new Object[]{p.name, p.arrival, p.burst, p.finish, p.tat(), p.wt(), p.rt()});
            sWT.set(r.avgWT()); sTAT.set(r.avgTAT()); sRT.set(r.avgRT());
            gantt.set(r.rows);
            status.setText("Done - all processes finished at time " + (r.rows.size() - 1) + ".");
            status.setForeground(new Color(0x047857));
            if (analysis != null) analysis.update();
        } catch (Exception ex) {
            status.setText("Please check the table: " + ex.getMessage()); status.setForeground(new Color(0xB91C1C));
        }
    }

    // ================= helper widgets =================
    static JLabel lbl(String t, Font f, Color c) { JLabel l = new JLabel(t); l.setFont(f); l.setForeground(c); return l; }
    static JLabel hint(String s) { return lbl("<html><body style='width:270px'>" + s + "</body></html>", FS, MUTED); }
    static JSpinner spin(int v, int min, int max) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(v, min, max, 1)); s.setFont(F);
        s.setMaximumSize(new Dimension(100, 30)); return s; }
    static JPanel field(String title, String hint, JComponent c) {
        JPanel p = new JPanel(); p.setOpaque(false); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); p.setAlignmentX(0f);
        JLabel t = lbl(title, FB, TEXT); t.setAlignmentX(0f); c.setAlignmentX(0f);
        JLabel h = hint(hint); h.setAlignmentX(0f);
        p.add(t); p.add(Box.createVerticalStrut(3)); p.add(c); p.add(Box.createVerticalStrut(3)); p.add(h);
        return p; }

    static void style(JTable t) {
        t.setRowHeight(28); t.setShowGrid(false); t.setIntercellSpacing(new Dimension(0, 0)); t.setFont(F);
        t.setFillsViewportHeight(true); t.setSelectionBackground(new Color(0xE0E7FF)); t.setSelectionForeground(TEXT);
        t.setDefaultRenderer(Object.class, new Zebra());
        JTableHeader h = t.getTableHeader(); h.setReorderingAllowed(false); h.setPreferredSize(new Dimension(0, 30));
        h.setDefaultRenderer(new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(JTable tb, Object v, boolean s, boolean f, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(tb, v, false, false, r, c);
                l.setFont(FB.deriveFont(12f)); l.setForeground(MUTED); l.setBackground(new Color(0xF3F4F6)); l.setOpaque(true);
                l.setBorder(new CompoundBorder(new MatteBorder(0, 0, 1, 0, LINE), new EmptyBorder(0, 10, 0, 10)));
                l.setHorizontalAlignment(c == 0 && tb.getColumnCount() <= 4 ? LEFT : LEFT); return l; }
        });
    }

    static class RO extends DefaultTableModel {
        RO(String... c) { super((Object[]) c, 0); }
        public boolean isCellEditable(int r, int c) { return false; }
    }

    static class Zebra extends DefaultTableCellRenderer {
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
            super.getTableCellRendererComponent(t, v, sel, false, r, c);
            setBorder(new EmptyBorder(0, 10, 0, 10)); setFont(F); setForeground(TEXT); setHorizontalAlignment(LEFT);
            setBackground(sel ? t.getSelectionBackground() : r % 2 == 0 ? Color.WHITE : new Color(0xF9FAFB));
            setToolTipText(v == null ? null : v.toString()); return this; }
    }

    static class Card extends JPanel {
        Card(LayoutManager l) { super(l); setOpaque(false); setBorder(new EmptyBorder(14, 16, 14, 16)); }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            RoundRectangle2D r = new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            g.setColor(Color.WHITE); g.fill(r); g.setColor(LINE); g.draw(r); g.dispose(); super.paintComponent(g0); }
    }

    static class Stat extends Card {
        final JLabel val = new JLabel("-");
        Stat(String cap, Color accent) {
            super(new BorderLayout(0, 2));
            val.setFont(F.deriveFont(Font.BOLD, 28f)); val.setForeground(accent);
            add(lbl(cap, FS, MUTED), BorderLayout.NORTH); add(val, BorderLayout.CENTER); add(lbl("time units", FS, MUTED), BorderLayout.SOUTH); }
        void set(double d) { val.setText(String.format("%.2f", d)); }
    }

    /** Fully custom-painted button, looks the same on every Look & Feel. */
    static class Btn extends JButton {
        final boolean primary;
        Btn(String t, boolean primary) {
            super(t); this.primary = primary;
            setContentAreaFilled(false); setBorderPainted(false); setFocusPainted(false); setOpaque(false);
            setFont(FB); setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)); setPreferredSize(new Dimension(100, 36)); }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            boolean hov = getModel().isRollover() || getModel().isPressed();
            g.setColor(primary ? (hov ? PRIMARY_D : PRIMARY) : (hov ? new Color(0xE5E7EB) : new Color(0xF3F4F6)));
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            g.setFont(FB); g.setColor(primary ? Color.WHITE : TEXT);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose(); }
    }

    // ================= Gantt =================
    static class Gantt extends JPanel {
        static final int U = 32, X0 = 16, Y0 = 46, H = 40;
        List<String[]> rows = new ArrayList<>();
        Gantt() { setBackground(Color.WHITE); setPreferredSize(new Dimension(400, 130)); }
        void set(List<String[]> r) { rows = r; setPreferredSize(new Dimension(Math.max(400, len(r) * U + 2 * X0 + 20), 130)); revalidate(); repaint(); }
        static int len(List<String[]> r) { int n = r.size(); return n > 0 && r.get(n - 1)[1].equals("Done") ? n - 1 : n; }
        static String key(String s) { return s.startsWith("CS") ? "CS" : s; }
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (rows.isEmpty()) { g.setColor(MUTED); g.setFont(F); g.drawString("Press \"Run simulation\" to see the timeline.", X0, 60); g.dispose(); return; }
            // legend
            int lx = X0; g.setFont(FS);
            List<String> names = new ArrayList<>(COLORS.keySet()); names.add("CS"); names.add("idle");
            for (String n : names) {
                g.setColor(n.equals("CS") ? new Color(0x9CA3AF) : n.equals("idle") ? new Color(0xE5E7EB) : COLORS.get(n));
                g.fillRoundRect(lx, 12, 12, 12, 4, 4);
                g.setColor(TEXT); String t = n.equals("CS") ? "context switch" : n; g.drawString(t, lx + 17, 23);
                lx += 17 + g.getFontMetrics().stringWidth(t) + 16;
            }
            int n = len(rows), i = 0;
            while (i < n) {
                String k = key(rows.get(i)[1]); int j = i;
                while (j < n && key(rows.get(j)[1]).equals(k)) j++;
                int x = X0 + i * U, w = (j - i) * U;
                Color c = k.equals("CS") ? new Color(0x9CA3AF) : k.equals("Idle") ? new Color(0xE5E7EB) : COLORS.getOrDefault(k, PRIMARY);
                g.setColor(c); g.fillRoundRect(x + 1, Y0, w - 2, H, 10, 10);
                g.setFont(FB); g.setColor(k.equals("Idle") ? MUTED : Color.WHITE);
                String t = k.equals("Idle") ? "idle" : k; FontMetrics fm = g.getFontMetrics();
                if (fm.stringWidth(t) < w - 4) g.drawString(t, x + (w - fm.stringWidth(t)) / 2, Y0 + H / 2 + 5);
                g.setFont(FS); g.setColor(MUTED); g.drawString(rows.get(i)[0], x - 2, Y0 + H + 18);
                i = j;
            }
            g.setFont(FS); g.setColor(MUTED); g.drawString("" + n, X0 + n * U - 2, Y0 + H + 18);
            g.dispose();
        }
    }

    // ================= Line chart =================
    static class Chart extends JPanel {
        String[] xs = new String[0], names = new String[0]; double[][] ys = new double[0][0];
        final List<double[]> pts = new ArrayList<>(); final List<String> tips = new ArrayList<>(); int hover = -1;
        Chart() {
            setBackground(Color.WHITE); setPreferredSize(new Dimension(420, 270));
            addMouseMotionListener(new MouseMotionAdapter() {
                public void mouseMoved(MouseEvent e) {
                    int best = -1; double bd = 14 * 14;
                    for (int i = 0; i < pts.size(); i++) {
                        double dx = pts.get(i)[0] - e.getX(), dy = pts.get(i)[1] - e.getY(), d = dx * dx + dy * dy;
                        if (d < bd) { bd = d; best = i; } }
                    if (best != hover) { hover = best; repaint(); } } });
            addMouseListener(new MouseAdapter() { public void mouseExited(MouseEvent e) { hover = -1; repaint(); } });
        }
        void set(String[] x, String[] n, double[][] y) { xs = x; names = n; ys = y; hover = -1; repaint(); }
        static double[] nice(double max) {
            max = Math.max(max, 1); double raw = max / 5, mag = Math.pow(10, Math.floor(Math.log10(raw))), step = mag * 10;
            for (double m : new double[]{1, 2, 2.5, 5, 10}) if (m * mag >= raw) { step = m * mag; break; }
            return new double[]{step, Math.ceil(max / step - 1e-9) * step}; }
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            pts.clear(); tips.clear();
            int w = getWidth(), h = getHeight(), L = 44, R = 18, T = 52, B = 46;
            if (ys.length == 0) { g.setColor(MUTED); g.setFont(F); g.drawString("No data yet - press Run simulation.", L, h / 2); g.dispose(); return; }
            double max = 0; for (double[] r : ys) for (double d : r) max = Math.max(max, d);
            double[] sc = nice(max); double step = sc[0], ymax = sc[1];
            // legend
            g.setFont(FS); int lx = L;
            for (int k = 0; k < names.length; k++) {
                g.setColor(PAL[k % PAL.length]); g.fillOval(lx, 12, 10, 10);
                g.setColor(TEXT); g.drawString(names[k], lx + 14, 22); lx += 14 + g.getFontMetrics().stringWidth(names[k]) + 14; }
            // grid + y labels
            for (double v = 0; v <= ymax + 1e-9; v += step) {
                int y = h - B - (int) ((h - B - T) * v / ymax);
                g.setColor(LINE); g.drawLine(L, y, w - R, y);
                g.setColor(MUTED); String s = step % 1 != 0 ? String.format("%.1f", v) : String.format("%.0f", v);
                g.drawString(s, L - 8 - g.getFontMetrics().stringWidth(s), y + 4); }
            g.setColor(MUTED); g.drawString("time units", 4, T - 8);
            int n = xs.length;
            for (int i = 0; i < n; i++) {
                int x = n == 1 ? (L + w - R) / 2 : L + (w - L - R) * i / (n - 1);
                g.setColor(MUTED); int sw = g.getFontMetrics().stringWidth(xs[i]);
                if (n <= 16 || i % 2 == 0) g.drawString(xs[i], x - sw / 2, h - B + 16); }
            String xt = "Quantum (Q)"; g.drawString(xt, (L + w - R) / 2 - g.getFontMetrics().stringWidth(xt) / 2, h - 8);
            // lines
            g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int k = 0; k < ys.length; k++) {
                Color c = PAL[k % PAL.length]; int px = 0, py = 0;
                for (int i = 0; i < ys[k].length; i++) {
                    int x = n == 1 ? (L + w - R) / 2 : L + (w - L - R) * i / (n - 1);
                    int y = h - B - (int) ((h - B - T) * ys[k][i] / ymax);
                    g.setColor(c); if (i > 0) g.drawLine(px, py, x, y);
                    px = x; py = y; }
                for (int i = 0; i < ys[k].length; i++) {
                    int x = n == 1 ? (L + w - R) / 2 : L + (w - L - R) * i / (n - 1);
                    int y = h - B - (int) ((h - B - T) * ys[k][i] / ymax);
                    pts.add(new double[]{x, y}); tips.add(names[k] + ",  Q = " + xs[i] + "  ->  " + String.format("%.2f", ys[k][i]));
                    boolean hv = pts.size() - 1 == hover; int r = hv ? 6 : 4;
                    g.setColor(Color.WHITE); g.fillOval(x - r, y - r, 2 * r, 2 * r);
                    g.setColor(c); g.setStroke(new BasicStroke(2f)); g.drawOval(x - r, y - r, 2 * r, 2 * r);
                    g.setStroke(new BasicStroke(2.4f)); } }
            if (hover >= 0 && hover < pts.size()) {
                g.setFont(FB.deriveFont(12f)); String t = tips.get(hover); int tw = g.getFontMetrics().stringWidth(t) + 16;
                int bx = (int) Math.max(4, Math.min(w - tw - 4, pts.get(hover)[0] - tw / 2.0)), by = (int) pts.get(hover)[1] - 38;
                if (by < 4) by = (int) pts.get(hover)[1] + 12;
                g.setColor(new Color(0x111827)); g.fillRoundRect(bx, by, tw, 24, 8, 8);
                g.setColor(Color.WHITE); g.drawString(t, bx + 8, by + 16); }
            g.dispose();
        }
    }

    // ================= Q / CS analysis tab =================
    static class GridTable extends JTable {
        double[][] vals = new double[0][0]; double best = Double.NaN;
        GridTable() {
            super(new RO()); style(this); setRowHeight(26);
            setDefaultRenderer(Object.class, new Zebra() {
                public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                    super.getTableCellRendererComponent(t, v, false, false, r, c);
                    setHorizontalAlignment(CENTER); setBorder(new EmptyBorder(0, 2, 0, 2));
                    if (c == 0) { setFont(FB); setForeground(MUTED); setBackground(new Color(0xF3F4F6)); }
                    else if (r < vals.length && c - 1 < vals[r].length && Math.abs(vals[r][c - 1] - best) < 1e-9) {
                        setBackground(GOOD); setFont(FB); setForeground(new Color(0x065F46)); }
                    return this; } });
        }
        void set(String[] qs, double[][] v, double b) {
            vals = v; best = b;
            String[] cols = new String[qs.length + 1]; cols[0] = "CS \\ Q"; System.arraycopy(qs, 0, cols, 1, qs.length);
            Object[][] d = new Object[v.length][];
            for (int r = 0; r < v.length; r++) {
                d[r] = new Object[qs.length + 1]; d[r][0] = "CS=" + r;
                for (int c = 0; c < qs.length; c++) d[r][c + 1] = String.format("%.2f", v[r][c]); }
            ((DefaultTableModel) getModel()).setDataVector(d, cols);
            getColumnModel().getColumn(0).setMinWidth(62); getColumnModel().getColumn(0).setMaxWidth(70);
        }
    }

    static class Analysis extends JPanel {
        final Supplier<List<Proc>> src;
        final JComboBox<String> algo = new JComboBox<>(new String[]{"ACS - Adaptive Container Scheduler", "Preemptive SJF"});
        final JSpinner maxQ = spin(8, 2, 15), maxCS = spin(3, 1, 8);
        final Chart wtC = new Chart(), tatC = new Chart();
        final GridTable wtT = new GridTable(), tatT = new GridTable();
        final JLabel insight = lbl(" ", F, TEXT);

        Analysis(Supplier<List<Proc>> src) {
            super(new BorderLayout(0, 12)); this.src = src;
            setBackground(BG); setBorder(new EmptyBorder(12, 0, 0, 0));
            Card ctl = new Card(new FlowLayout(FlowLayout.LEFT, 10, 0));
            algo.setFont(F);
            ctl.add(lbl("Scheduler:", FB, TEXT)); ctl.add(algo);
            ctl.add(lbl("Try Q from 1 to", FB, TEXT)); ctl.add(maxQ);
            ctl.add(lbl("and CS from 0 to", FB, TEXT)); ctl.add(maxCS);
            Btn up = new Btn("Update graphs", true); up.setPreferredSize(new Dimension(130, 34)); up.addActionListener(e -> update());
            ctl.add(up);
            algo.addActionListener(e -> update());
            Card ins = new Card(new BorderLayout()); ins.add(insight);
            JPanel north = new JPanel(new BorderLayout(0, 12)); north.setOpaque(false);
            north.add(ctl, BorderLayout.NORTH); north.add(ins, BorderLayout.CENTER);
            add(north, BorderLayout.NORTH);
            JPanel two = new JPanel(new GridLayout(1, 2, 12, 0)); two.setOpaque(false);
            two.add(panel("Average waiting time", wtC, wtT)); two.add(panel("Average turnaround time", tatC, tatT));
            JScrollPane sp = new JScrollPane(two); sp.setBorder(null); sp.getViewport().setOpaque(false); sp.setOpaque(false);
            add(sp, BorderLayout.CENTER);
        }

        static Card panel(String title, Chart c, GridTable t) {
            Card k = new Card(new BorderLayout(0, 8));
            k.add(lbl(title + "  (each line = one CS value)", FT, TEXT), BorderLayout.NORTH);
            k.add(c, BorderLayout.CENTER);
            JPanel s = new JPanel(new BorderLayout(0, 4)); s.setOpaque(false);
            JScrollPane sp = new JScrollPane(t); sp.setBorder(new LineBorder(LINE)); sp.setPreferredSize(new Dimension(100, 130));
            sp.getViewport().setBackground(Color.WHITE);
            s.add(sp, BorderLayout.CENTER); s.add(hint("Table: rows = CS, columns = Q. Green = lowest value."), BorderLayout.SOUTH);
            k.add(s, BorderLayout.SOUTH); return k; }

        static int bestQ(double[] row) { int b = 0; for (int i = 1; i < row.length; i++) if (row[i] < row[b] - 1e-9) b = i; return b + 1; }

        void update() {
            List<Proc> ps; try { ps = src.get(); } catch (Exception e) { return; }
            boolean acs = algo.getSelectedIndex() == 0; int mq = (int) maxQ.getValue(), mc = (int) maxCS.getValue();
            String[] qs = new String[mq]; for (int q = 1; q <= mq; q++) qs[q - 1] = "" + q;
            String[] names = new String[mc + 1]; double[][] wt = new double[mc + 1][mq], tat = new double[mc + 1][mq];
            for (int cs = 0; cs <= mc; cs++) {
                names[cs] = "CS = " + cs;
                for (int q = 1; q <= mq; q++) { Result r = SchedulerGUI.simulate(ps, acs, q, cs); wt[cs][q - 1] = r.avgWT(); tat[cs][q - 1] = r.avgTAT(); } }
            wtC.set(qs, names, wt); tatC.set(qs, names, tat);
            wtT.set(qs, wt, min(wt)); tatT.set(qs, tat, min(tat));
            StringBuilder sb = new StringBuilder("<html><body style='width:760px'>");
            if (!acs) sb.append("<b>Note:</b> Preemptive SJF does not use a quantum, so every line is flat. Only the context-switch time changes the result. Choose ACS to see how Q matters.<br>");
            else {
                sb.append("<b>Best Q for waiting time:</b> ");
                for (int cs = 0; cs <= mc; cs++) sb.append("CS=").append(cs).append(" &rarr; Q=").append(bestQ(wt[cs])).append(cs < mc ? " &nbsp;&middot;&nbsp; " : "");
                sb.append("<br><b>Best Q for turnaround time:</b> ");
                for (int cs = 0; cs <= mc; cs++) sb.append("CS=").append(cs).append(" &rarr; Q=").append(bestQ(tat[cs])).append(cs < mc ? " &nbsp;&middot;&nbsp; " : "");
                sb.append("<br>");
            }
            sb.append("<span style='color:#6B7280'>Reading the graphs: a higher line means processes wait longer. Higher CS shifts every line up because the CPU wastes time switching. Hover a point to see its value.</span>");
            insight.setText(sb.append("</body></html>").toString());
        }
        static double min(double[][] v) { double m = Double.MAX_VALUE; for (double[] r : v) for (double d : r) m = Math.min(m, d); return m; }
    }

    /** Runs the chosen scheduler (keeps the GUI independent of which class implements which algorithm). */
    static Result simulate(List<Proc> ps, boolean acs, int q, int cs) {
        return acs ? ACSScheduler.run(ps, q, cs) : SJFScheduler.run(ps, cs);
    }

    // ================= main =================
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("cli")) {   // java SchedulerGUI cli -> prints both PDF test cases
            List<Proc> tc1 = List.of(new Proc("P1", 0, 4, 2), new Proc("P2", 1, 7, 3), new Proc("P3", 3, 2, 1));
            List<Proc> tc2 = List.of(new Proc("P1", 0, 5, 4), new Proc("P2", 1, 6, 2), new Proc("P3", 4, 4, 3));
            System.out.println("=== TC1 ACS (Q=3, CS=0) ==="); Result.print(SchedulerGUI.simulate(tc1, true, 3, 0));
            System.out.println("=== TC2 ACS (Q=3, CS=1) ==="); Result.print(SchedulerGUI.simulate(tc2, true, 3, 1));
            System.out.println("=== TC1 SJF (CS=0) ==="); Result.print(SchedulerGUI.simulate(tc1, false, 3, 0));
            System.out.println("=== TC2 SJF (CS=1) ==="); Result.print(SchedulerGUI.simulate(tc2, false, 3, 1));
            return;
        }
        try { for (UIManager.LookAndFeelInfo i : UIManager.getInstalledLookAndFeels()) if (i.getName().equals("Nimbus")) { UIManager.put("defaultFont", F); UIManager.setLookAndFeel(i.getClassName()); } }
        catch (Exception ignored) { }
        SwingUtilities.invokeLater(() -> new SchedulerGUI().setVisible(true));
    }
}
