package com.aceerp.service;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class StockTransferService {

    private static final Logger logger = LoggerFactory.getLogger(StockTransferService.class);

    public List<Warehouse> getAllWarehouses() {
        List<Warehouse> list = new ArrayList<>();
        String sql = "SELECT * FROM warehouses WHERE is_active = TRUE ORDER BY code ASC";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Warehouse w = new Warehouse();
                w.setId(rs.getInt("id"));
                w.setCode(rs.getString("code"));
                w.setName(rs.getString("name"));
                w.setLocationType(rs.getString("location_type"));
                w.setAddress(rs.getString("address"));
                w.setActive(rs.getBoolean("is_active"));
                list.add(w);
            }
        } catch (SQLException e) {
            logger.error("Failed to list warehouses: {}", e.getMessage(), e);
        }
        return list;
    }

    public List<StockTransfer> getAllTransfers() {
        List<StockTransfer> list = new ArrayList<>();
        String sql = """
            SELECT t.id, t.transfer_number, t.source_warehouse_id, t.destination_warehouse_id,
                   t.transfer_date, t.status, t.notes,
                   ws.name AS src_name, wd.name AS dst_name
            FROM stock_transfers t
            JOIN warehouses ws ON t.source_warehouse_id = ws.id
            JOIN warehouses wd ON t.destination_warehouse_id = wd.id
            ORDER BY t.id DESC
            """;
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                StockTransfer st = new StockTransfer();
                st.setId(rs.getInt("id"));
                st.setTransferNumber(rs.getString("transfer_number"));
                st.setSourceWarehouseId(rs.getInt("source_warehouse_id"));
                st.setSourceWarehouseName(rs.getString("src_name"));
                st.setDestinationWarehouseId(rs.getInt("destination_warehouse_id"));
                st.setDestinationWarehouseName(rs.getString("dst_name"));
                st.setTransferDate(rs.getDate("transfer_date").toLocalDate());
                st.setStatus(rs.getString("status"));
                st.setNotes(rs.getString("notes"));
                list.add(st);
            }
        } catch (SQLException e) {
            logger.error("Failed to list stock transfers: {}", e.getMessage(), e);
        }
        return list;
    }

    public StockTransfer executeStockTransfer(StockTransfer transfer, int userId) {
        if (transfer.getSourceWarehouseId() == transfer.getDestinationWarehouseId()) {
            throw new IllegalArgumentException("Source and Destination warehouses cannot be the same.");
        }
        if (transfer.getLines().isEmpty()) {
            throw new IllegalArgumentException("Please add at least one line item to transfer.");
        }

        if (transfer.getTransferNumber() == null || transfer.getTransferNumber().isBlank()) {
            transfer.setTransferNumber(generateNextTransferNumber());
        }

        Connection conn = null;
        try {
            conn = DatabaseConfig.getInstance().getConnection();
            conn.setAutoCommit(false);

            // 1. Check & Update Warehouse Stocks
            for (StockTransferLine line : transfer.getLines()) {
                // Deduct source stock
                updateWarehouseStock(conn, transfer.getSourceWarehouseId(), line.getProductId(), -line.getQuantityTransferred());
                // Add destination stock
                updateWarehouseStock(conn, transfer.getDestinationWarehouseId(), line.getProductId(), line.getQuantityTransferred());
            }

            // 2. Insert Transfer Record
            String insertHeader = """
                INSERT INTO stock_transfers (transfer_number, source_warehouse_id, destination_warehouse_id, transfer_date, status, notes, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """;
            try (PreparedStatement ps = conn.prepareStatement(insertHeader)) {
                ps.setString(1, transfer.getTransferNumber());
                ps.setInt(2, transfer.getSourceWarehouseId());
                ps.setInt(3, transfer.getDestinationWarehouseId());
                ps.setDate(4, Date.valueOf(transfer.getTransferDate()));
                ps.setString(5, transfer.getStatus());
                ps.setString(6, transfer.getNotes());
                if (userId > 0) ps.setInt(7, userId); else ps.setNull(7, Types.INTEGER);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) transfer.setId(rs.getInt(1));
                }
            }

            // 3. Insert Lines
            String insertLine = """
                INSERT INTO stock_transfer_lines (stock_transfer_id, product_id, quantity_transferred, notes)
                VALUES (?, ?, ?, ?)
                """;
            try (PreparedStatement ps = conn.prepareStatement(insertLine)) {
                for (StockTransferLine line : transfer.getLines()) {
                    ps.setInt(1, transfer.getId());
                    ps.setInt(2, line.getProductId());
                    ps.setInt(3, line.getQuantityTransferred());
                    ps.setString(4, line.getNotes());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            conn.commit();
            AuditLogService.log("STOCK_TRANSFER", "STOCK_TRANSFER", transfer.getId(), "Transferred stock " + transfer.getTransferNumber());
            return transfer;

        } catch (Exception e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ignored) {}
            logger.error("Stock transfer failed: {}", e.getMessage(), e);
            throw new RuntimeException("Stock transfer failed: " + e.getMessage(), e);
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) {}
        }
    }

    private void updateWarehouseStock(Connection conn, int warehouseId, int productId, int deltaQty) throws SQLException {
        String upsert = """
            INSERT INTO warehouse_stocks (warehouse_id, product_id, stock_quantity)
            VALUES (?, ?, ?)
            ON CONFLICT (warehouse_id, product_id)
            DO UPDATE SET stock_quantity = warehouse_stocks.stock_quantity + EXCLUDED.stock_quantity
            """;
        try (PreparedStatement ps = conn.prepareStatement(upsert)) {
            ps.setInt(1, warehouseId);
            ps.setInt(2, productId);
            ps.setInt(3, deltaQty);
            ps.executeUpdate();
        }
    }

    private String generateNextTransferNumber() {
        String sql = "SELECT transfer_number FROM stock_transfers ORDER BY id DESC LIMIT 1";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String last = rs.getString(1);
                if (last != null && last.startsWith("TRF-")) {
                    try {
                        int num = Integer.parseInt(last.substring(4)) + 1;
                        return String.format("TRF-%05d", num);
                    } catch (NumberFormatException ignored) {}
                }
            }
        } catch (SQLException ignored) {}
        return "TRF-00001";
    }
}
