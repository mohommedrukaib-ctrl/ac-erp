package com.aceerp.service;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.SupplierBill;
import com.aceerp.model.SupplierBillLine;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SupplierBillService {

    public SupplierBillService() {}

    public List<SupplierBill> listAll() {
        return getAllBills();
    }

    public List<SupplierBill> getAllBills() {
        List<SupplierBill> bills = new ArrayList<>();
        String sql = "SELECT b.*, s.name AS supplier_name FROM supplier_bills b " +
                     "LEFT JOIN suppliers s ON b.supplier_id = s.id ORDER BY b.id DESC";

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                SupplierBill bill = mapRowToBill(rs);
                bill.setLines(getLinesForBill(conn, bill.getId()));
                bills.add(bill);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return bills;
    }

    public Optional<SupplierBill> getById(int id) {
        String sql = "SELECT b.*, s.name AS supplier_name FROM supplier_bills b " +
                     "LEFT JOIN suppliers s ON b.supplier_id = s.id WHERE b.id = ?";

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    SupplierBill bill = mapRowToBill(rs);
                    bill.setLines(getLinesForBill(conn, bill.getId()));
                    return Optional.of(bill);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    public SupplierBill saveBill(SupplierBill bill) {
        bill.recalculateTotal();

        if (bill.getBillNumber() == null || bill.getBillNumber().isBlank()) {
            bill.setBillNumber(generateNextBillNumber());
        }

        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (bill.getId() <= 0) {
                    String insertBillSql = "INSERT INTO supplier_bills " +
                            "(bill_number, supplier_id, po_id, grn_id, bill_date, due_date, " +
                            "supplier_invoice_ref, status, subtotal, tax_amount, total_amount, paid_amount, notes) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";

                    try (PreparedStatement stmt = conn.prepareStatement(insertBillSql)) {
                        stmt.setString(1, bill.getBillNumber());
                        stmt.setInt(2, bill.getSupplierId());
                        if (bill.getPoId() != null && bill.getPoId() > 0) stmt.setInt(3, bill.getPoId()); else stmt.setNull(3, Types.INTEGER);
                        if (bill.getGrnId() != null && bill.getGrnId() > 0) stmt.setInt(4, bill.getGrnId()); else stmt.setNull(4, Types.INTEGER);
                        stmt.setDate(5, bill.getBillDate() != null ? Date.valueOf(bill.getBillDate()) : Date.valueOf(LocalDate.now()));
                        stmt.setDate(6, bill.getDueDate() != null ? Date.valueOf(bill.getDueDate()) : Date.valueOf(LocalDate.now().plusDays(30)));
                        stmt.setString(7, bill.getSupplierInvoiceRef());
                        stmt.setString(8, bill.getStatus() != null ? bill.getStatus() : "UNPAID");
                        stmt.setBigDecimal(9, bill.getSubtotal());
                        stmt.setBigDecimal(10, bill.getTaxAmount());
                        stmt.setBigDecimal(11, bill.getTotalAmount());
                        stmt.setBigDecimal(12, bill.getPaidAmount() != null ? bill.getPaidAmount() : java.math.BigDecimal.ZERO);
                        stmt.setString(13, bill.getNotes());

                        ResultSet rs = stmt.executeQuery();
                        if (rs.next()) {
                            bill.setId(rs.getInt(1));
                        }
                    }
                } else {
                    String updateBillSql = "UPDATE supplier_bills SET " +
                            "supplier_id = ?, po_id = ?, grn_id = ?, bill_date = ?, due_date = ?, " +
                            "supplier_invoice_ref = ?, status = ?, subtotal = ?, tax_amount = ?, total_amount = ?, " +
                            "paid_amount = ?, notes = ? WHERE id = ?";

                    try (PreparedStatement stmt = conn.prepareStatement(updateBillSql)) {
                        stmt.setInt(1, bill.getSupplierId());
                        if (bill.getPoId() != null && bill.getPoId() > 0) stmt.setInt(2, bill.getPoId()); else stmt.setNull(2, Types.INTEGER);
                        if (bill.getGrnId() != null && bill.getGrnId() > 0) stmt.setInt(3, bill.getGrnId()); else stmt.setNull(3, Types.INTEGER);
                        stmt.setDate(4, bill.getBillDate() != null ? Date.valueOf(bill.getBillDate()) : Date.valueOf(LocalDate.now()));
                        stmt.setDate(5, bill.getDueDate() != null ? Date.valueOf(bill.getDueDate()) : Date.valueOf(LocalDate.now().plusDays(30)));
                        stmt.setString(6, bill.getSupplierInvoiceRef());
                        stmt.setString(7, bill.getStatus());
                        stmt.setBigDecimal(8, bill.getSubtotal());
                        stmt.setBigDecimal(9, bill.getTaxAmount());
                        stmt.setBigDecimal(10, bill.getTotalAmount());
                        stmt.setBigDecimal(11, bill.getPaidAmount() != null ? bill.getPaidAmount() : java.math.BigDecimal.ZERO);
                        stmt.setString(12, bill.getNotes());
                        stmt.setInt(13, bill.getId());
                        stmt.executeUpdate();
                    }

                    try (PreparedStatement delStmt = conn.prepareStatement("DELETE FROM supplier_bill_lines WHERE bill_id = ?")) {
                        delStmt.setInt(1, bill.getId());
                        delStmt.executeUpdate();
                    }
                }

                if (bill.getLines() != null && !bill.getLines().isEmpty()) {
                    String insertLineSql = "INSERT INTO supplier_bill_lines " +
                            "(bill_id, product_id, description, quantity, unit_price, tax_rate, tax_amount, total_price, line_total) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

                    try (PreparedStatement stmt = conn.prepareStatement(insertLineSql)) {
                        for (SupplierBillLine line : bill.getLines()) {
                            stmt.setInt(1, bill.getId());
                            if (line.getProductId() > 0) stmt.setInt(2, line.getProductId()); else stmt.setNull(2, Types.INTEGER);
                            stmt.setString(3, line.getDescription());
                            stmt.setBigDecimal(4, line.getQuantity());
                            stmt.setBigDecimal(5, line.getUnitPrice());
                            stmt.setBigDecimal(6, line.getTaxRate());
                            stmt.setBigDecimal(7, line.getTaxAmount());
                            stmt.setBigDecimal(8, line.getTotalPrice());
                            stmt.setBigDecimal(9, line.getLineTotal());
                            stmt.addBatch();
                        }
                        stmt.executeBatch();
                    }
                }

                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw new RuntimeException("Failed to save supplier bill: " + ex.getMessage(), ex);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error while saving supplier bill: " + e.getMessage(), e);
        }

        return bill;
    }

    public String generateNextBillNumber() {
        String todayPrefix = "BILL-" + LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
        String sql = "SELECT bill_number FROM supplier_bills WHERE bill_number LIKE ? ORDER BY id DESC LIMIT 1";

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, todayPrefix + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String lastNumber = rs.getString("bill_number");
                    String sequenceStr = lastNumber.substring(lastNumber.lastIndexOf('-') + 1);
                    int nextSeq = Integer.parseInt(sequenceStr) + 1;
                    return String.format("%s%04d", todayPrefix, nextSeq);
                }
            }
        } catch (Exception ignored) {}

        return todayPrefix + "0001";
    }

    private SupplierBill mapRowToBill(ResultSet rs) throws SQLException {
        SupplierBill b = new SupplierBill();
        b.setId(rs.getInt("id"));
        b.setBillNumber(rs.getString("bill_number"));
        b.setSupplierId(rs.getInt("supplier_id"));
        b.setSupplierName(rs.getString("supplier_name"));

        Object poObj = rs.getObject("po_id");
        if (poObj != null) b.setPoId((Integer) poObj);

        Object grnObj = rs.getObject("grn_id");
        if (grnObj != null) b.setGrnId((Integer) grnObj);

        Date bDate = rs.getDate("bill_date");
        if (bDate != null) b.setBillDate(bDate.toLocalDate());

        Date dDate = rs.getDate("due_date");
        if (dDate != null) b.setDueDate(dDate.toLocalDate());

        b.setSupplierInvoiceRef(rs.getString("supplier_invoice_ref"));
        b.setStatus(rs.getString("status"));
        b.setSubtotal(rs.getBigDecimal("subtotal"));
        b.setTaxAmount(rs.getBigDecimal("tax_amount"));
        b.setTotalAmount(rs.getBigDecimal("total_amount"));
        b.setPaidAmount(rs.getBigDecimal("paid_amount"));
        b.setNotes(rs.getString("notes"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) b.setCreatedAt(createdAt.toLocalDateTime());

        return b;
    }

    private List<SupplierBillLine> getLinesForBill(Connection conn, int billId) throws SQLException {
        List<SupplierBillLine> lines = new ArrayList<>();
        String sql = "SELECT * FROM supplier_bill_lines WHERE bill_id = ? ORDER BY id ASC";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, billId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    SupplierBillLine line = new SupplierBillLine();
                    line.setId(rs.getInt("id"));
                    line.setBillId(rs.getInt("bill_id"));

                    Object pObj = rs.getObject("product_id");
                    if (pObj != null) line.setProductId((Integer) pObj);

                    line.setDescription(rs.getString("description"));
                    line.setQuantity(rs.getBigDecimal("quantity"));
                    line.setUnitPrice(rs.getBigDecimal("unit_price"));
                    line.setTaxRate(rs.getBigDecimal("tax_rate"));
                    line.setTaxAmount(rs.getBigDecimal("tax_amount"));
                    line.setTotalPrice(rs.getBigDecimal("total_price"));
                    line.setLineTotal(rs.getBigDecimal("line_total"));
                    line.setTaxable(line.getTaxRate() != null && line.getTaxRate().compareTo(java.math.BigDecimal.ZERO) > 0);
                    lines.add(line);
                }
            }
        }
        return lines;
    }
}