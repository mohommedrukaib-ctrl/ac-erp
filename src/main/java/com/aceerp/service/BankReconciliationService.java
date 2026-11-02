
package com.aceerp.service;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.Account;
import com.aceerp.model.BankReconciliation;
import com.aceerp.model.ReconciliationItemDTO;
import com.aceerp.model.UserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class BankReconciliationService {

    private static final Logger logger = LoggerFactory.getLogger(BankReconciliationService.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    public List<Account> getBankAccounts() {
        List<Account> accounts = new ArrayList<>();
        String sql = """
            SELECT id, code, name, account_type, balance
            FROM accounts
            WHERE account_type = 'ASSET' AND is_active = TRUE
            ORDER BY code ASC
            """;
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Account acc = new Account();
                acc.setId(rs.getInt("id"));
                acc.setAccountCode(rs.getString("code"));
                acc.setAccountName(rs.getString("name"));
                acc.setAccountType(rs.getString("account_type"));
                acc.setCurrentBalance(rs.getBigDecimal("balance"));
                accounts.add(acc);
            }
        } catch (SQLException e) {
            logger.error("Failed to load bank accounts: {}", e.getMessage(), e);
        }
        return accounts;
    }

    public BigDecimal getLastReconciledBalance(int accountId) {
        String sql = """
            SELECT statement_ending_balance
            FROM bank_reconciliations
            WHERE account_id = ? AND status = 'RECONCILED'
            ORDER BY statement_date DESC, id DESC
            LIMIT 1
            """;
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal(1);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get last reconciled balance: {}", e.getMessage(), e);
        }
        return ZERO;
    }

    public List<ReconciliationItemDTO> getUnclearedGLLines(int accountId, LocalDate cutoffDate) {
        List<ReconciliationItemDTO> items = new ArrayList<>();
        String sql = """
            SELECT jel.id AS line_id, jel.journal_entry_id, jel.debit, jel.credit, jel.description AS line_desc,
                   je.entry_number, je.entry_date, je.memo,
                   a.code AS acc_code, a.name AS acc_name
            FROM journal_entry_lines jel
            JOIN journal_entries je ON jel.journal_entry_id = je.id
            JOIN accounts a ON jel.account_id = a.id
            WHERE jel.account_id = ?
              AND jel.cleared_status = FALSE
              AND je.entry_date <= ?
              AND je.status = 'POSTED'
            ORDER BY je.entry_date ASC, je.id ASC
            """;
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            ps.setDate(2, Date.valueOf(cutoffDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ReconciliationItemDTO dto = new ReconciliationItemDTO();
                    dto.setJournalEntryLineId(rs.getInt("line_id"));
                    dto.setJournalEntryId(rs.getInt("journal_entry_id"));
                    dto.setEntryNumber(rs.getString("entry_number"));
                    Date d = rs.getDate("entry_date");
                    if (d != null) dto.setEntryDate(d.toLocalDate());
                    dto.setMemo(rs.getString("memo") != null ? rs.getString("memo") : rs.getString("line_desc"));
                    dto.setAccountCode(rs.getString("acc_code"));
                    dto.setAccountName(rs.getString("acc_name"));
                    BigDecimal dr = rs.getBigDecimal("debit") != null ? rs.getBigDecimal("debit") : ZERO;
                    BigDecimal cr = rs.getBigDecimal("credit") != null ? rs.getBigDecimal("credit") : ZERO;
                    dto.setDebit(dr);
                    dto.setCredit(cr);

                    if (dr.compareTo(ZERO) > 0) {
                        dto.setDeposit(true);
                        dto.setAmount(dr);
                    } else {
                        dto.setDeposit(false);
                        dto.setAmount(cr);
                    }
                    dto.setReference(rs.getString("entry_number"));
                    items.add(dto);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to fetch uncleared GL lines: {}", e.getMessage(), e);
        }
        return items;
    }

    public int parseAndAutoMatchCsv(int accountId, File csvFile, List<ReconciliationItemDTO> glItems) {
        if (csvFile == null || !csvFile.exists()) return 0;
        int matchedCount = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
            String line;
            boolean headerSkipped = false;
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split(",");
                if (!headerSkipped) {
                    headerSkipped = true;
                    if (line.toLowerCase().contains("date")) continue;
                }
                if (parts.length < 2) continue;

                LocalDate transDate = null;
                try {
                    transDate = LocalDate.parse(parts[0].trim(), dtf);
                } catch (Exception ignored) {
                    continue;
                }

                BigDecimal amt = ZERO;
                try {
                    amt = new BigDecimal(parts[parts.length - 1].trim().replace("$", "").replace(",", "")).abs();
                } catch (Exception ignored) {
                    continue;
                }

                for (ReconciliationItemDTO item : glItems) {
                    if (!item.isCleared() && item.getAmount().compareTo(amt) == 0) {
                        if (item.getEntryDate() != null && Math.abs(item.getEntryDate().toEpochDay() - transDate.toEpochDay()) <= 7) {
                            item.setCleared(true);
                            matchedCount++;
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to parse CSV statement: {}", e.getMessage(), e);
        }
        return matchedCount;
    }

    public BankReconciliation finalizeReconciliation(BankReconciliation recon, List<ReconciliationItemDTO> clearedItems) {
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            conn.setAutoCommit(false);

            String insertRecon = """
                INSERT INTO bank_reconciliations
                    (account_id, statement_date, beginning_balance, statement_ending_balance,
                     cleared_deposits_count, cleared_deposits_amount, cleared_checks_count, cleared_checks_amount,
                     cleared_balance, difference, status, notes, reconciled_at, reconciled_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'RECONCILED', ?, CURRENT_TIMESTAMP, ?)
                RETURNING id
                """;

            int userId = UserSession.getInstance().getCurrentUser() != null ? UserSession.getInstance().getCurrentUser().getId() : 1;

            try (PreparedStatement ps = conn.prepareStatement(insertRecon)) {
                ps.setInt(1, recon.getAccountId());
                ps.setDate(2, Date.valueOf(recon.getStatementDate()));
                ps.setBigDecimal(3, recon.getBeginningBalance());
                ps.setBigDecimal(4, recon.getStatementEndingBalance());
                ps.setInt(5, recon.getClearedDepositsCount());
                ps.setBigDecimal(6, recon.getClearedDepositsAmount());
                ps.setInt(7, recon.getClearedChecksCount());
                ps.setBigDecimal(8, recon.getClearedChecksAmount());
                ps.setBigDecimal(9, recon.getClearedBalance());
                ps.setBigDecimal(10, recon.getDifference());
                ps.setString(11, recon.getNotes());
                ps.setInt(12, userId);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) recon.setId(rs.getInt(1));
                }
            }

            String updateLine = """
                UPDATE journal_entry_lines
                SET cleared_status = TRUE, cleared_date = ?, bank_reconciliation_id = ?
                WHERE id = ?
                """;
            try (PreparedStatement ps = conn.prepareStatement(updateLine)) {
                for (ReconciliationItemDTO item : clearedItems) {
                    ps.setDate(1, Date.valueOf(recon.getStatementDate()));
                    ps.setInt(2, recon.getId());
                    ps.setInt(3, item.getJournalEntryLineId());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            conn.commit();
            recon.setStatus("RECONCILED");

            AuditLogService.log("RECONCILE_BANK", "BANK_RECONCILIATION", recon.getId(),
                    "Reconciled Bank Account ID " + recon.getAccountId() + " on " + recon.getStatementDate());

            return recon;
        } catch (Exception e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ignored) {}
            logger.error("Failed to finalize bank reconciliation: {}", e.getMessage(), e);
            throw new RuntimeException("Reconciliation failed: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
    }
}
