package com.gdelt.db;

import com.gdelt.model.GdeltEvent;
import com.gdelt.util.AppConfig;
import java.sql.*;
import java.util.*;

/**
 * SQLite 数据库单例 — 线程安全
 * v3.0: 仅事件表，移除ZIP BLOB存储
 */
public class DatabaseHelper {
    private static volatile DatabaseHelper instance;
    private final Object lock = new Object();
    private Connection conn;

    static {
        try { new org.sqlite.JDBC();
            System.out.println("[OK] SQLite driver loaded");
        } catch (Throwable e) {
            try { Class.forName("org.sqlite.JDBC");
                System.out.println("[OK] SQLite driver via Class.forName");
            } catch (Throwable e2) {
                try { DriverManager.registerDriver(new org.sqlite.JDBC());
                    System.out.println("[OK] SQLite driver via DriverManager");
                } catch (Throwable e3) {
                    throw new ExceptionInInitializerError("Cannot load SQLite JDBC driver: " + e3);
                }
            }
        }
    }

    private DatabaseHelper() throws SQLException {
        java.io.File dbFile = new java.io.File(AppConfig.DB_PATH);
        java.io.File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        conn = DriverManager.getConnection(AppConfig.DB_URL);
        conn.setAutoCommit(true);
        createTablesIfNotExists();
        System.out.println("[OK] Database connected: " + AppConfig.DB_PATH);
    }

    public static DatabaseHelper getInstance() throws SQLException {
        if (instance == null) {
            synchronized (DatabaseHelper.class) {
                if (instance == null) { instance = new DatabaseHelper(); }
            }
        }
        if (instance.conn.isClosed()) {
            synchronized (DatabaseHelper.class) {
                if (instance.conn.isClosed()) { instance = new DatabaseHelper(); }
            }
        }
        return instance;
    }

    private void createTablesIfNotExists() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS events ("
                + "global_event_id INTEGER PRIMARY KEY, day TEXT NOT NULL, year INTEGER, "
                + "actor1_code TEXT, actor1_country_code TEXT, actor2_code TEXT, actor2_country_code TEXT, "
                + "event_code TEXT, event_base_code TEXT, quad_class INTEGER, "
                + "goldstein_scale REAL, num_mentions INTEGER, avg_tone REAL)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_day    ON events(day)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_a1cc   ON events(actor1_country_code)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_a2cc   ON events(actor2_country_code)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_qc     ON events(quad_class)");
        }
    }

    public int insertBatch(List<GdeltEvent> events) throws SQLException {
        if (events == null || events.isEmpty()) return 0;
        String sql = "INSERT OR IGNORE INTO events VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        synchronized (lock) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                int count = 0, total = 0;
                for (GdeltEvent e : events) {
                    ps.setLong(1, e.getGlobalEventId());
                    ps.setString(2, e.getDay() != null ? e.getDay().toString() : "");
                    ps.setInt(3, e.getYear());
                    ps.setString(4, e.getActor1Code());  ps.setString(5, e.getActor1CountryCode());
                    ps.setString(6, e.getActor2Code());  ps.setString(7, e.getActor2CountryCode());
                    ps.setString(8, e.getEventCode());   ps.setString(9, e.getEventBaseCode());
                    ps.setInt(10, e.getQuadClass());     ps.setDouble(11, e.getGoldsteinScale());
                    ps.setInt(12, e.getNumMentions());   ps.setDouble(13, e.getAvgTone());
                    ps.addBatch();
                    if (++count >= 5000) { ps.executeBatch(); conn.commit(); total += count; count = 0; }
                }
                if (count > 0) { ps.executeBatch(); conn.commit(); total += count; }
                return total;
            } finally { conn.setAutoCommit(true); }
        }
    }

    public List<GdeltEvent> query(String sql, Object... params) throws SQLException {
        List<GdeltEvent> list = new ArrayList<>();
        synchronized (lock) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(mapEventRow(rs));
                }
            }
        }
        return list;
    }

    public int countAll() {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM events")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) { return 0; }
    }

    private GdeltEvent mapEventRow(ResultSet rs) throws SQLException {
        GdeltEvent e = new GdeltEvent();
        e.setGlobalEventId(rs.getLong("global_event_id"));
        String dayStr = rs.getString("day");
        if (dayStr != null && !dayStr.isEmpty()) {
            try { e.setDay(java.time.LocalDate.parse(dayStr)); } catch (Exception ignored) {}
        }
        e.setActor1Code(rs.getString("actor1_code"));
        e.setActor1CountryCode(rs.getString("actor1_country_code"));
        e.setActor2Code(rs.getString("actor2_code"));
        e.setActor2CountryCode(rs.getString("actor2_country_code"));
        e.setEventCode(rs.getString("event_code"));
        e.setEventBaseCode(rs.getString("event_base_code"));
        e.setQuadClass(rs.getInt("quad_class"));
        e.setGoldsteinScale(rs.getDouble("goldstein_scale"));
        e.setNumMentions(rs.getInt("num_mentions"));
        e.setAvgTone(rs.getDouble("avg_tone"));
        return e;
    }

    public Connection getConnection() { return conn; }

    public void close() {
        try { if (conn != null && !conn.isClosed()) conn.close(); } catch (Exception ignored) {}
    }
}
