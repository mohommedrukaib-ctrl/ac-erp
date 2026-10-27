package com.aceerp.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class PurchaseReturnLine {
    private int id;
    private int returnId;
    private int productId;
    private String description;
    private BigDecimal quantity = BigDecimal.ONE;
    private BigDecimal unitCost = BigDecimal.ZERO;
    private BigDecimal lineTotal = BigDecimal.ZERO;

    public PurchaseReturnLine() {}

    public void recalculateTotal() {
        if (quantity == null) quantity = BigDecimal.ZERO;
        if (unitCost == null) unitCost = BigDecimal.ZERO;
        this.lineTotal = quantity.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
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

    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
}