package com.gdelt.util;

import java.io.File;
import java.net.URISyntaxException;

public class AppConfig {

    private static String jarDir() {
        try {
            File jar = new File(AppConfig.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
            return jar.getParent();
        } catch (URISyntaxException e) {
            return System.getProperty("user.dir");
        }
    }

    private static final String BASE = System.getenv("GDELT_HOME") != null
        ? System.getenv("GDELT_HOME")
        : jarDir();

    public static final String DATA_DIR     = envOr("GDELT_DATA_DIR",     BASE + "/data");
    public static final String DOWNLOAD_DIR = envOr("GDELT_DOWNLOAD_DIR", BASE + "/data/downloads");
    public static final String IMPORT_DIR   = envOr("GDELT_IMPORT_DIR",   BASE + "/data/downloads");
    public static final String DB_PATH      = envOr("GDELT_DB_PATH",      BASE + "/data/gdelt_data.db");
    public static final String DB_URL       = "jdbc:sqlite:" + DB_PATH;
    public static final String VERSION      = "3.1";

    private static String envOr(String key, String defaultVal) {
        String v = System.getenv(key);
        return (v != null && !v.isEmpty()) ? v : defaultVal;
    }
}
