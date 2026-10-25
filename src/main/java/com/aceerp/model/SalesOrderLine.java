package com.aceerp.model;

import java.math.BigDecimal;

public class SalesOrderLine {
    private int id;
    private int soId;
    private Integer productId;
    private Product product;
    private String description;
    private BigDecimal quantity = BigDecimal.ONE;
    private BigDecimal unitPrice = BigDecimal.ZERO;
    private BigDecimal totalPrice = BigDecimal.ZERO;

    public SalesOrderLine() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getSoId() { return soId; }
    public void setSoId(int soId) { this.soId = soId; }

    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

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

    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }

    public void recalculateTotal() {
        if (quantity != null && unitPrice != null) {
            this.totalPrice = quantity.multiply(unitPrice);
        } else {
            this.totalPrice = BigDecimal.ZERO;
        }
    }
}