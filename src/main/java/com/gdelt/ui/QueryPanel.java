package com.gdelt.ui;

import com.gdelt.service.QueryService;
import com.gdelt.model.GdeltEvent;
import javax.swing.*;
import javax.swing.table.*;
import javax.swing.border.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class QueryPanel extends JPanel {
    private JTextField startField, endField, actor1Field, actor2Field;
    private JTextArea resultArea;
    private JTable table;
    private DefaultTableModel tableModel;
    private final Runnable onDataChanged;

    private int currentPage = 0;
    private int totalCount = 0;
    private static final int PAGE_SIZE = 500;
    private JLabel pageLabel;
    private JButton prevBtn, nextBtn;

    private static final String[] EVENT_COLS =
        {"EventID", "日期", "Actor1", "Actor2", "EventCode", "Goldstein", "QuadClass"};
    private static final String[] HOT_COLS =
        {"Actor1", "Actor2", "事件数", "Avg Goldstein"};

    public QueryPanel(Runnable onDataChanged) {
        this.onDataChanged = onDataChanged;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel condPanel = new JPanel(new GridBagLayout());
        condPanel.setBorder(new TitledBorder("查询条件（留空=不限制=全部数据）"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 8, 5, 8);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        g.gridy = 0; g.gridx = 0;
        condPanel.add(new JLabel("开始日期:"), g);
        g.gridx = 1; startField = new JTextField(10);
        startField.setToolTipText("留空=不限起始日期，如 2026-06-01");
        condPanel.add(startField, g);
        g.gridx = 2; condPanel.add(new JLabel("结束日期:"), g);
        g.gridx = 3; endField = new JTextField(10);
        endField.setToolTipText("留空=不限结束日期");
        condPanel.add(endField, g);

        g.gridy = 1; g.gridx = 0;
        condPanel.add(new JLabel("发起国(Actor1):"), g);
        g.gridx = 1; actor1Field = new JTextField(10);
        actor1Field.setToolTipText("三位ISO代码, 如 USA, CHN, RUS。留空=不限");
        condPanel.add(actor1Field, g);
        g.gridx = 2; condPanel.add(new JLabel("目标国(Actor2):"), g);
        g.gridx = 3; actor2Field = new JTextField(10);
        actor2Field.setToolTipText("留空=不限");
        condPanel.add(actor2Field, g);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        JButton queryBtn = new JButton("🔍 查询事件");
        JButton hotBtn   = new JButton("🔥 热点TOP10");
        JButton distBtn  = new JButton("📊 统计分布");
        JButton clearBtn = new JButton("🗑 清除");
        for (JButton btn : new JButton[]{queryBtn, hotBtn, distBtn, clearBtn}) {
            btn.setFont(btn.getFont().deriveFont(Font.BOLD, 13f));
        }
        btnPanel.add(queryBtn); btnPanel.add(hotBtn); btnPanel.add(distBtn); btnPanel.add(clearBtn);

        prevBtn = new JButton("◀ 上一页");
        nextBtn = new JButton("下一页 ▶");
        pageLabel = new JLabel("  第 0 页 / 共 0 条");
        prevBtn.setEnabled(false); nextBtn.setEnabled(false);
        btnPanel.add(new JSeparator(SwingConstants.VERTICAL));
        btnPanel.add(prevBtn); btnPanel.add(pageLabel); btnPanel.add(nextBtn);

        JButton btn7d  = mkSmallBtn("7天");
        JButton btn30d = mkSmallBtn("30天");
        JButton btn90d = mkSmallBtn("90天");
        JButton btn1y  = mkSmallBtn("1年");
        JButton btnAll = mkSmallBtn("ALL");
        btnPanel.add(new JLabel(" 快捷:"));
        btnPanel.add(btn7d); btnPanel.add(btn30d); btnPanel.add(btn90d);
        btnPanel.add(btn1y); btnPanel.add(btnAll);

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(condPanel, BorderLayout.CENTER);
        topPanel.add(btnPanel, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(EVENT_COLS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(tableModel);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setFillsViewportHeight(true);
        table.setRowHeight(24);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD));

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(new TitledBorder("查询结果"));

        resultArea = new JTextArea(3, 40);
        resultArea.setEditable(false);
        resultArea.setBackground(new Color(245, 245, 245));
        resultArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        JScrollPane resultScroll = new JScrollPane(resultArea);
        resultScroll.setBorder(new TitledBorder("状态信息"));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, resultScroll);
        split.setResizeWeight(0.78); split.setDividerSize(6);
        add(split, BorderLayout.CENTER);

        queryBtn.addActionListener(e -> { currentPage = 0; executeQuery(); });
        hotBtn.addActionListener(e -> showHotPairs());
        distBtn.addActionListener(e -> showDistribution());
        clearBtn.addActionListener(e -> clearAll());
        prevBtn.addActionListener(e -> { if (currentPage > 0) { currentPage--; executeQuery(); } });
        nextBtn.addActionListener(e -> { if ((currentPage+1) * PAGE_SIZE < totalCount) { currentPage++; executeQuery(); } });

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        btn7d.addActionListener(e -> { LocalDate n = LocalDate.now(); endField.setText(n.format(fmt)); startField.setText(n.minusDays(7).format(fmt)); });
        btn30d.addActionListener(e ->{ LocalDate n = LocalDate.now(); endField.setText(n.format(fmt)); startField.setText(n.minusDays(30).format(fmt)); });
        btn90d.addActionListener(e ->{ LocalDate n = LocalDate.now(); endField.setText(n.format(fmt)); startField.setText(n.minusDays(90).format(fmt)); });
        btn1y.addActionListener(e -> { LocalDate n = LocalDate.now(); endField.setText(n.format(fmt)); startField.setText(n.minusYears(1).format(fmt)); });
        btnAll.addActionListener(e -> { startField.setText(""); endField.setText(""); resultArea.setText("已清空日期限制"); });
    }

    private JButton mkSmallBtn(String txt) {
        JButton b = new JButton(txt);
        b.setFont(b.getFont().deriveFont(Font.BOLD, 11f));
        b.setMargin(new Insets(2, 8, 2, 8));
        return b;
    }

    private String start() { return startField.getText().trim(); }
    private String end()   { return endField.getText().trim(); }

    private boolean validateDates() {
        String s = start(), e = end();
        if (s.isEmpty() && e.isEmpty()) return true;
        String regex = "\\d{4}-\\d{2}-\\d{2}";
        if (!s.isEmpty() && !s.matches(regex)) {
            resultArea.setText("\u274C \u5f00\u59cb\u65e5\u671f\u683c\u5f0f\u9519\u8bef\uff0c\u8bf7\u7528 yyyy-MM-dd");
            return false;
        }
        if (!e.isEmpty() && !e.matches(regex)) {
            resultArea.setText("\u274C \u7ed3\u675f\u65e5\u671f\u683c\u5f0f\u9519\u8bef\uff0c\u8bf7\u7528 yyyy-MM-dd");
            return false;
        }
        if (!s.isEmpty() && !e.isEmpty() && s.compareTo(e) > 0) {
            resultArea.setText("\u274C \u5f00\u59cb\u65e5\u671f\u4e0d\u80fd\u665a\u4e8e\u7ed3\u675f\u65e5\u671f");
            return false;
        }
        return true;
    }

    private void executeQuery() {
        if (!validateDates()) return;
        resetToEventColumns();
        tableModel.setRowCount(0);
        resultArea.setText("\u23f3 \u67e5\u8be2\u4e2d...");
        String a1 = actor1Field.getText().trim().toUpperCase();
        String a2 = actor2Field.getText().trim().toUpperCase();

        new SwingWorker<Object[], Void>() {
            @Override protected Object[] doInBackground() {
                int total = QueryService.count(start(), end(), a1, a2);
                List<GdeltEvent> events = QueryService.search(start(), end(), a1, a2, currentPage * PAGE_SIZE, PAGE_SIZE);
                return new Object[]{total, events};
            }
            @Override protected void done() {
                try {
                    Object[] r = get();
                    totalCount = (int) r[0];
                    @SuppressWarnings("unchecked") List<GdeltEvent> events = (List<GdeltEvent>) r[1];
                    for (GdeltEvent ev : events) {
                        tableModel.addRow(new Object[]{ev.getGlobalEventId(),
                            ev.getDay() != null ? ev.getDay().toString() : "",
                            nvl(ev.getActor1CountryCode()), nvl(ev.getActor2CountryCode()),
                            nvl(ev.getEventBaseCode()),
                            String.format("%.2f", ev.getGoldsteinScale()), ev.getQuadClassName()});
                    }
                    updatePagination();
                    if (totalCount == 0) {
                        resultArea.setText("\uD83D\uDCED \u65E0\u7ED3\u679C\u3002\u8BF7\u786E\u8BA4:\n1. \u5DF2\u5BFC\u5165\u6570\u636E  2. \u65E5\u671F\u8303\u56F4\u6B63\u786E  3. \u56FD\u5BB6\u4EE3\u7801\u6B63\u786E(\u5982USA,CHN)");
                    } else {
                        String range = !start().isEmpty() || !end().isEmpty() ? start() + " ~ " + end() : "\u5168\u90E8\u6570\u636E";
                        resultArea.setText(String.format("\u2705 %s | %s\u2194%s | \u5171 %,d \u6761 | \u5F53\u524D\u7B2C %,d-%,d \u6761",
                            range, a1.isEmpty()?"\u5168\u90E8":a1, a2.isEmpty()?"\u5168\u90E8":a2,
                            totalCount, currentPage*PAGE_SIZE+1, Math.min((currentPage+1)*PAGE_SIZE, totalCount)));
                    }
                } catch (Exception ex) { resultArea.setText("\u274C \u67E5\u8BE2\u5931\u8D25: " + safeMsg(ex)); }
            }
        }.execute();
    }

    private void showHotPairs() {
        if (!validateDates()) return;
        setHotColumns(); tableModel.setRowCount(0);
        resultArea.setText("\u23f3 \u67e5\u8be2\u70ed\u70b9\u5173\u7cfb...");
        prevBtn.setEnabled(false); nextBtn.setEnabled(false);
        pageLabel.setText("  TOP 10");
        new SwingWorker<List<String[]>, Void>() {
            @Override protected List<String[]> doInBackground() { return QueryService.getHotPairs(start(), end(), 20); }
            @Override protected void done() {
                try {
                    List<String[]> pairs = get();
                    for (String[] p : pairs) tableModel.addRow(p);
                    String range = !start().isEmpty() || !end().isEmpty() ? start() + " ~ " + end() : "\u5168\u90E8\u6570\u636E";
                    resultArea.setText(String.format("\uD83D\uDD25 \u70ED\u70B9\u5173\u7CFB TOP%d | %s", pairs.size(), range));
                } catch (Exception ex) { resultArea.setText("\u274C \u70ED\u70B9\u67E5\u8BE2\u5931\u8D25: " + safeMsg(ex)); }
            }
        }.execute();
    }

    private void showDistribution() {
        if (!validateDates()) return;
        tableModel.setColumnIdentifiers(new String[]{"\u4E8B\u4EF6\u7C7B\u578B", "", "\u6570\u91CF", "\u5360\u6BD4"});
        tableModel.setRowCount(0);
        resultArea.setText("\u23f3 \u7EDF\u8BA1\u4E2D...");
        prevBtn.setEnabled(false); nextBtn.setEnabled(false); pageLabel.setText("  \u5206\u5E03\u7EDF\u8BA1");
        new SwingWorker<Map<String, Integer>, Void>() {
            @Override protected Map<String, Integer> doInBackground() { return QueryService.getEventDistribution(start(), end()); }
            @Override protected void done() {
                try {
                    Map<String, Integer> dist = get();
                    int total = dist.values().stream().mapToInt(Integer::intValue).sum();
                    for (Map.Entry<String, Integer> e : dist.entrySet()) {
                        double pct = total > 0 ? 100.0 * e.getValue() / total : 0;
                        tableModel.addRow(new Object[]{e.getKey(), "", String.format("%,d", e.getValue()), String.format("%.1f%%", pct)});
                    }
                    String range = !start().isEmpty() || !end().isEmpty() ? start() + " ~ " + end() : "\u5168\u90E8\u6570\u636E";
                    resultArea.setText(String.format("\uD83D\uDCCA \u5206\u5E03\u7EDF\u8BA1 | %s | \u5171 %,d \u6761 | %d \u7C7B", range, total, dist.size()));
                } catch (Exception ex) { resultArea.setText("\u274C \u7EDF\u8BA1\u5931\u8D25: " + safeMsg(ex)); }
            }
        }.execute();
    }

    private void clearAll() {
        resetToEventColumns(); tableModel.setRowCount(0);
        currentPage = 0; totalCount = 0;
        prevBtn.setEnabled(false); nextBtn.setEnabled(false);
        pageLabel.setText("  \u7B2C 0 \u9875 / \u5171 0 \u6761");
        resultArea.setText("\uD83D\uDCA1 \u63D0\u793A\uFF1A\u65E5\u671F\u7559\u7A7A=\u67E5\u8BE2\u5168\u90E8\u6570\u636E | \u56FD\u5BB6\u4EE3\u7801\u5982 USA,CHN,RUS | \u5FEB\u6377\u6309\u94AE\u8BBE\u5B9A\u65E5\u671F\u8303\u56F4");
    }

    private void updatePagination() {
        int totalPages = totalCount > 0 ? (totalCount + PAGE_SIZE - 1) / PAGE_SIZE : 0;
        pageLabel.setText(String.format("  \u7B2C %d/%d \u9875 | \u5171 %,d \u6761", currentPage+1, totalPages, totalCount));
        prevBtn.setEnabled(currentPage > 0);
        nextBtn.setEnabled((currentPage + 1) * PAGE_SIZE < totalCount);
    }

    private void resetToEventColumns() { tableModel.setColumnIdentifiers(EVENT_COLS); }
    private void setHotColumns() { tableModel.setColumnIdentifiers(HOT_COLS); }
    private static String nvl(String s) { return s == null ? "" : s; }
    private static String safeMsg(Exception ex) {
        Throwable c = ex.getCause();
        return c != null && c.getMessage() != null ? c.getMessage() : ex.getMessage();
    }
}