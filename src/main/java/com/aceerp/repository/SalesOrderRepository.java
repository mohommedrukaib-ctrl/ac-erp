package com.aceerp.repository;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.Customer;
import com.aceerp.model.SalesOrder;
import com.aceerp.model.SalesOrderLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SalesOrderRepository {

    private static final Logger log = LoggerFactory.getLogger(SalesOrderRepository.class);

    public List<SalesOrder> findAll() {
        List<SalesOrder> list = new ArrayList<>();
        String sql = "SELECT so.*, c.name AS customer_name, c.phone AS customer_phone FROM sales_orders so " +
                     "LEFT JOIN customers c ON so.customer_id = c.id ORDER BY so.id DESC";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                SalesOrder so = mapRow(rs);
                so.setLines(findLinesBySoId(conn, so.getId()));
                list.add(so);
            }
        } catch (SQLException e) {
            log.error("Failed to load sales orders", e);
        }
        return list;
    }

    public Optional<SalesOrder> findById(int id) {
        String sql = "SELECT so.*, c.name AS customer_name, c.phone AS customer_phone FROM sales_orders so " +
                     "LEFT JOIN customers c ON so.customer_id = c.id WHERE so.id = ?";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    SalesOrder so = mapRow(rs);
                    so.setLines(findLinesBySoId(conn, so.getId()));
                    return Optional.of(so);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to find sales order {}", id, e);
        }
        return Optional.empty();
    }

    public SalesOrder save(SalesOrder so) {
        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (so.getId() <= 0) {
                    insertSalesOrder(conn, so);
                } else {
                    updateSalesOrder(conn, so);
                }
                saveLines(conn, so);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            log.error("Failed to save sales order", e);
            throw new RuntimeException("Failed to save sales order: " + e.getMessage(), e);
        }
        return so;
    }

    private void insertSalesOrder(Connection conn, SalesOrder so) throws SQLException {
        if (so.getCustomer() != null && so.getCustomer().getId() > 0) {
            so.setCustomerId(so.getCustomer().getId());
        }

        boolean hasOrderNumber = columnExists(conn, "sales_orders", "order_number");
        
        String sql = hasOrderNumber 
            ? "INSERT INTO sales_orders (so_number, order_number, quotation_id, customer_id, order_date, expected_delivery_date, status, subtotal, tax_amount, total_amount, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id"
            : "INSERT INTO sales_orders (so_number, quotation_id, customer_id, order_date, expected_delivery_date, status, subtotal, tax_amount, total_amount, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, so.getSoNumber());
            if (hasOrderNumber) {
                ps.setString(i++, so.getSoNumber());
            }
            
            Integer qId = so.getQuotationId();
            if (qId != null && qId > 0) ps.setInt(i++, qId); else ps.setNull(i++, Types.INTEGER);
            
            int cId = so.getCustomerId();
            if (cId > 0) ps.setInt(i++, cId); else ps.setNull(i++, Types.INTEGER);

            ps.setDate(i++, Date.valueOf(so.getOrderDate() != null ? so.getOrderDate() : java.time.LocalDate.now()));
            ps.setDate(i++, so.getExpectedDeliveryDate() != null ? Date.valueOf(so.getExpectedDeliveryDate()) : null);
            ps.setString(i++, so.getStatus() != null ? so.getStatus() : "DRAFT");
            ps.setBigDecimal(i++, so.getSubtotal() != null ? so.getSubtotal() : BigDecimal.ZERO);
            ps.setBigDecimal(i++, so.getTaxAmount() != null ? so.getTaxAmount() : BigDecimal.ZERO);
            ps.setBigDecimal(i++, so.getTotalAmount() != null ? so.getTotalAmount() : BigDecimal.ZERO);
            ps.setString(i, so.getNotes());
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) so.setId(rs.getInt(1));
            }
        }
    }

    private void updateSalesOrder(Connection conn, SalesOrder so) throws SQLException {
        if (so.getCustomer() != null && so.getCustomer().getId() > 0) {
            so.setCustomerId(so.getCustomer().getId());
        }

        String sql = "UPDATE sales_orders SET customer_id=?, order_date=?, expected_delivery_date=?, status=?, " +
                     "subtotal=?, tax_amount=?, total_amount=?, notes=?, updated_at=CURRENT_TIMESTAMP WHERE id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int cId = so.getCustomerId();
            if (cId > 0) ps.setInt(1, cId); else ps.setNull(1, Types.INTEGER);
            ps.setDate(2, Date.valueOf(so.getOrderDate() != null ? so.getOrderDate() : java.time.LocalDate.now()));
            ps.setDate(3, so.getExpectedDeliveryDate() != null ? Date.valueOf(so.getExpectedDeliveryDate()) : null);
            ps.setString(4, so.getStatus());
            ps.setBigDecimal(5, so.getSubtotal() != null ? so.getSubtotal() : BigDecimal.ZERO);
            ps.setBigDecimal(6, so.getTaxAmount() != null ? so.getTaxAmount() : BigDecimal.ZERO);
            ps.setBigDecimal(7, so.getTotalAmount() != null ? so.getTotalAmount() : BigDecimal.ZERO);
            ps.setString(8, so.getNotes());
            ps.setInt(9, so.getId());
            ps.executeUpdate();
        }
    }

    private void saveLines(Connection conn, SalesOrder so) throws SQLException {
        String deleteSql = "DELETE FROM sales_order_lines WHERE so_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
            ps.setInt(1, so.getId());
            ps.executeUpdate();
        }

        String insertSql = "INSERT INTO sales_order_lines (so_id, product_id, description, quantity, unit_price, tax_rate, tax_amount, line_total, total_price) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            for (SalesOrderLine line : so.getLines()) {
                BigDecimal qty = line.getQuantity() != null ? line.getQuantity() : BigDecimal.ONE;
                BigDecimal price = line.getUnitPrice() != null ? line.getUnitPrice() : BigDecimal.ZERO;
                BigDecimal taxAmt = line.getTaxAmount() != null ? line.getTaxAmount() : BigDecimal.ZERO;
                
                BigDecimal lineTot = line.getTotalPrice(); // Get bound line total
                if (lineTot == null) {
                    lineTot = qty.multiply(price).add(taxAmt);
                }

                ps.setInt(1, so.getId());
                Integer pId = line.getProductId();
                if (pId != null && pId > 0) ps.setInt(2, pId); else ps.setNull(2, Types.INTEGER);
                ps.setString(3, line.getDescription());
                ps.setBigDecimal(4, qty);
                ps.setBigDecimal(5, price);
                ps.setBigDecimal(6, line.getTaxRate() != null ? line.getTaxRate() : BigDecimal.ZERO);
                ps.setBigDecimal(7, taxAmt);
                ps.setBigDecimal(8, lineTot); // line_total (fallback)
                ps.setBigDecimal(9, lineTot); // total_price (canonical)
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private List<SalesOrderLine> findLinesBySoId(Connection conn, int soId) throws SQLException {
        List<SalesOrderLine> lines = new ArrayList<>();
        String sql = "SELECT * FROM sales_order_lines WHERE so_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, soId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SalesOrderLine line = new SalesOrderLine();
                    line.setId(rs.getInt("id"));
                    line.setSoId(soId);
                    int prodId = rs.getInt("product_id");
                    if (!rs.wasNull()) line.setProductId(prodId);
                    line.setDescription(rs.getString("description"));
                    line.setQuantity(rs.getBigDecimal("quantity"));
                    line.setUnitPrice(rs.getBigDecimal("unit_price"));
                    
                    try { line.setTaxRate(rs.getBigDecimal("tax_rate")); } catch (SQLException ignored) {}
                    try { line.setTaxAmount(rs.getBigDecimal("tax_amount")); } catch (SQLException ignored) {}
                    
                    BigDecimal tot = null;
                    try { tot = rs.getBigDecimal("total_price"); } catch (SQLException ignored) {}
                    if (tot == null) {
                        try { tot = rs.getBigDecimal("line_total"); } catch (SQLException ignored) {}
                    }
                    line.setTotalPrice(tot != null ? tot : BigDecimal.ZERO);
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    private SalesOrder mapRow(ResultSet rs) throws SQLException {
        SalesOrder so = new SalesOrder();
        so.setId(rs.getInt("id"));
        
        try { so.setSoNumber(rs.getString("so_number")); } 
        catch (SQLException e) { so.setSoNumber(rs.getString("order_number")); }
        
        int qId = rs.getInt("quotation_id");
        if (!rs.wasNull()) so.setQuotationId(qId);
        
        int custId = rs.getInt("customer_id");
        if (!rs.wasNull() && custId > 0) {
            so.setCustomerId(custId);
            String custName = rs.getString("customer_name");
            String custPhone = rs.getString("customer_phone");
            so.setCustomerName(custName);

            Customer customer = new Customer();
            customer.setId(custId);
            customer.setName(custName);
            customer.setPhone(custPhone);
            so.setCustomer(customer);
        }

        Date oDate = rs.getDate("order_date");
        if (oDate != null) so.setOrderDate(oDate.toLocalDate());
        Date edDate = rs.getDate("expected_delivery_date");
        if (edDate != null) so.setExpectedDeliveryDate(edDate.toLocalDate());
        so.setStatus(rs.getString("status"));
        so.setSubtotal(rs.getBigDecimal("subtotal"));
        so.setTaxAmount(rs.getBigDecimal("tax_amount"));
        so.setTotalAmount(rs.getBigDecimal("total_amount"));
        so.setNotes(rs.getString("notes"));
        return so;
    }

    private boolean columnExists(Connection conn, String table, String column) {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, table, column)) {
            if (rs.next()) return true;
        } catch (SQLException ignored) {}
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, table.toLowerCase(), column.toLowerCase())) {
            if (rs.next()) return true;
        } catch (SQLException ignored) {}
        return false;
    }
}