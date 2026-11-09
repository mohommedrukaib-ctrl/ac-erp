package com.aceerp.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class VendorCredit {
    private int id;
    private String vendorCreditNumber;
    private int supplierId;
    private String supplierName;
    private LocalDate issueDate = LocalDate.now();
    private String status = "OPEN";
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal taxAmount = BigDecimal.ZERO;
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private BigDecimal remainingAmount = BigDecimal.ZERO;
    private String memo;
    private Integer journalEntryId;
    private List<VendorCreditLine> lines = new ArrayList<>();

    public void recalculateTotals() {
        subtotal = BigDecimal.ZERO;
        taxAmount = BigDecimal.ZERO;
        totalAmount = BigDecimal.ZERO;

        for (VendorCreditLine l : lines) {
            l.recalculateTotal();
            subtotal = subtotal.add(l.getTotalAmount().subtract(l.getTaxAmount()));
            taxAmount = taxAmount.add(l.getTaxAmount());
            totalAmount = totalAmount.add(l.getTotalAmount());
        }
        if (id <= 0 && (remainingAmount == null || remainingAmount.compareTo(BigDecimal.ZERO) == 0)) {
            remainingAmount = totalAmount;
        }
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getVendorCreditNumber() { return vendorCreditNumber; }
    public void setVendorCreditNumber(String vendorCreditNumber) { this.vendorCreditNumber = vendorCreditNumber; }

    public int getSupplierId() { return supplierId; }
    public void setSupplierId(int supplierId) { this.supplierId = supplierId; }

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getRemainingAmount() { return remainingAmount; }
    public void setRemainingAmount(BigDecimal remainingAmount) { this.remainingAmount = remainingAmount; }

    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }

    public Integer getJournalEntryId() { return journalEntryId; }
    public void setJournalEntryId(Integer journalEntryId) { this.journalEntryId = journalEntryId; }

    public List<VendorCreditLine> getLines() { return lines; }
    public void setLines(List<VendorCreditLine> lines) { this.lines = lines; recalculateTotals(); }
}   