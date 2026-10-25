package com.aceerp.repository;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.Product;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ProductRepository {

    private static final Logger log = LoggerFactory.getLogger(ProductRepository.class);

    public List<Product> findAll() {
        List<Product> list = new ArrayList<>();
        String sql = "SELECT * FROM products ORDER BY name";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(mapRow(rs));
        } catch (SQLException e) {
            log.error("Failed to load products", e);
        }
        return list;
    }

    public Optional<Product> findById(int id) {
        String sql = "SELECT * FROM products WHERE id = ?";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to find product {}", id, e);
        }
        return Optional.empty();
    }

    public int insert(Product p, Integer createdBy) throws SQLException {
        String sql = "INSERT INTO products (product_code, name, product_type, category, brand, " +
                "model, unit, selling_price, cost_price, tax_rate, stock_quantity, " +
                "min_stock_level, is_active, created_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, p.getProductCode());
            ps.setString(2, p.getName());
            ps.setString(3, p.getProductType());
            ps.setString(4, p.getCategory());
            ps.setString(5, p.getBrand());
            ps.setString(6, p.getModel());
            ps.setString(7, p.getUnit());
            ps.setBigDecimal(8, p.getSellingPrice());
            ps.setBigDecimal(9, p.getCostPrice());
            ps.setBigDecimal(10, p.getTaxRate());
            ps.setInt(11, p.getStockQuantity());
            ps.setInt(12, p.getMinStockLevel());
            ps.setBoolean(13, p.isActive());
            if (createdBy != null) ps.setInt(14, createdBy);
            else ps.setNull(14, Types.INTEGER);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        throw new SQLException("Failed to insert product");
    }

    public void update(Product p, Integer updatedBy) throws SQLException {
        String sql = "UPDATE products SET name=?, product_type=?, category=?, brand=?, model=?, " +
                "unit=?, selling_price=?, cost_price=?, tax_rate=?, stock_quantity=?, " +
                "min_stock_level=?, is_active=?, updated_by=? WHERE id=?";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, p.getName());
            ps.setString(2, p.getProductType());
            ps.setString(3, p.getCategory());
            ps.setString(4, p.getBrand());
            ps.setString(5, p.getModel());
            ps.setString(6, p.getUnit());
            ps.setBigDecimal(7, p.getSellingPrice());
            ps.setBigDecimal(8, p.getCostPrice());
            ps.setBigDecimal(9, p.getTaxRate());
            ps.setInt(10, p.getStockQuantity());
            ps.setInt(11, p.getMinStockLevel());
            ps.setBoolean(12, p.isActive());
            if (updatedBy != null) ps.setInt(13, updatedBy);
            else ps.setNull(13, Types.INTEGER);
            ps.setInt(14, p.getId());
            ps.executeUpdate();
        }
    }

    public void deactivate(int id, Integer updatedBy) throws SQLException {
        String sql = "UPDATE products SET is_active=false, updated_by=? WHERE id=?";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (updatedBy != null) ps.setInt(1, updatedBy);
            else ps.setNull(1, Types.INTEGER);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public String generateNextCode() {
        String sql = "SELECT product_code FROM products WHERE product_code LIKE 'PROD-%' ORDER BY product_code DESC LIMIT 1";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String last = rs.getString(1);
                int num = Integer.parseInt(last.substring(5)) + 1;
                return String.format("PROD-%03d", num);
            }
        } catch (Exception e) {
            log.warn("Could not generate product code", e);
        }
        return "PROD-001";
    }

    private Product mapRow(ResultSet rs) throws SQLException {
        Product p = new Product();
        p.setId(rs.getInt("id"));
        p.setProductCode(rs.getString("product_code"));
        p.setName(rs.getString("name"));
        p.setProductType(rs.getString("product_type"));
        p.setCategory(rs.getString("category"));
        p.setBrand(rs.getString("brand"));
        p.setModel(rs.getString("model"));
        p.setUnit(rs.getString("unit"));
        BigDecimal sp = rs.getBigDecimal("selling_price");
        p.setSellingPrice(sp != null ? sp : BigDecimal.ZERO);
        BigDecimal cp = rs.getBigDecimal("cost_price");
        p.setCostPrice(cp != null ? cp : BigDecimal.ZERO);
        BigDecimal tr = rs.getBigDecimal("tax_rate");
        p.setTaxRate(tr != null ? tr : BigDecimal.ZERO);
        p.setStockQuantity(rs.getInt("stock_quantity"));
        p.setMinStockLevel(rs.getInt("min_stock_level"));
        p.setActive(rs.getBoolean("is_active"));
      p.setCreatedAt(rs.getTimestamp("created_at"));
p.setUpdatedAt(rs.getTimestamp("updated_at"));
        return p;
    }

        /**
     * Searches products by name or code.
     * Used by the smart product picker — as you type, this finds matches.
     */
    public List<Product> search(String query) {
        List<Product> list = new ArrayList<>();
        String sql = "SELECT * FROM products WHERE is_active = true " +
            "AND (name ILIKE ? OR product_code ILIKE ?) " +
            "ORDER BY name LIMIT 20";

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String pattern = "%" + query + "%";
            ps.setString(1, pattern);
            ps.setString(2, pattern);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to search products: " + query, e);
        }
        return list;
    }

    

    /**
     * Updates the stock quantity for a product.
     * Uses a provided connection so it can be part of a transaction
     * together with the stock movement insert.
     */
    public void updateStock(int productId, int newStock, Connection conn) throws SQLException {
        String sql = "UPDATE products SET stock_quantity = ?, " +
            "updated_at = CURRENT_TIMESTAMP WHERE id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, newStock);
            ps.setInt(2, productId);
            ps.executeUpdate();
        }
        log.info("Updated product {} stock to {}", productId, newStock);
    }
        /**
     * Gets all distinct (unique) values from a specific column.
     * Used to populate smart search suggestions.
     * 
     * Example: getDistinctValues("brand") returns ["Daikin", "Gree", "LG", ...]
     * 
     * IMPORTANT: The column name is NOT from user input — it's hardcoded
     * by the service layer, so there's no SQL injection risk.
     */
    public List<String> getDistinctValues(String columnName) {
        List<String> values = new ArrayList<>();

        // Whitelist of allowed column names to prevent SQL injection
        List<String> allowed = List.of("product_type", "category", "brand", "unit");
        if (!allowed.contains(columnName)) {
            log.warn("Blocked invalid column name: {}", columnName);
            return values;
        }

        // Build query with the safe column name
        String sql = "SELECT DISTINCT " + columnName +
            " FROM products WHERE " + columnName +
            " IS NOT NULL AND " + columnName + " != '' " +
            "AND is_active = true ORDER BY " + columnName;

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                values.add(rs.getString(1));
            }
        } catch (SQLException e) {
            log.error("Failed to get distinct values for " + columnName, e);
        }
        return values;
    }

    public List<Product> getLowStock() {
        List<Product> list = new ArrayList<>();
        String sql = "SELECT * FROM products WHERE is_active = true " +
            "AND stock_quantity <= min_stock_level ORDER BY stock_quantity ASC";

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("Failed to get low stock products", e);
        }
        return list;
    }
}   