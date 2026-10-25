package com.aceerp.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Quotation {

    private int id;
    private String quoteNumber;
    private int customerId;
    private Customer customer;
    private String customerName;
    private LocalDate quoteDate = LocalDate.now();
    private LocalDate validUntil = LocalDate.now().plusDays(30);
    private String status = "DRAFT"; // DRAFT, SENT, ACCEPTED, REJECTED, EXPIRED, CONVERTED
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal taxAmount = BigDecimal.ZERO;
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private String notes;
    private int createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<QuotationLine> lines = new ArrayList<>();

    public Quotation() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getQuoteNumber() { return quoteNumber; }
    public void setQuoteNumber(String quoteNumber) { this.quoteNumber = quoteNumber; }

    public String getQuotationNumber() { return quoteNumber; }
    public void setQuotationNumber(String quotationNumber) { this.quoteNumber = quotationNumber; }

    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) {
        this.customer = customer;
        if (customer != null) {
            this.customerId = customer.getId();
            this.customerName = customer.getName();
        }
    }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    // Date Aliases
    public LocalDate getQuoteDate() { return quoteDate; }
    public void setQuoteDate(LocalDate quoteDate) { this.quoteDate = quoteDate; }

    public LocalDate getIssueDate() { return quoteDate; }
    public void setIssueDate(LocalDate issueDate) { this.quoteDate = issueDate; }

    public LocalDate getQuotationDate() { return quoteDate; }
    public void setQuotationDate(LocalDate quotationDate) { this.quoteDate = quotationDate; }

    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }

    public LocalDate getExpiryDate() { return validUntil; }
    public void setExpiryDate(LocalDate expiryDate) { this.validUntil = expiryDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }

    public BigDecimal getTaxTotal() { return taxAmount; }
    public void setTaxTotal(BigDecimal taxTotal) { this.taxAmount = taxTotal; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getGrandTotal() { return totalAmount; }
    public void setGrandTotal(BigDecimal grandTotal) { this.totalAmount = grandTotal; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getTermsConditions() { return notes; }
    public void setTermsConditions(String termsConditions) { this.notes = termsConditions; }

    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<QuotationLine> getLines() { return lines; }
    public void setLines(List<QuotationLine> lines) {
        this.lines = lines != null ? lines : new ArrayList<>();
        recalculateTotals();
    }

    public void recalculateTotals() {
        BigDecimal sum = BigDecimal.ZERO;
        for (QuotationLine line : lines) {
            line.recalculateTotal();
            if (line.getTotalPrice() != null) {
                sum = sum.add(line.getTotalPrice());
            }
        }
        this.subtotal = sum;
        this.totalAmount = sum.add(taxAmount != null ? taxAmount : BigDecimal.ZERO);
    }

    @Override
    public String toString() {
        return (quoteNumber != null ? quoteNumber : "Draft Quote") + 
               (customerName != null ? " - " + customerName : "");
    }
}