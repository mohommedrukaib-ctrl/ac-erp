package com.aceerp.repository;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.Supplier;
import com.aceerp.model.SupplierBill;
import com.aceerp.model.SupplierBillLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SupplierBillRepository {

    private static final Logger logger = LoggerFactory.getLogger(SupplierBillRepository.class);
    private static SupplierBillRepository instance;

    private SupplierBillRepository() {}

    public static synchronized SupplierBillRepository getInstance() {
        if (instance == null) {
            instance = new SupplierBillRepository();
        }
        return instance;
    }

    public List<SupplierBill> findAll() {
        List<SupplierBill> list = new ArrayList<>();
        String sql = """
            SELECT b.*, s.name AS supplier_name, s.phone AS supplier_phone, s.email AS supplier_email
            FROM supplier_bills b
            JOIN suppliers s ON b.supplier_id = s.id
            ORDER BY b.id DESC
        """;

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                SupplierBill bill = mapBill(rs);
                bill.setLines(findLinesByBillId(bill.getId(), conn));
                list.add(bill);
            }
        } catch (SQLException e) {
            logger.error("Error fetching supplier bills", e);
        }
        return list;
    }

    public SupplierBill findById(int id) {
        String sql = """
            SELECT b.*, s.name AS supplier_name, s.phone AS supplier_phone, s.email AS supplier_email
            FROM supplier_bills b
            JOIN suppliers s ON b.supplier_id = s.id
            WHERE b.id = ?
        """;

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    SupplierBill bill = mapBill(rs);
                    bill.setLines(findLinesByBillId(bill.getId(), conn));
                    return bill;
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching supplier bill id={}", id, e);
        }
        return null;
    }

    public SupplierBill save(SupplierBill bill) {
        String insertBillSql = """
            INSERT INTO supplier_bills (bill_number, supplier_id, po_id, grn_id, bill_date, due_date, supplier_invoice_ref, status, subtotal, tax_amount, total_amount, paid_amount, notes)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
        """;

        String insertLineSql = """
            INSERT INTO supplier_bill_lines (bill_id, product_id, description, quantity, unit_price, tax_rate, tax_amount, total_price, line_total)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                bill.recalculateTotal();

                try (PreparedStatement stmt = conn.prepareStatement(insertBillSql)) {
                    stmt.setString(1, bill.getBillNumber());
                    stmt.setInt(2, bill.getSupplierId());
                    stmt.setObject(3, bill.getPoId());
                    stmt.setObject(4, bill.getGrnId());
                    stmt.setDate(5, Date.valueOf(bill.getBillDate()));
                    stmt.setDate(6, bill.getDueDate() != null ? Date.valueOf(bill.getDueDate()) : null);
                    stmt.setString(7, bill.getSupplierInvoiceRef());
                    stmt.setString(8, bill.getStatus());
                    stmt.setBigDecimal(9, bill.getSubtotal());
                    stmt.setBigDecimal(10, bill.getTaxAmount());
                    stmt.setBigDecimal(11, bill.getTotalAmount());
                    stmt.setBigDecimal(12, bill.getPaidAmount());
                    stmt.setString(13, bill.getNotes());

                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            bill.setId(rs.getInt(1));
                        }
                    }
                }

                if (bill.getLines() != null) {
                    try (PreparedStatement stmt = conn.prepareStatement(insertLineSql)) {
                        for (SupplierBillLine line : bill.getLines()) {
                            stmt.setInt(1, bill.getId());
                            stmt.setObject(2, line.getProductId());
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
                return findById(bill.getId());

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            logger.error("Error saving supplier bill", e);
            throw new RuntimeException("Failed to save supplier bill", e);
        }
    }

    private List<SupplierBillLine> findLinesByBillId(int billId, Connection conn) throws SQLException {
        List<SupplierBillLine> lines = new ArrayList<>();
        String sql = "SELECT * FROM supplier_bill_lines WHERE bill_id = ? ORDER BY id ASC";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, billId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    SupplierBillLine line = new SupplierBillLine();
                    line.setId(rs.getInt("id"));
                    line.setBillId(rs.getInt("bill_id"));
                    line.setProductId((Integer) rs.getObject("product_id"));
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

    private SupplierBill mapBill(ResultSet rs) throws SQLException {
        SupplierBill bill = new SupplierBill();
        bill.setId(rs.getInt("id"));
        bill.setBillNumber(rs.getString("bill_number"));
        bill.setSupplierId(rs.getInt("supplier_id"));
        bill.setPoId((Integer) rs.getObject("po_id"));
        bill.setGrnId((Integer) rs.getObject("grn_id"));
        bill.setBillDate(rs.getDate("bill_date").toLocalDate());
        Date dDate = rs.getDate("due_date");
        if (dDate != null) bill.setDueDate(dDate.toLocalDate());
        bill.setSupplierInvoiceRef(rs.getString("supplier_invoice_ref"));
        bill.setStatus(rs.getString("status"));
        bill.setSubtotal(rs.getBigDecimal("subtotal"));
        bill.setTaxAmount(rs.getBigDecimal("tax_amount"));
        bill.setTotalAmount(rs.getBigDecimal("total_amount"));
        bill.setPaidAmount(rs.getBigDecimal("paid_amount"));
        bill.setNotes(rs.getString("notes"));

        Supplier supplier = new Supplier();
        supplier.setId(bill.getSupplierId());
        supplier.setName(rs.getString("supplier_name"));
        supplier.setPhone(rs.getString("supplier_phone"));
        supplier.setEmail(rs.getString("supplier_email"));
        bill.setSupplier(supplier);

        return bill;
    }
}