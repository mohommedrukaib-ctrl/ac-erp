package com.aceerp.repository;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.Invoice;
import com.aceerp.model.InvoiceLine;
import com.aceerp.model.Customer;
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
        String sql = "SELECT i.*, c.name AS customer_name FROM invoices i " +
                     "LEFT JOIN customers c ON i.customer_id = c.id ORDER BY i.id DESC";
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
        String sql = "SELECT i.*, c.name AS customer_name FROM invoices i " +
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
            log.error("Failed to find invoice by id: " + id, e);
        }
        return Optional.empty();
    }

    public Invoice save(Invoice invoice) {
        adjustStatus(invoice);

        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (invoice.getId() <= 0) {
                    insertInvoice(conn, invoice);
                } else {
                    updateInvoice(conn, invoice);
                }
                saveLines(conn, invoice);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            log.error("Failed to save Invoice", e);
        }
        return invoice;
    }

    private void adjustStatus(Invoice invoice) {
        BigDecimal paid = invoice.getPaidAmount() != null ? invoice.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal total = invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO;

        if (paid.compareTo(BigDecimal.ZERO) > 0) {
            if (paid.compareTo(total) >= 0) {
                invoice.setStatus("PAID");
            } else {
                invoice.setStatus("PARTIALLY_PAID");
            }
        } else if ("DRAFT".equals(invoice.getStatus()) && invoice.getId() > 0) {
            // Allows explicitly changing a Draft to Posted through the UI
        }
    }

    private void insertInvoice(Connection conn, Invoice inv) throws SQLException {
        String sql = "INSERT INTO invoices (invoice_number, customer_id, invoice_date, due_date, status, subtotal, tax_amount, total_amount, paid_amount, notes) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, inv.getInvoiceNumber());
            if (inv.getCustomerId() > 0) ps.setInt(2, inv.getCustomerId()); else ps.setNull(2, Types.INTEGER);
            ps.setDate(3, Date.valueOf(inv.getInvoiceDate() != null ? inv.getInvoiceDate() : java.time.LocalDate.now()));
            ps.setDate(4, inv.getDueDate() != null ? Date.valueOf(inv.getDueDate()) : null);
            ps.setString(5, inv.getStatus());
            ps.setBigDecimal(6, inv.getSubtotal());
            ps.setBigDecimal(7, inv.getTaxAmount());
            ps.setBigDecimal(8, inv.getTotalAmount());
            ps.setBigDecimal(9, inv.getPaidAmount() != null ? inv.getPaidAmount() : BigDecimal.ZERO);
            ps.setString(10, inv.getNotes());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) inv.setId(rs.getInt(1));
            }
        }
    }

    private void updateInvoice(Connection conn, Invoice inv) throws SQLException {
        String sql = "UPDATE invoices SET customer_id=?, invoice_date=?, due_date=?, status=?, subtotal=?, tax_amount=?, total_amount=?, paid_amount=?, notes=?, updated_at=CURRENT_TIMESTAMP WHERE id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (inv.getCustomerId() > 0) ps.setInt(1, inv.getCustomerId()); else ps.setNull(1, Types.INTEGER);
            ps.setDate(2, Date.valueOf(inv.getInvoiceDate() != null ? inv.getInvoiceDate() : java.time.LocalDate.now()));
            ps.setDate(3, inv.getDueDate() != null ? Date.valueOf(inv.getDueDate()) : null);
            ps.setString(4, inv.getStatus());
            ps.setBigDecimal(5, inv.getSubtotal());
            ps.setBigDecimal(6, inv.getTaxAmount());
            ps.setBigDecimal(7, inv.getTotalAmount());
            ps.setBigDecimal(8, inv.getPaidAmount() != null ? inv.getPaidAmount() : BigDecimal.ZERO);
            ps.setString(9, inv.getNotes());
            ps.setInt(10, inv.getId());
            ps.executeUpdate();
        }
    }

    private void saveLines(Connection conn, Invoice inv) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM invoice_lines WHERE invoice_id = ?")) {
            ps.setInt(1, inv.getId());
            ps.executeUpdate();
        }
        String sql = "INSERT INTO invoice_lines (invoice_id, product_id, description, quantity, unit_price, tax_rate, tax_amount, line_total) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (InvoiceLine line : inv.getLines()) {
                ps.setInt(1, inv.getId());
                if (line.getProductId() != null && line.getProductId() > 0) ps.setInt(2, line.getProductId()); else ps.setNull(2, Types.INTEGER);
                ps.setString(3, line.getDescription());
                ps.setBigDecimal(4, line.getQuantity());
                ps.setBigDecimal(5, line.getUnitPrice());
                ps.setBigDecimal(6, line.getTaxRate());
                ps.setBigDecimal(7, line.getTaxAmount());
                ps.setBigDecimal(8, line.getLineTotal());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public void delete(int id) throws SQLException {
        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps1 = conn.prepareStatement("DELETE FROM invoice_lines WHERE invoice_id = ?");
                 PreparedStatement ps2 = conn.prepareStatement("DELETE FROM invoices WHERE id = ?")) {
                ps1.setInt(1, id); ps1.executeUpdate();
                ps2.setInt(1, id); ps2.executeUpdate();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private List<InvoiceLine> findLinesByInvoiceId(Connection conn, int invoiceId) throws SQLException {
        List<InvoiceLine> lines = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM invoice_lines WHERE invoice_id = ?")) {
            ps.setInt(1, invoiceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    InvoiceLine line = new InvoiceLine();
                    line.setId(rs.getInt("id"));
                    line.setInvoiceId(invoiceId);
                    int prodId = rs.getInt("product_id");
                    if (!rs.wasNull()) line.setProductId(prodId);
                    line.setDescription(rs.getString("description"));
                    line.setQuantity(rs.getBigDecimal("quantity"));
                    line.setUnitPrice(rs.getBigDecimal("unit_price"));
                    line.setTaxRate(rs.getBigDecimal("tax_rate"));
                    line.setTaxAmount(rs.getBigDecimal("tax_amount"));
                    
                    try { line.setLineTotal(rs.getBigDecimal("line_total")); } 
                    catch (SQLException e) { line.setLineTotal(rs.getBigDecimal("total_price")); }
                    
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
        
        int custId = rs.getInt("customer_id");
        if (!rs.wasNull() && custId > 0) {
            inv.setCustomerId(custId);
            inv.setCustomerName(rs.getString("customer_name"));
            Customer c = new Customer();
            c.setId(custId);
            c.setName(rs.getString("customer_name"));
            inv.setCustomer(c);
        }

        Date iDate = rs.getDate("invoice_date");
        if (iDate != null) inv.setInvoiceDate(iDate.toLocalDate());
        Date dDate = rs.getDate("due_date");
        if (dDate != null) inv.setDueDate(dDate.toLocalDate());
        
        inv.setStatus(rs.getString("status"));
        inv.setSubtotal(rs.getBigDecimal("subtotal"));
        inv.setTaxAmount(rs.getBigDecimal("tax_amount"));
        inv.setTotalAmount(rs.getBigDecimal("total_amount"));
        inv.setPaidAmount(rs.getBigDecimal("paid_amount"));
        inv.setNotes(rs.getString("notes"));
        return inv;
    }
}