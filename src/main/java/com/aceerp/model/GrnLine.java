package com.aceerp.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class GrnLine {

    private int id;
    private int grnId;
    private Integer poLineId;
    private Integer productId;
    private Product product;
    private String productCode;
    private String description;
    private BigDecimal quantityOrdered = BigDecimal.ZERO;
    private BigDecimal quantityReceived = BigDecimal.ZERO;
    private BigDecimal unitCost = BigDecimal.ZERO;
    private BigDecimal lineTotal = BigDecimal.ZERO;

    public GrnLine() {
        recalculateTotal();
    }

    public void recalculateTotal() {
        BigDecimal qty = quantityReceived != null ? quantityReceived : BigDecimal.ZERO;
        BigDecimal cost = unitCost != null ? unitCost : BigDecimal.ZERO;
        lineTotal = qty.multiply(cost).setScale(2, RoundingMode.HALF_UP);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getGrnId() { return grnId; }
    public void setGrnId(int grnId) { this.grnId = grnId; }

    public Integer getPoLineId() { return poLineId; }
    public void setPoLineId(Integer poLineId) { this.poLineId = poLineId; }

    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) {
        this.product = product;
        if (product != null) {
            this.productId = product.getId();
            if (this.productCode == null || this.productCode.isBlank()) {
                this.productCode = product.getProductCode();
            }
            if (this.description == null || this.description.isBlank()) {
                this.description = product.getName();
            }
        }
    }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getQuantityOrdered() { return quantityOrdered; }
    public void setQuantityOrdered(BigDecimal quantityOrdered) { this.quantityOrdered = quantityOrdered; }

    public BigDecimal getQuantityReceived() { return quantityReceived; }
    public void setQuantityReceived(BigDecimal quantityReceived) {
        this.quantityReceived = quantityReceived;
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