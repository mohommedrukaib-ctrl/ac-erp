package com.aceerp.model;

import com.aceerp.util.TaxUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class SupplierBillLine {
    private int id;
    private int billId;
    private int productId;
    private String description;
    private BigDecimal quantity = BigDecimal.ONE;
    private BigDecimal unitPrice = BigDecimal.ZERO;
    private boolean taxable = true;
    private BigDecimal taxRate = BigDecimal.ZERO;
    private BigDecimal taxAmount = BigDecimal.ZERO;
    private BigDecimal totalPrice = BigDecimal.ZERO; // subtotal (qty * unitPrice)
    private BigDecimal lineTotal = BigDecimal.ZERO;  // subtotal + tax

    public SupplierBillLine() {}

    public void recalculateTotal() {
        if (quantity == null) quantity = BigDecimal.ZERO;
        if (unitPrice == null) unitPrice = BigDecimal.ZERO;
        this.totalPrice = quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);

        if (taxable) {
            if (taxRate == null || taxRate.compareTo(BigDecimal.ZERO) <= 0) {
                taxRate = TaxUtil.getDefaultTaxRate();
            }
            this.taxAmount = totalPrice.multiply(taxRate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        } else {
            this.taxRate = BigDecimal.ZERO;
            this.taxAmount = BigDecimal.ZERO;
        }
        this.lineTotal = totalPrice.add(taxAmount);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getBillId() { return billId; }
    public void setBillId(int billId) { this.billId = billId; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
        recalculateTotal();
    }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
        recalculateTotal();
    }

    public boolean isTaxable() { return taxable; }
    public void setTaxable(boolean taxable) {
        this.taxable = taxable;
        recalculateTotal();
    }

    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; }

    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }

    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }

    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
}