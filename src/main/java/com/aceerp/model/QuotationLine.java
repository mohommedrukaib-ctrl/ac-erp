package com.aceerp.model;

import java.math.BigDecimal;

public class QuotationLine {

    private int id;
    private int quotationId;
    private Integer productId;
    private String productCode;
    private Product product;
    private String description;
    private BigDecimal quantity = BigDecimal.ONE;
    private BigDecimal unitPrice = BigDecimal.ZERO;
    private BigDecimal taxRate = BigDecimal.ZERO;
    private BigDecimal totalPrice = BigDecimal.ZERO;

    public QuotationLine() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getQuotationId() { return quotationId; }
    public void setQuotationId(int quotationId) { this.quotationId = quotationId; }

    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) {
        this.product = product;
        if (product != null) {
            this.productId = product.getId();
            this.productCode = product.getProductCode();
            if (this.description == null || this.description.isBlank()) {
                this.description = product.getName();
            }
            if (this.unitPrice == null || this.unitPrice.compareTo(BigDecimal.ZERO) == 0) {
                this.unitPrice = product.getSellingPrice();
            }
        }
        recalculateTotal();
    }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity != null ? quantity : BigDecimal.ZERO;
        recalculateTotal();
    }
    public void setQuantity(int quantity) {
        setQuantity(BigDecimal.valueOf(quantity));
    }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice != null ? unitPrice : BigDecimal.ZERO;
        recalculateTotal();
    }

    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate != null ? taxRate : BigDecimal.ZERO;
        recalculateTotal();
    }

    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }

    public BigDecimal getLineTotal() { return totalPrice; }
    public void setLineTotal(BigDecimal lineTotal) { this.totalPrice = lineTotal; }

    public void recalculateTotal() {
        if (quantity != null && unitPrice != null) {
            BigDecimal base = quantity.multiply(unitPrice);
            if (taxRate != null && taxRate.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal taxMultiplier = BigDecimal.ONE.add(taxRate.divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP));
                this.totalPrice = base.multiply(taxMultiplier).setScale(2, java.math.RoundingMode.HALF_UP);
            } else {
                this.totalPrice = base.setScale(2, java.math.RoundingMode.HALF_UP);
            }
        } else {
            this.totalPrice = BigDecimal.ZERO;
        }
    }
}