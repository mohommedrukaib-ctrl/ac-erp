package com.aceerp.service;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.TechnicianCommission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CommissionService {
    private static final Logger logger = LoggerFactory.getLogger(CommissionService.class);

    public List<TechnicianCommission> listAll() {
        return listByStatus(null);
    }

    public List<TechnicianCommission> listByStatus(String status) {
        List<TechnicianCommission> list = new ArrayList<>();
        String sql = """
            SELECT tc.*, u.full_name AS technician_name, sj.job_number
            FROM technician_commissions tc
            LEFT JOIN users u ON tc.technician_id = u.id
            LEFT JOIN service_jobs sj ON tc.service_job_id = sj.id
            WHERE (? IS NULL OR tc.status = ?)
            ORDER BY tc.id DESC
            """;
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (status == null || status.isBlank() || "ALL".equals(status)) {
                ps.setNull(1, Types.VARCHAR);
                ps.setNull(2, Types.VARCHAR);
            } else {
                ps.setString(1, status);
                ps.setString(2, status);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            logger.error("list commissions failed: {}", e.getMessage(), e);
        }
        return list;
    }

    public void markPaid(int commissionId) throws SQLException {
        String sql = """
            UPDATE technician_commissions
            SET status = 'PAID', paid_at = CURRENT_TIMESTAMP
            WHERE id = ? AND status = 'PENDING'
            """;
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, commissionId);
            int n = ps.executeUpdate();
            if (n == 0) throw new SQLException("Commission not found or already paid.");
        }
    }

    public void markPaidBatch(List<Integer> ids) throws SQLException {
        if (ids == null || ids.isEmpty()) return;
        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                for (Integer id : ids) {
                    if (id == null || id <= 0) continue;
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE technician_commissions SET status='PAID', paid_at=CURRENT_TIMESTAMP WHERE id=? AND status='PENDING'")) {
                        ps.setInt(1, id);
                        ps.executeUpdate();
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    private TechnicianCommission mapRow(ResultSet rs) throws SQLException {
        TechnicianCommission t = new TechnicianCommission();
        t.setId(rs.getInt("id"));
        t.setTechnicianId(rs.getInt("technician_id"));
        t.setTechnicianName(rs.getString("technician_name"));
        t.setServiceJobId(rs.getInt("service_job_id"));
        t.setJobNumber(rs.getString("job_number"));
        BigDecimal amt = rs.getBigDecimal("commission_amount");
        t.setCommissionAmount(amt != null ? amt : BigDecimal.ZERO);
        t.setStatus(rs.getString("status"));
        Timestamp paid = rs.getTimestamp("paid_at");
        if (paid != null) t.setPaidAt(paid.toLocalDateTime());
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) t.setCreatedAt(created.toLocalDateTime());
        return t;
    }
}