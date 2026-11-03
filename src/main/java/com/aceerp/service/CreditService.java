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

public class CreditService {

    private static final Logger logger = LoggerFactory.getLogger(CreditService.class);
    private final AccountingService accountingService = new AccountingService();

    // -------------------------------------------------------------------------
    // CUSTOMER CREDIT MEMOS
    // -------------------------------------------------------------------------

    public CustomerCreditMemo saveCustomerCreditMemo(CustomerCreditMemo memo, int userId) {
        if (memo == null || memo.getCustomerId() <= 0) {
            throw new IllegalArgumentException("Customer is required for Credit Memo.");
        }
        memo.recalculateTotals();

        if (memo.getCreditMemoNumber() == null || memo.getCreditMemoNumber().isBlank()) {
            memo.setCreditMemoNumber(generateNextCreditMemoNumber());
        }

        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            conn.setAutoCommit(false);

            if (memo.getId() <= 0) {
                memo.setRemainingAmount(memo.getTotalAmount());
                String sql = """
                    INSERT INTO customer_credit_memos
                        (credit_memo_number, customer_id, issue_date, status, subtotal, tax_amount, total_amount, remaining_amount, memo, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    RETURNING id
                    """;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, memo.getCreditMemoNumber());
                    ps.setInt(2, memo.getCustomerId());
                    ps.setDate(3, Date.valueOf(memo.getIssueDate()));
                    ps.setString(4, memo.getStatus());
                    ps.setBigDecimal(5, memo.getSubtotal());
                    ps.setBigDecimal(6, memo.getTaxAmount());
                    ps.setBigDecimal(7, memo.getTotalAmount());
                    ps.setBigDecimal(8, memo.getRemainingAmount());
                    ps.setString(9, memo.getMemo());
                    if (userId > 0) ps.setInt(10, userId); else ps.setNull(10, Types.INTEGER);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) memo.setId(rs.getInt(1));
                    }
                }
            }

            // Insert Lines
            String insertLine = """
                INSERT INTO credit_memo_lines
                    (credit_memo_id, product_id, description, quantity, unit_price, tax_rate, tax_amount, total_amount, line_no)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            int lineNo = 1;
            try (PreparedStatement ps = conn.prepareStatement(insertLine)) {
                for (CreditMemoLine line : memo.getLines()) {
                    ps.setInt(1, memo.getId());
                    if (line.getProductId() != null && line.getProductId() > 0) {
                        ps.setInt(2, line.getProductId());
                    } else ps.setNull(2, Types.INTEGER);
                    ps.setString(3, line.getDescription());
                    ps.setBigDecimal(4, line.getQuantity());
                    ps.setBigDecimal(5, line.getUnitPrice());
                    ps.setBigDecimal(6, line.getTaxRate());
                    ps.setBigDecimal(7, line.getTaxAmount());
                    ps.setBigDecimal(8, line.getTotalAmount());
                    ps.setInt(9, lineNo++);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // Post GL Entry: DR 4200 (Sales Returns) + DR 2200 (Tax) / CR 1200 (AR)
            Integer arId = accountingService.findAccountIdByCode(conn, AccountingService.ACC_AR);
            Integer salesRetId = accountingService.findAccountIdByCode(conn, "4200");
            Integer taxId = accountingService.findAccountIdByCode(conn, AccountingService.ACC_TAX_PAYABLE);

            if (salesRetId == null) salesRetId = accountingService.findAccountIdByCode(conn, AccountingService.ACC_SALES);

            if (arId != null && salesRetId != null) {
                JournalEntry je = new JournalEntry();
                je.setEntryDate(memo.getIssueDate());
                je.setReferenceType("CREDIT_MEMO");
                je.setReferenceId(memo.getId());
                je.setDescription("Credit Memo " + memo.getCreditMemoNumber());
                je.setAutoPosted(true);

                List<JournalEntryLine> jeLines = new ArrayList<>();
                // DR Sales Returns
                JournalEntryLine drRet = new JournalEntryLine();
                drRet.setAccountId(salesRetId);
                drRet.setDebit(memo.getSubtotal());
                drRet.setCredit(BigDecimal.ZERO);
                drRet.setDescription("Sales Return " + memo.getCreditMemoNumber());
                jeLines.add(drRet);

                // DR Tax
                if (memo.getTaxAmount().compareTo(BigDecimal.ZERO) > 0 && taxId != null) {
                    JournalEntryLine drTax = new JournalEntryLine();
                    drTax.setAccountId(taxId);
                    drTax.setDebit(memo.getTaxAmount());
                    drTax.setCredit(BigDecimal.ZERO);
                    drTax.setDescription("Tax Credit " + memo.getCreditMemoNumber());
                    jeLines.add(drTax);
                }

                // CR Accounts Receivable
                JournalEntryLine crAr = new JournalEntryLine();
                crAr.setAccountId(arId);
                crAr.setDebit(BigDecimal.ZERO);
                crAr.setCredit(memo.getTotalAmount());
                crAr.setDescription("AR Credit Memo " + memo.getCreditMemoNumber());
                jeLines.add(crAr);

                je.setLines(jeLines);
                JournalEntry postedJe = accountingService.postJournalEntry(je);
                if (postedJe != null) {
                    memo.setJournalEntryId(postedJe.getId());
                    try (PreparedStatement ps = conn.prepareStatement("UPDATE customer_credit_memos SET journal_entry_id = ? WHERE id = ?")) {
                        ps.setInt(1, postedJe.getId());
                        ps.setInt(2, memo.getId());
                        ps.executeUpdate();
                    }
                }
            }

            conn.commit();
            AuditLogService.log("CREATE_CREDIT_MEMO", "CUSTOMER_CREDIT_MEMO", memo.getId(), "Issued Credit Memo " + memo.getCreditMemoNumber());
            return memo;

        } catch (Exception e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ignored) {}
            logger.error("Failed to save Credit Memo: {}", e.getMessage(), e);
            throw new RuntimeException("Save Credit Memo failed: " + e.getMessage(), e);
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
        }
    }

    public void applyCreditMemoToInvoice(int creditMemoId, int invoiceId, BigDecimal applyAmount, int userId) {
        if (applyAmount == null || applyAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Applied amount must be greater than zero.");
        }

        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            conn.setAutoCommit(false);

            // Fetch remaining credit memo balance
            BigDecimal memoRem = BigDecimal.ZERO;
            try (PreparedStatement ps = conn.prepareStatement("SELECT remaining_amount FROM customer_credit_memos WHERE id = ? FOR UPDATE")) {
                ps.setInt(1, creditMemoId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) memoRem = rs.getBigDecimal(1);
                    else throw new IllegalArgumentException("Credit Memo not found.");
                }
            }

            if (applyAmount.compareTo(memoRem) > 0) {
                throw new IllegalArgumentException("Applied amount (" + applyAmount + ") exceeds remaining credit (" + memoRem + ").");
            }

            // Record application
            String insertApp = """
                INSERT INTO credit_applications
                    (credit_type, customer_credit_memo_id, invoice_id, applied_amount, applied_date, created_by)
                VALUES ('CUSTOMER', ?, ?, ?, CURRENT_DATE, ?)
                """;
            try (PreparedStatement ps = conn.prepareStatement(insertApp)) {
                ps.setInt(1, creditMemoId);
                ps.setInt(2, invoiceId);
                ps.setBigDecimal(3, applyAmount);
                if (userId > 0) ps.setInt(4, userId); else ps.setNull(4, Types.INTEGER);
                ps.executeUpdate();
            }

            // Deduct Credit Memo balance
            BigDecimal newMemoRem = memoRem.subtract(applyAmount);
            String newStatus = newMemoRem.compareTo(BigDecimal.ZERO) == 0 ? "FULLY_APPLIED" : "PARTIALLY_APPLIED";
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE customer_credit_memos SET remaining_amount = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?")) {
                ps.setBigDecimal(1, newMemoRem);
                ps.setString(2, newStatus);
                ps.setInt(3, creditMemoId);
                ps.executeUpdate();
            }

            // Knock down Invoice paid/status balance
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE invoices SET paid_amount = COALESCE(paid_amount,0) + ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?")) {
                ps.setBigDecimal(1, applyAmount);
                ps.setInt(2, invoiceId);
                ps.executeUpdate();
            }

            conn.commit();
            AuditLogService.log("APPLY_CREDIT_MEMO", "CREDIT_APPLICATION", creditMemoId, "Applied " + applyAmount + " credit to Invoice #" + invoiceId);

        } catch (Exception e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ignored) {}
            logger.error("Apply Credit Memo failed: {}", e.getMessage(), e);
            throw new RuntimeException("Apply Credit Memo failed: " + e.getMessage(), e);
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
        }
    }

    // -------------------------------------------------------------------------
    // VENDOR CREDITS
    // -------------------------------------------------------------------------

    public VendorCredit saveVendorCredit(VendorCredit credit, int userId) {
        if (credit == null || credit.getSupplierId() <= 0) {
            throw new IllegalArgumentException("Supplier is required for Vendor Credit.");
        }
        credit.recalculateTotals();

        if (credit.getVendorCreditNumber() == null || credit.getVendorCreditNumber().isBlank()) {
            credit.setVendorCreditNumber(generateNextVendorCreditNumber());
        }

        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            conn.setAutoCommit(false);

            if (credit.getId() <= 0) {
                credit.setRemainingAmount(credit.getTotalAmount());
                String sql = """
                    INSERT INTO vendor_credits
                        (vendor_credit_number, supplier_id, issue_date, status, subtotal, tax_amount, total_amount, remaining_amount, memo, created_by)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    RETURNING id
                    """;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, credit.getVendorCreditNumber());
                    ps.setInt(2, credit.getSupplierId());
                    ps.setDate(3, Date.valueOf(credit.getIssueDate()));
                    ps.setString(4, credit.getStatus());
                    ps.setBigDecimal(5, credit.getSubtotal());
                    ps.setBigDecimal(6, credit.getTaxAmount());
                    ps.setBigDecimal(7, credit.getTotalAmount());
                    ps.setBigDecimal(8, credit.getRemainingAmount());
                    ps.setString(9, credit.getMemo());
                    if (userId > 0) ps.setInt(10, userId); else ps.setNull(10, Types.INTEGER);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) credit.setId(rs.getInt(1));
                    }
                }
            }

            // Insert Lines
            String insertLine = """
                INSERT INTO vendor_credit_lines
                    (vendor_credit_id, product_id, account_id, description, quantity, unit_cost, tax_rate, tax_amount, total_amount, line_no)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
            int lineNo = 1;
            try (PreparedStatement ps = conn.prepareStatement(insertLine)) {
                for (VendorCreditLine line : credit.getLines()) {
                    ps.setInt(1, credit.getId());
                    if (line.getProductId() != null && line.getProductId() > 0) ps.setInt(2, line.getProductId()); else ps.setNull(2, Types.INTEGER);
                    if (line.getAccountId() != null && line.getAccountId() > 0) ps.setInt(3, line.getAccountId()); else ps.setNull(3, Types.INTEGER);
                    ps.setString(4, line.getDescription());
                    ps.setBigDecimal(5, line.getQuantity());
                    ps.setBigDecimal(6, line.getUnitCost());
                    ps.setBigDecimal(7, line.getTaxRate());
                    ps.setBigDecimal(8, line.getTaxAmount());
                    ps.setBigDecimal(9, line.getTotalAmount());
                    ps.setInt(10, lineNo++);
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // Post GL Entry: DR 2100 (AP) / CR 5200 (Purchase Returns) + CR 2200 (Tax)
            Integer apId = accountingService.findAccountIdByCode(conn, AccountingService.ACC_AP);
            Integer purRetId = accountingService.findAccountIdByCode(conn, "5200");
            Integer taxId = accountingService.findAccountIdByCode(conn, AccountingService.ACC_TAX_PAYABLE);

            if (purRetId == null) purRetId = accountingService.findAccountIdByCode(conn, AccountingService.ACC_PURCHASE);

            if (apId != null && purRetId != null) {
                JournalEntry je = new JournalEntry();
                je.setEntryDate(credit.getIssueDate());
                je.setReferenceType("VENDOR_CREDIT");
                je.setReferenceId(credit.getId());
                je.setDescription("Vendor Credit " + credit.getVendorCreditNumber());
                je.setAutoPosted(true);

                List<JournalEntryLine> jeLines = new ArrayList<>();

                // DR Accounts Payable
                JournalEntryLine drAp = new JournalEntryLine();
                drAp.setAccountId(apId);
                drAp.setDebit(credit.getTotalAmount());
                drAp.setCredit(BigDecimal.ZERO);
                drAp.setDescription("AP Vendor Credit " + credit.getVendorCreditNumber());
                jeLines.add(drAp);

                // CR Purchase Returns
                JournalEntryLine crRet = new JournalEntryLine();
                crRet.setAccountId(purRetId);
                crRet.setDebit(BigDecimal.ZERO);
                crRet.setCredit(credit.getSubtotal());
                crRet.setDescription("Purchase Return " + credit.getVendorCreditNumber());
                jeLines.add(crRet);

                // CR Tax
                if (credit.getTaxAmount().compareTo(BigDecimal.ZERO) > 0 && taxId != null) {
                    JournalEntryLine crTax = new JournalEntryLine();
                    crTax.setAccountId(taxId);
                    crTax.setDebit(BigDecimal.ZERO);
                    crTax.setCredit(credit.getTaxAmount());
                    crTax.setDescription("Tax Credit " + credit.getVendorCreditNumber());
                    jeLines.add(crTax);
                }

                je.setLines(jeLines);
                JournalEntry postedJe = accountingService.postJournalEntry(je);
                if (postedJe != null) {
                    credit.setJournalEntryId(postedJe.getId());
                    try (PreparedStatement ps = conn.prepareStatement("UPDATE vendor_credits SET journal_entry_id = ? WHERE id = ?")) {
                        ps.setInt(1, postedJe.getId());
                        ps.setInt(2, credit.getId());
                        ps.executeUpdate();
                    }
                }
            }

            conn.commit();
            AuditLogService.log("CREATE_VENDOR_CREDIT", "VENDOR_CREDIT", credit.getId(), "Issued Vendor Credit " + credit.getVendorCreditNumber());
            return credit;

        } catch (Exception e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ignored) {}
            logger.error("Failed to save Vendor Credit: {}", e.getMessage(), e);
            throw new RuntimeException("Save Vendor Credit failed: " + e.getMessage(), e);
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
        }
    }

    private String generateNextCreditMemoNumber() {
        String sql = "SELECT credit_memo_number FROM customer_credit_memos ORDER BY id DESC LIMIT 1";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String last = rs.getString(1);
                if (last != null && last.startsWith("CM-")) {
                    try {
                        int num = Integer.parseInt(last.substring(3)) + 1;
                        return String.format("CM-%05d", num);
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (SQLException ignored) {}
        return "CM-00001";
    }

    private String generateNextVendorCreditNumber() {
        String sql = "SELECT vendor_credit_number FROM vendor_credits ORDER BY id DESC LIMIT 1";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String last = rs.getString(1);
                if (last != null && last.startsWith("VC-")) {
                    try {
                        int num = Integer.parseInt(last.substring(3)) + 1;
                        return String.format("VC-%05d", num);
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (SQLException ignored) {}
        return "VC-00001";
    }
}