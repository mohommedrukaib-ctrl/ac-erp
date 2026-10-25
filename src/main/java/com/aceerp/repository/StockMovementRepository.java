package com.aceerp.repository;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.StockMovement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles all database operations for stock movements.
 * Uses JOINs to include product names and user names in results.
 */
public class StockMovementRepository {

    private static final Logger log = LoggerFactory.getLogger(StockMovementRepository.class);

    // The main query that joins with products and users tables
    private static final String SELECT_WITH_JOINS =
        "SELECT sm.*, p.name AS product_name, p.product_code, " +
        "       u.full_name AS created_by_name " +
        "FROM stock_movements sm " +
        "JOIN products p ON sm.product_id = p.id " +
        "LEFT JOIN users u ON sm.created_by = u.id ";

    /**
     * Records a new stock movement.
     * Uses a provided connection so it can be part of a transaction.
     * (The service layer manages the transaction — commit or rollback.)
     */
    public void recordMovement(StockMovement m, Connection conn) throws SQLException {
        String sql = "INSERT INTO stock_movements " +
            "(product_id, movement_type, quantity, previous_stock, new_stock, " +
            " reference_type, reference_number, notes, created_by) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, m.getProductId());
            ps.setString(2, m.getMovementType());
            ps.setInt(3, m.getQuantity());
            ps.setInt(4, m.getPreviousStock());
            ps.setInt(5, m.getNewStock());
            ps.setString(6, m.getReferenceType());
            ps.setString(7, m.getReferenceNumber());
            ps.setString(8, m.getNotes());
            if (m.getCreatedBy() > 0) {
                ps.setInt(9, m.getCreatedBy());
            } else {
                ps.setNull(9, Types.INTEGER);
            }
            ps.executeUpdate();
        }
        log.info("Recorded {} movement for product {}: qty={}, {} -> {}",
            m.getMovementType(), m.getProductId(), m.getQuantity(),
            m.getPreviousStock(), m.getNewStock());
    }

    /**
     * Gets recent movements, optionally filtered by type.
     * @param filterType "ALL", "IN", "OUT", or "ADJUSTMENT"
     * @param limit max number of rows to return
     */
    public List<StockMovement> getRecentMovements(String filterType, int limit) {
        List<StockMovement> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(SELECT_WITH_JOINS);

        if (filterType != null && !"ALL".equals(filterType)) {
            sql.append("WHERE sm.movement_type = ? ");
        }
        sql.append("ORDER BY sm.created_at DESC LIMIT ?");

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            if (filterType != null && !"ALL".equals(filterType)) {
                ps.setString(paramIndex++, filterType);
            }
            ps.setInt(paramIndex, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to get recent movements", e);
        }
        return list;
    }

    /**
     * Gets all movements for a specific product (for product history).
     */
    public List<StockMovement> getMovementsByProduct(int productId) {
        List<StockMovement> list = new ArrayList<>();
        String sql = SELECT_WITH_JOINS +
            "WHERE sm.product_id = ? ORDER BY sm.created_at DESC";

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, productId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to get movements for product " + productId, e);
        }
        return list;
    }

    /**
     * Converts a database row into a StockMovement object.
     */
    private StockMovement mapRow(ResultSet rs) throws SQLException {
        StockMovement m = new StockMovement();
        m.setId(rs.getInt("id"));
        m.setProductId(rs.getInt("product_id"));
        m.setProductName(rs.getString("product_name"));
        m.setProductCode(rs.getString("product_code"));
        m.setMovementType(rs.getString("movement_type"));
        m.setQuantity(rs.getInt("quantity"));
        m.setPreviousStock(rs.getInt("previous_stock"));
        m.setNewStock(rs.getInt("new_stock"));
        m.setReferenceType(rs.getString("reference_type"));
        m.setReferenceNumber(rs.getString("reference_number"));
        m.setNotes(rs.getString("notes"));
        m.setCreatedBy(rs.getInt("created_by"));
        m.setCreatedByName(rs.getString("created_by_name"));
        m.setCreatedAt(rs.getTimestamp("created_at"));
        return m;
    }
}