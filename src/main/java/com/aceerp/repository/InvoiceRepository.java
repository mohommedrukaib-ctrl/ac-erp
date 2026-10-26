package com.aceerp.repository;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.Invoice;
import com.aceerp.model.InvoiceLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InvoiceRepository {

    private static final Logger log = LoggerFactory.getLogger(InvoiceRepository.class);

    public List<Invoice> findAll() {
        List<Invoice> list = new ArrayList<>();
        String sql = "SELECT i.*, c.name as customer_name FROM invoices i " +
                     "LEFT JOIN customers c ON i.customer_id = c.id ORDER BY i.created_at DESC";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Invoice inv = mapRow(rs);
                inv.setLines(findLinesByInvoiceId(conn, inv.getId()));
                list.add(inv);
            }
        } catch (SQLException e) {
            log.error("Failed to load invoices", e);
        }
        return list;
    }

    public Optional<Invoice> findById(int id) {
        String sql = "SELECT i.*, c.name as customer_name FROM invoices i " +
                     "LEFT JOIN customers c ON i.customer_id = c.id WHERE i.id = ?";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Invoice inv = mapRow(rs);
                    inv.setLines(findLinesByInvoiceId(conn, inv.getId()));
                    return Optional.of(inv);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to find invoice {}", id, e);
        }
        return Optional.empty();
    }

    public Invoice save(Invoice invoice) {
        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (invoice.getId() == 0) {
                    insertInvoice(conn, invoice);
                } else {
                    updateInvoice(conn, invoice);
                }
                saveLines(conn, invoice);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                log.error("Failed transaction while saving invoice", e);
                throw e;
            }
        } catch (SQLException e) {
            log.error("Failed to save invoice", e);
        }
        return invoice;
    }

    private String sanitizeStatus(String status) {
        if (status == null || status.isBlank() || "UNPAID".equalsIgnoreCase(status)) {
            return "DRAFT";
        }
        return status.toUpperCase();
    }

    private void insertInvoice(Connection conn, Invoice inv) throws SQLException {
        String sql = "INSERT INTO invoices (invoice_number, customer_id, so_id, dn_id, invoice_date, due_date, " +
                     "status, subtotal, tax_amount, total_amount, tax_total, grand_total, paid_amount, notes, created_by) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, inv.getInvoiceNumber());
            ps.setInt(2, inv.getCustomerId());
            if (inv.getSoId() != null && inv.getSoId() > 0) ps.setInt(3, inv.getSoId()); else ps.setNull(3, Types.INTEGER);
            if (inv.getDnId() != null && inv.getDnId() > 0) ps.setInt(4, inv.getDnId()); else ps.setNull(4, Types.INTEGER);
            ps.setDate(5, Date.valueOf(inv.getIssueDate() != null ? inv.getIssueDate() : java.time.LocalDate.now()));
            ps.setDate(6, Date.valueOf(inv.getDueDate() != null ? inv.getDueDate() : java.time.LocalDate.now().plusDays(30)));
            ps.setString(7, sanitizeStatus(inv.getStatus()));
            ps.setBigDecimal(8, inv.getSubtotal() != null ? inv.getSubtotal() : BigDecimal.ZERO);
            ps.setBigDecimal(9, inv.getTaxTotal() != null ? inv.getTaxTotal() : BigDecimal.ZERO);
            ps.setBigDecimal(10, inv.getGrandTotal() != null ? inv.getGrandTotal() : BigDecimal.ZERO);
            ps.setBigDecimal(11, inv.getTaxTotal() != null ? inv.getTaxTotal() : BigDecimal.ZERO);
            ps.setBigDecimal(12, inv.getGrandTotal() != null ? inv.getGrandTotal() : BigDecimal.ZERO);
            ps.setBigDecimal(13, inv.getAmountPaid() != null ? inv.getAmountPaid() : BigDecimal.ZERO);
            ps.setString(14, inv.getNotes());
            if (inv.getCreatedBy() != 0) {
                ps.setInt(15, inv.getCreatedBy());
            } else {
                ps.setNull(15, Types.INTEGER);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) inv.setId(rs.getInt(1));
            }
        }
    }

    private void updateInvoice(Connection conn, Invoice inv) throws SQLException {
        String sql = "UPDATE invoices SET customer_id=?, so_id=?, dn_id=?, invoice_date=?, due_date=?, " +
                     "status=?, subtotal=?, tax_amount=?, total_amount=?, tax_total=?, grand_total=?, paid_amount=?, notes=?, updated_at=CURRENT_TIMESTAMP WHERE id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, inv.getCustomerId());
            if (inv.getSoId() != null && inv.getSoId() > 0) ps.setInt(2, inv.getSoId()); else ps.setNull(2, Types.INTEGER);
            if (inv.getDnId() != null && inv.getDnId() > 0) ps.setInt(3, inv.getDnId()); else ps.setNull(3, Types.INTEGER);
            ps.setDate(4, Date.valueOf(inv.getIssueDate() != null ? inv.getIssueDate() : java.time.LocalDate.now()));
            ps.setDate(5, Date.valueOf(inv.getDueDate() != null ? inv.getDueDate() : java.time.LocalDate.now().plusDays(30)));
            ps.setString(6, sanitizeStatus(inv.getStatus()));
            ps.setBigDecimal(7, inv.getSubtotal() != null ? inv.getSubtotal() : BigDecimal.ZERO);
            ps.setBigDecimal(8, inv.getTaxTotal() != null ? inv.getTaxTotal() : BigDecimal.ZERO);
            ps.setBigDecimal(9, inv.getGrandTotal() != null ? inv.getGrandTotal() : BigDecimal.ZERO);
            ps.setBigDecimal(10, inv.getTaxTotal() != null ? inv.getTaxTotal() : BigDecimal.ZERO);
            ps.setBigDecimal(11, inv.getGrandTotal() != null ? inv.getGrandTotal() : BigDecimal.ZERO);
            ps.setBigDecimal(12, inv.getAmountPaid() != null ? inv.getAmountPaid() : BigDecimal.ZERO);
            ps.setString(13, inv.getNotes());
            ps.setInt(14, inv.getId());
            ps.executeUpdate();
        }
    }

    private void saveLines(Connection conn, Invoice inv) throws SQLException {
        String deleteSql = "DELETE FROM invoice_lines WHERE invoice_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
            ps.setInt(1, inv.getId());
            ps.executeUpdate();
        }

        String insertSql = "INSERT INTO invoice_lines (invoice_id, product_id, description, quantity, unit_price, tax_rate, tax_amount, total_price, line_total) " +
                           "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            for (InvoiceLine line : inv.getLines()) {
                line.recalculateTotal();
                ps.setInt(1, inv.getId());
                if (line.getProductId() != null && line.getProductId() > 0) {
                    ps.setInt(2, line.getProductId());
                } else {
                    ps.setNull(2, Types.INTEGER);
                }
                ps.setString(3, line.getDescription() != null ? line.getDescription() : "");
                ps.setBigDecimal(4, line.getQuantity() != null ? line.getQuantity() : BigDecimal.ONE);
                ps.setBigDecimal(5, line.getUnitPrice() != null ? line.getUnitPrice() : BigDecimal.ZERO);
                ps.setBigDecimal(6, line.getTaxRate() != null ? line.getTaxRate() : BigDecimal.ZERO);
                ps.setBigDecimal(7, line.getTaxAmount() != null ? line.getTaxAmount() : BigDecimal.ZERO);
                ps.setBigDecimal(8, line.getLineTotal() != null ? line.getLineTotal() : BigDecimal.ZERO);
                ps.setBigDecimal(9, line.getLineTotal() != null ? line.getLineTotal() : BigDecimal.ZERO);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private List<InvoiceLine> findLinesByInvoiceId(Connection conn, int invoiceId) throws SQLException {
        List<InvoiceLine> lines = new ArrayList<>();
        String sql = "SELECT il.*, p.product_code FROM invoice_lines il " +
                     "LEFT JOIN products p ON il.product_id = p.id WHERE il.invoice_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    InvoiceLine line = new InvoiceLine();
                    line.setId(rs.getInt("id"));
                    line.setInvoiceId(invoiceId);
                    int prodId = rs.getInt("product_id");
                    if (!rs.wasNull()) {
                        line.setProductId(prodId);
                    }
                    try { line.setProductCode(rs.getString("product_code")); } catch (SQLException ignored) {}
                    line.setDescription(rs.getString("description"));
                    line.setQuantity(rs.getBigDecimal("quantity"));
                    line.setUnitPrice(rs.getBigDecimal("unit_price"));
                    
                    BigDecimal tRate = BigDecimal.ZERO;
                    try {
                        BigDecimal r = rs.getBigDecimal("tax_rate");
                        if (r != null) tRate = r;
                    } catch (SQLException ignored) {}
                    line.setTaxRate(tRate);

                    BigDecimal tAmt = BigDecimal.ZERO;
                    try {
                        BigDecimal a = rs.getBigDecimal("tax_amount");
                        if (a != null) tAmt = a;
                    } catch (SQLException ignored) {}
                    line.setTaxAmount(tAmt);
                    line.setTaxable(tRate.compareTo(BigDecimal.ZERO) > 0);

                    BigDecimal total = null;
                    try { total = rs.getBigDecimal("total_price"); } catch (SQLException ignored) {}
                    if (total == null) {
                        try { total = rs.getBigDecimal("line_total"); } catch (SQLException ignored) {}
                    }
                    line.setTotalPrice(total != null ? total : BigDecimal.ZERO);
                    lines.add(line);
                }
            }
        }
        return lines;
    }
    

    private Invoice mapRow(ResultSet rs) throws SQLException {
        Invoice inv = new Invoice();
        inv.setId(rs.getInt("id"));
        inv.setInvoiceNumber(rs.getString("invoice_number"));
        inv.setCustomerId(rs.getInt("customer_id"));
        inv.setCustomerName(rs.getString("customer_name"));
        int soId = rs.getInt("so_id");
        if (!rs.wasNull()) inv.setSoId(soId);
        int dnId = rs.getInt("dn_id");
        if (!rs.wasNull()) inv.setDnId(dnId);
        Date iDate = rs.getDate("invoice_date");
        if (iDate != null) inv.setInvoiceDate(iDate.toLocalDate());
        Date dDate = rs.getDate("due_date");
        if (dDate != null) inv.setDueDate(dDate.toLocalDate());
        inv.setStatus(rs.getString("status"));
        inv.setSubtotal(rs.getBigDecimal("subtotal"));

        BigDecimal tax = null;
        try { tax = rs.getBigDecimal("tax_amount"); } catch (SQLException ignored) {}
        if (tax == null) {
            try { tax = rs.getBigDecimal("tax_total"); } catch (SQLException ignored) {}
        }
        inv.setTaxAmount(tax != null ? tax : BigDecimal.ZERO);

        BigDecimal total = null;
        try { total = rs.getBigDecimal("total_amount"); } catch (SQLException ignored) {}
        if (total == null) {
            try { total = rs.getBigDecimal("grand_total"); } catch (SQLException ignored) {}
        }
        inv.setTotalAmount(total != null ? total : BigDecimal.ZERO);

        inv.setPaidAmount(rs.getBigDecimal("paid_amount"));
        inv.setNotes(rs.getString("notes"));
        inv.setCreatedBy(rs.getInt("created_by"));
        return inv;
    }
}