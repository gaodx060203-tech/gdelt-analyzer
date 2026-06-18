package com.gdelt.service;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class GdeltCrawler {

    private static final String MASTER_URL =
        "http://data.gdeltproject.org/gdeltv2/masterfilelist.txt";
    private static final String USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private static final Pattern URL_PATTERN =
        Pattern.compile("/(\\d{4})(\\d{2})(\\d{2})(\\d{2})\\d{4}\\.export\\.CSV\\.zip");

    public static String fetchMasterList() throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(MASTER_URL).openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);
        if (conn.getResponseCode() != 200) {
            conn.disconnect();
            throw new IOException("HTTP " + conn.getResponseCode());
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
        }
        conn.disconnect();
        return sb.toString();
    }

    public static Map<Integer, List<String>> groupByYear(String masterContent) {
        Map<Integer, List<String>> result = new TreeMap<>();
        for (String line : masterContent.split("\\r?\\n")) {
            if (line.trim().isEmpty()) continue;
            String[] parts = line.trim().split("\\s+");
            String url = parts[parts.length - 1];
            Matcher m = URL_PATTERN.matcher(url);
            if (!m.find()) continue;
            int year = Integer.parseInt(m.group(1));
            result.computeIfAbsent(year, k -> new ArrayList<>()).add(url);
        }
        return result;
    }

    public static Map<Integer, Map<Integer, List<String>>> groupByYearMonth(String masterContent) {
        Map<Integer, Map<Integer, List<String>>> result = new TreeMap<>();
        for (String line : masterContent.split("\\r?\\n")) {
            if (line.trim().isEmpty()) continue;
            String[] parts = line.trim().split("\\s+");
            String url = parts[parts.length - 1];
            Matcher m = URL_PATTERN.matcher(url);
            if (!m.find()) continue;
            int year = Integer.parseInt(m.group(1));
            int month = Integer.parseInt(m.group(2));
            result.computeIfAbsent(year, k -> new TreeMap<>())
                  .computeIfAbsent(month, k -> new ArrayList<>())
                  .add(url);
        }
        return result;
    }

    public static int[] parseDateFromUrl(String url) {
        Matcher m = URL_PATTERN.matcher(url);
        if (m.find()) {
            return new int[] {
                Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3)),
                Integer.parseInt(m.group(4))
            };
        }
        return null;
    }

    public static List<String> filterByMonth(
            Map<Integer, Map<Integer, List<String>>> yearMonthData,
            Set<Integer> selectedYears,
            Set<Integer> selectedMonths) {
        List<String> result = new ArrayList<>();
        if (selectedMonths.isEmpty()) return result;
        for (Integer year : selectedYears) {
            Map<Integer, List<String>> monthMap = yearMonthData.get(year);
            if (monthMap == null) continue;
            for (Integer month : selectedMonths) {
                List<String> urls = monthMap.get(month);
                if (urls != null) result.addAll(urls);
            }
        }
        return result;
    }

    public static List<String> filterByTime(List<String> urls, String period) {
        if (period == null || "all".equals(period)) return new ArrayList<>(urls);
        List<String> result = new ArrayList<>();
        for (String url : urls) {
            int[] dt = parseDateFromUrl(url);
            if (dt == null) { result.add(url); continue; }
            int hour = dt[3];
            boolean match = switch (period) {
                case "morning"  -> hour >= 6 && hour <= 8;
                case "forenoon" -> hour >= 9 && hour <= 11;
                case "afternoon"-> hour >= 12 && hour <= 17;
                case "evening"  -> hour >= 18 && hour <= 20;
                case "night"    -> hour >= 21 || hour <= 5;
                default -> true;
            };
            if (match) result.add(url);
        }
        return result;
    }

    public static Path downloadFile(String url, Path saveDir) throws IOException {
        Files.createDirectories(saveDir);
        String fileName = url.substring(url.lastIndexOf("/") + 1);
        Path outPath = saveDir.resolve(fileName);
        if (Files.exists(outPath) && Files.size(outPath) > 0) return outPath;
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);
        if (conn.getResponseCode() != 200) {
            conn.disconnect();
            throw new IOException("HTTP " + conn.getResponseCode());
        }
        try (InputStream is = conn.getInputStream()) {
            Files.copy(is, outPath, StandardCopyOption.REPLACE_EXISTING);
        }
        conn.disconnect();
        return outPath;
    }
}