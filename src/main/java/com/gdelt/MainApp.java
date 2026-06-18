package com.gdelt;

import com.gdelt.ui.MainFrame;
import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * GDELT地缘政治态势感知与分析系统 - 主入口
 */
public class MainApp {
    public static void main(String[] args) {
        System.setProperty("file.encoding", "UTF-8");
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("sun.java2d.font.DisableLibraries", "false");

        SwingUtilities.invokeLater(() -> {
            try {
                setGlobalFont();
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                MainFrame frame = new MainFrame();
                frame.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null,
                    "启动失败:\n" + e.getMessage(),
                    "GDELT Analyzer - 错误",
                    JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }

    /** 设置全局中文字体 */
    private static void setGlobalFont() {
        // 方案A: 直接用 SansSerif（几乎所有系统都支持中文回退）
        Font baseFont = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        
        // 方案B: 尝试更好的中文字体覆盖
        String[] preferred = {
            "Microsoft YaHei", "微软雅黑",
            "SimSun", "宋体",
            "SimHei", "黑体",
            "FangSong", "仿宋",
            "KaiTi", "楷体",
            "Noto Sans CJK SC",
            "Source Han Sans SC",
            "WenQuanYi Micro Hei",
            "WenQuanYi Zen Hei"
        };
        
        String[] available = GraphicsEnvironment
            .getLocalGraphicsEnvironment()
            .getAvailableFontFamilyNames();
        Set<String> availSet = new HashSet<>(Arrays.asList(available));
        
        for (String name : preferred) {
            if (availSet.contains(name)) {
                baseFont = new Font(name, Font.PLAIN, 13);
                break;
            }
        }

        Font labelFont  = baseFont.deriveFont(13f);
        Font buttonFont = baseFont.deriveFont(Font.BOLD, 13f);
        Font titleFont  = baseFont.deriveFont(Font.BOLD, 14f);
        Font monoFont   = new Font(Font.MONOSPACED, Font.PLAIN, 12);

        // 逐项覆盖所有字体键
        for (Object key : UIManager.getDefaults().keySet()) {
            if (key.toString().toLowerCase().contains("font")) {
                Object val = UIManager.getDefaults().get(key);
                if (val instanceof Font) {
                    String ks = key.toString().toLowerCase();
                    if (ks.contains("button") || ks.contains("toggle"))
                        UIManager.put(key, buttonFont);
                    else if (ks.contains("title") || ks.contains("tabbedpane") || ks.contains("header"))
                        UIManager.put(key, titleFont);
                    else if (ks.contains("textarea") || ks.contains("textfield") || ks.contains("monospaced"))
                        UIManager.put(key, monoFont);
                    else
                        UIManager.put(key, labelFont);
                }
            }
        }
    }
}
