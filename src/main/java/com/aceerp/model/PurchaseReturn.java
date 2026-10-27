package com.aceerp.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class PurchaseReturn {
    private int id;
    private String returnNumber;
    private int supplierId;
    private String supplierName;
    private Integer grnId;
    private Integer billId;
    private LocalDate returnDate = LocalDate.now();
    private String status = "COMPLETED";
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private String reason;
    private LocalDateTime createdAt;
    private List<PurchaseReturnLine> lines = new ArrayList<>();

    public PurchaseReturn() {}

    public void recalculateTotal() {
        totalAmount = BigDecimal.ZERO;
        if (lines != null) {
            for (PurchaseReturnLine line : lines) {
                line.recalculateTotal();
                if (line.getLineTotal() != null) {
                    totalAmount = totalAmount.add(line.getLineTotal());
                }
            }
        }
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getReturnNumber() { return returnNumber; }
    public void setReturnNumber(String returnNumber) { this.returnNumber = returnNumber; }

    public int getSupplierId() { return supplierId; }
    public void setSupplierId(int supplierId) { this.supplierId = supplierId; }

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public Integer getGrnId() { return grnId; }
    public void setGrnId(Integer grnId) { this.grnId = grnId; }

    public Integer getBillId() { return billId; }
    public void setBillId(Integer billId) { this.billId = billId; }

    public LocalDate getReturnDate() { return returnDate; }
    public void setReturnDate(LocalDate returnDate) { this.returnDate = returnDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<PurchaseReturnLine> getLines() { return lines; }
    public void setLines(List<PurchaseReturnLine> lines) { this.lines = lines; }
}