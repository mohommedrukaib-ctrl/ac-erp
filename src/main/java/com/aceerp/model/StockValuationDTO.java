package com.aceerp.model;

import java.math.BigDecimal;

public class StockValuationDTO {
    private String productCode;
    private String productName;
    private String category;
    private int stockQuantity;
    private BigDecimal costPrice = BigDecimal.ZERO;
    private BigDecimal sellingPrice = BigDecimal.ZERO;
    private BigDecimal totalCostValue = BigDecimal.ZERO;
    private BigDecimal totalRetailValue = BigDecimal.ZERO;

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public int getStockQuantity() { return stockQuantity; }
    public void setStockQuantity(int stockQuantity) {
        this.stockQuantity = stockQuantity;
        recalculate();
    }

    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) {
        this.costPrice = costPrice != null ? costPrice : BigDecimal.ZERO;
        recalculate();
    }

    public BigDecimal getSellingPrice() { return sellingPrice; }
    public void setSellingPrice(BigDecimal sellingPrice) {
        this.sellingPrice = sellingPrice != null ? sellingPrice : BigDecimal.ZERO;
        recalculate();
    }

    public BigDecimal getTotalCostValue() { return totalCostValue; }
    public BigDecimal getTotalRetailValue() { return totalRetailValue; }

    private void recalculate() {
        BigDecimal qty = BigDecimal.valueOf(Math.max(0, stockQuantity));
        totalCostValue = costPrice.multiply(qty);
        totalRetailValue = sellingPrice.multiply(qty);
    }
}