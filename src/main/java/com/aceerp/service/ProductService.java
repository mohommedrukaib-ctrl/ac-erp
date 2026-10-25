package com.aceerp.service;

import com.aceerp.model.Product;
import com.aceerp.model.UserSession;
import com.aceerp.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private final ProductRepository repo = new ProductRepository();

    public List<Product> listAll() {
        return repo.findAll();
    }

    public void addProduct(String name, String productType, String category,
                           String brand, String model, String unit,
                           BigDecimal sellingPrice, BigDecimal costPrice,
                           BigDecimal taxRate, int stockQty, int minStock)
            throws SQLException {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Product name is required.");
        }
        if (productType == null || productType.isEmpty()) {
            throw new IllegalArgumentException("Product type is required.");
        }
        Product p = new Product();
        p.setProductCode(repo.generateNextCode());
        p.setName(name.trim());
        p.setProductType(productType);
        p.setCategory(category != null ? category.trim() : null);
        p.setBrand(brand != null ? brand.trim() : null);
        p.setModel(model != null ? model.trim() : null);
        p.setUnit(unit != null ? unit.trim() : null);
        p.setSellingPrice(sellingPrice != null ? sellingPrice : BigDecimal.ZERO);
        p.setCostPrice(costPrice != null ? costPrice : BigDecimal.ZERO);
        p.setTaxRate(taxRate != null ? taxRate : BigDecimal.ZERO);
        p.setStockQuantity(stockQty);
        p.setMinStockLevel(minStock);

        repo.insert(p, getCurrentUserId());
        log.info("Product created: {}", p.getName());
    }

    public void updateProduct(Product p) throws SQLException {
        if (p.getName() == null || p.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Product name is required.");
        }
        repo.update(p, getCurrentUserId());
        log.info("Product updated: {}", p.getName());
    }

    public void deactivate(int id) throws SQLException {
        repo.deactivate(id, getCurrentUserId());
        log.info("Product deactivated: id={}", id);
    }

    private Integer getCurrentUserId() {
        try {
            if (UserSession.getInstance().isLoggedIn()) {
                return UserSession.getInstance().getCurrentUser().getId();
            }
        } catch (Exception e) { /* ignore */ }
        return null;
    }

        /**
     * Searches products by name or code for the smart picker.
     */
    public List<Product> searchProducts(String query) {
        if (query == null || query.isBlank()) {
            return new ArrayList<>();
        }
        return repo.search(query.trim());
    }

        /**
     * Gets all unique values for a product field (brand, category, etc.)
     * Used by the smart search to show suggestions.
     */
    public List<String> getDistinctValues(String fieldName) {
        return repo.getDistinctValues(fieldName);
    }
}