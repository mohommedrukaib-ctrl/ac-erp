package com.aceerp.service;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.SupplierPayment;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class SupplierPaymentService {

    private static Boolean hasBillIdCol;
    private static Boolean hasSupplierBillIdCol;

    public SupplierPaymentService() {}

    private static boolean checkColumnExists(Connection conn, String tableName, String columnName) {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, tableName, columnName)) {
            if (rs.next()) return true;
        } catch (Exception ignored) {}
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, tableName.toUpperCase(), columnName.toUpperCase())) {
            if (rs.next()) return true;
        } catch (Exception ignored) {}
        return false;
    }

    private static String getBillColumnName(Connection conn) {
        if (hasBillIdCol == null || hasSupplierBillIdCol == null) {
            hasBillIdCol = checkColumnExists(conn, "supplier_payments", "bill_id");
            hasSupplierBillIdCol = checkColumnExists(conn, "supplier_payments", "supplier_bill_id");
        }
        if (Boolean.TRUE.equals(hasSupplierBillIdCol)) {
            return "supplier_bill_id";
        }
        return "bill_id";
    }

    public List<SupplierPayment> getAllPayments() {
        List<SupplierPayment> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            String billCol = getBillColumnName(conn);
            String sql = "SELECT p.*, s.name AS supplier_name, b.bill_number FROM supplier_payments p " +
                         "LEFT JOIN suppliers s ON p.supplier_id = s.id " +
                         "LEFT JOIN supplier_bills b ON p." + billCol + " = b.id " +
                         "ORDER BY p.id DESC";

            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    SupplierPayment p = new SupplierPayment();
                    p.setId(rs.getInt("id"));
                    p.setPaymentNumber(rs.getString("payment_number"));

                    Object bId = null;
                    try {
                        bId = rs.getObject(billCol);
                    } catch (SQLException ignored) {
                        try { bId = rs.getObject("bill_id"); } catch (SQLException ignored2) {}
                    }
                    if (bId != null && bId instanceof Number num) {
                        p.setBillId(num.intValue());
                    }

                    p.setBillNumber(rs.getString("bill_number"));
                    p.setSupplierId(rs.getInt("supplier_id"));
                    p.setSupplierName(rs.getString("supplier_name"));

                    Date pDate = rs.getDate("payment_date");
                    if (pDate != null) p.setPaymentDate(pDate.toLocalDate());

                    p.setAmount(rs.getBigDecimal("amount"));
                    p.setPaymentMethod(rs.getString("payment_method"));
                    p.setReferenceNumber(rs.getString("reference_number"));
                    p.setNotes(rs.getString("notes"));

                    Timestamp ts = rs.getTimestamp("created_at");
                    if (ts != null) p.setCreatedAt(ts.toLocalDateTime());

                    list.add(p);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public void savePayment(SupplierPayment payment) {
        if (payment.getPaymentNumber() == null || payment.getPaymentNumber().isBlank()) {
            payment.setPaymentNumber(generateNextPaymentNumber());
        }

        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                String billCol = getBillColumnName(conn);
                String sql = "INSERT INTO supplier_payments " +
                        "(payment_number, " + billCol + ", supplier_id, payment_date, amount, payment_method, reference_number, notes) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, payment.getPaymentNumber());
                    if (payment.getBillId() != null && payment.getBillId() > 0) stmt.setInt(2, payment.getBillId());
                    else stmt.setNull(2, Types.INTEGER);

                    stmt.setInt(3, payment.getSupplierId());
                    stmt.setDate(4, Date.valueOf(payment.getPaymentDate() != null ? payment.getPaymentDate() : LocalDate.now()));
                    stmt.setBigDecimal(5, payment.getAmount());
                    stmt.setString(6, payment.getPaymentMethod());
                    stmt.setString(7, payment.getReferenceNumber());
                    stmt.setString(8, payment.getNotes());
                    stmt.executeUpdate();
                }

                // If linked to a bill, update bill's paid amount & status
                if (payment.getBillId() != null && payment.getBillId() > 0) {
                    String updateBillSql = "UPDATE supplier_bills SET paid_amount = paid_amount + ?, " +
                            "status = CASE WHEN (paid_amount + ?) >= total_amount THEN 'PAID' ELSE 'PARTIALLY_PAID' END " +
                            "WHERE id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(updateBillSql)) {
                        stmt.setBigDecimal(1, payment.getAmount());
                        stmt.setBigDecimal(2, payment.getAmount());
                        stmt.setInt(3, payment.getBillId());
                        stmt.executeUpdate();
                    }
                }

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw new RuntimeException("Error processing supplier payment: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
    }

    public String generateNextPaymentNumber() {
        String prefix = "VPAY-" + LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + "-";
        String sql = "SELECT payment_number FROM supplier_payments WHERE payment_number LIKE ? ORDER BY id DESC LIMIT 1";

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, prefix + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String last = rs.getString("payment_number");
                    int seq = Integer.parseInt(last.substring(last.lastIndexOf('-') + 1)) + 1;
                    return String.format("%s%04d", prefix, seq);
                }
            }
        } catch (Exception ignored) {}

        return prefix + "0001";
    }
}