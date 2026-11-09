package com.aceerp.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import com.aceerp.util.DocumentDiscount;

public class PurchaseOrderLine {
    private int id;
    private int poId;
    private Integer productId;
    private Product product;
    private String description;
    private BigDecimal quantity = BigDecimal.ONE;
    private BigDecimal unitPrice = BigDecimal.ZERO;

    /** Option B */
    private BigDecimal discountPercent = BigDecimal.ZERO;
    private String discountType = DocumentDiscount.PERCENT;
    private BigDecimal discountValue = BigDecimal.ZERO;
    private boolean packagePrice;
    private String notes;

    private BigDecimal taxRate = BigDecimal.ZERO;
    private BigDecimal taxAmount = BigDecimal.ZERO;
    private BigDecimal lineTotal = BigDecimal.ZERO;

    private final BooleanProperty taxable = new SimpleBooleanProperty(true);

    public PurchaseOrderLine() {
        taxable.addListener((obs, oldV, newV) -> recalculateTotal());
    }

    public void recalculateTotal() {
        if (quantity == null) quantity = BigDecimal.ZERO;
        if (unitPrice == null) unitPrice = BigDecimal.ZERO;
        if (taxRate == null) taxRate = BigDecimal.ZERO;
        if (discountValue == null) discountValue = BigDecimal.ZERO;

        BigDecimal gross = (packagePrice ? unitPrice : quantity.multiply(unitPrice)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountAmount = DocumentDiscount.calculateAmount(gross, discountType, discountValue);
        discountPercent = DocumentDiscount.PERCENT.equals(discountType) ? discountValue : BigDecimal.ZERO;
        BigDecimal net = gross.subtract(discountAmount);

        if (taxable.get()) {
            taxAmount = net.multiply(taxRate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        } else {
            taxAmount = BigDecimal.ZERO;
        }

        lineTotal = net.add(taxAmount);
    }

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

    public BigDecimal getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(BigDecimal discountPercent) {
        this.discountType = DocumentDiscount.PERCENT;
        this.discountValue = discountPercent != null ? discountPercent : BigDecimal.ZERO;
        this.discountPercent = this.discountValue;
        recalculateTotal();
    }

    public String getDiscountType() { return discountType; }
    public void setDiscountType(String type) {
        this.discountType = DocumentDiscount.normalizeType(type);
        this.discountPercent = DocumentDiscount.PERCENT.equals(this.discountType) ? discountValue : BigDecimal.ZERO;
        recalculateTotal();
    }

    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal value) {
        this.discountValue = value != null ? value.max(BigDecimal.ZERO) : BigDecimal.ZERO;
        this.discountPercent = DocumentDiscount.PERCENT.equals(discountType) ? this.discountValue : BigDecimal.ZERO;
        recalculateTotal();
    }

    public boolean isPackagePrice() { return packagePrice; }
    public void setPackagePrice(boolean packagePrice) { this.packagePrice = packagePrice; recalculateTotal(); }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public BigDecimal getTaxRate() { return taxRate; }
    public void setTaxRate(BigDecimal taxRate) { this.taxRate = taxRate; recalculateTotal(); }

    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }

    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
}