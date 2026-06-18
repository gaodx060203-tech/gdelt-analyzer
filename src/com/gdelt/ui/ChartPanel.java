package com.gdelt.ui;

import com.gdelt.service.QueryService;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.geom.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.Map;

/**
 * Visualization Panel — 3 self-drawn charts + date presets + all-time support
 * Pure Swing Graphics2D, zero external chart libraries
 */
public class ChartPanel extends JPanel {
    private CardLayout cardLayout;
    private JPanel cardPanel;
    private JComboBox<String> chartSelector;
    private JTextField startField, endField;
    private TrendChart trendChart;
    private BarChart barChart;
    private WorldMap mapChart;

    public ChartPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // === Control bar ===
        JPanel ctrl = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        ctrl.setBorder(new TitledBorder("图表控制"));

        ctrl.add(new JLabel("开始:"));
        ctrl.add(startField = new JTextField(8));
        startField.setToolTipText("留空=不限起始日期");
        ctrl.add(new JLabel("结束:"));
        ctrl.add(endField = new JTextField(8));
        endField.setToolTipText("留空=不限结束日期");

        // Date presets
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        JButton btn7d  = mkSmallBtn("7天");
        JButton btn30d = mkSmallBtn("30天");
        JButton btn90d = mkSmallBtn("90天");
        JButton btn1y  = mkSmallBtn("1年");
        JButton btnAll = mkSmallBtn("ALL");

        btn7d.addActionListener(e -> { LocalDate n = LocalDate.now(); endField.setText(n.format(fmt)); startField.setText(n.minusDays(7).format(fmt)); });
        btn30d.addActionListener(e ->{ LocalDate n = LocalDate.now(); endField.setText(n.format(fmt)); startField.setText(n.minusDays(30).format(fmt)); });
        btn90d.addActionListener(e ->{ LocalDate n = LocalDate.now(); endField.setText(n.format(fmt)); startField.setText(n.minusDays(90).format(fmt)); });
        btn1y.addActionListener(e -> { LocalDate n = LocalDate.now(); endField.setText(n.format(fmt)); startField.setText(n.minusYears(1).format(fmt)); });
        btnAll.addActionListener(e -> { startField.setText(""); endField.setText(""); });

        ctrl.add(btn7d); ctrl.add(btn30d); ctrl.add(btn90d);
        ctrl.add(btn1y); ctrl.add(btnAll);

        chartSelector = new JComboBox<>(new String[]{
            "📈 事件趋势折线图", "📊 事件类型柱状图", "🗺 世界热点地图"});
        JButton refreshBtn = new JButton("🔄 刷新");
        refreshBtn.setFont(refreshBtn.getFont().deriveFont(Font.BOLD));
        ctrl.add(chartSelector);
        ctrl.add(refreshBtn);
        add(ctrl, BorderLayout.NORTH);

        // === Card panel ===
        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        trendChart = new TrendChart();
        barChart = new BarChart();
        mapChart = new WorldMap();
        cardPanel.add(trendChart, "trend");
        cardPanel.add(barChart, "bar");
        cardPanel.add(mapChart, "map");
        add(cardPanel, BorderLayout.CENTER);

        chartSelector.addActionListener(e -> switchChart());
        refreshBtn.addActionListener(e -> refreshCurrent());
    }

    private JButton mkSmallBtn(String txt) {
        JButton b = new JButton(txt);
        b.setFont(b.getFont().deriveFont(Font.BOLD, 11f));
        b.setMargin(new Insets(2, 8, 2, 8));
        return b;
    }

    private void switchChart() {
        int idx = chartSelector.getSelectedIndex();
        cardLayout.show(cardPanel, idx == 0 ? "trend" : idx == 1 ? "bar" : "map");
    }

    private void refreshCurrent() {
        String start = startField.getText().trim();
        String end = endField.getText().trim();
        int idx = chartSelector.getSelectedIndex();
        if (idx == 0) trendChart.loadData(start, end);
        else if (idx == 1) barChart.loadData(start, end);
        else mapChart.loadData(start, end);
    }

    // ========== Trend Line Chart ==========
    
    // ==================== TrendChart ====================
    class TrendChart extends JPanel {
        private Map<String, Double> data = new LinkedHashMap<>();
        private String title = "";

        public TrendChart() {
            setPreferredSize(new Dimension(780, 420));
            setBackground(Color.WHITE);
        }

        public void loadData(String s, String e) {
            data = QueryService.getTrend(s, e);
            title = (!s.isEmpty() || !e.isEmpty() ? s + " ~ " + e : "全部数据");
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int W = getWidth(), H = getHeight();
            int M = 50, MW = W - 2*M, MH = H - 2*M;

            if (data.isEmpty()) {
                g2.setColor(Color.GRAY);
                g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
                g2.drawString("点击 [刷新] 加载趋势数据", W/2-100, H/2);
                return;
            }

            // Background
            g2.setColor(Color.WHITE);
            g2.fillRect(M, M, MW, MH);
            g2.setColor(new Color(230, 230, 230));
            g2.drawRect(M, M, MW, MH);

            // Find value range
            double max = 0, min = 0;
            for (double v : data.values()) {
                if (v > max) max = v;
                if (v < min) min = v;
            }
            if (max == min) { max += 1; min -= 1; }

            // Y-axis labels
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            g2.setColor(Color.DARK_GRAY);
            for (int i = 0; i <= 4; i++) {
                double val = min + (max - min) * i / 4;
                String label = String.format("%.0f", val);
                int y = M + MH - i * MH / 4;
                g2.drawString(label, M - 35, y + 4);
                g2.setColor(new Color(220, 220, 220));
                g2.drawLine(M, y, M + MW, y);
                g2.setColor(Color.DARK_GRAY);
            }

            // Plot line
            List<String> keys = new ArrayList<>(data.keySet());
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(new Color(65, 105, 225));
            Path2D path = new Path2D.Double();
            for (int i = 0; i < keys.size(); i++) {
                double val = data.get(keys.get(i));
                int x = M + (int)((double)i / (keys.size()-1) * MW);
                int y = M + MH - (int)((val - min) / (max - min) * MH);
                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            g2.draw(path);

            // Data points
            for (int i = 0; i < keys.size(); i++) {
                double val = data.get(keys.get(i));
                int x = M + (int)((double)i / (keys.size()-1) * MW);
                int y = M + MH - (int)((val - min) / (max - min) * MH);
                g2.setColor(new Color(65, 105, 225));
                g2.fillOval(x-3, y-3, 6, 6);
                g2.setColor(Color.WHITE);
                g2.fillOval(x-1, y-1, 2, 2);
            }

            // X-axis labels (show subset)
            g2.setColor(Color.DARK_GRAY);
            int step = Math.max(1, keys.size() / 10);
            for (int i = 0; i < keys.size(); i += step) {
                int x = M + (int)((double)i / (keys.size()-1) * MW);
                String label = keys.get(i);
                if (label.length() > 7) label = label.substring(0, 7);
                g2.drawString(label, x - 15, M + MH + 15);
            }

            // Title
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            g2.setColor(Color.BLACK);
            g2.drawString("事件趋势: " + title, M, M - 15);
        }
    }

    // ==================== BarChart ====================
    class BarChart extends JPanel {
        private Map<String, Double> data = new LinkedHashMap<>();
        private String title = "";

        public BarChart() {
            setPreferredSize(new Dimension(780, 420));
            setBackground(Color.WHITE);
        }

        public void loadData(String s, String e) {
            data = QueryService.getTypeDistribution(s, e);
            title = (!s.isEmpty() || !e.isEmpty() ? s + " ~ " + e : "全部数据");
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int W = getWidth(), H = getHeight();
            int M = 60, MW = W - 2*M, MH = H - 2*M;

            if (data.isEmpty()) {
                g2.setColor(Color.GRAY);
                g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
                g2.drawString("点击 [刷新] 加载分类数据", W/2-100, H/2);
                return;
            }

            g2.setColor(Color.WHITE);
            g2.fillRect(M, M, MW, MH);
            g2.setColor(new Color(230, 230, 230));
            g2.drawRect(M, M, MW, MH);

            double max = 1;
            for (double v : data.values()) if (v > max) max = v;

            List<String> keys = new ArrayList<>(data.keySet());
            int barW = Math.max(20, MW / keys.size() - 10);
            Color[] colors = {
                new Color(65, 105, 225), new Color(220, 20, 60),
                new Color(50, 205, 50), new Color(255, 140, 0),
                new Color(138, 43, 226), new Color(0, 139, 139)
            };

            for (int i = 0; i < keys.size(); i++) {
                double val = data.get(keys.get(i));
                int barH = (int)(val / max * MH);
                int x = M + i * (MW / keys.size()) + (MW/keys.size() - barW)/2;
                int y = M + MH - barH;

                g2.setColor(colors[i % colors.length]);
                g2.fillRect(x, y, barW, barH);
                g2.setColor(Color.DARK_GRAY);
                g2.drawRect(x, y, barW, barH);

                // Value label
                g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
                g2.drawString(String.format("%.0f", val), x + barW/2 - 10, y - 4);

                // Category label
                String label = keys.get(i);
                if (label.length() > 5) label = label.substring(0, 5);
                g2.drawString(label, x + barW/2 - 12, M + MH + 14);
            }

            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            g2.setColor(Color.BLACK);
            g2.drawString("事件分类分布: " + title, M, M - 15);
        }
    }

    // ==================== WorldMap ====================
    class WorldMap extends JPanel {
        private List<double[]> points = new ArrayList<>();
        private String infoText = "点击 [刷新] 加载数据";

        private static final double[][][] CONTINENTS = {
            {{-17,15},{-15,27},{-5,36},{10,37},{28,32},{35,28},{42,15},{50,10},
             {45,-5},{40,-15},{35,-28},{28,-34},{22,-34},{18,-28},{15,-20},
             {12,-10},{10,-2},{8,5},{0,5},{-5,2},{-10,5},{-15,8},{-17,15}},
            {{-10,36},{-5,43},{3,48},{0,50},{-5,55},{-8,58},{0,60},{5,62},
             {8,58},{12,55},{18,58},{22,65},{28,70},{32,70},{38,65},{42,58},
             {30,50},{35,45},{30,40},{25,35},{18,42},{12,44},{5,43},{-2,40},{-10,36}},
            {{42,58},{48,55},{55,60},{65,62},{75,70},{90,72},{105,73},{120,70},
             {135,60},{145,55},{152,50},{145,42},{140,35},{130,30},{120,25},
             {110,22},{105,10},{95,7},{88,20},{80,22},{75,15},{68,8},{60,20},
             {55,25},{50,32},{45,38},{38,38},{35,40},{42,58}},
            {{-130,55},{-125,50},{-122,37},{-120,34},{-115,32},{-105,25},
             {-98,20},{-90,20},{-82,25},{-80,15},{-77,8},{-82,10},{-85,15},
             {-90,20},{-95,28},{-100,30},{-105,30},{-110,33},{-120,40},
             {-125,48},{-130,55},{-135,58},{-142,60},{-150,62},{-160,63},
             {-168,66},{-162,70},{-145,70},{-130,72},{-110,72},{-90,70},
             {-75,65},{-65,60},{-55,50},{-60,45},{-130,55}},
            {{-80,10},{-77,8},{-75,5},{-70,2},{-68,5},{-65,0},{-60,-5},
             {-55,-8},{-50,-5},{-45,-2},{-40,-5},{-38,-10},{-40,-15},
             {-45,-20},{-50,-22},{-55,-25},{-58,-30},{-60,-35},{-63,-35},
             {-68,-30},{-70,-22},{-72,-15},{-75,-8},{-78,0},{-80,5},{-80,10}},
            {{115,-22},{120,-18},{130,-12},{140,-12},{148,-18},{150,-22},
             {153,-28},{150,-35},{148,-38},{140,-38},{135,-35},{130,-32},
             {125,-30},{118,-28},{113,-25},{115,-22}},
            {{-55,60},{-48,62},{-42,65},{-38,70},{-42,75},{-48,78},
             {-55,80},{-60,78},{-65,75},{-60,70},{-55,64},{-55,60}},
            {{-10,50},{-6,51},{-2,51},{2,52},{0,55},{-4,56},{-8,55},{-10,52},{-10,50}},
            {{130,31},{135,34},{140,36},{145,40},{143,44},{138,42},{133,38},{130,33},{130,31}},
            {{43,-12},{45,-15},{48,-18},{50,-22},{48,-26},{44,-24},{42,-20},{43,-16},{43,-12}}
        };

        private static final String[] CONTINENT_NAMES = {
            "Africa", "Europe", "Asia", "N.America", "S.America",
            "Australia", "Greenland", "UK", "Japan", "Madagascar"
        };

        public WorldMap() {
            setPreferredSize(new Dimension(800, 480));
            setBackground(new Color(235, 245, 255));
        }

        public void loadData(String s, String e) {
            points = QueryService.getGeoPoints(s, e);
            infoText = "数据点: " + points.size() + " | " +
                (!s.isEmpty() || !e.isEmpty() ? s + " ~ " + e : "全部数据");
            repaint();
        }

        private int lonToX(double lon, int M, int MW) {
            return M + (int)(MW * (lon + 180) / 360.0);
        }

        private int latToY(double lat, int M, int MH) {
            return M + (int)(MH * (90 - lat) / 180.0);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int W = getWidth(), H = getHeight();
            int M = 35, MW = W - 2*M, MH = H - 2*M;

            // === 1. Ocean depth gradient ===
            GradientPaint ocean = new GradientPaint(
                M, M, new Color(185, 215, 245),
                M, M + MH, new Color(140, 175, 215));
            g2.setPaint(ocean);
            g2.fillRect(M, M, MW, MH);

            // === 2. Grid lines ===
            g2.setStroke(new BasicStroke(0.5f, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER, 10f, new float[]{4, 8}, 0));
            g2.setColor(new Color(160, 195, 220, 100));
            for (int lat = -60; lat <= 60; lat += 30) {
                g2.drawLine(M, latToY(lat, M, MH), M+MW, latToY(lat, M, MH));
            }
            for (int lon = -150; lon <= 150; lon += 30) {
                g2.drawLine(lonToX(lon, M, MW), M, lonToX(lon, M, MW), M+MH);
            }

            g2.setColor(new Color(140, 170, 200, 90));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawLine(M, latToY(0, M, MH), M+MW, latToY(0, M, MH));
            g2.drawLine(lonToX(0, M, MW), M, lonToX(0, M, MW), M+MH);

            // === 3. Degree labels on borders ===
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
            g2.setColor(new Color(100, 130, 160));
            FontMetrics fm = g2.getFontMetrics();
            for (int lat = -60; lat <= 60; lat += 30) {
                int y = latToY(lat, M, MH);
                String label = Math.abs(lat) + "°" + (lat >= 0 ? "N" : "S");
                g2.drawString(label, M - fm.stringWidth(label) - 3, y + 4);
                g2.drawString(label, M + MW + 4, y + 4);
            }
            for (int lon = -150; lon <= 150; lon += 30) {
                int x = lonToX(lon, M, MW);
                String label = Math.abs(lon) + "°" + (lon >= 0 ? "E" : "W");
                g2.drawString(label, x - fm.stringWidth(label)/2, M - 5);
                g2.drawString(label, x - fm.stringWidth(label)/2, M + MH + 12);
            }

            // Tick marks
            g2.setStroke(new BasicStroke(0.7f));
            g2.setColor(new Color(120, 150, 170, 130));
            for (int lat = -60; lat <= 60; lat += 30) {
                int y = latToY(lat, M, MH);
                g2.drawLine(M-3, y, M, y); g2.drawLine(M+MW, y, M+MW+3, y);
            }
            for (int lon = -150; lon <= 150; lon += 30) {
                int x = lonToX(lon, M, MW);
                g2.drawLine(x, M-3, x, M); g2.drawLine(x, M+MH, x, M+MH+3);
            }

            // === 4. Continents ===
            for (int ci = 0; ci < CONTINENTS.length; ci++) {
                double[][] outline = CONTINENTS[ci];
                Path2D path = new Path2D.Double();
                path.moveTo(lonToX(outline[0][0], M, MW), latToY(outline[0][1], M, MH));
                for (int i = 1; i < outline.length; i++) {
                    path.lineTo(lonToX(outline[i][0], M, MW), latToY(outline[i][1], M, MH));
                }
                path.closePath();
                g2.setColor(new Color(238, 228, 208));
                g2.fill(path);
                g2.setColor(new Color(195, 185, 165));
                g2.setStroke(new BasicStroke(1.2f));
                g2.draw(path);

                if (ci < 6) {
                    double cx = 0, cy = 0;
                    for (double[] pt : outline) { cx += pt[0]; cy += pt[1]; }
                    cx /= outline.length; cy /= outline.length;
                    String name = CONTINENT_NAMES[ci];
                    g2.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 11));
                    FontMetrics fm2 = g2.getFontMetrics();
                    int tx = lonToX(cx, M, MW) - fm2.stringWidth(name)/2;
                    int ty = latToY(cy, M, MH);
                    g2.setColor(new Color(255, 255, 255, 140));
                    g2.drawString(name, tx+1, ty+1);
                    g2.setColor(new Color(120, 105, 85, 200));
                    g2.drawString(name, tx, ty);
                }
            }

            // === 5. Map border with shadow ===
            g2.setColor(new Color(60, 80, 100, 50));
            g2.setStroke(new BasicStroke(3f));
            g2.drawRect(M+2, M+2, MW, MH);
            g2.setColor(new Color(55, 75, 95));
            g2.setStroke(new BasicStroke(2f));
            g2.drawRect(M, M, MW, MH);

            // === 6. Data points ===
            if (points.isEmpty()) {
                g2.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 15));
                FontMetrics fm3 = g2.getFontMetrics();
                String msg = infoText;
                int msgW = fm3.stringWidth(msg);
                g2.setColor(new Color(255, 255, 255, 170));
                g2.fillRoundRect(W/2 - msgW/2 - 18, H/2 - 18, msgW + 36, 36, 18, 18);
                g2.setColor(new Color(80, 100, 120));
                g2.drawString(msg, W/2 - msgW/2, H/2 + 5);
            } else {
                for (double[] pt : points) {
                    double lon = pt[0], lat = pt[1], gs = pt[2];
                    int px = lonToX(lon, M, MW);
                    int py = latToY(lat, M, MH);
                    if (px < M || px > M+MW || py < M || py > M+MH) continue;

                    int size = 6 + Math.min(12, (int)(pt[3] / 300));
                    Color base = gs < -5 ? new Color(220, 20, 60) :
                                 gs < 0  ? new Color(255, 140, 0) :
                                 new Color(0, 160, 0);

                    // Outer glow
                    g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 45));
                    g2.fillOval(px - size - 3, py - size - 3, (size+3)*2, (size+3)*2);
                    // Mid glow
                    g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 100));
                    g2.fillOval(px - size - 1, py - size - 1, (size+1)*2, (size+1)*2);
                    // Core
                    g2.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 225));
                    g2.fillOval(px - size/2, py - size/2, size, size);
                    // Highlight
                    g2.setColor(new Color(255, 255, 255, 120));
                    g2.fillOval(px - size/4, py - size/4, size/2, size/2);
                }
            }

            // === 7. Title bar ===
            int tbW = 280, tbH = 26;
            int tbX = M + MW/2 - tbW/2, tbY = M - tbH;
            g2.setColor(new Color(20, 40, 60, 180));
            g2.fillRoundRect(tbX, tbY, tbW, tbH, 8, 8);
            g2.setColor(new Color(255, 255, 255, 30));
            g2.drawLine(tbX + 10, tbY + tbH - 1, tbX + tbW - 10, tbY + tbH - 1);
            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            g2.setColor(Color.WHITE);
            String title = "全球事件分布热力图 (Goldstein Scale)";
            FontMetrics fm4 = g2.getFontMetrics();
            g2.drawString(title, M + MW/2 - fm4.stringWidth(title)/2, tbY + 18);

            // Info text
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            g2.setColor(new Color(70, 85, 105));
            g2.drawString(infoText, M + 4, M + MH + 20);

            // === 8. Legend ===
            int lx = M + MW - 180, ly = M + 8, lw = 172, lh = 78;
            g2.setColor(new Color(0, 0, 0, 35));
            g2.fillRoundRect(lx+2, ly+2, lw, lh, 10, 10);
            g2.setColor(new Color(255, 255, 255, 215));
            g2.fillRoundRect(lx, ly, lw, lh, 10, 10);
            g2.setColor(new Color(170, 180, 190, 190));
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(lx, ly, lw, lh, 10, 10);
            g2.setColor(new Color(255, 255, 255, 160));
            g2.drawLine(lx+6, ly+1, lx+lw-6, ly+1);

            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
            g2.setColor(new Color(40, 50, 60));
            g2.drawString("图例", lx + 12, ly + 18);

            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
            String[] legends = {"严重冲突 (GS<-5)", "中度紧张 (GS<0)", "友好合作 (GS>0)"};
            Color[] legColors = {new Color(220, 20, 60), new Color(255, 140, 0), new Color(0, 160, 0)};
            for (int i = 0; i < 3; i++) {
                int cy = ly + 25 + i * 16;
                g2.setColor(legColors[i]);
                g2.fillOval(lx + 10, cy, 8, 8);
                g2.setColor(legColors[i].darker());
                g2.drawOval(lx + 10, cy, 8, 8);
                g2.setColor(new Color(50, 60, 70));
                g2.drawString(legends[i], lx + 24, cy + 9);
            }
        }
    }

    // ==================== EventTypeChart ====================
    class EventTypeChart extends JPanel {
        private Map<String, Integer> eventCounts = new LinkedHashMap<>();
        private String subtitle = "";

        public EventTypeChart() {
            setPreferredSize(new Dimension(780, 420));
            setBackground(Color.WHITE);
        }

        public void loadData(String s, String e) {
            eventCounts = QueryService.getEventTypeCounts(s, e);
            subtitle = (!s.isEmpty() || !e.isEmpty() ? s + " ~ " + e : "全部数据");
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int W = getWidth(), H = getHeight();
            int cx = W/2, cy = H/2;
            int R = Math.min(W, H)/2 - 60;

            if (eventCounts.isEmpty()) {
                g2.setColor(Color.GRAY);
                g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
                g2.drawString("点击 [刷新] 加载事件类型数据", W/2-110, H/2);
                return;
            }

            int total = 0;
            for (int v : eventCounts.values()) total += v;

            Color[] pieColors = {
                new Color(65, 105, 225), new Color(220, 20, 60),
                new Color(50, 205, 50), new Color(255, 140, 0),
                new Color(138, 43, 226), new Color(0, 139, 139),
                new Color(255, 215, 0), new Color(139, 69, 19)
            };

            int startAngle = 0, ci = 0;
            for (Map.Entry<String, Integer> entry : eventCounts.entrySet()) {
                int arcAngle = (int)(360.0 * entry.getValue() / total);
                g2.setColor(pieColors[ci % pieColors.length]);
                g2.fillArc(cx - R, cy - R, 2*R, 2*R, startAngle, arcAngle);
                g2.setColor(Color.DARK_GRAY);
                g2.drawArc(cx - R, cy - R, 2*R, 2*R, startAngle, arcAngle);
                startAngle += arcAngle;
                ci++;
            }

            // Legend
            int lx = 20, ly = 30;
            g2.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            ci = 0;
            for (Map.Entry<String, Integer> entry : eventCounts.entrySet()) {
                g2.setColor(pieColors[ci % pieColors.length]);
                g2.fillRect(lx, ly + ci*20, 12, 12);
                g2.setColor(Color.DARK_GRAY);
                g2.drawRect(lx, ly + ci*20, 12, 12);
                double pct = 100.0 * entry.getValue() / total;
                String label = entry.getKey() + " (" + entry.getValue() + ", " + String.format("%.1f", pct) + "%)";
                g2.drawString(label, lx + 18, ly + ci*20 + 11);
                ci++;
            }

            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            g2.setColor(Color.BLACK);
            g2.drawString("事件类型分布: " + subtitle, 20, H - 20);
        }
    }
}
