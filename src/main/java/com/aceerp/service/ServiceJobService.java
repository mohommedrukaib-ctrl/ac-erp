package com.aceerp.service;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.ServiceJob;
import com.aceerp.model.ServiceJobLine;
import com.aceerp.model.User;
import com.aceerp.model.UserRole;
import com.aceerp.model.UserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ServiceJobService {
    private static final Logger logger = LoggerFactory.getLogger(ServiceJobService.class);

    public List<ServiceJob> getAllJobs() {
        List<ServiceJob> list = new ArrayList<>();
        String sql = """
            SELECT sj.*, c.name AS customer_name, u.full_name AS technician_name
            FROM service_jobs sj
            JOIN customers c ON sj.customer_id = c.id
            LEFT JOIN users u ON sj.technician_id = u.id
            ORDER BY sj.id DESC
        """;
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                ServiceJob job = mapResultSetToJob(rs);
                job.setLines(getLinesForJob(conn, job.getId()));
                list.add(job);
            }
        } catch (SQLException e) {
            logger.error("Error loading service jobs: {}", e.getMessage(), e);
        }
        return list;
    }

    public ServiceJob getById(int id) {
        String sql = """
            SELECT sj.*, c.name AS customer_name, u.full_name AS technician_name
            FROM service_jobs sj
            JOIN customers c ON sj.customer_id = c.id
            LEFT JOIN users u ON sj.technician_id = u.id
            WHERE sj.id = ?
        """;
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    ServiceJob job = mapResultSetToJob(rs);
                    job.setLines(getLinesForJob(conn, job.getId()));
                    return job;
                }
            }
        } catch (SQLException e) {
            logger.error("Error loading service job by id: {}", e.getMessage(), e);
        }
        return null;
    }

    private List<ServiceJobLine> getLinesForJob(Connection conn, int jobId) throws SQLException {
        List<ServiceJobLine> lines = new ArrayList<>();
        String sql = """
            SELECT sjl.*, p.code AS product_code, p.name AS product_name
            FROM service_job_lines sjl
            JOIN products p ON sjl.product_id = p.id
            WHERE sjl.service_job_id = ?
        """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, jobId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ServiceJobLine line = new ServiceJobLine();
                    line.setId(rs.getInt("id"));
                    line.setServiceJobId(rs.getInt("service_job_id"));
                    line.setProductId(rs.getInt("product_id"));
                    line.setProductCode(rs.getString("product_code"));
                    line.setProductName(rs.getString("product_name"));
                    line.setQuantity(rs.getInt("quantity"));
                    line.setUnitPrice(rs.getBigDecimal("unit_price"));
                    line.setLineTotal(rs.getBigDecimal("line_total"));
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    public boolean saveJob(ServiceJob job) {
        job.recalculateTotal();
        boolean isNew = job.getId() <= 0;

        String insertHeader = """
            INSERT INTO service_jobs (job_number, customer_id, technician_id, job_type, status, scheduled_date, completion_date, ac_model_info, reported_issue, resolution_notes, labor_cost, parts_total, grand_total, created_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
        """;

        String updateHeader = """
            UPDATE service_jobs SET customer_id=?, technician_id=?, job_type=?, status=?, scheduled_date=?, completion_date=?, ac_model_info=?, reported_issue=?, resolution_notes=?, labor_cost=?, parts_total=?, grand_total=?
            WHERE id=?
        """;

        String deleteLines = "DELETE FROM service_job_lines WHERE service_job_id = ?";
        String insertLine = "INSERT INTO service_job_lines (service_job_id, product_id, quantity, unit_price, line_total) VALUES (?, ?, ?, ?, ?)";
        String deductStock = "UPDATE products SET stock_quantity = stock_quantity - ? WHERE id = ?";
        String logStockMovement = "INSERT INTO stock_movements (product_id, movement_type, quantity, reference_type, reference_id, notes) VALUES (?, 'SERVICE_USE', ?, 'SERVICE_JOB', ?, ?)";

        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Check previous status if updating
                String previousStatus = "";
                if (!isNew) {
                    try (PreparedStatement pCheck = conn.prepareStatement("SELECT status FROM service_jobs WHERE id = ?")) {
                        pCheck.setInt(1, job.getId());
                        ResultSet rCheck = pCheck.executeQuery();
                        if (rCheck.next()) previousStatus = rCheck.getString(1);
                    }
                }

                if (isNew) {
                    if (job.getJobNumber() == null || job.getJobNumber().isBlank()) {
                        job.setJobNumber(generateNextJobNumber(conn));
                    }
                    try (PreparedStatement stmt = conn.prepareStatement(insertHeader)) {
                        stmt.setString(1, job.getJobNumber());
                        stmt.setInt(2, job.getCustomerId());
                        setNullableInt(stmt, 3, job.getTechnicianId());
                        stmt.setString(4, job.getJobType());
                        stmt.setString(5, job.getStatus());
                        stmt.setDate(6, job.getScheduledDate() != null ? Date.valueOf(job.getScheduledDate()) : Date.valueOf(LocalDate.now()));
                        stmt.setDate(7, job.getCompletionDate() != null ? Date.valueOf(job.getCompletionDate()) : null);
                        stmt.setString(8, job.getAcModelInfo());
                        stmt.setString(9, job.getReportedIssue());
                        stmt.setString(10, job.getResolutionNotes());
                        stmt.setBigDecimal(11, job.getLaborCost());
                        stmt.setBigDecimal(12, job.getPartsTotal());
                        stmt.setBigDecimal(13, job.getGrandTotal());
                        if (UserSession.getInstance().getCurrentUser() != null) {
                            stmt.setInt(14, UserSession.getInstance().getCurrentUser().getId());
                        } else {
                            stmt.setNull(14, Types.INTEGER);
                        }
                        ResultSet rs = stmt.executeQuery();
                        if (rs.next()) {
                            job.setId(rs.getInt(1));
                        }
                    }
                } else {
                    try (PreparedStatement stmt = conn.prepareStatement(updateHeader)) {
                        stmt.setInt(1, job.getCustomerId());
                        setNullableInt(stmt, 2, job.getTechnicianId());
                        stmt.setString(3, job.getJobType());
                        stmt.setString(4, job.getStatus());
                        stmt.setDate(5, job.getScheduledDate() != null ? Date.valueOf(job.getScheduledDate()) : Date.valueOf(LocalDate.now()));
                        stmt.setDate(6, job.getCompletionDate() != null ? Date.valueOf(job.getCompletionDate()) : null);
                        stmt.setString(7, job.getAcModelInfo());
                        stmt.setString(8, job.getReportedIssue());
                        stmt.setString(9, job.getResolutionNotes());
                        stmt.setBigDecimal(10, job.getLaborCost());
                        stmt.setBigDecimal(11, job.getPartsTotal());
                        stmt.setBigDecimal(12, job.getGrandTotal());
                        stmt.setInt(13, job.getId());
                        stmt.executeUpdate();
                    }

                    try (PreparedStatement stmt = conn.prepareStatement(deleteLines)) {
                        stmt.setInt(1, job.getId());
                        stmt.executeUpdate();
                    }
                }

                // Insert Lines
                for (ServiceJobLine line : job.getLines()) {
                    try (PreparedStatement stmt = conn.prepareStatement(insertLine)) {
                        stmt.setInt(1, job.getId());
                        stmt.setInt(2, line.getProductId());
                        stmt.setInt(3, line.getQuantity());
                        stmt.setBigDecimal(4, line.getUnitPrice());
                        stmt.setBigDecimal(5, line.getLineTotal());
                        stmt.executeUpdate();
                    }
                }

                // Auto-Deduct Inventory if transitioning to COMPLETED
                if ("COMPLETED".equals(job.getStatus()) && !"COMPLETED".equals(previousStatus)) {
                    for (ServiceJobLine line : job.getLines()) {
                        try (PreparedStatement stmt = conn.prepareStatement(deductStock)) {
                            stmt.setInt(1, line.getQuantity());
                            stmt.setInt(2, line.getProductId());
                            stmt.executeUpdate();
                        }
                        try (PreparedStatement stmt = conn.prepareStatement(logStockMovement)) {
                            stmt.setInt(1, line.getProductId());
                            stmt.setInt(2, line.getQuantity());
                            stmt.setInt(3, job.getId());
                            stmt.setString(4, "Used in Service Job: " + job.getJobNumber());
                            stmt.executeUpdate();
                        }
                    }
                }

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                logger.error("Error saving service job: {}", e.getMessage(), e);
                return false;
            }
        } catch (SQLException e) {
            logger.error("DB connection error: {}", e.getMessage(), e);
            return false;
        }
    }

    public List<User> getTechnicians() {
        List<User> list = new ArrayList<>();
        String sql = "SELECT id, username, full_name, role FROM users WHERE is_active = TRUE ORDER BY full_name ASC";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                User u = new User();
                u.setId(rs.getInt("id"));
                u.setUsername(rs.getString("username"));
                u.setFullName(rs.getString("full_name"));
                u.setRole(UserRole.valueOf(rs.getString("role")));
                list.add(u);
            }
        } catch (SQLException e) {
            logger.error("Error loading technicians: {}", e.getMessage(), e);
        }
        return list;
    }

    private ServiceJob mapResultSetToJob(ResultSet rs) throws SQLException {
        ServiceJob job = new ServiceJob();
        job.setId(rs.getInt("id"));
        job.setJobNumber(rs.getString("job_number"));
        job.setCustomerId(rs.getInt("customer_id"));
        job.setCustomerName(rs.getString("customer_name"));
        int techId = rs.getInt("technician_id");
        if (!rs.wasNull()) job.setTechnicianId(techId);
        job.setTechnicianName(rs.getString("technician_name"));
        job.setJobType(rs.getString("job_type"));
        job.setStatus(rs.getString("status"));
        Date sched = rs.getDate("scheduled_date");
        if (sched != null) job.setScheduledDate(sched.toLocalDate());
        Date comp = rs.getDate("completion_date");
        if (comp != null) job.setCompletionDate(comp.toLocalDate());
        job.setAcModelInfo(rs.getString("ac_model_info"));
        job.setReportedIssue(rs.getString("reported_issue"));
        job.setResolutionNotes(rs.getString("resolution_notes"));
        job.setLaborCost(rs.getBigDecimal("labor_cost"));
        job.setPartsTotal(rs.getBigDecimal("parts_total"));
        job.setGrandTotal(rs.getBigDecimal("grand_total"));
        return job;
    }

    private void setNullableInt(PreparedStatement stmt, int index, Integer value) throws SQLException {
        if (value != null && value > 0) stmt.setInt(index, value);
        else stmt.setNull(index, Types.INTEGER);
    }

    public String generateNextJobNumber(Connection conn) throws SQLException {
        String sql = "SELECT COUNT(*) FROM service_jobs";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            long count = rs.next() ? rs.getLong(1) + 1 : 1;
            return String.format("JOB-%06d", count);
        }
    }
}