package com.aceerp.repository;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.SalesOrder;
import com.aceerp.model.SalesOrderLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SalesOrderRepository {

    private static final Logger log = LoggerFactory.getLogger(SalesOrderRepository.class);

    public List<SalesOrder> findAll() {
        List<SalesOrder> list = new ArrayList<>();
        String sql = "SELECT so.*, c.name as customer_name FROM sales_orders so " +
                     "LEFT JOIN customers c ON so.customer_id = c.id ORDER BY so.created_at DESC";
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
        String sql = "SELECT so.*, c.name as customer_name FROM sales_orders so " +
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
                if (so.getId() == 0) {
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
        }
        return so;
    }

    private void insertSalesOrder(Connection conn, SalesOrder so) throws SQLException {
        String sql = "INSERT INTO sales_orders (so_number, quotation_id, customer_id, order_date, expected_delivery_date, status, subtotal, tax_amount, total_amount, notes) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, so.getSoNumber());
            if (so.getQuotationId() != null) ps.setInt(2, so.getQuotationId()); else ps.setNull(2, Types.INTEGER);
            ps.setInt(3, so.getCustomerId());
            ps.setDate(4, Date.valueOf(so.getOrderDate() != null ? so.getOrderDate() : java.time.LocalDate.now()));
            ps.setDate(5, so.getExpectedDeliveryDate() != null ? Date.valueOf(so.getExpectedDeliveryDate()) : null);
            ps.setString(6, so.getStatus());
            ps.setBigDecimal(7, so.getSubtotal());
            ps.setBigDecimal(8, so.getTaxAmount());
            ps.setBigDecimal(9, so.getTotalAmount());
            ps.setString(10, so.getNotes());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) so.setId(rs.getInt(1));
            }
        }
    }

    private void updateSalesOrder(Connection conn, SalesOrder so) throws SQLException {
        String sql = "UPDATE sales_orders SET customer_id=?, order_date=?, expected_delivery_date=?, status=?, subtotal=?, tax_amount=?, total_amount=?, notes=?, updated_at=CURRENT_TIMESTAMP WHERE id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, so.getCustomerId());
            ps.setDate(2, Date.valueOf(so.getOrderDate() != null ? so.getOrderDate() : java.time.LocalDate.now()));
            ps.setDate(3, so.getExpectedDeliveryDate() != null ? Date.valueOf(so.getExpectedDeliveryDate()) : null);
            ps.setString(4, so.getStatus());
            ps.setBigDecimal(5, so.getSubtotal());
            ps.setBigDecimal(6, so.getTaxAmount());
            ps.setBigDecimal(7, so.getTotalAmount());
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

        String insertSql = "INSERT INTO sales_order_lines (so_id, product_id, description, quantity, unit_price, total_price) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            for (SalesOrderLine line : so.getLines()) {
                ps.setInt(1, so.getId());
                if (line.getProductId() != null) ps.setInt(2, line.getProductId()); else ps.setNull(2, Types.INTEGER);
                ps.setString(3, line.getDescription());
                ps.setBigDecimal(4, line.getQuantity());
                ps.setBigDecimal(5, line.getUnitPrice());
                ps.setBigDecimal(6, line.getTotalPrice());
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
                    line.setTotalPrice(rs.getBigDecimal("total_price"));
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    private SalesOrder mapRow(ResultSet rs) throws SQLException {
        SalesOrder so = new SalesOrder();
        so.setId(rs.getInt("id"));
        so.setSoNumber(rs.getString("so_number"));
        int qId = rs.getInt("quotation_id");
        if (!rs.wasNull()) so.setQuotationId(qId);
        so.setCustomerId(rs.getInt("customer_id"));
        so.setCustomerName(rs.getString("customer_name"));
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
}