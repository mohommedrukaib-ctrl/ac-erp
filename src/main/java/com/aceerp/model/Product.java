package com.aceerp.model;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * Represents a product in the ACE ERP inventory.
 * 
 * Fields map directly to the "products" table in the database.
 * Every field has a getter (to read) and setter (to write).
 */
public class Product {

    // --- Fields (match the database columns) ---
    private int id;
    private String productCode;    // e.g. "PROD-001" (auto-generated)
    private String name;           // e.g. "1.5 Ton Split AC"
    private String productType;    // e.g. "Split", "Window", "Ducted"
    private String category;       // e.g. "Residential", "Commercial"
    private String brand;          // e.g. "Daikin", "Gree"
    private String model;          // e.g. "FTKF50TV16"
    private String unit;           // e.g. "Piece", "Set", "Meter"
    private BigDecimal sellingPrice;
    private BigDecimal costPrice;
    private BigDecimal taxRate;    // e.g. 5.00 for 5%
    private int stockQuantity;
    private int minStockLevel;     // alert when stock drops below this
    private boolean active;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // --- Constructors ---

    /** Empty constructor — used when building from database rows */
    public Product() {
        this.active = true;
        this.stockQuantity = 0;
        this.minStockLevel = 0;
        this.sellingPrice = BigDecimal.ZERO;
        this.costPrice = BigDecimal.ZERO;
        this.taxRate = BigDecimal.ZERO;
    }

    // --- Getters and Setters ---
    // Getter = read the value.  Setter = change the value.

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getSellingPrice() {
        return sellingPrice;
    }

    public void setSellingPrice(BigDecimal sellingPrice) {
        this.sellingPrice = sellingPrice;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public void setCostPrice(BigDecimal costPrice) {
        this.costPrice = costPrice;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(int stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public int getMinStockLevel() {
        return minStockLevel;
    }

    public void setMinStockLevel(int minStockLevel) {
        this.minStockLevel = minStockLevel;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    // --- Helper methods (used by the controller for display) ---

    /**
     * Returns a human-readable product type.
     * If productType is null or empty, shows "N/A".
     */
    public String getTypeDisplay() {
        if (productType == null || productType.isBlank()) {
            return "N/A";
        }
        return productType;
    }

    /**
     * Returns a status label for the table.
     * Shows stock warning if quantity is at or below minimum.
     */
    public String getStatusDisplay() {
        if (!active) {
            return "Inactive";
        }
        if (stockQuantity <= 0) {
            return "Out of Stock";
        }
        if (stockQuantity <= minStockLevel) {
            return "Low Stock";
        }
        return "In Stock";
    }

    @Override
    public String toString() {
        return "Product{" +
                "id=" + id +
                ", code='" + productCode + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}