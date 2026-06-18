package com.gdelt.service;

import com.gdelt.db.DatabaseHelper;
import com.gdelt.model.GdeltEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipInputStream;
import javax.swing.*;

/**
 * 数据导入服务 — ZIP→TSV解析→数据库
 * v3.0: 纯导入，移除ZIP BLOB存储
 */
public class ImportService {

    public static int importFromZip(File zipFile, JProgressBar progress) {
        int total = 0;
        List<GdeltEvent> batch = new ArrayList<>(5000);

        try (ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zipFile)),
                StandardCharsets.UTF_8)) {
            zis.getNextEntry();
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(zis, StandardCharsets.UTF_8), 1024 * 1024);

            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split("\t", -1);
                if (fields.length < 35) continue;
                GdeltEvent e = GdeltEvent.fromTSVLine(fields);
                if (e.getGlobalEventId() > 0) {
                    batch.add(e);
                    total++;
                }
                if (batch.size() >= 5000) {
                    DatabaseHelper.getInstance().insertBatch(batch);
                    batch.clear();
                    if (progress != null) {
                        final int t = total;
                        SwingUtilities.invokeLater(() -> progress.setValue(t / 1000));
                    }
                }
                if (total % 100000 == 0) {
                    System.out.println("  Imported " + String.format("%,d", total) + " events...");
                }
            }
            if (!batch.isEmpty()) {
                DatabaseHelper.getInstance().insertBatch(batch);
            }
            System.out.println("  File done: " + zipFile.getName()
                + " → " + String.format("%,d", total) + " events");

        } catch (Exception e) {
            System.err.println("[ERROR] Import " + zipFile.getName() + ": " + e.getMessage());
            return -1;
        }
        return total;
    }

    public static int getTotalEvents() {
        try { return DatabaseHelper.getInstance().countAll(); } catch (Exception e) { return 0; }
    }
}
