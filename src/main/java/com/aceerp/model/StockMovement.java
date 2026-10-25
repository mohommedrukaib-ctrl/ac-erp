package com.aceerp.model;

import java.sql.Timestamp;

/**
 * Represents one stock movement (in, out, or adjustment).
 * Think of it like a bank transaction — it records what changed and why.
 */
public class StockMovement {

    private int id;
    private int productId;
    private String productName;    // from JOIN with products table
    private String productCode;    // from JOIN with products table
    private String movementType;   // IN, OUT, ADJUSTMENT
    private int quantity;          // the amount that changed
    private int previousStock;     // stock before this movement
    private int newStock;          // stock after this movement
    private String referenceType;  // PURCHASE, SALE, JOB, RETURN, ADJUSTMENT
    private String referenceNumber;// e.g. PO-001
    private String notes;
    private int createdBy;
    private String createdByName;  // from JOIN with users table
    private Timestamp createdAt;

    public StockMovement() {}

    // --- Getters and Setters ---

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getMovementType() { return movementType; }
    public void setMovementType(String movementType) { this.movementType = movementType; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public int getPreviousStock() { return previousStock; }
    public void setPreviousStock(int previousStock) { this.previousStock = previousStock; }

    public int getNewStock() { return newStock; }
    public void setNewStock(int newStock) { this.newStock = newStock; }

    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }

    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    // --- Display helpers (used by the table columns) ---

    /** Shows "+5" for IN, "-3" for OUT, "+/-2" for ADJUSTMENT */
    public String getQuantityDisplay() {
        if ("IN".equals(movementType)) {
            return "+" + quantity;
        } else if ("OUT".equals(movementType)) {
            return "-" + quantity;
        } else {
            int diff = newStock - previousStock;
            return (diff >= 0 ? "+" : "") + diff;
        }
    }

    /** Shows friendly type name */
    public String getTypeDisplay() {
        if ("IN".equals(movementType)) return "Stock In";
        if ("OUT".equals(movementType)) return "Stock Out";
        if ("ADJUSTMENT".equals(movementType)) return "Adjustment";
        return movementType;
    }

    /** Shows product code + name together */
    public String getProductDisplay() {
        if (productCode != null && productName != null) {
            return productCode + " - " + productName;
        }
        if (productName != null) return productName;
        return "Product #" + productId;
    }

    /** Shows reference type + number together */
    public String getReferenceDisplay() {
        if (referenceType == null && referenceNumber == null) return "";
        String ref = "";
        if (referenceType != null) ref += referenceType;
        if (referenceNumber != null) ref += (ref.isEmpty() ? "" : " ") + referenceNumber;
        return ref;
    }
}