package com.aceerp.service;

import com.aceerp.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SettingsService {

    private static final Logger logger = LoggerFactory.getLogger(SettingsService.class);
    private static SettingsService instance;

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    private SettingsService() {
        ensureTableExists();
        loadAllSettings();
    }

    public static synchronized SettingsService getInstance() {
        if (instance == null) {
            instance = new SettingsService();
        }
        return instance;
    }

    private void ensureTableExists() {
        String sql = """
            CREATE TABLE IF NOT EXISTS system_settings (
                setting_key VARCHAR(100) PRIMARY KEY,
                setting_value TEXT NOT NULL
            );
            """;
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            logger.error("Failed to ensure system_settings table exists: {}", e.getMessage(), e);
        }
    }

    private void loadAllSettings() {
        cache.clear();
        String sql = "SELECT setting_key, setting_value FROM system_settings";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                cache.put(rs.getString("setting_key"), rs.getString("setting_value"));
            }
        } catch (SQLException e) {
            logger.error("Failed to load settings from DB: {}", e.getMessage(), e);
        }
    }

    /**
     * Reloads all settings directly from the database into cache.
     */
    public void reload() {
        loadAllSettings();
    }

    // ---- Getters ----

    public String getSetting(String key, String defaultValue) {
        if (key == null) return defaultValue;
        String val = cache.get(key);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }

    public String getSetting(String key) {
        return getSetting(key, null);
    }

    public String get(String key, String defaultValue) {
        return getSetting(key, defaultValue);
    }

    public String get(String key) {
        return getSetting(key, null);
    }

    // ---- Setters ----

    public void setSetting(String key, String value) {
        if (key == null) return;
        String valToSave = value == null ? "" : value.trim();
        cache.put(key, valToSave);

        String sql = """
            INSERT INTO system_settings (setting_key, setting_value)
            VALUES (?, ?)
            ON CONFLICT (setting_key)
            DO UPDATE SET setting_value = EXCLUDED.setting_value;
            """;

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, valToSave);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to persist setting '{}': {}", key, e.getMessage(), e);
        }
    }

    public void set(String key, String value) {
        setSetting(key, value);
    }

    /**
     * Batch saves multiple settings during application setup.
     */
    public void saveAllDuringSetup(Map<String, String> settingsMap) {
        if (settingsMap == null || settingsMap.isEmpty()) return;

        String sql = """
            INSERT INTO system_settings (setting_key, setting_value)
            VALUES (?, ?)
            ON CONFLICT (setting_key)
            DO UPDATE SET setting_value = EXCLUDED.setting_value;
            """;

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (Map.Entry<String, String> entry : settingsMap.entrySet()) {
                String k = entry.getKey();
                String v = entry.getValue() == null ? "" : entry.getValue().trim();
                cache.put(k, v);
                ps.setString(1, k);
                ps.setString(2, v);
                ps.addBatch();
            }
            ps.executeBatch();
            conn.commit();
            conn.setAutoCommit(true);
        } catch (SQLException e) {
            logger.error("Failed to save settings during setup: {}", e.getMessage(), e);
        }
    }

    // ---- Typed Helpers ----

    public int getIntSetting(String key, int defaultValue) {
        String val = getSetting(key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public double getDoubleSetting(String key, double defaultValue) {
        String val = getSetting(key, String.valueOf(defaultValue));
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBooleanSetting(String key, boolean defaultValue) {
        String val = getSetting(key, String.valueOf(defaultValue));
        return Boolean.parseBoolean(val);
    }
}