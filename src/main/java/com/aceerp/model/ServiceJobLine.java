package com.aceerp.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class ServiceJobLine {
    private int id;
    private int serviceJobId;
    private int productId;
    private String productCode;
    private String productName;
    private int quantity = 1;
    private BigDecimal unitPrice = BigDecimal.ZERO;
    private BigDecimal discountPercent = BigDecimal.ZERO;
    private String notes;
    private BigDecimal lineTotal = BigDecimal.ZERO;

    public ServiceJobLine() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getServiceJobId() { return serviceJobId; }
    public void setServiceJobId(int serviceJobId) { this.serviceJobId = serviceJobId; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) {
        this.quantity = Math.max(1, quantity);
        recalculateTotal();
    }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice != null ? unitPrice : BigDecimal.ZERO;
        recalculateTotal();
    }

    public BigDecimal getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(BigDecimal discountPercent) {
        this.discountPercent = discountPercent != null ? discountPercent : BigDecimal.ZERO;
        recalculateTotal();
    }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }

    public void recalculateTotal() {
        BigDecimal gross = (unitPrice != null ? unitPrice : BigDecimal.ZERO).multiply(BigDecimal.valueOf(quantity));
        BigDecimal disc = discountPercent != null ? discountPercent : BigDecimal.ZERO;
        if (disc.compareTo(BigDecimal.ZERO) < 0) disc = BigDecimal.ZERO;
        if (disc.compareTo(BigDecimal.valueOf(100)) > 0) disc = BigDecimal.valueOf(100);
        this.discountPercent = disc;

        BigDecimal discountAmt = gross.multiply(disc).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        this.lineTotal = gross.subtract(discountAmt);
    }
}