package com.aceerp.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class PurchaseOrderLine {
    private int id;
    private int poId;
    private Integer productId;
    private Product product;
    private String description;
    private BigDecimal quantity = BigDecimal.ONE;
    private BigDecimal unitPrice = BigDecimal.ZERO;
    private BigDecimal taxRate = BigDecimal.ZERO;
    private BigDecimal taxAmount = BigDecimal.ZERO;
    private BigDecimal lineTotal = BigDecimal.ZERO;
    
    // UI Checkbox Property
    private final BooleanProperty taxable = new SimpleBooleanProperty(true);

    public PurchaseOrderLine() {
        // Whenever the checkbox is clicked, recalculate total
        taxable.addListener((obs, oldV, newV) -> recalculateTotal());
    }

    public void recalculateTotal() {
        if (quantity == null) quantity = BigDecimal.ZERO;
        if (unitPrice == null) unitPrice = BigDecimal.ZERO;
        if (taxRate == null) taxRate = BigDecimal.ZERO;

        BigDecimal baseTotal = quantity.multiply(unitPrice);
        
        if (taxable.get()) {
            taxAmount = baseTotal.multiply(taxRate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        } else {
            taxAmount = BigDecimal.ZERO;
        }
        
        lineTotal = baseTotal.add(taxAmount);
    }

    // JavaFX Property for CheckBoxTableCell
    public BooleanProperty taxableProperty() { return taxable; }
    public boolean isTaxable() { return taxable.get(); }
    public void setTaxable(boolean val) { this.taxable.set(val); recalculateTotal(); }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getPoId() { return poId; }
    public void setPoId(int poId) { this.poId = poId; }
    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; recalculateTotal(); }
    
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; recalculateTotal(); }
    
    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; recalculateTotal(); }
    
    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }
    
    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
}