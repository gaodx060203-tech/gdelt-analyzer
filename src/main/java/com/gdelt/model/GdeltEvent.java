package com.gdelt.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * GDELT 2.0 Event 数据模型 (58字段中的15个核心字段)
 * 所有解析方法都带防御性默认值
 */
public class GdeltEvent {
    // 核心标识
    private long globalEventId;
    private LocalDate day;
    private int year;
    private int monthYear;

    // Actor1
    private String actor1Code;
    private String actor1Name;
    private String actor1CountryCode;

    // Actor2
    private String actor2Code;
    private String actor2Name;
    private String actor2CountryCode;

    // 事件分类
    private String eventCode;
    private String eventBaseCode;
    private int quadClass;          // 1=口头合作 2=实质合作 3=口头冲突 4=实质冲突

    // 量化指标
    private double goldsteinScale;  // -10(最冲突) ~ +10(最合作)
    private int numMentions;
    private int numSources;
    private int numArticles;
    private double avgTone;         // -100(最负面) ~ +100(最正面)

    // 地理
    private String actionGeoCountryCode;
    private double actionGeoLat;
    private double actionGeoLon;

    public GdeltEvent() {}

    // ===== Getters =====
    public long getGlobalEventId() { return globalEventId; }
    public LocalDate getDay() { return day; }
    public int getYear() { return year; }
    public int getMonthYear() { return monthYear; }
    public String getActor1Code() { return actor1Code; }
    public String getActor1Name() { return actor1Name; }
    public String getActor1CountryCode() { return actor1CountryCode; }
    public String getActor2Code() { return actor2Code; }
    public String getActor2Name() { return actor2Name; }
    public String getActor2CountryCode() { return actor2CountryCode; }
    public String getEventCode() { return eventCode; }
    public String getEventBaseCode() { return eventBaseCode; }
    public int getQuadClass() { return quadClass; }
    public double getGoldsteinScale() { return goldsteinScale; }
    public int getNumMentions() { return numMentions; }
    public int getNumSources() { return numSources; }
    public int getNumArticles() { return numArticles; }
    public double getAvgTone() { return avgTone; }
    public String getActionGeoCountryCode() { return actionGeoCountryCode; }
    public double getActionGeoLat() { return actionGeoLat; }
    public double getActionGeoLon() { return actionGeoLon; }

    // ===== Setters =====
    public void setGlobalEventId(long v) { this.globalEventId = v; }
    public void setDay(LocalDate v) { this.day = v; }
    public void setYear(int v) { this.year = v; }
    public void setActor1Code(String v) { this.actor1Code = v; }
    public void setActor1CountryCode(String v) { this.actor1CountryCode = v; }
    public void setActor2Code(String v) { this.actor2Code = v; }
    public void setActor2CountryCode(String v) { this.actor2CountryCode = v; }
    public void setEventCode(String v) { this.eventCode = v; }
    public void setEventBaseCode(String v) { this.eventBaseCode = v; }
    public void setQuadClass(int v) { this.quadClass = v; }
    public void setGoldsteinScale(double v) { this.goldsteinScale = v; }
    public void setNumMentions(int v) { this.numMentions = v; }
    public void setAvgTone(double v) { this.avgTone = v; }

    /** 获取QuadClass中文名 */
    public String getQuadClassName() {
        switch (quadClass) {
            case 1: return "口头合作";
            case 2: return "实质合作";
            case 3: return "口头冲突";
            case 4: return "实质冲突";
            default: return "未知(" + quadClass + ")";
        }
    }

    // ===== TSV解析 (GDELT 2.0格式, \t分隔, 58列) =====
    // 关键列索引:
    // 0=GlobalEventID, 1=Day(yyyyMMdd), 2=MonthYear, 3=Year
    // 5=Actor1Code, 6=Actor1Name, 7=Actor1CountryCode
    // 15=Actor2Code, 16=Actor2Name, 17=Actor2CountryCode
    // 26=EventCode, 27=EventBaseCode, 28=EventRootCode, 29=QuadClass
    // 30=GoldsteinScale, 31=NumMentions, 32=NumSources, 33=NumArticles, 34=AvgTone
    // 53=ActionGeo_CountryCode, 56=ActionGeo_Lat, 57=ActionGeo_Long

    public static GdeltEvent fromTSVLine(String[] fields) {
        GdeltEvent e = new GdeltEvent();
        if (fields == null || fields.length < 35) return e;

        try {
            e.globalEventId = parseLong(fields[0]);

            if (fields[1].length() >= 8) {
                e.day = LocalDate.parse(fields[1].substring(0, 8),
                    DateTimeFormatter.ofPattern("yyyyMMdd"));
            }
            e.monthYear = parseInt(fields[2]);
            e.year = parseInt(fields[3]);

            e.actor1Code = safeStr(fields[5]);
            e.actor1Name = safeStr(fields[6]);
            e.actor1CountryCode = safeStr(fields[7]);

            e.actor2Code = safeStr(fields[15]);
            e.actor2Name = safeStr(fields[16]);
            e.actor2CountryCode = safeStr(fields[17]);

            e.eventCode = safeStr(fields[26]);
            e.eventBaseCode = safeStr(fields[27]);
            e.quadClass = parseInt(fields[29]);
            e.goldsteinScale = parseDouble(fields[30]);
            e.numMentions = parseInt(fields[31]);
            e.numSources = parseInt(fields[32]);
            e.numArticles = parseInt(fields[33]);
            e.avgTone = parseDouble(fields[34]);

            e.actionGeoCountryCode = safeStr(fields[53]);
            e.actionGeoLat = parseDouble(fields[56]);
            e.actionGeoLon = parseDouble(fields[57]);
        } catch (Exception ex) {
            // 部分字段解析失败不影响已解析的字段
        }
        return e;
    }

    // ===== 安全解析工具 =====
    private static String safeStr(String s) {
        return (s == null || s.isEmpty()) ? "" : s;
    }
    private static int parseInt(String s) {
        if (s == null || s.isEmpty()) return 0;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }
    private static long parseLong(String s) {
        if (s == null || s.isEmpty()) return 0;
        try { return Long.parseLong(s); } catch (NumberFormatException e) { return 0; }
    }
    private static double parseDouble(String s) {
        if (s == null || s.isEmpty()) return 0.0;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return 0.0; }
    }
}
