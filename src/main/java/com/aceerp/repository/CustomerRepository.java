package com.aceerp.repository;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.Customer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CustomerRepository {

    private static final Logger log = LoggerFactory.getLogger(CustomerRepository.class);

    public List<Customer> findAll() {
        List<Customer> list = new ArrayList<>();
        String sql = "SELECT * FROM customers ORDER BY name";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to load customers", e);
        }
        return list;
    }

    public Optional<Customer> findById(int id) {
        String sql = "SELECT * FROM customers WHERE id = ?";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to find customer {}", id, e);
        }
        return Optional.empty();
    }

    public int insert(Customer c, Integer createdBy) throws SQLException {
        String sql = "INSERT INTO customers (customer_code, name, phone, email, address, city, " +
                "tax_number, notes, balance, is_active, created_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, c.getCustomerCode());
            ps.setString(2, c.getName());
            ps.setString(3, c.getPhone());
            ps.setString(4, c.getEmail());
            ps.setString(5, c.getAddress());
            ps.setString(6, c.getCity());
            ps.setString(7, c.getTaxNumber());
            ps.setString(8, c.getNotes());
            ps.setBigDecimal(9, c.getBalance());
            ps.setBoolean(10, c.isActive());
            if (createdBy != null) ps.setInt(11, createdBy);
            else ps.setNull(11, Types.INTEGER);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        throw new SQLException("Failed to insert customer");
    }

    public void update(Customer c, Integer updatedBy) throws SQLException {
        String sql = "UPDATE customers SET name=?, phone=?, email=?, address=?, city=?, " +
                "tax_number=?, notes=?, is_active=?, updated_by=? WHERE id=?";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, c.getName());
            ps.setString(2, c.getPhone());
            ps.setString(3, c.getEmail());
            ps.setString(4, c.getAddress());
            ps.setString(5, c.getCity());
            ps.setString(6, c.getTaxNumber());
            ps.setString(7, c.getNotes());
            ps.setBoolean(8, c.isActive());
            if (updatedBy != null) ps.setInt(9, updatedBy);
            else ps.setNull(9, Types.INTEGER);
            ps.setInt(10, c.getId());
            ps.executeUpdate();
        }
    }

    public void deactivate(int id, Integer updatedBy) throws SQLException {
        String sql = "UPDATE customers SET is_active=false, updated_by=? WHERE id=?";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (updatedBy != null) ps.setInt(1, updatedBy);
            else ps.setNull(1, Types.INTEGER);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public String generateNextCode() {
        String sql = "SELECT customer_code FROM customers WHERE customer_code LIKE 'CUST-%' ORDER BY customer_code DESC LIMIT 1";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String last = rs.getString(1);
                int num = Integer.parseInt(last.substring(5)) + 1;
                return String.format("CUST-%03d", num);
            }
        } catch (Exception e) {
            log.warn("Could not generate customer code", e);
        }
        return "CUST-001";
    }

    private Customer mapRow(ResultSet rs) throws SQLException {
        Customer c = new Customer();
        c.setId(rs.getInt("id"));
        c.setCustomerCode(rs.getString("customer_code"));
        c.setName(rs.getString("name"));
        c.setPhone(rs.getString("phone"));
        c.setEmail(rs.getString("email"));
        c.setAddress(rs.getString("address"));
        c.setCity(rs.getString("city"));
        c.setTaxNumber(rs.getString("tax_number"));
        c.setNotes(rs.getString("notes"));
        BigDecimal bal = rs.getBigDecimal("balance");
        c.setBalance(bal != null ? bal : BigDecimal.ZERO);
        c.setActive(rs.getBoolean("is_active"));
        c.setCreatedAt(rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toLocalDateTime() : null);
        c.setUpdatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null);
        return c;
    }
}