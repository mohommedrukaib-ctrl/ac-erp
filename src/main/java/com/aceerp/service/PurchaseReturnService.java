package com.aceerp.service;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.PurchaseReturn;
import com.aceerp.model.PurchaseReturnLine;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class PurchaseReturnService {

    public PurchaseReturnService() {}

    public List<PurchaseReturn> getAllReturns() {
        List<PurchaseReturn> list = new ArrayList<>();
        String sql = "SELECT pr.*, s.name AS supplier_name FROM purchase_returns pr " +
                     "LEFT JOIN suppliers s ON pr.supplier_id = s.id ORDER BY pr.id DESC";

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                PurchaseReturn pr = new PurchaseReturn();
                pr.setId(rs.getInt("id"));
                pr.setReturnNumber(rs.getString("return_number"));
                pr.setSupplierId(rs.getInt("supplier_id"));
                pr.setSupplierName(rs.getString("supplier_name"));

                Object gObj = rs.getObject("grn_id");
                if (gObj != null) pr.setGrnId((Integer) gObj);

                Object bObj = rs.getObject("bill_id");
                if (bObj != null) pr.setBillId((Integer) bObj);

                Date rDate = rs.getDate("return_date");
                if (rDate != null) pr.setReturnDate(rDate.toLocalDate());

                pr.setStatus(rs.getString("status"));
                pr.setTotalAmount(rs.getBigDecimal("total_amount"));
                pr.setReason(rs.getString("reason"));

                pr.setLines(getLinesForReturn(conn, pr.getId()));
                list.add(pr);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void processReturnAndDeductStock(PurchaseReturn pr) {
        pr.recalculateTotal();

        if (pr.getReturnNumber() == null || pr.getReturnNumber().isBlank()) {
            pr.setReturnNumber(generateNextReturnNumber());
        }

        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Insert Return Header
                String insertSql = "INSERT INTO purchase_returns " +
                        "(return_number, supplier_id, grn_id, bill_id, return_date, status, total_amount, reason) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";

                try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                    stmt.setString(1, pr.getReturnNumber());
                    stmt.setInt(2, pr.getSupplierId());
                    if (pr.getGrnId() != null && pr.getGrnId() > 0) stmt.setInt(3, pr.getGrnId()); else stmt.setNull(3, Types.INTEGER);
                    if (pr.getBillId() != null && pr.getBillId() > 0) stmt.setInt(4, pr.getBillId()); else stmt.setNull(4, Types.INTEGER);
                    stmt.setDate(5, Date.valueOf(pr.getReturnDate() != null ? pr.getReturnDate() : LocalDate.now()));
                    stmt.setString(6, pr.getStatus() != null ? pr.getStatus() : "COMPLETED");
                    stmt.setBigDecimal(7, pr.getTotalAmount());
                    stmt.setString(8, pr.getReason());

                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) pr.setId(rs.getInt(1));
                }

                // 2. Insert Lines & Deduct Inventory
                if (pr.getLines() != null && !pr.getLines().isEmpty()) {
                    String lineSql = "INSERT INTO purchase_return_lines (return_id, product_id, description, quantity, unit_cost, line_total) VALUES (?, ?, ?, ?, ?, ?)";
                    String stockDeductSql = "UPDATE products SET stock_quantity = stock_quantity - ? WHERE id = ?";
                    String stockMovementSql = "INSERT INTO stock_movements (product_id, movement_type, quantity, reference_type, reference_id, notes) VALUES (?, 'PURCHASE_RETURN', ?, 'DEBIT_NOTE', ?, ?)";

                    try (PreparedStatement stmtLine = conn.prepareStatement(lineSql);
                         PreparedStatement stmtStock = conn.prepareStatement(stockDeductSql);
                         PreparedStatement stmtMove = conn.prepareStatement(stockMovementSql)) {

                        for (PurchaseReturnLine line : pr.getLines()) {
                            stmtLine.setInt(1, pr.getId());
                            if (line.getProductId() > 0) stmtLine.setInt(2, line.getProductId()); else stmtLine.setNull(2, Types.INTEGER);
                            stmtLine.setString(3, line.getDescription());
                            stmtLine.setBigDecimal(4, line.getQuantity());
                            stmtLine.setBigDecimal(5, line.getUnitCost());
                            stmtLine.setBigDecimal(6, line.getLineTotal());
                            stmtLine.addBatch();

                            if (line.getProductId() > 0 && line.getQuantity().compareTo(java.math.BigDecimal.ZERO) > 0) {
                                stmtStock.setBigDecimal(1, line.getQuantity());
                                stmtStock.setInt(2, line.getProductId());
                                stmtStock.addBatch();

                                stmtMove.setInt(1, line.getProductId());
                                stmtMove.setBigDecimal(2, line.getQuantity().negate());
                                stmtMove.setInt(3, pr.getId());
                                stmtMove.setString(4, "Return #" + pr.getReturnNumber() + ": " + line.getDescription());
                                stmtMove.addBatch();
                            }
                        }
                        stmtLine.executeBatch();
                        stmtStock.executeBatch();
                        stmtMove.executeBatch();
                    }
                }

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw new RuntimeException("Failed to process purchase return: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error during return: " + e.getMessage(), e);
        }
    }

    public String generateNextReturnNumber() {
        String prefix = "DN-" + LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
        String sql = "SELECT return_number FROM purchase_returns WHERE return_number LIKE ? ORDER BY id DESC LIMIT 1";

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, prefix + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String last = rs.getString("return_number");
                    int seq = Integer.parseInt(last.substring(last.lastIndexOf('-') + 1)) + 1;
                    return String.format("%s%04d", prefix, seq);
                }
            }
        } catch (Exception ignored) {}

        return prefix + "0001";
    }

    private List<PurchaseReturnLine> getLinesForReturn(Connection conn, int returnId) throws SQLException {
        List<PurchaseReturnLine> lines = new ArrayList<>();
        String sql = "SELECT * FROM purchase_return_lines WHERE return_id = ? ORDER BY id ASC";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, returnId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    PurchaseReturnLine line = new PurchaseReturnLine();
                    line.setId(rs.getInt("id"));
                    line.setReturnId(rs.getInt("return_id"));

                    Object pObj = rs.getObject("product_id");
                    if (pObj != null) line.setProductId((Integer) pObj);

                    line.setDescription(rs.getString("description"));
                    line.setQuantity(rs.getBigDecimal("quantity"));
                    line.setUnitCost(rs.getBigDecimal("unit_cost"));
                    line.setLineTotal(rs.getBigDecimal("line_total"));
                    lines.add(line);
                }
            }
        }
        return lines;
    }
}