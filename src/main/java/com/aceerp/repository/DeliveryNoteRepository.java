package com.aceerp.repository;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.DeliveryNote;
import com.aceerp.model.DeliveryNoteLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class DeliveryNoteRepository {
    private static final Logger logger = LoggerFactory.getLogger(DeliveryNoteRepository.class);

    public List<DeliveryNote> findAll() {
        List<DeliveryNote> list = new ArrayList<>();
        String sql = "SELECT dn.*, c.name AS customer_name, so.order_number AS so_number " +
                     "FROM delivery_notes dn " +
                     "JOIN customers c ON dn.customer_id = c.id " +
                     "LEFT JOIN sales_orders so ON dn.so_id = so.id " +
                     "ORDER BY dn.id DESC";

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                DeliveryNote dn = mapResultSetToDN(rs);
                dn.setLines(findLinesByDnId(conn, dn.getId()));
                list.add(dn);
            }
        } catch (SQLException e) {
            logger.error("Error listing delivery notes", e);
        }
        return list;
    }

    public DeliveryNote save(DeliveryNote dn) throws SQLException {
        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            conn.setAutoCommit(false);

            if (dn.getId() == null) {
                // INSERT
                String sql = "INSERT INTO delivery_notes (dn_number, so_id, customer_id, dispatch_date, " +
                             "driver_name, vehicle_no, delivery_address, status, notes, created_by) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, dn.getDnNumber());
                    if (dn.getSoId() != null) stmt.setInt(2, dn.getSoId()); else stmt.setNull(2, Types.INTEGER);
                    stmt.setInt(3, dn.getCustomerId());
                    stmt.setDate(4, Date.valueOf(dn.getDispatchDate()));
                    stmt.setString(5, dn.getDriverName());
                    stmt.setString(6, dn.getVehicleNo());
                    stmt.setString(7, dn.getDeliveryAddress());
                    stmt.setString(8, dn.getStatus() != null ? dn.getStatus() : "DISPATCHED");
                    stmt.setString(9, dn.getNotes());
                    if (dn.getCreatedBy() != null) stmt.setInt(10, dn.getCreatedBy()); else stmt.setNull(10, Types.INTEGER);

                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        dn.setId(rs.getInt(1));
                    }
                }
            } else {
                // UPDATE
                String sql = "UPDATE delivery_notes SET dn_number=?, so_id=?, customer_id=?, dispatch_date=?, " +
                             "driver_name=?, vehicle_no=?, delivery_address=?, status=?, notes=? WHERE id=?";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, dn.getDnNumber());
                    if (dn.getSoId() != null) stmt.setInt(2, dn.getSoId()); else stmt.setNull(2, Types.INTEGER);
                    stmt.setInt(3, dn.getCustomerId());
                    stmt.setDate(4, Date.valueOf(dn.getDispatchDate()));
                    stmt.setString(5, dn.getDriverName());
                    stmt.setString(6, dn.getVehicleNo());
                    stmt.setString(7, dn.getDeliveryAddress());
                    stmt.setString(8, dn.getStatus());
                    stmt.setString(9, dn.getNotes());
                    stmt.setInt(10, dn.getId());
                    stmt.executeUpdate();
                }

                // Remove existing lines for re-insertion
                try (PreparedStatement del = conn.prepareStatement("DELETE FROM delivery_note_lines WHERE dn_id=?")) {
                    del.setInt(1, dn.getId());
                    del.executeUpdate();
                }
            }

            // Save lines & Deduct Stock
            saveLinesAndDeductStock(conn, dn);

            conn.commit();
            return dn;
        } catch (SQLException e) {
            if (conn != null) conn.rollback();
            logger.error("Failed saving delivery note", e);
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }

    private void saveLinesAndDeductStock(Connection conn, DeliveryNote dn) throws SQLException {
        String lineSql = "INSERT INTO delivery_note_lines (dn_id, product_id, ordered_qty, shipped_qty, notes) VALUES (?, ?, ?, ?, ?)";
        String stockSql = "UPDATE products SET current_stock = current_stock - ? WHERE id = ? AND (is_service IS NULL OR is_service = FALSE)";
        String moveSql = "INSERT INTO stock_movements (product_id, movement_type, quantity, reference_type, reference_id, notes, created_by) VALUES (?, 'OUT', ?, 'DELIVERY_NOTE', ?, ?, ?)";

        try (PreparedStatement lineStmt = conn.prepareStatement(lineSql);
             PreparedStatement stockStmt = conn.prepareStatement(stockSql);
             PreparedStatement moveStmt = conn.prepareStatement(moveSql)) {

            for (DeliveryNoteLine line : dn.getLines()) {
                if (line.getProductId() == null || line.getShippedQty() <= 0) continue;

                // 1. Insert Line
                lineStmt.setInt(1, dn.getId());
                lineStmt.setInt(2, line.getProductId());
                lineStmt.setDouble(3, line.getOrderedQty());
                lineStmt.setDouble(4, line.getShippedQty());
                lineStmt.setString(5, line.getNotes());
                lineStmt.addBatch();

                // 2. Deduct Inventory Stock
                stockStmt.setDouble(1, line.getShippedQty());
                stockStmt.setInt(2, line.getProductId());
                stockStmt.addBatch();

                // 3. Log Stock Movement
                moveStmt.setInt(1, line.getProductId());
                moveStmt.setDouble(2, line.getShippedQty());
                moveStmt.setInt(3, dn.getId());
                moveStmt.setString(4, "Dispatch via DN " + dn.getDnNumber());
                if (dn.getCreatedBy() != null) moveStmt.setInt(5, dn.getCreatedBy()); else moveStmt.setNull(5, Types.INTEGER);
                moveStmt.addBatch();
            }

            lineStmt.executeBatch();
            stockStmt.executeBatch();
            moveStmt.executeBatch();
        }
    }

    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM delivery_notes WHERE id = ?";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            stmt.executeUpdate();
        }
    }

    public String generateNextDNNumber() {
        String sql = "SELECT dn_number FROM delivery_notes ORDER BY id DESC LIMIT 1";
        try (Connection conn =DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                String last = rs.getString(1);
                if (last != null && last.startsWith("DN-")) {
                    int num = Integer.parseInt(last.replace("DN-", "")) + 1;
                    return String.format("DN-%05d", num);
                }
            }
        } catch (Exception e) {
            logger.warn("Generating default DN number", e);
        }
        return "DN-00001";
    }

    private List<DeliveryNoteLine> findLinesByDnId(Connection conn, int dnId) throws SQLException {
        List<DeliveryNoteLine> lines = new ArrayList<>();
        String sql = "SELECT dnl.*, p.name AS product_name, p.sku FROM delivery_note_lines dnl " +
                     "JOIN products p ON dnl.product_id = p.id WHERE dnl.dn_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, dnId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                DeliveryNoteLine line = new DeliveryNoteLine();
                line.setId(rs.getInt("id"));
                line.setDnId(rs.getInt("dn_id"));
                line.setProductId(rs.getInt("product_id"));
                line.setProductName(rs.getString("product_name"));
                line.setSku(rs.getString("sku"));
                line.setOrderedQty(rs.getDouble("ordered_qty"));
                line.setShippedQty(rs.getDouble("shipped_qty"));
                line.setNotes(rs.getString("notes"));
                lines.add(line);
            }
        }
        return lines;
    }

    private DeliveryNote mapResultSetToDN(ResultSet rs) throws SQLException {
        DeliveryNote dn = new DeliveryNote();
        dn.setId(rs.getInt("id"));
        dn.setDnNumber(rs.getString("dn_number"));
        dn.setSoId(rs.getObject("so_id") != null ? rs.getInt("so_id") : null);
        dn.setSoNumber(rs.getString("so_number"));
        dn.setCustomerId(rs.getInt("customer_id"));
        dn.setCustomerName(rs.getString("customer_name"));
        dn.setDispatchDate(rs.getDate("dispatch_date").toLocalDate());
        dn.setDriverName(rs.getString("driver_name"));
        dn.setVehicleNo(rs.getString("vehicle_no"));
        dn.setDeliveryAddress(rs.getString("delivery_address"));
        dn.setStatus(rs.getString("status"));
        dn.setNotes(rs.getString("notes"));
        return dn;
    }
}