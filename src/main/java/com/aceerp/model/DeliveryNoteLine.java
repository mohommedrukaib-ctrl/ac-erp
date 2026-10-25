package com.aceerp.model;

import javafx.beans.property.*;

public class DeliveryNoteLine {
    private Integer id;
    private Integer dnId;
    private Integer productId;
    private final StringProperty productName = new SimpleStringProperty("");
    private final StringProperty sku = new SimpleStringProperty("");
    private final DoubleProperty orderedQty = new SimpleDoubleProperty(0.0);
    private final DoubleProperty shippedQty = new SimpleDoubleProperty(0.0);
    private final StringProperty notes = new SimpleStringProperty("");

    public DeliveryNoteLine() {}

    public DeliveryNoteLine(Integer productId, String productName, String sku, double orderedQty, double shippedQty) {
        this.productId = productId;
        setProductName(productName);
        setSku(sku);
        setOrderedQty(orderedQty);
        setShippedQty(shippedQty);
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getDnId() { return dnId; }
    public void setDnId(Integer dnId) { this.dnId = dnId; }

    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }

    public String getProductName() { return productName.get(); }
    public void setProductName(String value) { productName.set(value); }
    public StringProperty productNameProperty() { return productName; }

    public String getSku() { return sku.get(); }
    public void setSku(String value) { sku.set(value); }
    public StringProperty skuProperty() { return sku; }

    public double getOrderedQty() { return orderedQty.get(); }
    public void setOrderedQty(double value) { orderedQty.set(value); }
    public DoubleProperty orderedQtyProperty() { return orderedQty; }

    public double getShippedQty() { return shippedQty.get(); }
    public void setShippedQty(double value) { shippedQty.set(value); }
    public DoubleProperty shippedQtyProperty() { return shippedQty; }

    public String getNotes() { return notes.get(); }
    public void setNotes(String value) { notes.set(value); }
    public StringProperty notesProperty() { return notes; }
}