package com.aceerp.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import com.aceerp.util.DocumentDiscount;

public class ServiceJobLine {
    private int id;
    private int serviceJobId;
    private int productId;
    private String productCode;
    private String productName;
    private String serialNumber;
    private int quantity = 1;
    private BigDecimal unitPrice = BigDecimal.ZERO;
    private BigDecimal discountPercent = BigDecimal.ZERO;
    private String discountType = DocumentDiscount.PERCENT;
    private BigDecimal discountValue = BigDecimal.ZERO;
    private boolean packagePrice;
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

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

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
        this.discountType = DocumentDiscount.PERCENT;
        this.discountValue = discountPercent != null ? discountPercent : BigDecimal.ZERO;
        this.discountPercent = this.discountValue;
        recalculateTotal();
    }

    public String getDiscountType() { return discountType; }
    public void setDiscountType(String type) {
        this.discountType = DocumentDiscount.normalizeType(type);
        this.discountPercent = DocumentDiscount.PERCENT.equals(this.discountType) ? discountValue : BigDecimal.ZERO;
        recalculateTotal();
    }

    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal value) {
        this.discountValue = value != null ? value.max(BigDecimal.ZERO) : BigDecimal.ZERO;
        this.discountPercent = DocumentDiscount.PERCENT.equals(discountType) ? this.discountValue : BigDecimal.ZERO;
        recalculateTotal();
    }

    public boolean isPackagePrice() { return packagePrice; }
    public void setPackagePrice(boolean packagePrice) { this.packagePrice = packagePrice; recalculateTotal(); }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }

    public void recalculateTotal() {
        BigDecimal price = unitPrice != null ? unitPrice : BigDecimal.ZERO;
        BigDecimal gross = packagePrice ? price : price.multiply(BigDecimal.valueOf(quantity));
        this.discountValue = discountValue != null ? discountValue.max(BigDecimal.ZERO) : BigDecimal.ZERO;
        this.discountPercent = DocumentDiscount.PERCENT.equals(discountType) ? discountValue : BigDecimal.ZERO;
        BigDecimal discountAmt = DocumentDiscount.calculateAmount(gross, discountType, discountValue);
        this.lineTotal = gross.subtract(discountAmt).max(BigDecimal.ZERO);
    }
}
