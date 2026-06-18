package com.gdelt.service;

import com.gdelt.db.DatabaseHelper;
import java.sql.*;
import java.util.*;

/**
 * 数据分析服务 - K-Means聚类 / 双边趋势 / 双边冲突概率预测
 * v3.1: 冲突预测改为两国双边模式，四项指标加权综合
 */
public class AnalysisService {

    /** K-Means国家聚类 (K=3) */
    public static String kMeansClustering() {
        List<String> countries = new ArrayList<>();
        List<double[]> points = new ArrayList<>();
        String sql = "SELECT actor1_country_code, AVG(goldstein_scale), COUNT(*) " +
                     "FROM events WHERE actor1_country_code != '' " +
                     "GROUP BY actor1_country_code HAVING COUNT(*) >= 100";
        try (Statement stmt = DatabaseHelper.getInstance().getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                countries.add(rs.getString(1));
                points.add(new double[]{rs.getDouble(2), Math.log(rs.getInt(3))});
            }
        } catch (SQLException e) {
            return "聚类失败: " + e.getMessage();
        }
        if (points.size() < 3) return "数据不足(需≥3个国家, 当前" + points.size() + "个)";

        // Min-Max归一化
        double[] mins = {Double.MAX_VALUE, Double.MAX_VALUE};
        double[] maxs = {Double.MIN_VALUE, Double.MIN_VALUE};
        for (double[] p : points) {
            for (int i = 0; i < 2; i++) {
                mins[i] = Math.min(mins[i], p[i]);
                maxs[i] = Math.max(maxs[i], p[i]);
            }
        }
        for (double[] p : points) {
            for (int i = 0; i < 2; i++) {
                p[i] = (maxs[i] - mins[i] > 0) ? (p[i] - mins[i]) / (maxs[i] - mins[i]) : 0;
            }
        }

        // K-Means
        int K = 3, N = points.size();
        double[][] centers = new double[K][2];
        Random rand = new Random(42);
        for (int i = 0; i < K; i++) centers[i] = points.get(rand.nextInt(N)).clone();

        int[] labels = new int[N];
        for (int iter = 0; iter < 30; iter++) {
            boolean changed = false;
            for (int i = 0; i < N; i++) {
                int best = 0;
                double bestDist = dist(points.get(i), centers[0]);
                for (int j = 1; j < K; j++) {
                    double d = dist(points.get(i), centers[j]);
                    if (d < bestDist) { bestDist = d; best = j; }
                }
                if (labels[i] != best) { labels[i] = best; changed = true; }
            }
            if (!changed) break;
            double[][] sums = new double[K][2];
            int[] cnts = new int[K];
            for (int i = 0; i < N; i++) {
                sums[labels[i]][0] += points.get(i)[0];
                sums[labels[i]][1] += points.get(i)[1];
                cnts[labels[i]]++;
            }
            for (int j = 0; j < K; j++) {
                if (cnts[j] > 0) { centers[j][0] = sums[j][0]/cnts[j]; centers[j][1] = sums[j][1]/cnts[j]; }
            }
        }

        // 报告
        StringBuilder sb = new StringBuilder("========== K-Means国家聚类(K=3) ==========\n\n");
        for (int j = 0; j < K; j++) {
            sb.append("【聚类").append(j+1).append("】中心→ GS:")
              .append(String.format("%.2f", centers[j][0]))
              .append(" 规模:").append(String.format("%.2f", centers[j][1])).append("\n  成员: ");
            int cnt = 0;
            for (int i = 0; i < N; i++) {
                if (labels[i] == j) { sb.append(countries.get(i)).append(" "); cnt++; }
            }
            sb.append("\n  共").append(cnt).append("国\n\n");
        }
        return sb.toString();
    }

    /** 双边关系月度趋势 */
    public static Map<String, List<Double>> trend(String c1, String c2) {
        Map<String, List<Double>> result = new LinkedHashMap<>();
        List<Double> values = new ArrayList<>();
        String sql = "SELECT substr(day,1,7), AVG(goldstein_scale) FROM events " +
                     "WHERE actor1_country_code=? AND actor2_country_code=? " +
                     "GROUP BY 1 ORDER BY 1";
        try (PreparedStatement ps = DatabaseHelper.getInstance()
                .getConnection().prepareStatement(sql)) {
            ps.setString(1, c1); ps.setString(2, c2);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) values.add(rs.getDouble(2));
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] trend: " + e.getMessage());
        }
        result.put("values", values);
        return result;
    }

    /**
     * 双边冲突概率预测 (v3.1 核心算法)
     * 
     * @param countryA     国家A代码 (如 USA)
     * @param countryB     国家B代码 (如 CHN)
     * @param lookbackDays 回溯分析天数
     * @param predictDays  未来预测天数
     * @return 格式化预测报告
     */
    public static String bilateralConflictPredict(String countryA, String countryB,
                                                   int lookbackDays, int predictDays) {
        // ===== Step 1: 查询两国双向交互数据 =====
        List<DailyBilateral> dailyData = new ArrayList<>();
        String sql = "SELECT day, AVG(goldstein_scale), AVG(avg_tone), COUNT(*), " +
                     "SUM(CASE WHEN quad_class >= 3 THEN 1 ELSE 0 END) " +
                     "FROM events WHERE day >= date('now', ? || ' days') " +
                     "AND ((actor1_country_code=? AND actor2_country_code=?) " +
                     "OR (actor1_country_code=? AND actor2_country_code=?)) " +
                     "GROUP BY day ORDER BY day";

        String daysParam = "-" + lookbackDays;
        try (PreparedStatement ps = DatabaseHelper.getInstance()
                .getConnection().prepareStatement(sql)) {
            ps.setString(1, daysParam);
            ps.setString(2, countryA); ps.setString(3, countryB);
            ps.setString(4, countryB); ps.setString(5, countryA);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DailyBilateral d = new DailyBilateral();
                    d.date = rs.getString(1);
                    d.avgGoldstein = rs.getDouble(2);
                    d.avgTone = rs.getDouble(3);
                    d.eventCount = rs.getInt(4);
                    d.conflictCount = rs.getInt(5);
                    dailyData.add(d);
                }
            }
        } catch (SQLException e) {
            return "❌ 查询失败: " + e.getMessage() + "\n请确认已导入数据且国家代码正确";
        }

        if (dailyData.size() < 7) {
            return "⚠ 数据不足: " + countryA + " ↔ " + countryB +
                   " 仅有 " + dailyData.size() + " 天交互记录\n" +
                   "需要至少 7 天数据才能进行可靠预测\n\n" +
                   "建议:\n  1. 检查国家代码是否正确 (如 USA/CHN/RUS)\n" +
                   "  2. 增大回溯天数\n  3. 确认已导入该时间段数据";
        }

        int n = dailyData.size();
        int halfIdx = n / 2;

        // ===== Step 2: 计算四项预测指标 =====

        // 指标① Goldstein趋势 (线性回归斜率) — 权重 35%
        double goldsteinSlope = linearRegressionSlope(dailyData, "goldstein");
        double goldsteinRecent = avgGoldsteinRecent(dailyData, 7);

        // 指标② 冲突事件占比 — 权重 30%
        int totalEvents = dailyData.stream().mapToInt(d -> d.eventCount).sum();
        int totalConflicts = dailyData.stream().mapToInt(d -> d.conflictCount).sum();
        double conflictRatio = totalEvents > 0 ? (double) totalConflicts / totalEvents : 0;

        // 指标③ AvgTone趋势 (线性回归斜率) — 权重 20%
        double toneSlope = linearRegressionSlope(dailyData, "tone");
        double toneRecent = avgToneRecent(dailyData, 7);

        // 指标④ 事件频率飙升指数 — 权重 15%
        double recentFreq = 0, olderFreq = 0;
        int recentDays = Math.min(7, n);
        int olderStart = Math.max(0, n - 14);
        int olderEnd = Math.max(0, n - 7);
        for (int i = n - recentDays; i < n; i++) recentFreq += dailyData.get(i).eventCount;
        for (int i = olderStart; i < olderEnd; i++) olderFreq += dailyData.get(i).eventCount;
        recentFreq /= recentDays;
        olderFreq /= (olderEnd - olderStart > 0 ? olderEnd - olderStart : 1);
        double surgeIndex = olderFreq > 0 ? (recentFreq - olderFreq) / olderFreq : 0;

        // ===== Step 3: 加权综合 → 冲突概率 =====

        // Goldstein指标: 斜率越负 + 近期越负 → 概率越高
        double goldsteinScore = clamp01((-goldsteinSlope * 5.0) + ((-goldsteinRecent + 5.0) / 10.0));

        // 冲突占比指标: 占比越高 → 概率越高
        double conflictScore = clamp01(conflictRatio * 2.0);

        // 语调指标: 语调越降 + 越负 → 概率越高
        double toneScore = clamp01((-toneSlope * 2.0) + ((-toneRecent + 5.0) / 13.0));

        // 频率飙升指标
        double surgeScore = clamp01(surgeIndex + 0.5);

        double probability = goldsteinScore * 0.35 + conflictScore * 0.30
                           + toneScore * 0.20 + surgeScore * 0.15;

        // ===== Step 4: 风险分级 =====
        String riskLevel, riskColor;
        if (probability < 0.20)      { riskLevel = "低风险";   riskColor = "🟢"; }
        else if (probability < 0.40) { riskLevel = "关注";     riskColor = "🔵"; }
        else if (probability < 0.60) { riskLevel = "警戒";     riskColor = "🟡"; }
        else if (probability < 0.80) { riskLevel = "高风险";   riskColor = "🟠"; }
        else                         { riskLevel = "极高风险"; riskColor = "🔴"; }

        // ===== Step 5: 未来预测 =====
        StringBuilder futurePred = new StringBuilder();
        double predGoldstein = goldsteinRecent;
        double predTone = toneRecent;
        for (int i = 1; i <= predictDays; i++) {
            predGoldstein += goldsteinSlope;
            predTone += toneSlope;
            futurePred.append(String.format("  D+%d: Goldstein %+.2f | Tone %+.2f\n",
                    i, predGoldstein, predTone));
        }

        // ===== Step 6: 生成报告 =====
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════╗\n");
        sb.append("║  双边冲突概率预测报告                    ║\n");
        sb.append("╠══════════════════════════════════════════╣\n");
        sb.append(String.format("║  %s  ↔  %s\n", countryA, countryB));
        sb.append(String.format("║  回溯: %d天 | 预测: %d天\n", lookbackDays, predictDays));
        sb.append("╚══════════════════════════════════════════╝\n\n");

        sb.append(String.format("━━━ 综合冲突概率: %.1f%%  %s %s ━━━\n\n",
                probability * 100, riskColor, riskLevel));

        sb.append("【四项指标贡献度】\n");
        sb.append(progressBar("Goldstein趋势", goldsteinScore, 0.35));
        sb.append(progressBar("冲突事件占比", conflictScore, 0.30));
        sb.append(progressBar("语调趋势",     toneScore,     0.20));
        sb.append(progressBar("频率飙升指数", surgeScore,    0.15));
        sb.append("\n");

        sb.append("【数据概览】\n");
        sb.append(String.format("  总交互天数 : %d 天\n", n));
        sb.append(String.format("  总事件数   : %d 次\n", totalEvents));
        sb.append(String.format("  日均事件   : %.1f 次/天\n", (double)totalEvents / n));
        sb.append(String.format("  冲突占比   : %.1f%% (%d次)\n", conflictRatio * 100, totalConflicts));
        sb.append(String.format("  近期Goldstein均值 : %+.2f\n", goldsteinRecent));
        sb.append(String.format("  近期AvgTone均值   : %+.2f\n", toneRecent));
        sb.append(String.format("  Goldstein趋势斜率 : %+.4f/天\n", goldsteinSlope));
        sb.append(String.format("  AvgTone趋势斜率   : %+.4f/天\n", toneSlope));
        sb.append("\n");

        sb.append(String.format("【未来%d天预测】\n", predictDays));
        sb.append(futurePred);
        sb.append("\n");

        // 趋势判断
        if (goldsteinSlope < -0.1) {
            sb.append("⚠ Goldstein持续下降，双方关系正在恶化！\n");
        } else if (goldsteinSlope > 0.1) {
            sb.append("✓ Goldstein持续上升，双方关系正在改善。\n");
        }
        if (conflictRatio > 0.5) {
            sb.append("⚠ 冲突事件占比超过50%，对抗已成主旋律。\n");
        }
        if (surgeIndex > 0.5) {
            sb.append("⚠ 近期事件频率飙升，局势可能正在升级。\n");
        }
        if (probability >= 0.6) {
            sb.append("\n🔴 建议密切关注该双边关系动态！\n");
        }

        return sb.toString();
    }

    // ==================== 辅助方法 ====================

    /** 线性回归斜率 */
    private static double linearRegressionSlope(List<DailyBilateral> data, String field) {
        int n = data.size();
        if (n < 2) return 0;
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = field.equals("goldstein") ? data.get(i).avgGoldstein : data.get(i).avgTone;
            sumX += x; sumY += y; sumXY += x * y; sumX2 += x * x;
        }
        double denom = n * sumX2 - sumX * sumX;
        return denom != 0 ? (n * sumXY - sumX * sumY) / denom : 0;
    }

    /** 最近N天Goldstein均值 */
    private static double avgGoldsteinRecent(List<DailyBilateral> data, int days) {
        int n = data.size();
        int start = Math.max(0, n - days);
        double sum = 0;
        int count = 0;
        for (int i = start; i < n; i++) {
            sum += data.get(i).avgGoldstein;
            count++;
        }
        return count > 0 ? sum / count : 0;
    }

    /** 最近N天Tone均值 */
    private static double avgToneRecent(List<DailyBilateral> data, int days) {
        int n = data.size();
        int start = Math.max(0, n - days);
        double sum = 0;
        int count = 0;
        for (int i = start; i < n; i++) {
            sum += data.get(i).avgTone;
            count++;
        }
        return count > 0 ? sum / count : 0;
    }

    /** 限制到 [0, 1] 范围 */
    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    /** 进度条可视化 */
    private static String progressBar(String label, double score, double weight) {
        int barLen = 20;
        int filled = (int) (score * barLen);
        double contribution = score * weight * 100;
        StringBuilder bar = new StringBuilder();
        bar.append(String.format("  %-14s [", label));
        for (int i = 0; i < barLen; i++) {
            bar.append(i < filled ? "█" : "░");
        }
        bar.append(String.format("] %.1f%%  (贡献: %.1f%%)\n", score * 100, contribution));
        return bar.toString();
    }

    private static double dist(double[] a, double[] b) {
        return Math.pow(a[0]-b[0], 2) + Math.pow(a[1]-b[1], 2);
    }

    // ==================== 内部数据类 ====================

    /** 每日双边交互数据 */
    private static class DailyBilateral {
        String date;
        double avgGoldstein;
        double avgTone;
        int eventCount;
        int conflictCount;
    }
}
