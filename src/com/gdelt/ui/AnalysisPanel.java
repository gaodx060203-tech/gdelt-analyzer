package com.gdelt.ui;

import com.gdelt.service.AnalysisService;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;

/**
 * 地缘政治挖掘分析面板
 * v3.1: 冲突预测改为双边模式 — 四指标加权 + 风险分级 + 进度条可视化
 */
public class AnalysisPanel extends JPanel {

    private JTextArea outputArea;
    private JTextField countryAField, countryBField;
    private JSpinner lookbackSpinner, predictSpinner;
    private JLabel probabilityLabel, riskLabel;
    private JProgressBar barGoldstein, barConflict, barTone, barSurge;
    private JTextArea detailArea;
    private JPanel predictResultPanel;

    public AnalysisPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ===== 顶部: 参数设置区 =====
        JPanel paramPanel = new JPanel(new BorderLayout(10, 5));
        paramPanel.setBorder(new TitledBorder("🔮 双边冲突概率预测参数"));

        // 国家输入行
        JPanel countryRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        countryRow.add(new JLabel("国家A:"));
        countryAField = new JTextField("USA", 6);
        countryAField.setToolTipText("输入国家代码，如 USA/CHN/RUS/GBR");
        countryRow.add(countryAField);
        countryRow.add(new JLabel("↔"));
        countryBField = new JTextField("CHN", 6);
        countryBField.setToolTipText("输入国家代码，如 USA/CHN/RUS/GBR");
        countryRow.add(countryBField);
        countryRow.add(new JLabel("  回溯:"));
        lookbackSpinner = new JSpinner(new SpinnerNumberModel(90, 7, 3650, 7));
        lookbackSpinner.setToolTipText("回溯分析的天数 (7-3650天)");
        countryRow.add(lookbackSpinner);
        countryRow.add(new JLabel("天"));
        countryRow.add(new JLabel("预测:"));
        predictSpinner = new JSpinner(new SpinnerNumberModel(30, 1, 365, 1));
        predictSpinner.setToolTipText("未来预测的天数 (1-365天)");
        countryRow.add(predictSpinner);
        countryRow.add(new JLabel("天"));

        // 快捷预设 + 按钮行
        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        actionRow.add(new JLabel("快捷预设:"));

        String[][] presets = {
            {"中美", "USA", "CHN"}, {"俄乌", "RUS", "UKR"}, {"印巴", "IND", "PAK"},
            {"韩朝", "KOR", "PRK"}, {"美俄", "USA", "RUS"}, {"美伊", "USA", "IRN"}
        };
        for (String[] p : presets) {
            JButton btn = new JButton(p[0]);
            btn.setToolTipText(p[1] + " ↔ " + p[2]);
            btn.addActionListener(e -> {
                countryAField.setText(p[1]);
                countryBField.setText(p[2]);
            });
            actionRow.add(btn);
        }

        JButton predictBtn = new JButton("🔮 开始预测");
        predictBtn.addActionListener(e -> runPrediction());
        actionRow.add(Box.createHorizontalStrut(20));
        actionRow.add(predictBtn);

        JButton kmeansBtn = new JButton("🧩 K-Means聚类");
        kmeansBtn.addActionListener(e -> runKMeans());
        actionRow.add(kmeansBtn);

        JButton trendBtn = new JButton("📈 趋势分析");
        trendBtn.addActionListener(e -> runTrend());
        actionRow.add(trendBtn);

        paramPanel.add(countryRow, BorderLayout.NORTH);
        paramPanel.add(actionRow, BorderLayout.SOUTH);
        add(paramPanel, BorderLayout.NORTH);

        // ===== 中部: 预测结果区 =====
        JPanel centerPanel = new JPanel(new BorderLayout(10, 10));

        // 左侧: 概率 + 指标进度条
        predictResultPanel = new JPanel();
        predictResultPanel.setLayout(new BoxLayout(predictResultPanel, BoxLayout.Y_AXIS));
        predictResultPanel.setBorder(new TitledBorder("预测结果"));
        predictResultPanel.setPreferredSize(new Dimension(350, 300));

        // 概率大字
        probabilityLabel = new JLabel("--%", SwingConstants.CENTER);
        probabilityLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 64));
        probabilityLabel.setForeground(new Color(100, 100, 100));
        probabilityLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        predictResultPanel.add(probabilityLabel);

        // 风险等级
        riskLabel = new JLabel("等待预测", SwingConstants.CENTER);
        riskLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        riskLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        predictResultPanel.add(riskLabel);
        predictResultPanel.add(Box.createVerticalStrut(15));

        // 四项指标进度条
        JPanel barsPanel = new JPanel(new GridLayout(4, 1, 5, 2));
        barsPanel.setBorder(new TitledBorder("指标贡献度"));

        barGoldstein = createIndicatorBar(barsPanel, "Goldstein趋势 (35%)", new Color(66, 133, 244));
        barConflict  = createIndicatorBar(barsPanel, "冲突占比   (30%)", new Color(234, 67, 53));
        barTone      = createIndicatorBar(barsPanel, "语调趋势   (20%)", new Color(251, 188, 4));
        barSurge     = createIndicatorBar(barsPanel, "频率飙升   (15%)", new Color(52, 168, 83));

        predictResultPanel.add(barsPanel);

        // 右侧: 详细报告
        detailArea = new JTextArea();
        detailArea.setEditable(false);
        detailArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        detailArea.setBackground(new Color(250, 250, 250));
        JScrollPane detailScroll = new JScrollPane(detailArea);
        detailScroll.setBorder(new TitledBorder("详细分析报告"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                predictResultPanel, detailScroll);
        splitPane.setResizeWeight(0.35);
        centerPanel.add(splitPane, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);

        // ===== 底部: 日志输出区 =====
        outputArea = new JTextArea(4, 80);
        outputArea.setEditable(false);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        outputArea.setBackground(new Color(245, 245, 245));
        JScrollPane bottomScroll = new JScrollPane(outputArea);
        bottomScroll.setBorder(new TitledBorder("运行日志"));
        add(bottomScroll, BorderLayout.SOUTH);
    }

    /** 创建单个指标进度条 */
    private JProgressBar createIndicatorBar(JPanel parent, String name, Color color) {
        JPanel row = new JPanel(new BorderLayout(5, 0));
        JLabel label = new JLabel(name);
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        row.add(label, BorderLayout.WEST);

        JProgressBar bar = new JProgressBar(0, 100);
        bar.setStringPainted(true);
        bar.setForeground(color);
        bar.setValue(0);
        row.add(bar, BorderLayout.CENTER);
        parent.add(row);
        return bar;
    }

    /** 执行双边冲突预测 */
    private void runPrediction() {
        String countryA = countryAField.getText().trim().toUpperCase();
        String countryB = countryBField.getText().trim().toUpperCase();
        int lookbackDays = (int) lookbackSpinner.getValue();
        int predictDays = (int) predictSpinner.getValue();

        if (countryA.isEmpty() || countryB.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入两个国家代码", "参数错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (countryA.equals(countryB)) {
            JOptionPane.showMessageDialog(this, "请选择两个不同的国家", "参数错误", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 显示等待状态
        probabilityLabel.setText("...");
        probabilityLabel.setForeground(new Color(100, 100, 100));
        riskLabel.setText("分析中...");
        barGoldstein.setValue(0);
        barConflict.setValue(0);
        barTone.setValue(0);
        barSurge.setValue(0);
        detailArea.setText("⏳ 正在查询 " + countryA + " ↔ " + countryB + " 的交互数据...\n");

        final String ca = countryA, cb = countryB;
        final int lb = lookbackDays, pd = predictDays;

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return AnalysisService.bilateralConflictPredict(ca, cb, lb, pd);
            }
            @Override
            protected void done() {
                try {
                    String result = get();
                    detailArea.setText(result);
                    outputArea.append("[预测完成] " + ca + " ↔ " + cb + "\n");
                    parseAndUpdateUI(result);
                } catch (Exception ex) {
                    detailArea.setText("❌ 预测失败: " + ex.getMessage());
                    probabilityLabel.setText("ERR");
                    riskLabel.setText("执行出错");
                    outputArea.append("[错误] " + ex.getMessage() + "\n");
                }
            }
        }.execute();
    }

    /** 从报告文本中解析数据更新 UI 组件 */
    private void parseAndUpdateUI(String report) {
        try {
            // 解析综合冲突概率
            if (report.contains("综合冲突概率:")) {
                int idx = report.indexOf("综合冲突概率:");
                int pctEnd = report.indexOf("%", idx);
                if (pctEnd > idx) {
                    String pctStr = report.substring(idx + 8, pctEnd).trim();
                    double pct = Double.parseDouble(pctStr);
                    probabilityLabel.setText(String.format("%.1f%%", pct));

                    // 颜色
                    if (pct < 20) probabilityLabel.setForeground(new Color(52, 168, 83));
                    else if (pct < 40) probabilityLabel.setForeground(new Color(66, 133, 244));
                    else if (pct < 60) probabilityLabel.setForeground(new Color(251, 188, 4));
                    else if (pct < 80) probabilityLabel.setForeground(new Color(255, 152, 0));
                    else probabilityLabel.setForeground(new Color(234, 67, 53));
                }
            }

            // 解析风险等级
            if (report.contains("低风险")) riskLabel.setText("🟢 低风险");
            else if (report.contains("极高风险")) riskLabel.setText("🔴 极高风险");
            else if (report.contains("高风险")) riskLabel.setText("🟠 高风险");
            else if (report.contains("警戒")) riskLabel.setText("🟡 警戒");
            else if (report.contains("关注")) riskLabel.setText("🔵 关注");

            // 解析四项指标百分比
            int[] barValues = new int[4];
            JProgressBar[] bars = {barGoldstein, barConflict, barTone, barSurge};
            String[] keywords = {"Goldstein趋势", "冲突事件占比", "语调趋势", "频率飙升指数"};

            for (int i = 0; i < 4; i++) {
                int idx = report.indexOf(keywords[i]);
                if (idx >= 0) {
                    int pctStart = report.indexOf("]", idx);
                    if (pctStart >= 0) {
                        int pctEnd = report.indexOf("%", pctStart);
                        if (pctEnd > pctStart) {
                            try {
                                double val = Double.parseDouble(
                                    report.substring(pctStart + 1, pctEnd).trim());
                                barValues[i] = (int) val;
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }

            // 动画更新进度条
            for (int i = 0; i < 4; i++) {
                final int index = i;
                final int target = barValues[i];
                new Thread(() -> {
                    for (int v = 0; v <= target; v += 2) {
                        final int val = v;
                        SwingUtilities.invokeLater(() -> bars[index].setValue(val));
                        try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                    }
                    SwingUtilities.invokeLater(() -> bars[index].setValue(target));
                }).start();
            }

        } catch (Exception e) {
            outputArea.append("[UI解析警告] " + e.getMessage() + "\n");
        }
    }

    /** K-Means 聚类 */
    private void runKMeans() {
        detailArea.setText("⏳ K-Means聚类分析中，请稍候...");
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return AnalysisService.kMeansClustering();
            }
            @Override
            protected void done() {
                try {
                    String result = get();
                    detailArea.setText(result);
                    outputArea.append("[K-Means] 聚类完成\n");
                } catch (Exception ex) {
                    detailArea.setText("聚类失败: " + ex.getMessage());
                }
            }
        }.execute();
    }

    /** 双边趋势 */
    private void runTrend() {
        String c1 = countryAField.getText().trim();
        String c2 = countryBField.getText().trim();
        if (c1.isEmpty() || c2.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入两个国家代码");
            return;
        }
        detailArea.setText("⏳ 趋势分析中...");
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                var data = AnalysisService.trend(c1, c2);
                var vals = data.get("values");
                if (vals == null || vals.isEmpty())
                    return "无趋势数据（请先导入数据）";
                StringBuilder sb = new StringBuilder();
                sb.append("========== ").append(c1).append(" ↔ ").append(c2)
                  .append(" 关系趋势 ==========\n\n");
                double sum = 0;
                for (int i = 0; i < vals.size(); i++) {
                    sb.append(String.format("  时段%2d: Goldstein = %+.2f\n", i+1, vals.get(i)));
                    sum += vals.get(i);
                }
                double avg = sum / vals.size();
                sb.append(String.format("\n均值: %+.2f", avg));
                sb.append(avg > 0 ? " (偏友好)" : avg < 0 ? " (偏冲突)" : " (中性)");
                return sb.toString();
            }
            @Override
            protected void done() {
                try {
                    detailArea.setText(get());
                    outputArea.append("[趋势分析] 完成\n");
                } catch (Exception ex) {
                    detailArea.setText("趋势分析失败: " + ex.getMessage());
                }
            }
        }.execute();
    }
}
