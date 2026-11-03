package com.aceerp.model;

public class StockTransferLine {
    private int id;
    private int stockTransferId;
    private int productId;
    private String productCode;
    private String productName;
    private int quantityTransferred = 1;
    private String notes;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getStockTransferId() { return stockTransferId; }
    public void setStockTransferId(int stockTransferId) { this.stockTransferId = stockTransferId; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public int getQuantityTransferred() { return quantityTransferred; }
    public void setQuantityTransferred(int quantityTransferred) { this.quantityTransferred = quantityTransferred; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}



