package com.aceerp.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import com.aceerp.util.DocumentDiscount;

public class VendorCreditLine {
    private int id;
    private int vendorCreditId;
    private Integer productId;
    private Integer accountId;
    private String description;
    private BigDecimal quantity = BigDecimal.ONE;
    private BigDecimal unitCost = BigDecimal.ZERO;
    private String discountType = DocumentDiscount.PERCENT;
    private BigDecimal discountValue = BigDecimal.ZERO;
    private boolean packagePrice;
    private BigDecimal taxRate = BigDecimal.ZERO;
    private BigDecimal taxAmount = BigDecimal.ZERO;
    private BigDecimal totalAmount = BigDecimal.ZERO;

    public void recalculateTotal() {
        if (quantity == null) quantity = BigDecimal.ONE;
        if (unitCost == null) unitCost = BigDecimal.ZERO;
        if (taxRate == null) taxRate = BigDecimal.ZERO;

        BigDecimal gross = (packagePrice ? unitCost : quantity.multiply(unitCost))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal sub = gross.subtract(DocumentDiscount.calculateAmount(gross, discountType, discountValue))
                .max(BigDecimal.ZERO);
        taxAmount = sub.multiply(taxRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        totalAmount = sub.add(taxAmount).setScale(2, RoundingMode.HALF_UP);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getVendorCreditId() { return vendorCreditId; }
    public void setVendorCreditId(int vendorCreditId) { this.vendorCreditId = vendorCreditId; }

    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }

    public Integer getAccountId() { return accountId; }
    public void setAccountId(Integer accountId) { this.accountId = accountId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; recalculateTotal(); }

    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; recalculateTotal(); }

    public String getDiscountType() { return discountType; }
    public void setDiscountType(String type) { this.discountType = DocumentDiscount.normalizeType(type); recalculateTotal(); }

    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal value) {
        this.discountValue = value != null ? value.max(BigDecimal.ZERO) : BigDecimal.ZERO;
        recalculateTotal();
    }

    public boolean isPackagePrice() { return packagePrice; }
    public void setPackagePrice(boolean packagePrice) { this.packagePrice = packagePrice; recalculateTotal(); }

    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; recalculateTotal(); }

    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
}