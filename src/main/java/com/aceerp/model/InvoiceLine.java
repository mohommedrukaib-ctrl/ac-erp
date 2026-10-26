package com.aceerp.model;

import com.aceerp.util.TaxUtil;
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

    private boolean taxable = TaxUtil.isTaxEnabled();
    private BigDecimal taxRate = TaxUtil.getDefaultTaxRate();
    private BigDecimal taxAmount = BigDecimal.ZERO;
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

    public boolean isTaxable() { return taxable; }
    public void setTaxable(boolean taxable) {
        this.taxable = taxable;
        if (taxable) {
            this.taxRate = TaxUtil.getDefaultTaxRate();
        } else {
            this.taxRate = BigDecimal.ZERO;
        }
        recalculateTotal();
    }

    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) {
        this.taxRate = taxRate != null ? taxRate : BigDecimal.ZERO;
        recalculateTotal();
    }

    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }

    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }

    public BigDecimal getLineTotal() { return totalPrice; }
    public void setLineTotal(BigDecimal lineTotal) { this.totalPrice = lineTotal; }

    public void recalculateTotal() {
        BigDecimal qty = quantity != null ? quantity : BigDecimal.ZERO;
        BigDecimal price = unitPrice != null ? unitPrice : BigDecimal.ZERO;
        this.totalPrice = qty.multiply(price).setScale(2, RoundingMode.HALF_UP);

        if (taxable && taxRate != null && taxRate.compareTo(BigDecimal.ZERO) > 0) {
            this.taxAmount = this.totalPrice.multiply(taxRate)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            this.taxAmount = BigDecimal.ZERO;
        }
    }
}