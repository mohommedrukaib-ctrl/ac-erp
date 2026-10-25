package com.aceerp.repository;

import com.aceerp.config.DatabaseConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * Repository for the app_settings key-value store.
 * 
 * Settings are stored as rows: setting_key, setting_value, category, etc.
 * This class provides simple get/set operations.
 */
public class SettingsRepository {

    private static final Logger logger = LoggerFactory.getLogger(SettingsRepository.class);

    /**
     * Get a single setting value by key. Returns null if not found.
     */
    public String get(String key) {
        String sql = "SELECT setting_value FROM app_settings WHERE setting_key = ?";

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, key);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("setting_value");
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to read setting '{}': {}", key, e.getMessage());
        }
        return null;
    }

    /**
     * Get all settings in a given category as a Map.
     * Example: getAllByCategory("company") returns 
     *   { "company.name": "My AC Business", "company.phone": "...", ... }
     */
    public Map<String, String> getAllByCategory(String category) {
        String sql = "SELECT setting_key, setting_value FROM app_settings WHERE category = ? ORDER BY setting_key";
        Map<String, String> result = new HashMap<>();

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, category);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("setting_key"), rs.getString("setting_value"));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to read settings category '{}': {}", category, e.getMessage());
        }
        return result;
    }

    /**
     * Get all settings as a Map (for caching).
     */
    public Map<String, String> getAll() {
        String sql = "SELECT setting_key, setting_value FROM app_settings";
        Map<String, String> result = new HashMap<>();

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                result.put(rs.getString("setting_key"), rs.getString("setting_value"));
            }
        } catch (SQLException e) {
            logger.error("Failed to read all settings: {}", e.getMessage());
        }
        return result;
    }

    /**
     * Update a single setting value (key must already exist in the database).
     */
    public void set(String key, String value, Integer updatedByUserId) throws SQLException {
        String sql = """
            UPDATE app_settings
            SET setting_value = ?, updated_at = CURRENT_TIMESTAMP, updated_by = ?
            WHERE setting_key = ?
            """;

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, value);
            if (updatedByUserId != null) {
                stmt.setInt(2, updatedByUserId);
            } else {
                stmt.setNull(2, java.sql.Types.INTEGER);
            }
            stmt.setString(3, key);

            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new SQLException("Setting key not found: " + key);
            }
            logger.debug("Updated setting: {} = {}", key, value);
        }
    }

    /**
     * Update multiple settings atomically in a single transaction.
     * If any update fails, ALL changes are rolled back.
     * 
     * This is important for the Settings screen's Save button, where the user
     * expects all their changes to apply together or not at all.
     */
    public void setMany(Map<String, String> values, Integer updatedByUserId) throws SQLException {
        String sql = """
            UPDATE app_settings
            SET setting_value = ?, updated_at = CURRENT_TIMESTAMP, updated_by = ?
            WHERE setting_key = ?
            """;

        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            conn.setAutoCommit(false); // Start transaction

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (Map.Entry<String, String> entry : values.entrySet()) {
                    stmt.setString(1, entry.getValue());
                    if (updatedByUserId != null) {
                        stmt.setInt(2, updatedByUserId);
                    } else {
                        stmt.setNull(2, java.sql.Types.INTEGER);
                    }
                    stmt.setString(3, entry.getKey());
                    stmt.addBatch(); // Queue the update
                }
                stmt.executeBatch(); // Execute all queued updates
                conn.commit();       // Save all changes
                logger.info("Batch updated {} setting(s).", values.size());

            } catch (SQLException e) {
                conn.rollback(); // Undo all changes
                logger.error("Batch settings update failed, rolled back: {}", e.getMessage());
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
}