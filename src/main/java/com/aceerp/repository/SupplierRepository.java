package com.aceerp.repository;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SupplierRepository {

    private static final Logger log = LoggerFactory.getLogger(SupplierRepository.class);

    public List<Supplier> findAll() {
        List<Supplier> list = new ArrayList<>();
        String sql = "SELECT * FROM suppliers ORDER BY name";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            log.error("Failed to load suppliers", e);
        }
        return list;
    }

    public Optional<Supplier> findById(int id) {
        String sql = "SELECT * FROM suppliers WHERE id = ?";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to find supplier {}", id, e);
        }
        return Optional.empty();
    }

    public int insert(Supplier s, Integer createdBy) throws SQLException {
        String sql = "INSERT INTO suppliers (supplier_code, name, contact_person, phone, email, " +
                "address, city, tax_number, notes, balance, is_active, created_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, s.getSupplierCode());
            ps.setString(2, s.getName());
            ps.setString(3, s.getContactPerson());
            ps.setString(4, s.getPhone());
            ps.setString(5, s.getEmail());
            ps.setString(6, s.getAddress());
            ps.setString(7, s.getCity());
            ps.setString(8, s.getTaxNumber());
            ps.setString(9, s.getNotes());
            ps.setBigDecimal(10, s.getBalance());
            ps.setBoolean(11, s.isActive());
            if (createdBy != null) ps.setInt(12, createdBy);
            else ps.setNull(12, Types.INTEGER);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        throw new SQLException("Failed to insert supplier");
    }

    public void update(Supplier s, Integer updatedBy) throws SQLException {
        String sql = "UPDATE suppliers SET name=?, contact_person=?, phone=?, email=?, " +
                "address=?, city=?, tax_number=?, notes=?, is_active=?, updated_by=? WHERE id=?";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, s.getName());
            ps.setString(2, s.getContactPerson());
            ps.setString(3, s.getPhone());
            ps.setString(4, s.getEmail());
            ps.setString(5, s.getAddress());
            ps.setString(6, s.getCity());
            ps.setString(7, s.getTaxNumber());
            ps.setString(8, s.getNotes());
            ps.setBoolean(9, s.isActive());
            if (updatedBy != null) ps.setInt(10, updatedBy);
            else ps.setNull(10, Types.INTEGER);
            ps.setInt(11, s.getId());
            ps.executeUpdate();
        }
    }

    public void deactivate(int id, Integer updatedBy) throws SQLException {
        String sql = "UPDATE suppliers SET is_active=false, updated_by=? WHERE id=?";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (updatedBy != null) ps.setInt(1, updatedBy);
            else ps.setNull(1, Types.INTEGER);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public String generateNextCode() {
        String sql = "SELECT supplier_code FROM suppliers WHERE supplier_code LIKE 'SUP-%' ORDER BY supplier_code DESC LIMIT 1";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String last = rs.getString(1);
                int num = Integer.parseInt(last.substring(4)) + 1;
                return String.format("SUP-%03d", num);
            }
        } catch (Exception e) {
            log.warn("Could not generate supplier code", e);
        }
        return "SUP-001";
    }

    private Supplier mapRow(ResultSet rs) throws SQLException {
        Supplier s = new Supplier();
        s.setId(rs.getInt("id"));
        s.setSupplierCode(rs.getString("supplier_code"));
        s.setName(rs.getString("name"));
        s.setContactPerson(rs.getString("contact_person"));
        s.setPhone(rs.getString("phone"));
        s.setEmail(rs.getString("email"));
        s.setAddress(rs.getString("address"));
        s.setCity(rs.getString("city"));
        s.setTaxNumber(rs.getString("tax_number"));
        s.setNotes(rs.getString("notes"));
        BigDecimal bal = rs.getBigDecimal("balance");
        s.setBalance(bal != null ? bal : BigDecimal.ZERO);
        s.setActive(rs.getBoolean("is_active"));
        s.setCreatedAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        s.setUpdatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null);
        return s;
    }
}