import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;


public class SchedulerGUI extends JFrame {
    // ---------- look & feel ----------
    static final Color BG = new Color(0xF4F6FB), PRIMARY = new Color(0x4F46E5), PRIMARY_D = new Color(0x4338CA),
            TEXT = new Color(0x111827), MUTED = new Color(0x6B7280), LINE = new Color(0xE5E7EB);
    static final Font F = new Font("Segoe UI", Font.PLAIN, 13), FB = F.deriveFont(Font.BOLD), FS = F.deriveFont(12f),
            FT = F.deriveFont(Font.BOLD, 15f);
    static final Color[] PAL = {new Color(0x6366F1), new Color(0xF59E0B), new Color(0x10B981), new Color(0xEF4444),
            new Color(0x06B6D4), new Color(0xA855F7), new Color(0xEC4899), new Color(0x84CC16), new Color(0x3B82F6), new Color(0xF97316)};
    static final Map<String, Color> COLORS = new LinkedHashMap<>();   

    // ---------- widgets ----------
    final DefaultTableModel procModel = new DefaultTableModel(new String[]{"Process", "Arrival", "Burst", "Priority"}, 0);
    final JTable procTable = new JTable(procModel);
    final JSpinner qSp = spin(3, 1, 100), csSp = spin(0, 0, 100), agingSp = spin(4, 1, 100);
    final JComboBox<String> algo = new JComboBox<>(new String[]{"ACS - Adaptive Container Scheduler", "Preemptive SJF"});
    final RO stepModel = new RO("Time", "CPU", "Tier 1  (new / interactive jobs)", "Tier 2  (long / batch jobs)");
    final RO metModel = new RO("Process", "Arrival", "Burst", "Finish", "Turnaround", "Waiting", "Response");
    final Stat sWT = new Stat("Average waiting time", new Color(0xF59E0B)),
            sTAT = new Stat("Average turnaround time", PRIMARY), sRT = new Stat("Average response time", new Color(0x10B981));
    final Gantt gantt = new Gantt();
    final JLabel status = lbl(" ", FS, MUTED);
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
        algo.addActionListener(e -> {
            boolean acs = algo.getSelectedIndex() == 0;
            qSp.setEnabled(acs);
            agingSp.setEnabled(acs);
        });
        setSize(1260, 820); setMinimumSize(new Dimension(1050, 700)); setLocationRelativeTo(null);
        load(new int[][]{{0, 4, 2}, {1, 7, 3}, {3, 2, 1}}, 3, 0);
    }

    // ================= layout =================
    JComponent header() {
        JPanel h = new JPanel(new GridLayout(1, 1));
        h.setBackground(PRIMARY); h.setBorder(new EmptyBorder(14, 22, 14, 22));
        h.add(lbl("CPU Scheduler Simulator", F.deriveFont(Font.BOLD, 22f), Color.WHITE));
        return h;
    }

    JComponent sidebar() {
        // --- card 1: processes
        Card c1 = new Card(new BorderLayout(0, 8));
        c1.add(lbl("1   Add your processes", FT, TEXT), BorderLayout.NORTH);
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
        f.add(field("Scheduler", algo));
        f.add(Box.createVerticalStrut(8));
        f.add(field("Quantum (Q)", qSp));
        f.add(Box.createVerticalStrut(8));
        f.add(field("Context-switch time (CS)", csSp));
        f.add(Box.createVerticalStrut(8));
        f.add(field("Aging interval", agingSp));
        f.add(Box.createVerticalStrut(12));
        Btn run = new Btn("Run simulation", true);
        run.setAlignmentX(0f); run.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        run.addActionListener(e -> run());
        f.add(run);
        status.setAlignmentX(0f); f.add(Box.createVerticalStrut(6)); f.add(status);
        c2.add(f, BorderLayout.CENTER);

        c1.setMinimumSize(new Dimension(300, 220));
        JScrollPane settings = new JScrollPane(c2); settings.setBorder(null);
        settings.getVerticalScrollBar().setUnitIncrement(16);
        JSplitPane side = new JSplitPane(JSplitPane.VERTICAL_SPLIT, c1, settings);
        side.setResizeWeight(0.58); side.setContinuousLayout(true); side.setBorder(null); side.setDividerSize(8);
        side.setPreferredSize(new Dimension(340, 0));
        return side;
    }

    JComponent tabs() {
        JTabbedPane t = new JTabbedPane(); t.setFont(FB);
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
        sp.add(sc); t.addTab("Step-by-step", sp);

        return t;
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
            Result r = SchedulerGUI.simulate(read(), algo.getSelectedIndex() == 0, (int) qSp.getValue(),
                    (int) csSp.getValue(), (int) agingSp.getValue());
            COLORS.clear();
            int k = 0; for (Proc p : r.procs) COLORS.put(p.name, PAL[k++ % PAL.length]);
            stepModel.setRowCount(0); for (String[] w : r.rows) stepModel.addRow(w);
            metModel.setRowCount(0);
            for (Proc p : r.procs) metModel.addRow(new Object[]{p.name, p.arrival, p.burst, p.finish, p.tat(), p.wt(), p.rt()});
            sWT.set(r.avgWT()); sTAT.set(r.avgTAT()); sRT.set(r.avgRT());
            gantt.set(r.rows);
            status.setForeground(new Color(0x047857));
        } catch (Exception ex) {
            status.setText("Please check the table: " + ex.getMessage()); status.setForeground(new Color(0xB91C1C));
        }
    }

    // ================= helper widgets =================
    static JLabel lbl(String t, Font f, Color c) { JLabel l = new JLabel(t); l.setFont(f); l.setForeground(c); return l; }
    static JSpinner spin(int v, int min, int max) {
        JSpinner s = new JSpinner(new SpinnerNumberModel(v, min, max, 1)); s.setFont(F);
        s.setMaximumSize(new Dimension(100, 30)); return s; }
    static JPanel field(String title, JComponent c) {
        JPanel p = new JPanel(); p.setOpaque(false); p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS)); p.setAlignmentX(0f);
        JLabel t = lbl(title, FB, TEXT); t.setAlignmentX(0f); c.setAlignmentX(0f);
        p.add(t); p.add(Box.createVerticalStrut(3)); p.add(c);
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

    /** Runs the chosen scheduler (keeps the GUI independent of which class implements which algorithm). */
    static Result simulate(List<Proc> ps, boolean acs, int q, int cs, int aging) {
        return acs ? ACSScheduler.run(ps, q, cs, aging) : SJFScheduler.run(ps, cs);
    }

    // ================= main =================
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("cli")) {
            List<Proc> tc1 = List.of(new Proc("P1", 0, 4, 2), new Proc("P2", 1, 7, 3), new Proc("P3", 3, 2, 1));
            List<Proc> tc2 = List.of(new Proc("P1", 0, 5, 4), new Proc("P2", 1, 6, 2), new Proc("P3", 4, 4, 3));
            System.out.println("=== TC1 ACS (Q=3, CS=0, aging=4) ==="); Result.print(SchedulerGUI.simulate(tc1, true, 3, 0, 4));
            System.out.println("=== TC2 ACS (Q=3, CS=1, aging=4) ==="); Result.print(SchedulerGUI.simulate(tc2, true, 3, 1, 4));
            System.out.println("=== TC1 SJF (CS=0) ==="); Result.print(SchedulerGUI.simulate(tc1, false, 3, 0, 4));
            System.out.println("=== TC2 SJF (CS=1) ==="); Result.print(SchedulerGUI.simulate(tc2, false, 3, 1, 4));
            return;
        }
        try { for (UIManager.LookAndFeelInfo i : UIManager.getInstalledLookAndFeels()) if (i.getName().equals("Nimbus")) { UIManager.put("defaultFont", F); UIManager.setLookAndFeel(i.getClassName()); } }
        catch (Exception ignored) { }
        SwingUtilities.invokeLater(() -> new SchedulerGUI().setVisible(true));
    }
}
