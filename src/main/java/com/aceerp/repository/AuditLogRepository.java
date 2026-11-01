package com.aceerp.repository;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class AuditLogRepository {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogRepository.class);

    public List<AuditLog> findLogs(String keyword, String actionFilter, String entityTypeFilter,
                                  LocalDate startDate, LocalDate endDate, int limit, int offset) {
        List<AuditLog> logs = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT id, user_id, username, action, entity_type, entity_id, description, created_at FROM audit_logs WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND (LOWER(username) LIKE ? OR LOWER(description) LIKE ? OR CAST(entity_id AS TEXT) LIKE ? OR LOWER(entity_type) LIKE ?) ");
            String term = "%" + keyword.trim().toLowerCase() + "%";
            params.add(term);
            params.add(term);
            params.add(term);
            params.add(term);
        }

        if (actionFilter != null && !actionFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(actionFilter)) {
            sql.append(" AND action = ? ");
            params.add(actionFilter.trim());
        }

        if (entityTypeFilter != null && !entityTypeFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(entityTypeFilter)) {
            sql.append(" AND entity_type = ? ");
            params.add(entityTypeFilter.trim());
        }

        if (startDate != null) {
            sql.append(" AND created_at >= ? ");
            params.add(Timestamp.valueOf(LocalDateTime.of(startDate, LocalTime.MIN)));
        }

        if (endDate != null) {
            sql.append(" AND created_at <= ? ");
            params.add(Timestamp.valueOf(LocalDateTime.of(endDate, LocalTime.MAX)));
        }

        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ? ");
        params.add(limit > 0 ? limit : 100);
        params.add(Math.max(offset, 0));

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof String s) {
                    stmt.setString(i + 1, s);
                } else if (p instanceof Timestamp ts) {
                    stmt.setTimestamp(i + 1, ts);
                } else if (p instanceof Integer n) {
                    stmt.setInt(i + 1, n);
                }
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    logs.add(mapResultSetToAuditLog(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error querying audit logs: {}", e.getMessage(), e);
        }
        return logs;
    }

    public int countLogs(String keyword, String actionFilter, String entityTypeFilter,
                         LocalDate startDate, LocalDate endDate) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM audit_logs WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append(" AND (LOWER(username) LIKE ? OR LOWER(description) LIKE ? OR CAST(entity_id AS TEXT) LIKE ? OR LOWER(entity_type) LIKE ?) ");
            String term = "%" + keyword.trim().toLowerCase() + "%";
            params.add(term);
            params.add(term);
            params.add(term);
            params.add(term);
        }

        if (actionFilter != null && !actionFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(actionFilter)) {
            sql.append(" AND action = ? ");
            params.add(actionFilter.trim());
        }

        if (entityTypeFilter != null && !entityTypeFilter.trim().isEmpty() && !"ALL".equalsIgnoreCase(entityTypeFilter)) {
            sql.append(" AND entity_type = ? ");
            params.add(entityTypeFilter.trim());
        }

        if (startDate != null) {
            sql.append(" AND created_at >= ? ");
            params.add(Timestamp.valueOf(LocalDateTime.of(startDate, LocalTime.MIN)));
        }

        if (endDate != null) {
            sql.append(" AND created_at <= ? ");
            params.add(Timestamp.valueOf(LocalDateTime.of(endDate, LocalTime.MAX)));
        }

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof String s) {
                    stmt.setString(i + 1, s);
                } else if (p instanceof Timestamp ts) {
                    stmt.setTimestamp(i + 1, ts);
                }
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.error("Error counting audit logs: {}", e.getMessage(), e);
        }
        return 0;
    }

    public List<String> getDistinctActions() {
        List<String> actions = new ArrayList<>();
        String sql = "SELECT DISTINCT action FROM audit_logs WHERE action IS NOT NULL AND action <> '' ORDER BY action";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                actions.add(rs.getString("action"));
            }
        } catch (SQLException e) {
            logger.error("Error fetching distinct audit log actions: {}", e.getMessage(), e);
        }
        return actions;
    }

    public List<String> getDistinctEntityTypes() {
        List<String> types = new ArrayList<>();
        String sql = "SELECT DISTINCT entity_type FROM audit_logs WHERE entity_type IS NOT NULL AND entity_type <> '' ORDER BY entity_type";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                types.add(rs.getString("entity_type"));
            }
        } catch (SQLException e) {
            logger.error("Error fetching distinct audit log entity types: {}", e.getMessage(), e);
        }
        return types;
    }

    private AuditLog mapResultSetToAuditLog(ResultSet rs) throws SQLException {
        AuditLog log = new AuditLog();
        log.setId(rs.getInt("id"));
        log.setUserId(rs.getInt("user_id"));
        log.setUsername(rs.getString("username"));
        log.setAction(rs.getString("action"));
        log.setEntityType(rs.getString("entity_type"));
        log.setEntityId(rs.getInt("entity_id"));
        log.setDescription(rs.getString("description"));
        log.setCreatedAt(rs.getTimestamp("created_at"));
        return log;
    }
}