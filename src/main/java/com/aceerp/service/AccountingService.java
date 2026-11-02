package com.aceerp.service;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.Account;
import com.aceerp.model.JournalEntry;
import com.aceerp.model.JournalEntryLine;
import com.aceerp.model.UserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Double-entry General Ledger engine.
 * All postings MUST balance: SUM(debit) == SUM(credit).
 */
public class AccountingService {

    private static final Logger logger = LoggerFactory.getLogger(AccountingService.class);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    // Well-known system account codes
    public static final String ACC_CASH        = "1010";
    public static final String ACC_BANK        = "1020";
    public static final String ACC_AR          = "1200";
    public static final String ACC_INVENTORY   = "1300";
    public static final String ACC_AP          = "2100";
    public static final String ACC_TAX_PAYABLE = "2200";
    public static final String ACC_SALES       = "4000";
    public static final String ACC_SERVICE_REV = "4100";
    public static final String ACC_COGS        = "5000";
    public static final String ACC_PURCHASE    = "5100";

    // -------------------------------------------------------------------------
    // Chart of Accounts
    // -------------------------------------------------------------------------

    public List<Account> listAllAccounts() {
        return getAllAccounts();
    }

    public List<Account> getAllAccounts() {
        List<Account> list = new ArrayList<>();
        String sql = """
            SELECT id, code, name, account_type, balance, parent_id,
                   COALESCE(normal_balance,
                     CASE WHEN account_type IN ('LIABILITY','EQUITY','REVENUE')
                          THEN 'CREDIT' ELSE 'DEBIT' END) AS normal_balance,
                   COALESCE(is_system, FALSE) AS is_system,
                   COALESCE(is_active, TRUE) AS is_active,
                   description
            FROM accounts
            ORDER BY code ASC
            """;
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapAccount(rs));
            }
        } catch (SQLException e) {
            logger.error("Failed to list accounts: {}", e.getMessage(), e);
        }
        return list;
    }

    public Account findAccountByCode(String code) {
        if (code == null || code.isBlank()) return null;
        String sql = """
            SELECT id, code, name, account_type, balance, parent_id,
                   COALESCE(normal_balance,
                     CASE WHEN account_type IN ('LIABILITY','EQUITY','REVENUE')
                          THEN 'CREDIT' ELSE 'DEBIT' END) AS normal_balance,
                   COALESCE(is_system, FALSE) AS is_system,
                   COALESCE(is_active, TRUE) AS is_active,
                   description
            FROM accounts WHERE code = ?
            """;
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapAccount(rs);
            }
        } catch (SQLException e) {
            logger.error("Failed to find account {}: {}", code, e.getMessage(), e);
        }
        return null;
    }

    public Integer findAccountIdByCode(Connection conn, String code) throws SQLException {
        String sql = "SELECT id FROM accounts WHERE code = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        }
        return null;
    }

    private Account mapAccount(ResultSet rs) throws SQLException {
        Account acc = new Account();
        acc.setId(rs.getInt("id"));
        acc.setAccountCode(rs.getString("code"));
        acc.setAccountName(rs.getString("name"));
        acc.setAccountType(rs.getString("account_type"));
        acc.setCurrentBalance(rs.getBigDecimal("balance"));
        int pid = rs.getInt("parent_id");
        if (!rs.wasNull()) acc.setParentId(pid);
        acc.setNormalBalance(rs.getString("normal_balance"));
        acc.setSystemAccount(rs.getBoolean("is_system"));
        acc.setActive(rs.getBoolean("is_active"));
        acc.setDescription(rs.getString("description"));
        return acc;
    }

    // -------------------------------------------------------------------------
    // Journal Entry queries
    // -------------------------------------------------------------------------

    public List<JournalEntry> listJournalEntries() {
        return getJournalEntries();
    }

    public List<JournalEntry> getJournalEntries() {
        List<JournalEntry> list = new ArrayList<>();
        String sql = """
            SELECT je.id, je.entry_number, je.entry_date,
                   je.reference_type, je.reference_id, je.memo, je.status,
                   COALESCE(je.total_debit, 0) AS total_debit,
                   COALESCE(je.total_credit, 0) AS total_credit,
                   COALESCE(je.is_auto, FALSE) AS is_auto
            FROM journal_entries je
            ORDER BY je.entry_date DESC, je.id DESC
            """;
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                JournalEntry entry = new JournalEntry();
                entry.setId(rs.getInt("id"));
                entry.setEntryNumber(rs.getString("entry_number"));
                Date d = rs.getDate("entry_date");
                if (d != null) entry.setEntryDate(d.toLocalDate());
                String refType = rs.getString("reference_type");
                Integer refId = rs.getObject("reference_id") != null ? rs.getInt("reference_id") : null;
                entry.setReferenceType(refType);
                entry.setReferenceId(refId);
                entry.setReference(formatReference(refType, refId));
                entry.setDescription(rs.getString("memo"));
                entry.setStatus(rs.getString("status"));
                entry.setTotalDebit(rs.getBigDecimal("total_debit"));
                entry.setTotalCredit(rs.getBigDecimal("total_credit"));
                entry.setTotalAmount(entry.getTotalDebit());
                entry.setAutoPosted(rs.getBoolean("is_auto"));
                list.add(entry);
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
            logger.error("Failed to load journal lines: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    public List<JournalEntryLine> getLinesForEntry(Connection conn, int entryId) {
        List<JournalEntryLine> list = new ArrayList<>();
        String sql = """
            SELECT jel.id, jel.journal_entry_id, jel.account_id, jel.description,
                   jel.debit, jel.credit, a.code AS acc_code, a.name AS acc_name
            FROM journal_entry_lines jel
            LEFT JOIN accounts a ON jel.account_id = a.id
            WHERE jel.journal_entry_id = ?
            ORDER BY jel.line_no ASC, jel.id ASC
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, entryId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JournalEntryLine line = new JournalEntryLine();
                    line.setId(rs.getInt("id"));
                    line.setJournalEntryId(rs.getInt("journal_entry_id"));
                    line.setAccountId(rs.getInt("account_id"));
                    line.setDescription(rs.getString("description"));
                    line.setDebit(nz(rs.getBigDecimal("debit")));
                    line.setCredit(nz(rs.getBigDecimal("credit")));
                    line.setAccountCode(rs.getString("acc_code"));
                    line.setAccountName(rs.getString("acc_name"));
                    list.add(line);
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to get lines for entry {}: {}", entryId, e.getMessage(), e);
        }
        return list;
    }

    // -------------------------------------------------------------------------
    // CORE: Post balanced journal entry
    // -------------------------------------------------------------------------

    /**
     * Posts a journal entry. Throws IllegalArgumentException if unbalanced or invalid.
     * Updates account balances atomically.
     */
    public JournalEntry postJournalEntry(JournalEntry entry) {
        if (entry == null) throw new IllegalArgumentException("Journal entry is required.");
        if (entry.getLines() == null || entry.getLines().size() < 2) {
            throw new IllegalArgumentException("A journal entry needs at least two lines.");
        }

        entry.recalculateTotals();
        if (!entry.isBalanced()) {
            throw new IllegalArgumentException(String.format(
                    "Entry is not balanced. Debits %s != Credits %s",
                    entry.getTotalDebit(), entry.getTotalCredit()));
        }

        // Validate each line
        for (JournalEntryLine line : entry.getLines()) {
            if (line.getAccountId() <= 0) {
                throw new IllegalArgumentException("Every line must have an account.");
            }
            BigDecimal dr = nz(line.getDebit());
            BigDecimal cr = nz(line.getCredit());
            if (dr.compareTo(ZERO) < 0 || cr.compareTo(ZERO) < 0) {
                throw new IllegalArgumentException("Debit/Credit cannot be negative.");
            }
            if (dr.compareTo(ZERO) > 0 && cr.compareTo(ZERO) > 0) {
                throw new IllegalArgumentException("A line cannot have both debit and credit.");
            }
            if (dr.compareTo(ZERO) == 0 && cr.compareTo(ZERO) == 0) {
                throw new IllegalArgumentException("A line must have either a debit or a credit.");
            }
        }

        if (entry.getEntryDate() == null) entry.setEntryDate(LocalDate.now());
        if (entry.getStatus() == null || entry.getStatus().isBlank()) entry.setStatus("POSTED");

        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            conn.setAutoCommit(false);

            // Period lock check
            assertPeriodOpen(conn, entry.getEntryDate());

            if (entry.getEntryNumber() == null || entry.getEntryNumber().isBlank()) {
                entry.setEntryNumber(generateNextEntryNumber(conn));
            }

            if (UserSession.getInstance().getCurrentUser() != null) {
                entry.setCreatedBy(UserSession.getInstance().getCurrentUser().getId());
            }

            // Insert header
            String insertJe = """
                INSERT INTO journal_entries
                    (entry_number, entry_date, reference_type, reference_id, memo, status,
                     total_debit, total_credit, is_auto, posted_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?)
                RETURNING id
                """;
            try (PreparedStatement ps = conn.prepareStatement(insertJe)) {
                ps.setString(1, entry.getEntryNumber());
                ps.setDate(2, Date.valueOf(entry.getEntryDate()));
                ps.setString(3, entry.getReferenceType());
                if (entry.getReferenceId() != null && entry.getReferenceId() > 0) {
                    ps.setInt(4, entry.getReferenceId());
                } else {
                    ps.setNull(4, Types.INTEGER);
                }
                ps.setString(5, entry.getDescription());
                ps.setString(6, entry.getStatus());
                ps.setBigDecimal(7, entry.getTotalDebit());
                ps.setBigDecimal(8, entry.getTotalCredit());
                ps.setBoolean(9, entry.isAutoPosted());
                if (entry.getCreatedBy() != null && entry.getCreatedBy() > 0) {
                    ps.setInt(10, entry.getCreatedBy());
                } else {
                    ps.setNull(10, Types.INTEGER);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) entry.setId(rs.getInt(1));
                }
            }

            // Insert lines + update balances
            String insertLine = """
                INSERT INTO journal_entry_lines
                    (journal_entry_id, account_id, debit, credit, description, line_no)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
            int lineNo = 1;
            try (PreparedStatement ps = conn.prepareStatement(insertLine)) {
                for (JournalEntryLine line : entry.getLines()) {
                    ps.setInt(1, entry.getId());
                    ps.setInt(2, line.getAccountId());
                    ps.setBigDecimal(3, nz(line.getDebit()));
                    ps.setBigDecimal(4, nz(line.getCredit()));
                    ps.setString(5, line.getDescription());
                    ps.setInt(6, lineNo++);
                    ps.addBatch();

                    applyBalanceDelta(conn, line.getAccountId(), nz(line.getDebit()), nz(line.getCredit()));
                }
                ps.executeBatch();
            }

            conn.commit();
            logger.info("Posted JE {} DR={} CR={}", entry.getEntryNumber(),
                    entry.getTotalDebit(), entry.getTotalCredit());

            // Audit
            try {
                AuditLogService.log("POST_JOURNAL", "JOURNAL_ENTRY", entry.getId(),
                        "Posted " + entry.getEntryNumber() + " — " +
                                (entry.getDescription() != null ? entry.getDescription() : ""));
            } catch (Exception auditEx) {
                logger.warn("Audit log failed for JE: {}", auditEx.getMessage());
            }

            return entry;
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ignored) {}
            logger.error("Failed to post journal entry: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to post journal entry: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
            }
        }
    }

    /**
     * Apply debit/credit to the running account balance based on account type.
     * Asset/Expense: balance += DR - CR
     * Liability/Equity/Revenue: balance += CR - DR
     */
    private void applyBalanceDelta(Connection conn, int accountId, BigDecimal debit, BigDecimal credit)
            throws SQLException {
        String typeSql = "SELECT account_type FROM accounts WHERE id = ?";
        String accountType;
        try (PreparedStatement ps = conn.prepareStatement(typeSql)) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Account not found: id=" + accountId);
                accountType = rs.getString(1);
            }
        }

        BigDecimal delta;
        if ("ASSET".equalsIgnoreCase(accountType) || "EXPENSE".equalsIgnoreCase(accountType)) {
            delta = debit.subtract(credit);
        } else {
            delta = credit.subtract(debit);
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE accounts SET balance = COALESCE(balance,0) + ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?")) {
            ps.setBigDecimal(1, delta);
            ps.setInt(2, accountId);
            ps.executeUpdate();
        }
    }

    private void assertPeriodOpen(Connection conn, LocalDate date) throws SQLException {
        String sql = """
            SELECT is_closed FROM fiscal_periods
            WHERE start_date <= ? AND end_date >= ?
            ORDER BY start_date DESC LIMIT 1
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(date));
            ps.setDate(2, Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getBoolean("is_closed")) {
                    throw new SQLException("Cannot post: fiscal period for " + date + " is closed.");
                }
            }
        }
    }

    public String generateNextEntryNumber(Connection conn) throws SQLException {
        String sql = "SELECT entry_number FROM journal_entries ORDER BY id DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String last = rs.getString(1);
                if (last != null && last.startsWith("JV-")) {
                    try {
                        int num = Integer.parseInt(last.substring(3)) + 1;
                        return String.format("JV-%06d", num);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return "JV-000001";
    }

    // -------------------------------------------------------------------------
    // AUTO-POST helpers (call from Invoice/Bill/Payment services)
    // -------------------------------------------------------------------------

    /** Customer Invoice: DR AR | CR Sales (+ CR Tax if any) */
    public void postCustomerInvoice(int invoiceId, String invoiceNumber, LocalDate date,
                                    BigDecimal subtotal, BigDecimal taxAmount, BigDecimal total) {
        try {
            JournalEntry je = new JournalEntry();
            je.setEntryDate(date != null ? date : LocalDate.now());
            je.setReferenceType("INVOICE");
            je.setReferenceId(invoiceId);
            je.setReference(invoiceNumber);
            je.setDescription("Auto-post Invoice " + invoiceNumber);
            je.setAutoPosted(true);
            je.setStatus("POSTED");

            try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
                Integer arId = findAccountIdByCode(conn, ACC_AR);
                Integer salesId = findAccountIdByCode(conn, ACC_SALES);
                Integer taxId = findAccountIdByCode(conn, ACC_TAX_PAYABLE);
                if (arId == null || salesId == null) {
                    logger.warn("COA missing AR/Sales — skip auto-post for {}", invoiceNumber);
                    return;
                }

                List<JournalEntryLine> lines = new ArrayList<>();
                lines.add(line(arId, total, ZERO, "AR — " + invoiceNumber));
                lines.add(line(salesId, ZERO, nz(subtotal), "Sales — " + invoiceNumber));
                if (taxAmount != null && taxAmount.compareTo(ZERO) > 0 && taxId != null) {
                    lines.add(line(taxId, ZERO, nz(taxAmount), "Tax — " + invoiceNumber));
                }
                je.setLines(lines);
            }
            postJournalEntry(je);
        } catch (Exception e) {
            logger.error("Auto-post invoice {} failed: {}", invoiceNumber, e.getMessage(), e);
        }
    }

    /** Customer Payment: DR Bank/Cash | CR AR */
    public void postCustomerPayment(int paymentId, String paymentRef, LocalDate date,
                                    BigDecimal amount, boolean toBank) {
        try {
            JournalEntry je = new JournalEntry();
            je.setEntryDate(date != null ? date : LocalDate.now());
            je.setReferenceType("CUSTOMER_PAYMENT");
            je.setReferenceId(paymentId);
            je.setDescription("Auto-post Customer Payment " + paymentRef);
            je.setAutoPosted(true);
            je.setStatus("POSTED");

            try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
                Integer cashId = findAccountIdByCode(conn, toBank ? ACC_BANK : ACC_CASH);
                Integer arId = findAccountIdByCode(conn, ACC_AR);
                if (cashId == null || arId == null) {
                    logger.warn("COA missing Cash/AR — skip payment auto-post");
                    return;
                }
                List<JournalEntryLine> lines = new ArrayList<>();
                lines.add(line(cashId, nz(amount), ZERO, "Receipt " + paymentRef));
                lines.add(line(arId, ZERO, nz(amount), "AR clearance " + paymentRef));
                je.setLines(lines);
            }
            postJournalEntry(je);
        } catch (Exception e) {
            logger.error("Auto-post payment failed: {}", e.getMessage(), e);
        }
    }

    /** Supplier Bill: DR Expense/COGS (+ DR Tax) | CR AP */
    public void postSupplierBill(int billId, String billNumber, LocalDate date,
                                 BigDecimal subtotal, BigDecimal taxAmount, BigDecimal total,
                                 boolean inventoryRelated) {
        try {
            JournalEntry je = new JournalEntry();
            je.setEntryDate(date != null ? date : LocalDate.now());
            je.setReferenceType("SUPPLIER_BILL");
            je.setReferenceId(billId);
            je.setDescription("Auto-post Supplier Bill " + billNumber);
            je.setAutoPosted(true);
            je.setStatus("POSTED");

            try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
                Integer apId = findAccountIdByCode(conn, ACC_AP);
                Integer expId = findAccountIdByCode(conn, inventoryRelated ? ACC_INVENTORY : ACC_PURCHASE);
                Integer taxId = findAccountIdByCode(conn, ACC_TAX_PAYABLE);
                if (apId == null || expId == null) {
                    logger.warn("COA missing AP/Expense — skip bill auto-post");
                    return;
                }
                List<JournalEntryLine> lines = new ArrayList<>();
                lines.add(line(expId, nz(subtotal), ZERO, "Bill " + billNumber));
                if (taxAmount != null && taxAmount.compareTo(ZERO) > 0 && taxId != null) {
                    // Input tax recoverable — treat as asset-like reduction of tax liability (DR tax)
                    lines.add(line(taxId, nz(taxAmount), ZERO, "Input tax " + billNumber));
                }
                lines.add(line(apId, ZERO, nz(total), "AP — " + billNumber));
                je.setLines(lines);
            }
            postJournalEntry(je);
        } catch (Exception e) {
            logger.error("Auto-post bill {} failed: {}", billNumber, e.getMessage(), e);
        }
    }

    /** Supplier Payment: DR AP | CR Bank */
    public void postSupplierPayment(int paymentId, String paymentRef, LocalDate date, BigDecimal amount) {
        try {
            JournalEntry je = new JournalEntry();
            je.setEntryDate(date != null ? date : LocalDate.now());
            je.setReferenceType("SUPPLIER_PAYMENT");
            je.setReferenceId(paymentId);
            je.setDescription("Auto-post Supplier Payment " + paymentRef);
            je.setAutoPosted(true);
            je.setStatus("POSTED");

            try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
                Integer apId = findAccountIdByCode(conn, ACC_AP);
                Integer bankId = findAccountIdByCode(conn, ACC_BANK);
                if (apId == null || bankId == null) {
                    logger.warn("COA missing AP/Bank — skip supplier payment auto-post");
                    return;
                }
                List<JournalEntryLine> lines = new ArrayList<>();
                lines.add(line(apId, nz(amount), ZERO, "Pay AP " + paymentRef));
                lines.add(line(bankId, ZERO, nz(amount), "Bank out " + paymentRef));
                je.setLines(lines);
            }
            postJournalEntry(je);
        } catch (Exception e) {
            logger.error("Auto-post supplier payment failed: {}", e.getMessage(), e);
        }
    }

    private JournalEntryLine line(int accountId, BigDecimal debit, BigDecimal credit, String desc) {
        JournalEntryLine l = new JournalEntryLine();
        l.setAccountId(accountId);
        l.setDebit(nz(debit));
        l.setCredit(nz(credit));
        l.setDescription(desc);
        return l;
    }

    // -------------------------------------------------------------------------
    // Trial Balance
    // -------------------------------------------------------------------------

    public List<TrialBalanceRow> getTrialBalance() {
        List<TrialBalanceRow> rows = new ArrayList<>();
        String sql = """
            SELECT code, name, account_type,
                   COALESCE(normal_balance,
                     CASE WHEN account_type IN ('LIABILITY','EQUITY','REVENUE')
                          THEN 'CREDIT' ELSE 'DEBIT' END) AS normal_balance,
                   COALESCE(balance, 0) AS balance
            FROM accounts
            WHERE COALESCE(is_active, TRUE) = TRUE
            ORDER BY code
            """;
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                BigDecimal bal = nz(rs.getBigDecimal("balance"));
                if (bal.compareTo(ZERO) == 0) continue; // skip zero rows
                String type = rs.getString("account_type");
                boolean debitNormal = "ASSET".equalsIgnoreCase(type) || "EXPENSE".equalsIgnoreCase(type);

                BigDecimal dr = ZERO;
                BigDecimal cr = ZERO;
                if (debitNormal) {
                    if (bal.compareTo(ZERO) >= 0) dr = bal; else cr = bal.abs();
                } else {
                    if (bal.compareTo(ZERO) >= 0) cr = bal; else dr = bal.abs();
                }
                rows.add(new TrialBalanceRow(
                        rs.getString("code"),
                        rs.getString("name"),
                        type,
                        dr,
                        cr
                ));
            }
        } catch (SQLException e) {
            logger.error("Trial balance failed: {}", e.getMessage(), e);
        }
        return rows;
    }

    /** Simple DTO for Trial Balance grid. */
    public record TrialBalanceRow(
            String accountCode,
            String accountName,
            String accountType,
            BigDecimal debit,
            BigDecimal credit
    ) {}

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? ZERO : v.setScale(2, RoundingMode.HALF_UP);
    }

    private static String formatReference(String type, Integer id) {
        if (type == null) return "";
        return id != null ? type + " #" + id : type;
    }
}