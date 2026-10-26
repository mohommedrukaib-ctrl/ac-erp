package com.aceerp.repository;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.PurchaseOrder;
import com.aceerp.model.PurchaseOrderLine;
import com.aceerp.model.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PurchaseOrderRepository {

    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderRepository.class);

    public List<PurchaseOrder> findAll() {
        List<PurchaseOrder> list = new ArrayList<>();
        String sql = "SELECT po.*, s.name AS supplier_name FROM purchase_orders po " +
                     "LEFT JOIN suppliers s ON po.supplier_id = s.id ORDER BY po.id DESC";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                PurchaseOrder po = mapRow(rs);
                po.setLines(findLinesByPoId(conn, po.getId()));
                list.add(po);
            }
        } catch (SQLException e) {
            log.error("Failed to load purchase orders", e);
        }
        return list;
    }

    public PurchaseOrder save(PurchaseOrder po) {
        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (po.getId() <= 0) {
                    insertPO(conn, po);
                } else {
                    updatePO(conn, po);
                }
                saveLines(conn, po);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            log.error("Failed to save PO", e);
        }
        return po;
    }

    private void insertPO(Connection conn, PurchaseOrder po) throws SQLException {
        if (po.getSupplier() != null && (po.getSupplierId() == null || po.getSupplierId() <= 0)) {
            po.setSupplierId(po.getSupplier().getId());
        }

        String sql = "INSERT INTO purchase_orders (po_number, supplier_id, order_date, expected_date, status, subtotal, tax_amount, total_amount, notes) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, po.getPoNumber());
            if (po.getSupplierId() != null && po.getSupplierId() > 0) ps.setInt(2, po.getSupplierId()); else ps.setNull(2, Types.INTEGER);
            ps.setDate(3, Date.valueOf(po.getOrderDate() != null ? po.getOrderDate() : java.time.LocalDate.now()));
            ps.setDate(4, po.getExpectedDate() != null ? Date.valueOf(po.getExpectedDate()) : null);
            ps.setString(5, po.getStatus());
            ps.setBigDecimal(6, po.getSubtotal());
            ps.setBigDecimal(7, po.getTaxAmount());
            ps.setBigDecimal(8, po.getTotalAmount());
            ps.setString(9, po.getNotes());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) po.setId(rs.getInt(1));
            }
        }
    }

    private void updatePO(Connection conn, PurchaseOrder po) throws SQLException {
        if (po.getSupplier() != null && (po.getSupplierId() == null || po.getSupplierId() <= 0)) {
            po.setSupplierId(po.getSupplier().getId());
        }

        String sql = "UPDATE purchase_orders SET supplier_id=?, order_date=?, expected_date=?, status=?, subtotal=?, tax_amount=?, total_amount=?, notes=?, updated_at=CURRENT_TIMESTAMP WHERE id=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (po.getSupplierId() != null && po.getSupplierId() > 0) ps.setInt(1, po.getSupplierId()); else ps.setNull(1, Types.INTEGER);
            ps.setDate(2, Date.valueOf(po.getOrderDate() != null ? po.getOrderDate() : java.time.LocalDate.now()));
            ps.setDate(3, po.getExpectedDate() != null ? Date.valueOf(po.getExpectedDate()) : null);
            ps.setString(4, po.getStatus());
            ps.setBigDecimal(5, po.getSubtotal());
            ps.setBigDecimal(6, po.getTaxAmount());
            ps.setBigDecimal(7, po.getTotalAmount());
            ps.setString(8, po.getNotes());
            ps.setInt(9, po.getId());
            ps.executeUpdate();
        }
    }

    private void saveLines(Connection conn, PurchaseOrder po) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM purchase_order_lines WHERE po_id = ?")) {
            ps.setInt(1, po.getId());
            ps.executeUpdate();
        }

        String insertSql = "INSERT INTO purchase_order_lines (po_id, product_id, description, quantity, unit_price, tax_rate, tax_amount, line_total) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            for (PurchaseOrderLine line : po.getLines()) {
                ps.setInt(1, po.getId());
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
            try (PreparedStatement ps1 = conn.prepareStatement("DELETE FROM purchase_order_lines WHERE po_id = ?");
                 PreparedStatement ps2 = conn.prepareStatement("DELETE FROM purchase_orders WHERE id = ?")) {
                ps1.setInt(1, id); ps1.executeUpdate();
                ps2.setInt(1, id); ps2.executeUpdate();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public String generateNextPoNumber() {
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT po_number FROM purchase_orders ORDER BY id DESC LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String last = rs.getString(1);
                if (last != null && last.startsWith("PO-")) {
                    int num = Integer.parseInt(last.replace("PO-", "")) + 1;
                    return String.format("PO-%05d", num);
                }
            }
        } catch (Exception e) {
            log.warn("Generating default PO number", e);
        }
        return "PO-00001";
    }

    private List<PurchaseOrderLine> findLinesByPoId(Connection conn, int poId) throws SQLException {
        List<PurchaseOrderLine> lines = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM purchase_order_lines WHERE po_id = ?")) {
            ps.setInt(1, poId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PurchaseOrderLine line = new PurchaseOrderLine();
                    line.setId(rs.getInt("id"));
                    line.setPoId(poId);
                    int prodId = rs.getInt("product_id");
                    if (!rs.wasNull()) line.setProductId(prodId);
                    line.setDescription(rs.getString("description"));
                    line.setQuantity(rs.getBigDecimal("quantity"));
                    line.setUnitPrice(rs.getBigDecimal("unit_price"));
                    line.setTaxRate(rs.getBigDecimal("tax_rate"));
                    line.setTaxAmount(rs.getBigDecimal("tax_amount"));
                    line.setLineTotal(rs.getBigDecimal("line_total"));
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    private PurchaseOrder mapRow(ResultSet rs) throws SQLException {
        PurchaseOrder po = new PurchaseOrder();
        po.setId(rs.getInt("id"));
        po.setPoNumber(rs.getString("po_number"));
        
        int suppId = rs.getInt("supplier_id");
        if (!rs.wasNull() && suppId > 0) {
            po.setSupplierId(suppId);
            po.setSupplierName(rs.getString("supplier_name"));
            Supplier s = new Supplier();
            s.setId(suppId);
            s.setName(rs.getString("supplier_name"));
            po.setSupplier(s);
        }

        Date oDate = rs.getDate("order_date");
        if (oDate != null) po.setOrderDate(oDate.toLocalDate());
        Date eDate = rs.getDate("expected_date");
        if (eDate != null) po.setExpectedDate(eDate.toLocalDate());
        po.setStatus(rs.getString("status"));
        po.setSubtotal(rs.getBigDecimal("subtotal"));
        po.setTaxAmount(rs.getBigDecimal("tax_amount"));
        po.setTotalAmount(rs.getBigDecimal("total_amount"));
        po.setNotes(rs.getString("notes"));
        return po;
    }
}