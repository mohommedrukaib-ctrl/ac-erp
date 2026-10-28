package com.aceerp.service;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.AuditLog;
import com.aceerp.model.UserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AuditLogService {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogService.class);

    /**
     * Logs an action using an existing database connection (useful for wrapping in transactions).
     */
    public static void log(Connection conn, String action, String entityType, int entityId, String description) {
        String sql = "INSERT INTO audit_logs (user_id, username, action, entity_type, entity_id, description) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            if (UserSession.getInstance().isLoggedIn()) {
                stmt.setInt(1, UserSession.getInstance().getCurrentUser().getId());
                stmt.setString(2, UserSession.getInstance().getCurrentUser().getUsername());
            } else {
                stmt.setNull(1, java.sql.Types.INTEGER);
                stmt.setString(2, "SYSTEM");
            }
            
            stmt.setString(3, action);
            stmt.setString(4, entityType);
            stmt.setInt(5, entityId);
            stmt.setString(6, description);
            
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to insert audit log: {}", e.getMessage(), e);
        }
    }

    /**
     * Logs an action opening its own connection.
     */
    public static void log(String action, String entityType, int entityId, String description) {
        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            log(conn, action, entityType, entityId, description);
        } catch (SQLException e) {
            logger.error("Failed to get connection for audit log: {}", e.getMessage(), e);
        }
    }

    public List<AuditLog> getLogsForEntity(String entityType, int entityId) {
        List<AuditLog> logs = new ArrayList<>();
        String sql = "SELECT * FROM audit_logs WHERE entity_type = ? AND entity_id = ? ORDER BY created_at DESC";
        
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, entityType);
            stmt.setInt(2, entityId);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                AuditLog log = new AuditLog();
                log.setId(rs.getInt("id"));
                log.setUserId(rs.getInt("user_id"));
                log.setUsername(rs.getString("username"));
                log.setAction(rs.getString("action"));
                log.setEntityType(rs.getString("entity_type"));
                log.setEntityId(rs.getInt("entity_id"));
                log.setDescription(rs.getString("description"));
                log.setCreatedAt(rs.getTimestamp("created_at"));
                logs.add(log);
            }
        } catch (SQLException e) {
            logger.error("Failed to fetch audit logs: {}", e.getMessage(), e);
        }
        return logs;
    }
}