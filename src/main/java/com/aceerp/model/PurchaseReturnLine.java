package com.aceerp.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import com.aceerp.util.DocumentDiscount;

public class PurchaseReturnLine {
    private int id;
    private int returnId;
    private int productId;
    private String description;
    private BigDecimal quantity = BigDecimal.ONE;
    private BigDecimal unitCost = BigDecimal.ZERO;
    private String discountType = DocumentDiscount.PERCENT;
    private BigDecimal discountValue = BigDecimal.ZERO;
    private boolean packagePrice;
    private BigDecimal lineTotal = BigDecimal.ZERO;

    public PurchaseReturnLine() {}

    public void recalculateTotal() {
        if (quantity == null) quantity = BigDecimal.ZERO;
        if (unitCost == null) unitCost = BigDecimal.ZERO;
        BigDecimal gross = (packagePrice ? unitCost : quantity.multiply(unitCost))
                .setScale(2, RoundingMode.HALF_UP);
        this.lineTotal = gross.subtract(DocumentDiscount.calculateAmount(gross, discountType, discountValue))
                .max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getReturnId() { return returnId; }
    public void setReturnId(int returnId) { this.returnId = returnId; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
        recalculateTotal();
    }

    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) {
        this.unitCost = unitCost;
        recalculateTotal();
    }

    public String getDiscountType() { return discountType; }
    public void setDiscountType(String type) { this.discountType = DocumentDiscount.normalizeType(type); recalculateTotal(); }

    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal value) {
        this.discountValue = value != null ? value.max(BigDecimal.ZERO) : BigDecimal.ZERO;
        recalculateTotal();
    }

    public boolean isPackagePrice() { return packagePrice; }
    public void setPackagePrice(boolean packagePrice) { this.packagePrice = packagePrice; recalculateTotal(); }

    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
}