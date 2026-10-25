package com.aceerp.repository;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.Customer;
import com.aceerp.model.Quotation;
import com.aceerp.model.QuotationLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class QuotationRepository {

    private static final Logger log = LoggerFactory.getLogger(QuotationRepository.class);

    public List<Quotation> findAll() {
        List<Quotation> list = new ArrayList<>();
        String sql = "SELECT q.*, c.name as customer_name, c.phone as customer_phone " +
                     "FROM quotations q " +
                     "LEFT JOIN customers c ON q.customer_id = c.id " +
                     "ORDER BY q.created_at DESC";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Quotation q = mapRow(rs);
                q.setLines(findLinesByQuotationId(conn, q.getId()));
                list.add(q);
            }
        } catch (SQLException e) {
            log.error("Failed to load quotations", e);
        }
        return list;
    }

    public Optional<Quotation> findById(int id) {
        String sql = "SELECT q.*, c.name as customer_name, c.phone as customer_phone " +
                     "FROM quotations q " +
                     "LEFT JOIN customers c ON q.customer_id = c.id " +
                     "WHERE q.id = ?";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Quotation q = mapRow(rs);
                    q.setLines(findLinesByQuotationId(conn, q.getId()));
                    return Optional.of(q);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to find quotation {}", id, e);
        }
        return Optional.empty();
    }

    public Quotation save(Quotation quotation) {
        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (quotation.getId() == 0) {
                    insertQuotation(conn, quotation);
                } else {
                    updateQuotation(conn, quotation);
                }
                saveLines(conn, quotation);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                log.error("Failed transaction while saving quotation", e);
                throw e;
            }
        } catch (SQLException e) {
            log.error("Failed to save quotation", e);
        }
        return quotation;
    }

    private void insertQuotation(Connection conn, Quotation q) throws SQLException {
        String sql = "INSERT INTO quotations (quote_number, customer_id, issue_date, expiry_date, status, " +
                     "subtotal, tax_amount, total_amount, tax_total, grand_total, terms_conditions, notes, created_by) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, q.getQuoteNumber());
            ps.setInt(2, q.getCustomerId());
            ps.setDate(3, Date.valueOf(q.getIssueDate() != null ? q.getIssueDate() : java.time.LocalDate.now()));
            ps.setDate(4, q.getExpiryDate() != null ? Date.valueOf(q.getExpiryDate()) : null);
            ps.setString(5, q.getStatus() != null ? q.getStatus() : "DRAFT");
            ps.setBigDecimal(6, q.getSubtotal() != null ? q.getSubtotal() : BigDecimal.ZERO);
            ps.setBigDecimal(7, q.getTaxTotal() != null ? q.getTaxTotal() : BigDecimal.ZERO);
            ps.setBigDecimal(8, q.getGrandTotal() != null ? q.getGrandTotal() : BigDecimal.ZERO);
            ps.setBigDecimal(9, q.getTaxTotal() != null ? q.getTaxTotal() : BigDecimal.ZERO);
            ps.setBigDecimal(10, q.getGrandTotal() != null ? q.getGrandTotal() : BigDecimal.ZERO);
            ps.setString(11, q.getTermsConditions());
            ps.setString(12, q.getNotes());
            if (q.getCreatedBy() != 0) {
                ps.setInt(13, q.getCreatedBy());
            } else {
                ps.setNull(13, Types.INTEGER);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) q.setId(rs.getInt(1));
            }
        }
    }

    private void updateQuotation(Connection conn, Quotation q) throws SQLException {
        String sql = "UPDATE quotations SET customer_id=?, issue_date=?, expiry_date=?, status=?, " +
                     "subtotal=?, tax_amount=?, total_amount=?, tax_total=?, grand_total=?, terms_conditions=?, notes=?, updated_at=CURRENT_TIMESTAMP " +
                     "WHERE id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, q.getCustomerId());
            ps.setDate(2, Date.valueOf(q.getIssueDate() != null ? q.getIssueDate() : java.time.LocalDate.now()));
            ps.setDate(3, q.getExpiryDate() != null ? Date.valueOf(q.getExpiryDate()) : null);
            ps.setString(4, q.getStatus() != null ? q.getStatus() : "DRAFT");
            ps.setBigDecimal(5, q.getSubtotal() != null ? q.getSubtotal() : BigDecimal.ZERO);
            ps.setBigDecimal(6, q.getTaxTotal() != null ? q.getTaxTotal() : BigDecimal.ZERO);
            ps.setBigDecimal(7, q.getGrandTotal() != null ? q.getGrandTotal() : BigDecimal.ZERO);
            ps.setBigDecimal(8, q.getTaxTotal() != null ? q.getTaxTotal() : BigDecimal.ZERO);
            ps.setBigDecimal(9, q.getGrandTotal() != null ? q.getGrandTotal() : BigDecimal.ZERO);
            ps.setString(10, q.getTermsConditions());
            ps.setString(11, q.getNotes());
            ps.setInt(12, q.getId());
            ps.executeUpdate();
        }
    }

    private void saveLines(Connection conn, Quotation q) throws SQLException {
        String deleteSql = "DELETE FROM quotation_lines WHERE quotation_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
            ps.setInt(1, q.getId());
            ps.executeUpdate();
        }

        String insertSql = "INSERT INTO quotation_lines (quotation_id, product_id, description, quantity, unit_price, tax_rate, total_price, line_total) " +
                           "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            for (QuotationLine line : q.getLines()) {
                ps.setInt(1, q.getId());
                if (line.getProductId() != null && line.getProductId() > 0) {
                    ps.setInt(2, line.getProductId());
                } else {
                    ps.setNull(2, Types.INTEGER);
                }
                ps.setString(3, line.getDescription() != null ? line.getDescription() : "Custom Line Item");
                ps.setBigDecimal(4, line.getQuantity() != null ? line.getQuantity() : BigDecimal.ONE);
                ps.setBigDecimal(5, line.getUnitPrice() != null ? line.getUnitPrice() : BigDecimal.ZERO);
                ps.setBigDecimal(6, line.getTaxRate() != null ? line.getTaxRate() : BigDecimal.ZERO);
                ps.setBigDecimal(7, line.getLineTotal() != null ? line.getLineTotal() : BigDecimal.ZERO);
                ps.setBigDecimal(8, line.getLineTotal() != null ? line.getLineTotal() : BigDecimal.ZERO);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private List<QuotationLine> findLinesByQuotationId(Connection conn, int quotationId) throws SQLException {
        List<QuotationLine> lines = new ArrayList<>();
        String sql = "SELECT ql.*, p.name as product_name, p.product_code " +
                     "FROM quotation_lines ql " +
                     "LEFT JOIN products p ON ql.product_id = p.id " +
                     "WHERE ql.quotation_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, quotationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    QuotationLine line = new QuotationLine();
                    line.setId(rs.getInt("id"));
                    line.setQuotationId(quotationId);
                    int prodId = rs.getInt("product_id");
                    if (!rs.wasNull()) {
                        line.setProductId(prodId);
                        line.setProductCode(rs.getString("product_code"));
                    }
                    line.setDescription(rs.getString("description"));
                    line.setQuantity(rs.getBigDecimal("quantity"));
                    line.setUnitPrice(rs.getBigDecimal("unit_price"));
                    line.setTaxRate(rs.getBigDecimal("tax_rate"));
                    
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

    private Quotation mapRow(ResultSet rs) throws SQLException {
        Quotation q = new Quotation();
        q.setId(rs.getInt("id"));
        q.setQuoteNumber(rs.getString("quote_number"));
        q.setCustomerId(rs.getInt("customer_id"));
        q.setCustomerName(rs.getString("customer_name"));

        Customer c = new Customer();
        c.setId(q.getCustomerId());
        c.setName(q.getCustomerName());
        c.setPhone(rs.getString("customer_phone"));
        q.setCustomer(c);

        Date iDate = rs.getDate("issue_date");
        if (iDate != null) q.setQuoteDate(iDate.toLocalDate());
        Date eDate = rs.getDate("expiry_date");
        if (eDate != null) q.setExpiryDate(eDate.toLocalDate());
        q.setStatus(rs.getString("status"));
        q.setSubtotal(rs.getBigDecimal("subtotal"));

        BigDecimal tax = null;
        try { tax = rs.getBigDecimal("tax_amount"); } catch (SQLException ignored) {}
        if (tax == null) {
            try { tax = rs.getBigDecimal("tax_total"); } catch (SQLException ignored) {}
        }
        q.setTaxAmount(tax != null ? tax : BigDecimal.ZERO);

        BigDecimal total = null;
        try { total = rs.getBigDecimal("total_amount"); } catch (SQLException ignored) {}
        if (total == null) {
            try { total = rs.getBigDecimal("grand_total"); } catch (SQLException ignored) {}
        }
        q.setTotalAmount(total != null ? total : BigDecimal.ZERO);

        q.setTermsConditions(rs.getString("terms_conditions"));
        q.setNotes(rs.getString("notes"));
        q.setCreatedBy(rs.getInt("created_by"));
        return q;
    }
}