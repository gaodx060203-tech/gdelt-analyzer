package com.gdelt.ui;

import com.gdelt.service.ImportService;
import com.gdelt.util.AppConfig;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * 主窗口 — 5个功能Tab：爬虫下载 | 数据导入 | 查询检索 | 挖掘分析 | 可视化
 * v3.0: 新增爬虫下载标签页，移除HTTP服务器
 */
public class MainFrame extends JFrame {
    private JTabbedPane tabbedPane;
    private JLabel statusLabel;
    private JLabel dbCountLabel;
    private ImportPanel importPanel;

    public MainFrame() {
        initUI();
        updateStatus();
    }

    private void initUI() {
        setTitle("GDELT 地缘政治态势感知分析系统 v" + AppConfig.VERSION);
        setSize(1200, 800);
        setMinimumSize(new Dimension(900, 600));
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) { shutdown(); }
        });

        setLocationRelativeTo(null);
        setJMenuBar(createMenuBar());

        tabbedPane = new JTabbedPane();
        tabbedPane.addTab("📡 爬虫下载", createCrawlerTab());
        tabbedPane.addTab("📥 数据导入", createImportTab());
        tabbedPane.addTab("🔍 查询检索", createQueryTab());
        tabbedPane.addTab("📊 挖掘分析", createAnalysisTab());
        tabbedPane.addTab("📈 可视化",   createChartTab());

        tabbedPane.setMnemonicAt(0, KeyEvent.VK_1);
        tabbedPane.setMnemonicAt(1, KeyEvent.VK_2);
        tabbedPane.setMnemonicAt(2, KeyEvent.VK_3);
        tabbedPane.setMnemonicAt(3, KeyEvent.VK_4);
        tabbedPane.setMnemonicAt(4, KeyEvent.VK_5);

        add(tabbedPane, BorderLayout.CENTER);

        // 底部状态栏
        JPanel statusBar = new JPanel(new BorderLayout(10, 0));
        statusBar.setBorder(BorderFactory.createLoweredBevelBorder());
        statusLabel  = new JLabel(" 就绪");
        dbCountLabel = new JLabel("数据库: 检查中...  ");

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightPanel.add(dbCountLabel);

        statusBar.add(statusLabel, BorderLayout.WEST);
        statusBar.add(rightPanel, BorderLayout.EAST);
        statusBar.add(new JLabel("  Ctrl+1-5 切换标签 | F5刷新  "), BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        new javax.swing.Timer(30000, e -> updateStatus()).start();
    }

    private JMenuBar createMenuBar() {
        JMenuBar mb = new JMenuBar();

        JMenu fileMenu = new JMenu("文件(F)");
        fileMenu.setMnemonic(KeyEvent.VK_F);

        JMenuItem refreshItem = new JMenuItem("刷新数据库状态", KeyEvent.VK_R);
        refreshItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0));
        refreshItem.addActionListener(e -> updateStatus());
        fileMenu.add(refreshItem);
        fileMenu.addSeparator();

        JMenuItem exitItem = new JMenuItem("退出", KeyEvent.VK_X);
        exitItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F4, KeyEvent.ALT_DOWN_MASK));
        exitItem.addActionListener(e -> shutdown());
        fileMenu.add(exitItem);

        JMenu helpMenu = new JMenu("帮助(H)");
        helpMenu.setMnemonic(KeyEvent.VK_H);
        JMenuItem aboutItem = new JMenuItem("关于", KeyEvent.VK_A);
        aboutItem.addActionListener(e -> JOptionPane.showMessageDialog(this,
            "GDELT 地缘政治态势感知分析系统 v" + AppConfig.VERSION + "\n\n"
            + "功能:\n"
            + "  📡 爬虫下载 — 从GDELT官网选择性下载\n"
            + "  📥 数据导入 — ZIP解析入库\n"
            + "  🔍 事件查询 + 热点分析\n"
            + "  📊 K-Means + 趋势预测\n"
            + "  📈 纯Swing可视化\n\n"
            + "数据来源: https://www.gdeltproject.org/",
            "关于", JOptionPane.INFORMATION_MESSAGE));
        helpMenu.add(aboutItem);

        mb.add(fileMenu);
        mb.add(helpMenu);
        return mb;
    }

    // === 懒加载Tab ===
    private JPanel crawlerTab, importTab, queryTab, analysisTab, chartTab;

    private JPanel createCrawlerTab() {
        if (crawlerTab == null) crawlerTab = new CrawlerPanel();
        return crawlerTab;
    }
    private JPanel createImportTab() {
        if (importTab == null) {
            importPanel = new ImportPanel();
            importTab = importPanel;
        }
        return importTab;
    }
    private JPanel createQueryTab() {
        if (queryTab == null) queryTab = new QueryPanel(this::updateStatus);
        return queryTab;
    }
    private JPanel createAnalysisTab() {
        if (analysisTab == null) analysisTab = new AnalysisPanel();
        return analysisTab;
    }
    private JPanel createChartTab() {
        if (chartTab == null) chartTab = new ChartPanel();
        return chartTab;
    }

    /** 更新状态栏 */
    public void updateStatus() {
        int events = ImportService.getTotalEvents();
        dbCountLabel.setText("数据库: " + (events > 0 ? String.format("%,d", events) + " 条事件" : "空") + "  ");
        statusLabel.setText(" 就绪 - " + java.time.LocalTime.now().toString().substring(0, 8));
    }

    private void shutdown() {
        System.out.println("[INFO] Shutting down...");
        try {
            com.gdelt.db.DatabaseHelper.getInstance().close();
        } catch (Exception ignored) {}
        System.exit(0);
    }
}
