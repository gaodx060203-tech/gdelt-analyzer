package com.gdelt.service;

import com.gdelt.db.DatabaseHelper;
import com.gdelt.model.GdeltEvent;
import java.sql.*;
import java.util.*;

/**
 * Multi-dimensional Query Service — SUPPORTS ALL-TIME QUERIES
 * Empty startDate/endDate = no date filter = query entire database
 */
public class QueryService {

    /** Count events matching criteria */
    public static int count(String startDate, String endDate, String actor1, String actor2) {
        StringBuilder where = new StringBuilder("1=1");
        List<Object> params = new ArrayList<>();
        buildDateWhere(where, params, startDate, endDate);
        buildActorWhere(where, params, actor1, actor2);
        String sql = "SELECT COUNT(*) FROM events WHERE " + where;
        try (PreparedStatement ps = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i+1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        } catch (SQLException e) {
            System.err.println("[ERROR] count: " + e.getMessage());
            return 0;
        }
    }

    /** Search events with optional dates + pagination */
    public static List<GdeltEvent> search(String startDate, String endDate,
                                           String actor1, String actor2,
                                           int offset, int limit) {
        StringBuilder where = new StringBuilder("1=1");
        List<Object> params = new ArrayList<>();
        buildDateWhere(where, params, startDate, endDate);
        buildActorWhere(where, params, actor1, actor2);
        String sql = "SELECT * FROM events WHERE " + where + " ORDER BY day DESC LIMIT ? OFFSET ?";
        params.add(limit);
        params.add(offset);
        try {
            return DatabaseHelper.getInstance().query(sql.toString(), params.toArray());
        } catch (SQLException e) {
            System.err.println("[ERROR] search: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /** Convenience: search without pagination (max 2000) */
    public static List<GdeltEvent> search(String startDate, String endDate,
                                           String actor1, String actor2) {
        return search(startDate, endDate, actor1, actor2, 0, 2000);
    }

    /** Hot country pairs TOP N */
    public static List<String[]> getHotPairs(String startDate, String endDate, int limit) {
        List<String[]> pairs = new ArrayList<>();
        StringBuilder where = new StringBuilder("actor1_country_code != '' AND actor2_country_code != ''");
        List<Object> params = new ArrayList<>();
        buildDateWhere(where, params, startDate, endDate);
        String sql = "SELECT actor1_country_code, actor2_country_code, COUNT(*) AS cnt, " +
                     "ROUND(AVG(goldstein_scale), 2) AS avg_gs FROM events WHERE " + where +
                     " GROUP BY 1, 2 ORDER BY cnt DESC LIMIT ?";
        params.add(limit);
        try (PreparedStatement ps = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i+1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    pairs.add(new String[]{
                        rs.getString(1), rs.getString(2),
                        String.format("%,d", rs.getInt(3)),
                        String.format("%.2f", rs.getDouble(4))
                    });
                }
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] getHotPairs: " + e.getMessage());
        }
        return pairs;
    }

    /** Convenience: top 10 */
    public static List<String[]> getHotPairs(String startDate, String endDate) {
        return getHotPairs(startDate, endDate, 10);
    }

    /** Event quad-class distribution */
    public static Map<String, Integer> getEventDistribution(String startDate, String endDate) {
        Map<String, Integer> dist = new LinkedHashMap<>();
        StringBuilder where = new StringBuilder("1=1");
        List<Object> params = new ArrayList<>();
        buildDateWhere(where, params, startDate, endDate);
        String sql = "SELECT quad_class, COUNT(*) FROM events WHERE " + where +
                     " GROUP BY quad_class ORDER BY quad_class";
        try (PreparedStatement ps = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i+1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int qc = rs.getInt(1);
                    String label;
                    switch (qc) {
                        case 1: label = "口头合作"; break;
                        case 2: label = "实质合作"; break;
                        case 3: label = "口头冲突"; break;
                        case 4: label = "实质冲突"; break;
                        default: label = "其他(" + qc + ")";
                    }
                    dist.put(label, rs.getInt(2));
                }
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] getEventDistribution: " + e.getMessage());
        }
        return dist;
    }

    /** Get daily event count time series */
    public static Map<String, Integer> getDailyTimeSeries(String startDate, String endDate) {
        Map<String, Integer> series = new LinkedHashMap<>();
        StringBuilder where = new StringBuilder("1=1");
        List<Object> params = new ArrayList<>();
        buildDateWhere(where, params, startDate, endDate);
        String sql = "SELECT day, COUNT(*) FROM events WHERE " + where +
                     " GROUP BY day ORDER BY day";
        try (PreparedStatement ps = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i+1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) series.put(rs.getString(1), rs.getInt(2));
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] getDailyTimeSeries: " + e.getMessage());
        }
        return series;
    }

    /** Get geo distribution points */
    public static List<double[]> getGeoPoints(String startDate, String endDate) {
        List<double[]> points = new ArrayList<>();
        StringBuilder where = new StringBuilder("actor1_country_code != ''");
        List<Object> params = new ArrayList<>();
        buildDateWhere(where, params, startDate, endDate);
        String sql = "SELECT actor1_country_code, AVG(goldstein_scale), COUNT(*) FROM events WHERE " +
                     where + " GROUP BY actor1_country_code HAVING COUNT(*) >= 5";
        try (PreparedStatement ps = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i+1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, double[]> coords = COUNTRY_COORDS;
                while (rs.next()) {
                    String cc = rs.getString(1);
                    double[] coord = coords.get(cc);
                    if (coord != null) {
                        points.add(new double[]{coord[1], coord[0],
                            rs.getDouble(2), rs.getInt(3)});
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] getGeoPoints: " + e.getMessage());
        }
        return points;
    }

        /** Get daily trend data (event counts per day as Double) */
    public static Map<String, Double> getTrend(String startDate, String endDate) {
        Map<String, Double> trend = new LinkedHashMap<>();
        StringBuilder where = new StringBuilder("1=1");
        List<Object> params = new ArrayList<>();
        buildDateWhere(where, params, startDate, endDate);
        String sql = "SELECT day, COUNT(*) FROM events WHERE " + where +
                     " GROUP BY day ORDER BY day";
        try (PreparedStatement ps = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i+1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) trend.put(rs.getString(1), (double) rs.getInt(2));
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] getTrend: " + e.getMessage());
        }
        return trend;
    }

    /** Get event type distribution (quad class -> count as Double) */
    public static Map<String, Double> getTypeDistribution(String startDate, String endDate) {
        Map<String, Double> dist = new LinkedHashMap<>();
        StringBuilder where = new StringBuilder("1=1");
        List<Object> params = new ArrayList<>();
        buildDateWhere(where, params, startDate, endDate);
        String sql = "SELECT quad_class, COUNT(*) FROM events WHERE " + where +
                     " GROUP BY quad_class ORDER BY quad_class";
        try (PreparedStatement ps = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i+1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int qc = rs.getInt(1);
                    String label;
                    switch (qc) {
                        case 1: label = "口头合作"; break;
                        case 2: label = "实质合作"; break;
                        case 3: label = "口头冲突"; break;
                        case 4: label = "实质冲突"; break;
                        default: label = "其他(" + qc + ")";
                    }
                    dist.put(label, (double) rs.getInt(2));
                }
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] getTypeDistribution: " + e.getMessage());
        }
        return dist;
    }

    /** Get event type counts for pie chart (quad class -> count) */
    public static Map<String, Integer> getEventTypeCounts(String startDate, String endDate) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        StringBuilder where = new StringBuilder("1=1");
        List<Object> params = new ArrayList<>();
        buildDateWhere(where, params, startDate, endDate);
        String sql = "SELECT quad_class, COUNT(*) FROM events WHERE " + where +
                     " GROUP BY quad_class ORDER BY quad_class";
        try (PreparedStatement ps = DatabaseHelper.getInstance().getConnection().prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) ps.setObject(i+1, params.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int qc = rs.getInt(1);
                    String label;
                    switch (qc) {
                        case 1: label = "口头合作"; break;
                        case 2: label = "实质合作"; break;
                        case 3: label = "口头冲突"; break;
                        case 4: label = "实质冲突"; break;
                        default: label = "其他(" + qc + ")";
                    }
                    counts.put(label, rs.getInt(2));
                }
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] getEventTypeCounts: " + e.getMessage());
        }
        return counts;
    }

// ===== Helper methods =====

    private static void buildDateWhere(StringBuilder where, List<Object> params,
                                        String start, String end) {
        if (start != null && !start.isEmpty() && end != null && !end.isEmpty()) {
            where.append(" AND day BETWEEN ? AND ?");
            params.add(start);
            params.add(end);
        } else if (start != null && !start.isEmpty()) {
            where.append(" AND day >= ?");
            params.add(start);
        } else if (end != null && !end.isEmpty()) {
            where.append(" AND day <= ?");
            params.add(end);
        }
        // if both empty: no date filter (all time)
    }

    private static void buildActorWhere(StringBuilder where, List<Object> params,
                                         String actor1, String actor2) {
        if (actor1 != null && !actor1.isEmpty()) {
            where.append(" AND actor1_country_code = ?");
            params.add(actor1.toUpperCase());
        }
        if (actor2 != null && !actor2.isEmpty()) {
            where.append(" AND actor2_country_code = ?");
            params.add(actor2.toUpperCase());
        }
    }

    /** Get date range of all stored events */
    public static String[] getGlobalDateRange() {
        try (Statement stmt = DatabaseHelper.getInstance().getConnection().createStatement();
             ResultSet rs = stmt.executeQuery("SELECT MIN(day), MAX(day), COUNT(*) FROM events")) {
            if (rs.next()) {
                return new String[]{rs.getString(1), rs.getString(2), String.valueOf(rs.getInt(3))};
            }
        } catch (SQLException e) { /* ignore */ }
        return new String[]{"N/A", "N/A", "0"};
    }

    // ===== 50+ country coordinate map =====
    private static final Map<String, double[]> COUNTRY_COORDS = new HashMap<>();
    static {
        double[][] data = {
            {37.1,-95.7},{35.9,104.2},{61.5,105.3},{55.4,-3.4},{46.2,2.2},
            {51.2,10.5},{36.2,138.3},{20.6,78.9},{-14.2,-51.9},{56.1,-106.3},
            {-25.3,133.8},{35.9,128.0},{32.4,53.7},{39.0,35.2},{31.0,34.9},
            {48.4,31.2},{-29.0,24.9},{23.6,-102.5},{23.9,45.1},{41.9,12.6},
            {40.5,-3.7},{52.1,19.4},{52.5,13.4},{60.1,18.6},{64.0,26.0},
            {55.8,9.5},{52.1,5.3},{50.8,4.5},{47.5,7.6},{47.0,19.5},
            {44.4,26.1},{42.7,23.3},{37.9,23.7},{58.6,25.0},{56.9,24.6},
            {54.7,25.3},{30.0,31.2},{33.9,36.0},{33.3,44.4},{15.4,44.2},
            {25.0,55.0},{-1.3,36.8},{9.0,8.7},{12.9,105.0},{14.1,101.0},
            {23.7,121.0},{1.3,103.8},{3.1,101.7},{-6.2,106.8},{35.7,51.4}
        };
        String[] codes = {
            "USA","CHN","RUS","GBR","FRA","DEU","JPN","IND","BRA","CAN",
            "AUS","KOR","IRN","TUR","ISR","UKR","ZAF","MEX","SAU","ITA",
            "ESP","POL","SWE","FIN","DNK","NLD","BEL","CHE","HUN","ROU",
            "BGR","GRC","EST","LVA","LTU","EGY","SYR","IRQ","YEM","ARE",
            "KEN","NGA","KHM","THA","TWN","SGP","MYS","IDN","IRN2"
        };
        for (int i = 0; i < Math.min(data.length, codes.length); i++) {
            COUNTRY_COORDS.put(codes[i], data[i]);
        }
    }
}
