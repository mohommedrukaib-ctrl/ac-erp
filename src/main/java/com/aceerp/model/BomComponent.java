package com.aceerp.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class BomComponent {
    private int id;
    private int bomId;
    private int componentProductId;
    private String componentProductCode;
    private String componentProductName;
    private String unit = "PCS";
    private BigDecimal quantity = BigDecimal.ONE;
    private BigDecimal unitCost = BigDecimal.ZERO;

    public BigDecimal getTotalCost() {
        if (quantity == null || unitCost == null) return BigDecimal.ZERO;
        return quantity.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getBomId() { return bomId; }
    public void setBomId(int bomId) { this.bomId = bomId; }

    public int getComponentProductId() { return componentProductId; }
    public void setComponentProductId(int componentProductId) { this.componentProductId = componentProductId; }

    public String getComponentProductCode() { return componentProductCode; }
    public void setComponentProductCode(String componentProductCode) { this.componentProductCode = componentProductCode; }

    public String getComponentProductName() { return componentProductName; }
    public void setComponentProductName(String componentProductName) { this.componentProductName = componentProductName; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }
}