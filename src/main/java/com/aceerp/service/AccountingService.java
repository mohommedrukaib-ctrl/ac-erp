package com.aceerp.service;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.Account;
import com.aceerp.model.JournalEntry;
import com.aceerp.model.JournalEntryLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class AccountingService {

    private static final Logger logger = LoggerFactory.getLogger(AccountingService.class);

    private static Boolean hasAccountCode;
    private static Boolean hasAccountName;
    private static Boolean hasAccountType;
    private static Boolean hasCurrentBalance;

    private static Boolean hasJeReference;
    private static Boolean hasJeReferenceId;
    private static Boolean hasJeDescription;
    private static Boolean hasJeMemo;

    private static boolean checkColumnExists(Connection conn, String tableName, String columnName) {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, tableName, columnName)) {
            if (rs.next()) return true;
        } catch (Exception ignored) {}
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, tableName.toUpperCase(), columnName.toUpperCase())) {
            if (rs.next()) return true;
        } catch (Exception ignored) {}
        return false;
    }

    public List<Account> listAllAccounts() {
        return getAllAccounts();
    }

    public List<Account> getAllAccounts() {
        List<Account> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            if (hasAccountCode == null) {
                hasAccountCode = checkColumnExists(conn, "accounts", "account_code");
            }
            if (hasAccountName == null) {
                hasAccountName = checkColumnExists(conn, "accounts", "account_name");
            }
            if (hasAccountType == null) {
                hasAccountType = checkColumnExists(conn, "accounts", "account_type");
            }
            if (hasCurrentBalance == null) {
                hasCurrentBalance = checkColumnExists(conn, "accounts", "current_balance");
            }

            String codeCol = Boolean.TRUE.equals(hasAccountCode) ? "account_code" : "code";
            String nameCol = Boolean.TRUE.equals(hasAccountName) ? "account_name" : "name";
            String typeCol = Boolean.TRUE.equals(hasAccountType) ? "account_type" : "type";
            String balCol = Boolean.TRUE.equals(hasCurrentBalance) ? "current_balance" : "balance";

            String sql = String.format("SELECT id, %s AS code, %s AS name, %s AS type, %s AS balance FROM accounts ORDER BY %s ASC",
                    codeCol, nameCol, typeCol, balCol, codeCol);

            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Account acc = new Account();
                    acc.setId(rs.getInt("id"));
                    acc.setAccountCode(rs.getString("code"));
                    acc.setAccountName(rs.getString("name"));
                    acc.setAccountType(rs.getString("type"));
                    acc.setCurrentBalance(rs.getBigDecimal("balance"));
                    list.add(acc);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to list accounts: {}", e.getMessage(), e);
        }
        return list;
    }

    public List<JournalEntry> listJournalEntries() {
        return getJournalEntries();
    }

    public List<JournalEntry> getJournalEntries() {
        List<JournalEntry> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            if (hasJeReference == null) {
                hasJeReference = checkColumnExists(conn, "journal_entries", "reference");
            }
            if (hasJeReferenceId == null) {
                hasJeReferenceId = checkColumnExists(conn, "journal_entries", "reference_id");
            }
            if (hasJeDescription == null) {
                hasJeDescription = checkColumnExists(conn, "journal_entries", "description");
            }
            if (hasJeMemo == null) {
                hasJeMemo = checkColumnExists(conn, "journal_entries", "memo");
            }

            String refCol = Boolean.TRUE.equals(hasJeReference) ? "reference" :
                    (Boolean.TRUE.equals(hasJeReferenceId) ? "reference_id" : "ref");
            String descCol = Boolean.TRUE.equals(hasJeDescription) ? "description" :
                    (Boolean.TRUE.equals(hasJeMemo) ? "memo" : "notes");

            String sql = String.format("""
                SELECT je.id, je.entry_number, je.entry_date, je.%s AS ref_val, je.%s AS desc_val, je.status,
                       COALESCE(SUM(jel.debit), 0) AS total_amount
                FROM journal_entries je
                LEFT JOIN journal_entry_lines jel ON je.id = jel.journal_entry_id
                GROUP BY je.id, je.entry_number, je.entry_date, je.%s, je.%s, je.status
                ORDER BY je.entry_date DESC, je.id DESC
                """, refCol, descCol, refCol, descCol);

            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JournalEntry entry = new JournalEntry();
                    entry.setId(rs.getInt("id"));
                    entry.setEntryNumber(rs.getString("entry_number"));
                    Date date = rs.getDate("entry_date");
                    if (date != null) {
                        entry.setEntryDate(date.toLocalDate());
                    }
                    entry.setReference(rs.getString("ref_val"));
                    entry.setDescription(rs.getString("desc_val"));
                    entry.setStatus(rs.getString("status"));
                    entry.setTotalAmount(rs.getBigDecimal("total_amount"));
                    list.add(entry);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to list journal entries: {}", e.getMessage(), e);
        }
        return list;
    }

    public List<JournalEntryLine> getLinesForEntry(int entryId) {
        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            return getLinesForEntry(conn, entryId);
        } catch (SQLException e) {
            logger.error("Failed to acquire DB connection for journal lines: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    public List<JournalEntryLine> getLinesForEntry(Connection conn, int entryId) {
        List<JournalEntryLine> list = new ArrayList<>();
        try {
            if (hasAccountCode == null) {
                hasAccountCode = checkColumnExists(conn, "accounts", "account_code");
            }
            if (hasAccountName == null) {
                hasAccountName = checkColumnExists(conn, "accounts", "account_name");
            }

            String codeCol = Boolean.TRUE.equals(hasAccountCode) ? "account_code" : "code";
            String nameCol = Boolean.TRUE.equals(hasAccountName) ? "account_name" : "name";

            String sql = String.format("""
                SELECT jel.id, jel.journal_entry_id, jel.account_id, jel.description, jel.debit, jel.credit,
                       a.%s AS acc_code, a.%s AS acc_name
                FROM journal_entry_lines jel
                LEFT JOIN accounts a ON jel.account_id = a.id
                WHERE jel.journal_entry_id = ?
                ORDER BY jel.id ASC
                """, codeCol, nameCol);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, entryId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        JournalEntryLine line = new JournalEntryLine();
                        line.setId(rs.getInt("id"));
                        line.setJournalEntryId(rs.getInt("journal_entry_id"));
                        line.setAccountId(rs.getInt("account_id"));
                        line.setDescription(rs.getString("description"));
                        line.setDebit(rs.getBigDecimal("debit"));
                        line.setCredit(rs.getBigDecimal("credit"));
                        line.setAccountCode(rs.getString("acc_code"));
                        line.setAccountName(rs.getString("acc_name"));
                        list.add(line);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get lines for entry {}: {}", entryId, e.getMessage(), e);
        }
        return list;
    }
}