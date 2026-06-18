package com.gdelt.ui;

import com.gdelt.service.GdeltCrawler;
import com.gdelt.util.AppConfig;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

public class CrawlerPanel extends JPanel {

    private Map<Integer, List<String>> yearData;
    private Map<Integer, Map<Integer, List<String>>> yearMonthData;
    private String masterContent;

    private final Map<Integer, JCheckBox> yearBoxes = new TreeMap<>();
    private final JCheckBox[] monthBoxes = new JCheckBox[12];

    private JPanel yearPanel, monthPanel;
    private JButton fetchBtn, downloadBtn;
    private JComboBox<String> storageCombo, timeCombo;
    private JProgressBar progressBar;
    private JLabel statusLabel, progressLabel;
    private JTextArea logArea;

    private volatile boolean downloading = false;
    private Thread downloadThread;

    public CrawlerPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        top.setBorder(BorderFactory.createTitledBorder("操作"));

        fetchBtn = new JButton("获取文件列表");
        fetchBtn.addActionListener(e -> fetch());
        top.add(fetchBtn);

        top.add(new JLabel("存储:"));
        storageCombo = new JComboBox<>(new String[]{AppConfig.DOWNLOAD_DIR, "./downloads"});
        storageCombo.setEditable(true);
        storageCombo.setPreferredSize(new Dimension(200, 28));
        top.add(storageCombo);

        downloadBtn = new JButton("开始下载");
        downloadBtn.setEnabled(false);
        downloadBtn.addActionListener(e -> startDownload());
        top.add(downloadBtn);

        JButton stopBtn = new JButton("停止");
        stopBtn.addActionListener(e -> stopDownload());
        top.add(stopBtn);

        add(top, BorderLayout.NORTH);

        // ---- Filter panel: Year | Month | Time ----
        JPanel filterPanel = new JPanel(new GridLayout(1, 3, 10, 0));

        // Year column
        yearPanel = new JPanel();
        yearPanel.setLayout(new GridLayout(0, 1, 3, 2));
        JScrollPane yearSp = new JScrollPane(yearPanel);
        yearSp.setBorder(BorderFactory.createTitledBorder("选择年份"));
        yearSp.setPreferredSize(new Dimension(180, 250));
        JPanel yearCol = new JPanel(new BorderLayout());
        yearCol.add(yearSp, BorderLayout.CENTER);
        JPanel yearBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 2));
        JButton allYr = new JButton("全选");
        allYr.addActionListener(e -> { for (JCheckBox b : yearBoxes.values()) b.setSelected(true); });
        JButton invYr = new JButton("反选");
        invYr.addActionListener(e -> { for (JCheckBox b : yearBoxes.values()) b.setSelected(!b.isSelected()); });
        yearBtns.add(allYr); yearBtns.add(invYr);
        yearCol.add(yearBtns, BorderLayout.SOUTH);
        filterPanel.add(yearCol);

        // Month column
        monthPanel = new JPanel();
        monthPanel.setLayout(new GridLayout(0, 1, 3, 2));
        for (int i = 0; i < 12; i++) {
            monthBoxes[i] = new JCheckBox((i + 1) + "月", true);
            monthPanel.add(monthBoxes[i]);
        }
        JScrollPane monthSp = new JScrollPane(monthPanel);
        monthSp.setBorder(BorderFactory.createTitledBorder("月份筛选"));
        monthSp.setPreferredSize(new Dimension(100, 250));
        JPanel monthCol = new JPanel(new BorderLayout());
        monthCol.add(monthSp, BorderLayout.CENTER);
        JPanel monthBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 2));
        JButton allMo = new JButton("全选");
        allMo.addActionListener(e -> { for (JCheckBox b : monthBoxes) b.setSelected(true); });
        JButton invMo = new JButton("反选");
        invMo.addActionListener(e -> { for (JCheckBox b : monthBoxes) b.setSelected(!b.isSelected()); });
        monthBtns.add(allMo); monthBtns.add(invMo);
        monthCol.add(monthBtns, BorderLayout.SOUTH);
        filterPanel.add(monthCol);

        // Time column
        JPanel timeCol = new JPanel(new BorderLayout());
        timeCol.setBorder(BorderFactory.createTitledBorder("时间段"));
        timeCombo = new JComboBox<>(new String[]{
            "全天 (00-23)", "早晨 (06-08)", "上午 (09-11)",
            "下午 (12-17)", "晚间 (18-20)", "深夜 (21-05)"
        });
        timeCombo.setPreferredSize(new Dimension(160, 28));
        JPanel timeTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 10));
        timeTop.add(timeCombo);
        timeCol.add(timeTop, BorderLayout.NORTH);
        // Summary label
        JLabel filterSummary = new JLabel("<html>选择年份和月份<br>后可精确筛选</html>");
        filterSummary.setForeground(Color.GRAY);
        timeCol.add(filterSummary, BorderLayout.CENTER);
        filterPanel.add(timeCol);

        add(filterPanel, BorderLayout.CENTER);

        // Bottom: progress + log
        JPanel bottom = new JPanel(new BorderLayout(5, 5));

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressLabel = new JLabel("就绪");
        JPanel progRow = new JPanel(new BorderLayout());
        progRow.add(progressBar, BorderLayout.NORTH);
        progRow.add(progressLabel, BorderLayout.CENTER);
        bottom.add(progRow, BorderLayout.NORTH);

        logArea = new JTextArea(5, 60);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        bottom.add(new JScrollPane(logArea), BorderLayout.CENTER);

        statusLabel = new JLabel("点击获取文件列表开始");
        statusLabel.setBorder(BorderFactory.createLoweredSoftBevelBorder());
        bottom.add(statusLabel, BorderLayout.SOUTH);

        add(bottom, BorderLayout.SOUTH);
    }

    private void fetch() {
        fetchBtn.setEnabled(false);
        statusLabel.setText("正在获取 masterfilelist.txt ...");
        log("开始获取...");

        new SwingWorker<String, Void>() {
            protected String doInBackground() throws Exception {
                return GdeltCrawler.fetchMasterList();
            }
            protected void done() {
                try {
                    masterContent = get();
                    yearMonthData = GdeltCrawler.groupByYearMonth(masterContent);
                    yearData = GdeltCrawler.groupByYear(masterContent);
                    yearPanel.removeAll();
                    yearBoxes.clear();
                    int total = 0;
                    for (Map.Entry<Integer, List<String>> e : yearData.entrySet()) {
                        int cnt = e.getValue().size();
                        total += cnt;
                        JCheckBox cb = new JCheckBox(e.getKey() + " (" + cnt + " 文件)");
                        yearBoxes.put(e.getKey(), cb);
                        yearPanel.add(cb);
                    }
                    yearPanel.revalidate();
                    yearPanel.repaint();
                    statusLabel.setText("共 " + total + " 个文件, " + yearBoxes.size() + " 个年份");
                    downloadBtn.setEnabled(true);
                    log("解析完成: " + total + " 个文件");
                } catch (Exception ex) {
                    log("失败: " + ex.getMessage());
                    statusLabel.setText("获取失败");
                } finally {
                    fetchBtn.setEnabled(true);
                }
            }
        }.execute();
    }

    private String getSelectedTimePeriod() {
        int idx = timeCombo.getSelectedIndex();
        return switch (idx) {
            case 1 -> "morning";
            case 2 -> "forenoon";
            case 3 -> "afternoon";
            case 4 -> "evening";
            case 5 -> "night";
            default -> "all";
        };
    }

    private void startDownload() {
        Set<Integer> selYears = new TreeSet<>();
        for (Map.Entry<Integer, JCheckBox> e : yearBoxes.entrySet()) {
            if (e.getValue().isSelected()) selYears.add(e.getKey());
        }
        if (selYears.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先勾选年份！");
            return;
        }

        Set<Integer> selMonths = new TreeSet<>();
        for (int i = 0; i < 12; i++) {
            if (monthBoxes[i].isSelected()) selMonths.add(i + 1);
        }
        if (selMonths.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请至少选择一个月份！");
            return;
        }

        String timePeriod = getSelectedTimePeriod();

        // Apply month + time filters
        List<String> urls = GdeltCrawler.filterByMonth(yearMonthData, selYears, selMonths);
        urls = GdeltCrawler.filterByTime(urls, timePeriod);
        final List<String> finalUrls = urls;

        if (finalUrls.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "当前筛选条件下没有匹配的文件！\n请调整年份/月份/时间段。");
            return;
        }

        Path dir = Paths.get((String) storageCombo.getSelectedItem());
        String filterDesc = String.format(
            "年份:%d个  月份:%d个  时段:%s",
            selYears.size(), selMonths.size(),
            timeCombo.getSelectedItem());
        int confirm = JOptionPane.showConfirmDialog(this,
            "下载 " + finalUrls.size() + " 个文件到:\n" + dir.toAbsolutePath() +
            "\n\n筛选: " + filterDesc + "\n\n确认？",
            "确认", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        downloading = true;
        downloadBtn.setEnabled(false);
        fetchBtn.setEnabled(false);
        progressBar.setValue(0);
        logArea.setText("");
        log("筛选条件: " + filterDesc);
        log("匹配文件: " + finalUrls.size() + " 个");

        final int total = finalUrls.size();
        final int[] done = {0};
        final int[] fail = {0};
        final long t0 = System.currentTimeMillis();

        downloadThread = new Thread(() -> {
            try {
                for (int i = 0; i < finalUrls.size() && downloading; i++) {
                    String url = finalUrls.get(i);
                    try {
                        GdeltCrawler.downloadFile(url, dir);
                        done[0]++;
                        log(String.format("[%d/%d] OK  %s", i + 1, total,
                                url.substring(url.lastIndexOf("/") + 1)));
                    } catch (Exception ex) {
                        fail[0]++;
                        log(String.format("[%d/%d] FAIL %s", i + 1, total, ex.getMessage()));
                    }
                    final int idx = i + 1;
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(100 * idx / total);
                        long elapsed = Math.max(1, System.currentTimeMillis() - t0);
                        double spd = idx * 1000.0 / elapsed;
                        progressLabel.setText(String.format(
                                "%d/%d  速度:%.1f/秒  成功:%d  失败:%d",
                                idx, total, spd, done[0], fail[0]));
                    });
                }
            } catch (Exception ex) {
                log("异常: " + ex.getMessage());
            } finally {
                downloading = false;
                SwingUtilities.invokeLater(() -> {
                    downloadBtn.setEnabled(true);
                    fetchBtn.setEnabled(true);
                    statusLabel.setText("完成! 成功:" + done[0] + " 失败:" + fail[0]);
                });
            }
        });
        downloadThread.setDaemon(true);
        downloadThread.start();
    }

    private void stopDownload() {
        downloading = false;
        if (downloadThread != null) downloadThread.interrupt();
        log("已停止");
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public void shutdown() {
        downloading = false;
        if (downloadThread != null) downloadThread.interrupt();
    }
}