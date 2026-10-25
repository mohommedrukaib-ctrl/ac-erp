package com.aceerp.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class InvoiceLine {

    private int id;
    private int invoiceId;
    private Integer productId;
    private String productCode;
    private String description;
    private BigDecimal quantity = BigDecimal.ONE;
    private BigDecimal unitPrice = BigDecimal.ZERO;
    private BigDecimal taxRate = BigDecimal.ZERO;
    private BigDecimal totalPrice = BigDecimal.ZERO;

    public InvoiceLine() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getInvoiceId() { return invoiceId; }
    public void setInvoiceId(int invoiceId) { this.invoiceId = invoiceId; }

    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

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
                BigDecimal multiplier = BigDecimal.ONE.add(taxRate.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
                this.totalPrice = base.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
            } else {
                this.totalPrice = base.setScale(2, RoundingMode.HALF_UP);
            }
        } else {
            this.totalPrice = BigDecimal.ZERO;
        }
    }
}