package com.aceerp.model;

import com.aceerp.util.TaxUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class SalesOrderLine {

    private int id;
    private int soId;
    private Integer productId;
    private Product product;
    private String productCode;
    private String description;
    private BigDecimal quantity = BigDecimal.ONE;
    private BigDecimal unitPrice = BigDecimal.ZERO;

    private boolean taxable = TaxUtil.isTaxEnabled();
    private BigDecimal taxRate = TaxUtil.getDefaultTaxRate();
    private BigDecimal taxAmount = BigDecimal.ZERO;
    private BigDecimal totalPrice = BigDecimal.ZERO;

    public SalesOrderLine() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getSoId() { return soId; }
    public void setSoId(int soId) { this.soId = soId; }

    public int getSalesOrderId() { return soId; }
    public void setSalesOrderId(int salesOrderId) { this.soId = salesOrderId; }

    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) {
        this.product = product;
        if (product != null) {
            this.productId = product.getId();
            this.productCode = product.getProductCode();
            if (this.description == null || this.description.isBlank()) {
                this.description = product.getName();
            }
            if (this.unitPrice == null || this.unitPrice.compareTo(BigDecimal.ZERO) == 0) {
                this.unitPrice = product.getSellingPrice();
            }
        }
        recalculateTotal();
    }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { 
        this.quantity = quantity != null ? quantity : BigDecimal.ZERO; 
        recalculateTotal();
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