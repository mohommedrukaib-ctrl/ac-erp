package com.aceerp.service;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.AmcContract;
import com.aceerp.model.AmcSchedule;
import com.aceerp.model.UserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AmcContractService {
    private static final Logger logger = LoggerFactory.getLogger(AmcContractService.class);

    public List<AmcContract> listAll() {
        List<AmcContract> list = new ArrayList<>();
        String sql = """
            SELECT a.*, c.name AS customer_name
            FROM amc_contracts a
            LEFT JOIN customers c ON a.customer_id = c.id
            ORDER BY a.id DESC
            """;
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapContract(rs));
        } catch (SQLException e) {
            logger.error("Failed to list AMC contracts: {}", e.getMessage(), e);
        }
        return list;
    }

    public Optional<AmcContract> findByNumber(String contractNumber) {
        String sql = """
            SELECT a.*, c.name AS customer_name
            FROM amc_contracts a
            LEFT JOIN customers c ON a.customer_id = c.id
            WHERE LOWER(a.contract_number) = LOWER(?)
            """;
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, contractNumber);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapContract(rs));
            }
        } catch (SQLException e) {
            logger.error("findByNumber failed: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }

    /** Smart input: return existing contract or create a 1-year ACTIVE contract. */
    public AmcContract findOrCreateByNumber(String contractNumber, int customerId, Integer createdBy)
            throws SQLException {
        if (contractNumber == null || contractNumber.isBlank()) {
            throw new IllegalArgumentException("Contract number is required.");
        }
        if (customerId <= 0) {
            throw new IllegalArgumentException("Customer is required to create an AMC contract.");
        }
        Optional<AmcContract> existing = findByNumber(contractNumber.trim());
        if (existing.isPresent()) {
            return existing.get();
        }
        AmcContract c = new AmcContract();
        c.setContractNumber(contractNumber.trim());
        c.setCustomerId(customerId);
        c.setStartDate(LocalDate.now());
        c.setEndDate(LocalDate.now().plusYears(1));
        c.setStatus("ACTIVE");
        c.setTotalValue(BigDecimal.ZERO);
        c.setNotes("Auto-created from service job");
        c.setCreatedBy(createdBy);
        return save(c);
    }

    public AmcContract save(AmcContract c) throws SQLException {
        if (c.getContractNumber() == null || c.getContractNumber().isBlank()) {
            throw new IllegalArgumentException("Contract number is required.");
        }
        if (c.getCustomerId() <= 0) {
            throw new IllegalArgumentException("Customer is required.");
        }
        if (c.getStartDate() == null || c.getEndDate() == null) {
            throw new IllegalArgumentException("Start and end dates are required.");
        }
        if (c.getEndDate().isBefore(c.getStartDate())) {
            throw new IllegalArgumentException("End date cannot be before start date.");
        }

        if (c.getId() <= 0) {
            String sql = """
                INSERT INTO amc_contracts
                    (contract_number, customer_id, start_date, end_date, status, total_value, notes, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """;
            try (Connection conn = DatabaseConfig.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, c.getContractNumber().trim());
                ps.setInt(2, c.getCustomerId());
                ps.setDate(3, Date.valueOf(c.getStartDate()));
                ps.setDate(4, Date.valueOf(c.getEndDate()));
                ps.setString(5, c.getStatus() != null ? c.getStatus() : "ACTIVE");
                ps.setBigDecimal(6, c.getTotalValue() != null ? c.getTotalValue() : BigDecimal.ZERO);
                ps.setString(7, c.getNotes());
                if (c.getCreatedBy() != null) ps.setInt(8, c.getCreatedBy());
                else if (UserSession.getInstance().isLoggedIn())
                    ps.setInt(8, UserSession.getInstance().getCurrentUser().getId());
                else ps.setNull(8, Types.INTEGER);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) c.setId(rs.getInt(1));
                }
            }
        } else {
            String sql = """
                UPDATE amc_contracts
                SET contract_number=?, customer_id=?, start_date=?, end_date=?, status=?, total_value=?, notes=?
                WHERE id=?
                """;
            try (Connection conn = DatabaseConfig.getInstance().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, c.getContractNumber().trim());
                ps.setInt(2, c.getCustomerId());
                ps.setDate(3, Date.valueOf(c.getStartDate()));
                ps.setDate(4, Date.valueOf(c.getEndDate()));
                ps.setString(5, c.getStatus());
                ps.setBigDecimal(6, c.getTotalValue() != null ? c.getTotalValue() : BigDecimal.ZERO);
                ps.setString(7, c.getNotes());
                ps.setInt(8, c.getId());
                ps.executeUpdate();
            }
        }
        return c;
    }

    public List<AmcSchedule> getSchedules(int contractId) {
        List<AmcSchedule> list = new ArrayList<>();
        String sql = "SELECT * FROM amc_schedules WHERE contract_id = ? ORDER BY scheduled_date";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, contractId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AmcSchedule s = new AmcSchedule();
                    s.setId(rs.getInt("id"));
                    s.setContractId(rs.getInt("contract_id"));
                    Date d = rs.getDate("scheduled_date");
                    if (d != null) s.setScheduledDate(d.toLocalDate());
                    s.setStatus(rs.getString("status"));
                    int jid = rs.getInt("service_job_id");
                    if (!rs.wasNull()) s.setServiceJobId(jid);
                    s.setNotes(rs.getString("notes"));
                    list.add(s);
                }
            }
        } catch (SQLException e) {
            logger.error("getSchedules failed: {}", e.getMessage(), e);
        }
        return list;
    }

    public void addSchedule(AmcSchedule s) throws SQLException {
        String sql = """
            INSERT INTO amc_schedules (contract_id, scheduled_date, status, notes)
            VALUES (?, ?, ?, ?)
            """;
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, s.getContractId());
            ps.setDate(2, Date.valueOf(s.getScheduledDate()));
            ps.setString(3, s.getStatus() != null ? s.getStatus() : "PENDING");
            ps.setString(4, s.getNotes());
            ps.executeUpdate();
        }
    }

    public void generateQuarterlySchedule(int contractId, LocalDate start, LocalDate end) throws SQLException {
        LocalDate d = start;
        while (!d.isAfter(end)) {
            AmcSchedule s = new AmcSchedule();
            s.setContractId(contractId);
            s.setScheduledDate(d);
            s.setStatus("PENDING");
            s.setNotes("Auto-generated quarterly visit");
            addSchedule(s);
            d = d.plusMonths(3);
        }
    }

    private AmcContract mapContract(ResultSet rs) throws SQLException {
        AmcContract c = new AmcContract();
        c.setId(rs.getInt("id"));
        c.setContractNumber(rs.getString("contract_number"));
        c.setCustomerId(rs.getInt("customer_id"));
        c.setCustomerName(rs.getString("customer_name"));
        Date s = rs.getDate("start_date");
        if (s != null) c.setStartDate(s.toLocalDate());
        Date e = rs.getDate("end_date");
        if (e != null) c.setEndDate(e.toLocalDate());
        c.setStatus(rs.getString("status"));
        BigDecimal v = rs.getBigDecimal("total_value");
        c.setTotalValue(v != null ? v : BigDecimal.ZERO);
        c.setNotes(rs.getString("notes"));
        return c;
    }
}