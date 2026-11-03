package com.aceerp.service;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class JobCostingService {

    private static final Logger logger = LoggerFactory.getLogger(JobCostingService.class);
    private final AccountingService accountingService = new AccountingService();

    // -------------------------------------------------------------------------
    // BUDGET MANAGEMENT
    // -------------------------------------------------------------------------

    public JobBudget getBudgetForJob(int serviceJobId) {
        String sql = "SELECT * FROM job_budgets WHERE service_job_id = ?";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, serviceJobId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    JobBudget b = new JobBudget();
                    b.setId(rs.getInt("id"));
                    b.setServiceJobId(rs.getInt("service_job_id"));
                    b.setBudgetedMaterials(rs.getBigDecimal("budgeted_materials"));
                    b.setBudgetedLabor(rs.getBigDecimal("budgeted_labor"));
                    b.setBudgetedEquipment(rs.getBigDecimal("budgeted_equipment"));
                    b.setBudgetedSubcontractor(rs.getBigDecimal("budgeted_subcontractor"));
                    b.setBudgetedOverhead(rs.getBigDecimal("budgeted_overhead"));
                    b.setTotalBudgetedCost(rs.getBigDecimal("total_budgeted_cost"));
                    return b;
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to fetch budget for job {}: {}", serviceJobId, e.getMessage(), e);
        }
        JobBudget empty = new JobBudget();
        empty.setServiceJobId(serviceJobId);
        return empty;
    }

    public JobBudget saveBudget(JobBudget budget) {
        budget.recalculateTotal();
        String sql = """
            INSERT INTO job_budgets (service_job_id, budgeted_materials, budgeted_labor, budgeted_equipment, budgeted_subcontractor, budgeted_overhead, total_budgeted_cost)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (service_job_id)
            DO UPDATE SET budgeted_materials = EXCLUDED.budgeted_materials,
                          budgeted_labor = EXCLUDED.budgeted_labor,
                          budgeted_equipment = EXCLUDED.budgeted_equipment,
                          budgeted_subcontractor = EXCLUDED.budgeted_subcontractor,
                          budgeted_overhead = EXCLUDED.budgeted_overhead,
                          total_budgeted_cost = EXCLUDED.total_budgeted_cost,
                          updated_at = CURRENT_TIMESTAMP
            RETURNING id
            """;
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, budget.getServiceJobId());
            ps.setBigDecimal(2, budget.getBudgetedMaterials());
            ps.setBigDecimal(3, budget.getBudgetedLabor());
            ps.setBigDecimal(4, budget.getBudgetedEquipment());
            ps.setBigDecimal(5, budget.getBudgetedSubcontractor());
            ps.setBigDecimal(6, budget.getBudgetedOverhead());
            ps.setBigDecimal(7, budget.getTotalBudgetedCost());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) budget.setId(rs.getInt(1));
            }
            AuditLogService.log("SAVE_JOB_BUDGET", "JOB_BUDGET", budget.getServiceJobId(), "Saved job cost budget");
            return budget;
        } catch (SQLException e) {
            logger.error("Failed to save budget: {}", e.getMessage(), e);
            throw new RuntimeException("Save budget failed: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // ACTUAL COST LOGGING & WIP GL AUTO-POSTING
    // -------------------------------------------------------------------------

    public JobCostEntry addCostEntry(JobCostEntry entry, int userId) {
        if (entry.getServiceJobId() <= 0) throw new IllegalArgumentException("Service Job ID is required.");
        entry.recalculateTotal();

        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            conn.setAutoCommit(false);

            // GL Auto-Post: DR 1250 (WIP Inventory) / CR 1010 (Cash/Bank/AP depending on category)
            Integer wipAccId = accountingService.findAccountIdByCode(conn, "1250");
            Integer offsetAccId = accountingService.findAccountIdByCode(conn, AccountingService.ACC_CASH);

            if (wipAccId != null && offsetAccId != null) {
                JournalEntry je = new JournalEntry();
                je.setEntryDate(entry.getEntryDate() != null ? entry.getEntryDate() : LocalDate.now());
                je.setReferenceType("JOB_COST");
                je.setReferenceId(entry.getServiceJobId());
                je.setDescription("Job Cost WIP — " + entry.getCostCategory() + " (" + entry.getDescription() + ")");
                je.setAutoPosted(true);

                List<JournalEntryLine> jeLines = new ArrayList<>();

                // DR WIP Inventory (1250)
                JournalEntryLine drWip = new JournalEntryLine();
                drWip.setAccountId(wipAccId);
                drWip.setDebit(entry.getTotalCost());
                drWip.setCredit(BigDecimal.ZERO);
                drWip.setDescription("WIP " + entry.getCostCategory() + " — Job #" + entry.getServiceJobId());
                jeLines.add(drWip);

                // CR Cash/AP Offset
                JournalEntryLine crOffset = new JournalEntryLine();
                crOffset.setAccountId(offsetAccId);
                crOffset.setDebit(BigDecimal.ZERO);
                crOffset.setCredit(entry.getTotalCost());
                crOffset.setDescription("Job Cost Source Outflow");
                jeLines.add(crOffset);

                je.setLines(jeLines);
                JournalEntry postedJe = accountingService.postJournalEntry(je);
                if (postedJe != null) entry.setJournalEntryId(postedJe.getId());
            }

            String insert = """
                INSERT INTO job_cost_entries (service_job_id, cost_category, entry_date, description, quantity, unit_cost, total_cost, source_type, source_id, journal_entry_id, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """;
            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                ps.setInt(1, entry.getServiceJobId());
                ps.setString(2, entry.getCostCategory());
                ps.setDate(3, Date.valueOf(entry.getEntryDate()));
                ps.setString(4, entry.getDescription());
                ps.setBigDecimal(5, entry.getQuantity());
                ps.setBigDecimal(6, entry.getUnitCost());
                ps.setBigDecimal(7, entry.getTotalCost());
                ps.setString(8, entry.getSourceType() != null ? entry.getSourceType() : "MANUAL");
                if (entry.getSourceId() != null) ps.setInt(9, entry.getSourceId()); else ps.setNull(9, Types.INTEGER);
                if (entry.getJournalEntryId() != null) ps.setInt(10, entry.getJournalEntryId()); else ps.setNull(10, Types.INTEGER);
                if (userId > 0) ps.setInt(11, userId); else ps.setNull(11, Types.INTEGER);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) entry.setId(rs.getInt(1));
                }
            }

            conn.commit();
            AuditLogService.log("ADD_JOB_COST", "JOB_COST_ENTRY", entry.getId(), "Added " + entry.getCostCategory() + " cost to Job #" + entry.getServiceJobId());
            return entry;

        } catch (Exception e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ignored) {}
            logger.error("Failed to add job cost entry: {}", e.getMessage(), e);
            throw new RuntimeException("Add job cost failed: " + e.getMessage(), e);
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
        }
    }

    public List<JobCostEntry> getCostEntriesForJob(int serviceJobId) {
        List<JobCostEntry> list = new ArrayList<>();
        String sql = "SELECT * FROM job_cost_entries WHERE service_job_id = ? ORDER BY entry_date DESC, id DESC";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, serviceJobId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JobCostEntry e = new JobCostEntry();
                    e.setId(rs.getInt("id"));
                    e.setServiceJobId(rs.getInt("service_job_id"));
                    e.setCostCategory(rs.getString("cost_category"));
                    e.setEntryDate(rs.getDate("entry_date").toLocalDate());
                    e.setDescription(rs.getString("description"));
                    e.setQuantity(rs.getBigDecimal("quantity"));
                    e.setUnitCost(rs.getBigDecimal("unit_cost"));
                    e.setTotalCost(rs.getBigDecimal("total_cost"));
                    e.setSourceType(rs.getString("source_type"));
                    e.setSourceId((Integer) rs.getObject("source_id"));
                    e.setJournalEntryId((Integer) rs.getObject("journal_entry_id"));
                    list.add(e);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to list job costs for job {}: {}", serviceJobId, e.getMessage(), e);
        }
        return list;
    }

    // -------------------------------------------------------------------------
    // PROFITABILITY ANALYTICS
    // -------------------------------------------------------------------------

    public JobProfitabilityDTO getJobProfitability(int serviceJobId) {
        JobProfitabilityDTO dto = new JobProfitabilityDTO();
        dto.setServiceJobId(serviceJobId);

        String sqlJob = """
            SELECT j.id, j.job_number, j.job_type, j.status, j.grand_total,
                   c.name AS customer_name
            FROM service_jobs j
            LEFT JOIN customers c ON j.customer_id = c.id
            WHERE j.id = ?
            """;
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlJob)) {
            ps.setInt(1, serviceJobId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    dto.setJobRefNumber(rs.getString("job_number"));
                    dto.setCustomerName(rs.getString("customer_name"));
                    dto.setJobTitle(rs.getString("job_type"));
                    dto.setStatus(rs.getString("status"));
                    BigDecimal contract = rs.getBigDecimal("grand_total");
                    dto.setContractPrice(contract != null ? contract : BigDecimal.ZERO);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to fetch job profitability header: {}", e.getMessage(), e);
        }
        
        JobBudget budget = getBudgetForJob(serviceJobId);
        dto.setBudgetedCost(budget.getTotalBudgetedCost());

        String sqlActuals = "SELECT COALESCE(SUM(total_cost), 0) FROM job_cost_entries WHERE service_job_id = ?";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sqlActuals)) {
            ps.setInt(1, serviceJobId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    dto.setActualCost(rs.getBigDecimal(1));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to sum job costs: {}", e.getMessage(), e);
        }

        dto.calculate();
        return dto;
    }
}