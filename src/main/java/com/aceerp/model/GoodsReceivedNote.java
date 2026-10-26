package com.aceerp.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class GoodsReceivedNote {

    private int id;
    private String grnNumber;
    private int poId;
    private int supplierId;
    private Supplier supplier;
    private LocalDate receivedDate = LocalDate.now();
    private String deliveryNoteRef;
    private String status = "COMPLETED";
    private String notes;
    private Integer createdBy;
    private LocalDateTime createdAt;
    private List<GrnLine> lines = new ArrayList<>();

    public GoodsReceivedNote() {}

    public BigDecimal getTotalValue() {
        BigDecimal total = BigDecimal.ZERO;
        if (lines != null) {
            for (GrnLine line : lines) {
                if (line.getLineTotal() != null) {
                    total = total.add(line.getLineTotal());
                }
            }
        }
        return total;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getGrnNumber() { return grnNumber; }
    public void setGrnNumber(String grnNumber) { this.grnNumber = grnNumber; }

    public int getPoId() { return poId; }
    public void setPoId(int poId) { this.poId = poId; }

    public int getSupplierId() { return supplierId; }
    public void setSupplierId(int supplierId) { this.supplierId = supplierId; }

    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier supplier) {
        this.supplier = supplier;
        if (supplier != null) {
            this.supplierId = supplier.getId();
        }
    }

    public LocalDate getReceivedDate() { return receivedDate; }
    public void setReceivedDate(LocalDate receivedDate) { this.receivedDate = receivedDate; }

    public String getDeliveryNoteRef() { return deliveryNoteRef; }
    public void setDeliveryNoteRef(String deliveryNoteRef) { this.deliveryNoteRef = deliveryNoteRef; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<GrnLine> getLines() { return lines; }
    public void setLines(List<GrnLine> lines) { this.lines = lines; }
}