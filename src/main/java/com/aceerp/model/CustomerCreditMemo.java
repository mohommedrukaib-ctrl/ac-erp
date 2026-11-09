package com.aceerp.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class CustomerCreditMemo {
    private int id;
    private String creditMemoNumber;
    private int customerId;
    private String customerName;
    private LocalDate issueDate = LocalDate.now();
    private String status = "OPEN"; // DRAFT, OPEN, PARTIALLY_APPLIED, FULLY_APPLIED, REFUNDED, VOID
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal taxAmount = BigDecimal.ZERO;
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private BigDecimal remainingAmount = BigDecimal.ZERO;
    private String memo;
    private Integer journalEntryId;
    private List<CreditMemoLine> lines = new ArrayList<>();

    public void recalculateTotals() {
        subtotal = BigDecimal.ZERO;
        taxAmount = BigDecimal.ZERO;
        totalAmount = BigDecimal.ZERO;

        for (CreditMemoLine l : lines) {
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

    public String getCreditMemoNumber() { return creditMemoNumber; }
    public void setCreditMemoNumber(String creditMemoNumber) { this.creditMemoNumber = creditMemoNumber; }

    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

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

    public List<CreditMemoLine> getLines() { return lines; }
    public void setLines(List<CreditMemoLine> lines) { this.lines = lines; recalculateTotals(); }
}