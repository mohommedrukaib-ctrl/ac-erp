package com.aceerp.repository;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.GoodsReceivedNote;
import com.aceerp.model.GrnLine;
import com.aceerp.model.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class GoodsReceivedNoteRepository {

    private static final Logger logger = LoggerFactory.getLogger(GoodsReceivedNoteRepository.class);

    public List<GoodsReceivedNote> findAll() {
        List<GoodsReceivedNote> list = new ArrayList<>();
        String sql = """
            SELECT g.*, s.name AS supplier_name, s.phone AS supplier_phone
            FROM goods_received_notes g
            LEFT JOIN suppliers s ON g.supplier_id = s.id
            ORDER BY g.id DESC
            """;

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                GoodsReceivedNote grn = mapHeader(rs);
                grn.setLines(findLines(grn.getId(), conn));
                list.add(grn);
            }
        } catch (SQLException e) {
            logger.error("Error loading GRNs", e);
        }
        return list;
    }

    public GoodsReceivedNote findById(int id) {
        String sql = """
            SELECT g.*, s.name AS supplier_name, s.phone AS supplier_phone
            FROM goods_received_notes g
            LEFT JOIN suppliers s ON g.supplier_id = s.id
            WHERE g.id = ?
            """;

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    GoodsReceivedNote grn = mapHeader(rs);
                    grn.setLines(findLines(grn.getId(), conn));
                    return grn;
                }
            }
        } catch (SQLException e) {
            logger.error("Error loading GRN id={}", id, e);
        }
        return null;
    }

    /**
     * Saves GRN + lines, increases product stock, writes stock_movements,
     * and marks linked PO as RECEIVED — all in one transaction.
     */
    public GoodsReceivedNote saveAndPostStock(GoodsReceivedNote grn) {
        String insertGrn = """
            INSERT INTO goods_received_notes
                (grn_number, po_id, supplier_id, received_date, delivery_note_ref, status, notes, created_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

        String insertLine = """
            INSERT INTO grn_lines
                (grn_id, po_line_id, product_id, description, quantity_ordered, quantity_received, unit_cost, line_total)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        String updateStock = """
            UPDATE products
            SET stock_quantity = COALESCE(stock_quantity, 0) + ?
            WHERE id = ?
            """;

        String insertMovement = """
            INSERT INTO stock_movements
                (product_id, movement_type, quantity, reference_type, reference_id, notes, created_at)
            VALUES (?, 'PURCHASE_RECEIPT', ?, 'GRN', ?, ?, CURRENT_TIMESTAMP)
            """;

        String updatePo = "UPDATE purchase_orders SET status = 'RECEIVED' WHERE id = ?";

        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement stmt = conn.prepareStatement(insertGrn)) {
                    stmt.setString(1, grn.getGrnNumber());
                    if (grn.getPoId() > 0) {
                        stmt.setInt(2, grn.getPoId());
                    } else {
                        stmt.setNull(2, Types.INTEGER);
                    }
                    stmt.setInt(3, grn.getSupplierId());
                    stmt.setDate(4, Date.valueOf(
                            grn.getReceivedDate() != null ? grn.getReceivedDate() : java.time.LocalDate.now()));
                    stmt.setString(5, grn.getDeliveryNoteRef());
                    stmt.setString(6, grn.getStatus() != null ? grn.getStatus() : "COMPLETED");
                    stmt.setString(7, grn.getNotes());
                    if (grn.getCreatedBy() != null && grn.getCreatedBy() > 0) {
                        stmt.setInt(8, grn.getCreatedBy());
                    } else {
                        stmt.setNull(8, Types.INTEGER);
                    }

                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            grn.setId(rs.getInt(1));
                        }
                    }
                }

                if (grn.getLines() != null) {
                    try (PreparedStatement lineStmt = conn.prepareStatement(insertLine);
                         PreparedStatement stockStmt = conn.prepareStatement(updateStock);
                         PreparedStatement moveStmt = conn.prepareStatement(insertMovement)) {

                        for (GrnLine line : grn.getLines()) {
                            line.recalculateTotal();

                            lineStmt.setInt(1, grn.getId());
                            if (line.getPoLineId() != null && line.getPoLineId() > 0) {
                                lineStmt.setInt(2, line.getPoLineId());
                            } else {
                                lineStmt.setNull(2, Types.INTEGER);
                            }
                            if (line.getProductId() != null && line.getProductId() > 0) {
                                lineStmt.setInt(3, line.getProductId());
                            } else {
                                lineStmt.setNull(3, Types.INTEGER);
                            }
                            lineStmt.setString(4, line.getDescription());
                            lineStmt.setBigDecimal(5,
                                    line.getQuantityOrdered() != null ? line.getQuantityOrdered() : BigDecimal.ZERO);
                            lineStmt.setBigDecimal(6,
                                    line.getQuantityReceived() != null ? line.getQuantityReceived() : BigDecimal.ZERO);
                            lineStmt.setBigDecimal(7,
                                    line.getUnitCost() != null ? line.getUnitCost() : BigDecimal.ZERO);
                            lineStmt.setBigDecimal(8,
                                    line.getLineTotal() != null ? line.getLineTotal() : BigDecimal.ZERO);
                            lineStmt.addBatch();

                            // Only post stock when product + qty received > 0
                            if (line.getProductId() != null && line.getProductId() > 0
                                    && line.getQuantityReceived() != null
                                    && line.getQuantityReceived().compareTo(BigDecimal.ZERO) > 0) {

                                stockStmt.setBigDecimal(1, line.getQuantityReceived());
                                stockStmt.setInt(2, line.getProductId());
                                stockStmt.addBatch();

                                moveStmt.setInt(1, line.getProductId());
                                moveStmt.setBigDecimal(2, line.getQuantityReceived());
                                moveStmt.setInt(3, grn.getId());
                                moveStmt.setString(4, "GRN " + grn.getGrnNumber());
                                moveStmt.addBatch();
                            }
                        }

                        lineStmt.executeBatch();
                        stockStmt.executeBatch();
                        moveStmt.executeBatch();
                    }
                }

                if (grn.getPoId() > 0) {
                    try (PreparedStatement stmt = conn.prepareStatement(updatePo)) {
                        stmt.setInt(1, grn.getPoId());
                        stmt.executeUpdate();
                    }
                }

                conn.commit();
                return findById(grn.getId());

            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException e) {
            logger.error("Failed to save/post GRN", e);
            throw new RuntimeException("Failed to receive stock: " + e.getMessage(), e);
        }
    }

    private List<GrnLine> findLines(int grnId, Connection conn) throws SQLException {
        List<GrnLine> lines = new ArrayList<>();
        String sql = "SELECT * FROM grn_lines WHERE grn_id = ? ORDER BY id ASC";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, grnId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    GrnLine line = new GrnLine();
                    line.setId(rs.getInt("id"));
                    line.setGrnId(rs.getInt("grn_id"));
                    line.setPoLineId((Integer) rs.getObject("po_line_id"));
                    line.setProductId((Integer) rs.getObject("product_id"));
                    line.setDescription(rs.getString("description"));
                    line.setQuantityOrdered(rs.getBigDecimal("quantity_ordered"));
                    line.setQuantityReceived(rs.getBigDecimal("quantity_received"));
                    line.setUnitCost(rs.getBigDecimal("unit_cost"));
                    line.setLineTotal(rs.getBigDecimal("line_total"));
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    private GoodsReceivedNote mapHeader(ResultSet rs) throws SQLException {
        GoodsReceivedNote grn = new GoodsReceivedNote();
        grn.setId(rs.getInt("id"));
        grn.setGrnNumber(rs.getString("grn_number"));
        Object poObj = rs.getObject("po_id");
        grn.setPoId(poObj != null ? ((Number) poObj).intValue() : 0);
        grn.setSupplierId(rs.getInt("supplier_id"));
        Date rd = rs.getDate("received_date");
        if (rd != null) grn.setReceivedDate(rd.toLocalDate());
        grn.setDeliveryNoteRef(rs.getString("delivery_note_ref"));
        grn.setStatus(rs.getString("status"));
        grn.setNotes(rs.getString("notes"));

        String supplierName = rs.getString("supplier_name");
        if (supplierName != null) {
            Supplier s = new Supplier();
            s.setId(grn.getSupplierId());
            s.setName(supplierName);
            s.setPhone(rs.getString("supplier_phone"));
            grn.setSupplier(s);
        }
        return grn;
    }
}