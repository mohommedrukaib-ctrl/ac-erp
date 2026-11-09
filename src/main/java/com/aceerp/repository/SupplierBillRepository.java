package com.aceerp.repository;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.Supplier;
import com.aceerp.model.SupplierBill;
import com.aceerp.model.SupplierBillLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
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

    private static final String SELECT_BASE = """
        SELECT b.*, s.name AS supplier_name, s.phone AS supplier_phone, s.email AS supplier_email
        FROM supplier_bills b
        LEFT JOIN suppliers s ON b.supplier_id = s.id
    """;

    public List<SupplierBill> findAll() {
        List<SupplierBill> list = new ArrayList<>();
        String sql = SELECT_BASE + " ORDER BY b.id DESC";

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
        String sql = SELECT_BASE + " WHERE b.id = ?";

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
        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                bill.recalculateTotal();

                if (bill.getId() <= 0) {
                    insertBill(conn, bill);
                } else {
                    updateBill(conn, bill);
                }
                saveLines(conn, bill);

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            logger.error("Error saving supplier bill", e);
            throw new RuntimeException("Failed to save supplier bill: " + e.getMessage(), e);
        }
        SupplierBill reloaded = findById(bill.getId());
        return reloaded != null ? reloaded : bill;
    }

    private void insertBill(Connection conn, SupplierBill bill) throws SQLException {
        String sql = """
            INSERT INTO supplier_bills (bill_number, supplier_id, po_id, grn_id, bill_date, due_date,
                supplier_invoice_ref, status, subtotal, tax_amount, total_amount, paid_amount, notes)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
        """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, bill.getBillNumber());
            stmt.setInt(2, bill.getSupplierId());
            setNullableInt(stmt, 3, bill.getPoId());
            setNullableInt(stmt, 4, bill.getGrnId());
            stmt.setDate(5, Date.valueOf(bill.getBillDate() != null ? bill.getBillDate() : java.time.LocalDate.now()));
            stmt.setDate(6, bill.getDueDate() != null ? Date.valueOf(bill.getDueDate()) : null);
            stmt.setString(7, bill.getSupplierInvoiceRef());
            stmt.setString(8, bill.getStatus() != null ? bill.getStatus() : "UNPAID");
            stmt.setBigDecimal(9, nz(bill.getSubtotal()));
            stmt.setBigDecimal(10, nz(bill.getTaxAmount()));
            stmt.setBigDecimal(11, nz(bill.getTotalAmount()));
            stmt.setBigDecimal(12, nz(bill.getPaidAmount()));
            stmt.setString(13, bill.getNotes());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) bill.setId(rs.getInt(1));
            }
        }
    }

    private void updateBill(Connection conn, SupplierBill bill) throws SQLException {
        // paid_amount intentionally NOT updated here; it is managed by handlePayBill
        String sql = """
            UPDATE supplier_bills SET supplier_id=?, po_id=?, grn_id=?, bill_date=?, due_date=?,
                supplier_invoice_ref=?, status=?, subtotal=?, tax_amount=?, total_amount=?, notes=?
            WHERE id=?
        """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, bill.getSupplierId());
            setNullableInt(stmt, 2, bill.getPoId());
            setNullableInt(stmt, 3, bill.getGrnId());
            stmt.setDate(4, Date.valueOf(bill.getBillDate() != null ? bill.getBillDate() : java.time.LocalDate.now()));
            stmt.setDate(5, bill.getDueDate() != null ? Date.valueOf(bill.getDueDate()) : null);
            stmt.setString(6, bill.getSupplierInvoiceRef());
            stmt.setString(7, bill.getStatus() != null ? bill.getStatus() : "UNPAID");
            stmt.setBigDecimal(8, nz(bill.getSubtotal()));
            stmt.setBigDecimal(9, nz(bill.getTaxAmount()));
            stmt.setBigDecimal(10, nz(bill.getTotalAmount()));
            stmt.setString(11, bill.getNotes());
            stmt.setInt(12, bill.getId());
            stmt.executeUpdate();
        }
    }

    private void saveLines(Connection conn, SupplierBill bill) throws SQLException {
        try (PreparedStatement del = conn.prepareStatement("DELETE FROM supplier_bill_lines WHERE bill_id = ?")) {
            del.setInt(1, bill.getId());
            del.executeUpdate();
        }

        if (bill.getLines() == null || bill.getLines().isEmpty()) return;

        String sql = """
            INSERT INTO supplier_bill_lines (bill_id, product_id, description, quantity, unit_price,
                discount_percent, notes, tax_rate, tax_amount, total_price, line_total, discount_type, discount_value, package_price)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (SupplierBillLine line : bill.getLines()) {
                line.recalculateTotal();
                stmt.setInt(1, bill.getId());
                if (line.getProductId() > 0) stmt.setInt(2, line.getProductId());
                else stmt.setNull(2, Types.INTEGER);
                stmt.setString(3, line.getDescription() != null ? line.getDescription() : "Custom Line Item");
                stmt.setBigDecimal(4, line.getQuantity() != null ? line.getQuantity() : BigDecimal.ONE);
                stmt.setBigDecimal(5, nz(line.getUnitPrice()));
                stmt.setBigDecimal(6, nz(line.getDiscountPercent()));
                stmt.setString(7, line.getNotes());
                stmt.setBigDecimal(8, nz(line.getTaxRate()));
                stmt.setBigDecimal(9, nz(line.getTaxAmount()));
                stmt.setBigDecimal(10, nz(line.getTotalPrice()));
                stmt.setBigDecimal(11, nz(line.getLineTotal()));
                stmt.setString(12, line.getDiscountType());
                stmt.setBigDecimal(13, nz(line.getDiscountValue()));
                stmt.setBoolean(14, line.isPackagePrice());
                stmt.addBatch();
            }
            stmt.executeBatch();
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

                    int prodId = rs.getInt("product_id");
                    line.setProductId(rs.wasNull() ? 0 : prodId);

                    line.setDescription(rs.getString("description"));
                    line.setQuantity(rs.getBigDecimal("quantity"));
                    line.setUnitPrice(rs.getBigDecimal("unit_price"));

                    try {
                        line.setDiscountType(rs.getString("discount_type"));
                        line.setDiscountValue(rs.getBigDecimal("discount_value"));
                        line.setPackagePrice(rs.getBoolean("package_price"));
                    } catch (SQLException ignored) {}
                    try { line.setNotes(rs.getString("notes")); } catch (SQLException ignored) {}

                    BigDecimal rate = rs.getBigDecimal("tax_rate");
                    line.setTaxRate(rate);
                    // setTaxable triggers recalculateTotal() using the loaded rate
                    line.setTaxable(rate != null && rate.compareTo(BigDecimal.ZERO) > 0);
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

        int poId = rs.getInt("po_id");
        bill.setPoId(rs.wasNull() ? null : poId);
        int grnId = rs.getInt("grn_id");
        bill.setGrnId(rs.wasNull() ? null : grnId);

        Date bDate = rs.getDate("bill_date");
        if (bDate != null) bill.setBillDate(bDate.toLocalDate());
        Date dDate = rs.getDate("due_date");
        if (dDate != null) bill.setDueDate(dDate.toLocalDate());

        bill.setSupplierInvoiceRef(rs.getString("supplier_invoice_ref"));
        bill.setStatus(rs.getString("status"));
        bill.setSubtotal(nz(rs.getBigDecimal("subtotal")));
        bill.setTaxAmount(nz(rs.getBigDecimal("tax_amount")));
        bill.setTotalAmount(nz(rs.getBigDecimal("total_amount")));
        bill.setPaidAmount(nz(rs.getBigDecimal("paid_amount")));
        bill.setNotes(rs.getString("notes"));

        if (bill.getSupplierId() > 0) {
            Supplier supplier = new Supplier();
            supplier.setId(bill.getSupplierId());
            supplier.setName(rs.getString("supplier_name"));
            supplier.setPhone(rs.getString("supplier_phone"));
            supplier.setEmail(rs.getString("supplier_email"));
            bill.setSupplier(supplier);
        }
        return bill;
    }

    private static void setNullableInt(PreparedStatement ps, int idx, Integer val) throws SQLException {
        if (val != null && val > 0) ps.setInt(idx, val);
        else ps.setNull(idx, Types.INTEGER);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}