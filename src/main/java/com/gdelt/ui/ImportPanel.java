package com.gdelt.ui;

import com.gdelt.service.ImportService;
import com.gdelt.util.AppConfig;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.List;

public class ImportPanel extends JPanel {

    private JTextArea logArea;
    private JLabel statusLabel, dbStatusLabel;
    private JProgressBar progressBar;
    private JButton scanBtn, importBtn;
    private JComboBox<String> dirCombo;
    private DefaultListModel<String> fileListModel;
    private JList<String> fileList;

    private File[] scannedFiles = new File[0];
    private volatile boolean importing = false;

    public ImportPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        top.setBorder(BorderFactory.createTitledBorder("数据导入"));

        top.add(new JLabel("扫描目录:"));
        dirCombo = new JComboBox<>(new String[]{AppConfig.DOWNLOAD_DIR, AppConfig.IMPORT_DIR});
        dirCombo.setEditable(true);
        dirCombo.setPreferredSize(new Dimension(220, 28));
        top.add(dirCombo);

        scanBtn = new JButton("扫描本地ZIP");
        scanBtn.addActionListener(e -> scanLocal());
        top.add(scanBtn);

        importBtn = new JButton("导入数据库");
        importBtn.setEnabled(false);
        importBtn.addActionListener(e -> startImport());
        top.add(importBtn);

        dbStatusLabel = new JLabel("");
        top.add(dbStatusLabel);

        add(top, BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);

        fileListModel = new DefaultListModel<>();
        fileList = new JList<>(fileListModel);
        fileList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        // font handled by MainApp.setGlobalFont()
        JScrollPane listScroll = new JScrollPane(fileList);
        listScroll.setBorder(BorderFactory.createTitledBorder("ZIP文件列表"));

        logArea = new JTextArea(6, 60);
        logArea.setEditable(false);
        // font handled by MainApp.setGlobalFont()
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("导入日志"));

        split.setTopComponent(listScroll);
        split.setBottomComponent(logScroll);
        split.setDividerLocation(200);
        add(split, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(5, 3));
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        bottom.add(progressBar, BorderLayout.NORTH);

        statusLabel = new JLabel("就绪");
        statusLabel.setBorder(BorderFactory.createLoweredSoftBevelBorder());
        bottom.add(statusLabel, BorderLayout.SOUTH);

        add(bottom, BorderLayout.SOUTH);
    }

    private void scanLocal() {
        scanBtn.setEnabled(false);
        statusLabel.setText("正在扫描...");
        fileListModel.clear();
        scannedFiles = new File[0];

        new SwingWorker<File[], Void>() {
            protected File[] doInBackground() {
                String dir = (String) dirCombo.getSelectedItem();
                File d = new File(dir);
                if (!d.exists() || !d.isDirectory()) return new File[0];
                return d.listFiles(f -> f.getName().toLowerCase().endsWith(".zip")
                        || f.getName().toLowerCase().endsWith(".csv.zip"));
            }
            protected void done() {
                try {
                    scannedFiles = get();
                    fileListModel.clear();
                    long totalSize = 0;
                    for (File f : scannedFiles) {
                        totalSize += f.length();
                        fileListModel.addElement(f.getName() + "  [" +
                                String.format("%.1f", f.length() / 1048576.0) + " MB]");
                    }
                    statusLabel.setText("扫描到 " + scannedFiles.length + " 个ZIP, 共 " +
                            String.format("%.1f", totalSize / 1048576.0) + " MB");
                    importBtn.setEnabled(scannedFiles.length > 0);
                    checkDatabase();
                } catch (Exception ex) {
                    log("扫描失败: " + ex.getMessage());
                } finally {
                    scanBtn.setEnabled(true);
                }
            }
        }.execute();
    }

    private void startImport() {
        if (scannedFiles == null || scannedFiles.length == 0) {
            JOptionPane.showMessageDialog(this, "请先扫描ZIP文件！");
            return;
        }

        int[] sel = fileList.getSelectedIndices();
        final File[] zips = sel.length > 0
                ? java.util.Arrays.stream(sel).mapToObj(i -> scannedFiles[i]).toArray(File[]::new)
                : scannedFiles;

        int confirm = JOptionPane.showConfirmDialog(this,
                "即将导入 " + zips.length + " 个ZIP文件到数据库。\n\n确认开始？",
                "确认导入", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        importing = true;
        importBtn.setEnabled(false);
        scanBtn.setEnabled(false);
        progressBar.setValue(0);
        logArea.setText("");

        final int[] totalImported = {0};
        final int[] totalFailed = {0};

        new SwingWorker<Integer, String>() {
            protected Integer doInBackground() {
                for (int i = 0; i < zips.length; i++) {
                    if (!importing) break;
                    publish("开始: " + zips[i].getName());
                    try {
                        int cnt = ImportService.importFromZip(zips[i], null);
                        totalImported[0] += cnt;
                        publish("完成: " + zips[i].getName() + " (" + cnt + " 条)");
                    } catch (Exception e) {
                        totalFailed[0]++;
                        publish("失败: " + zips[i].getName() + " - " + e.getMessage());
                    }
                    setProgress(100 * (i + 1) / zips.length);
                }
                return totalImported[0];
            }
            protected void process(List<String> msgs) {
                for (String m : msgs) log(m);
            }
            protected void done() {
                importing = false;
                importBtn.setEnabled(true);
                scanBtn.setEnabled(true);
                try {
                    int total = get();
                    log("========== 导入完毕 ==========");
                    log("入库 " + String.format("%,d", total) + " 条事件");
                    if (totalFailed[0] > 0) log("失败 " + totalFailed[0] + " 个文件");
                    statusLabel.setText("导入完成: " + String.format("%,d", total) + " 条");
                    checkDatabase();
                } catch (Exception ex) {
                    log("导入异常: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void checkDatabase() {
        int count = ImportService.getTotalEvents();
        log("数据库: " + (count > 0
                ? String.format("%,d", count) + " 条事件"
                : "空数据库"));
        dbStatusLabel.setText("数据库: " + (count > 0 ? String.format("%,d", count) + " 条" : "空"));
    }

    private void log(String msg) {
        logArea.append(msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
}