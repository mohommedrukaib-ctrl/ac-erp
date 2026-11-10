package com.aceerp.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import com.aceerp.util.DocumentDiscount;
import com.aceerp.util.PackagePriceTotals;

public class Quotation implements PackagePriceDocument {

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
    private String documentDiscountType = DocumentDiscount.PERCENT;
    private BigDecimal documentDiscountValue = BigDecimal.ZERO;
    private BigDecimal documentDiscountAmount = BigDecimal.ZERO;
    private boolean packagePriceEnabled;
    private BigDecimal packagePriceAmount = BigDecimal.ZERO;
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

    public String getDocumentDiscountType() { return documentDiscountType; }
    public void setDocumentDiscountType(String type) { this.documentDiscountType = DocumentDiscount.normalizeType(type); }

    public BigDecimal getDocumentDiscountValue() { return documentDiscountValue; }
    public void setDocumentDiscountValue(BigDecimal value) { this.documentDiscountValue = value != null ? value : BigDecimal.ZERO; }

    public BigDecimal getDocumentDiscountAmount() { return documentDiscountAmount; }
    public void setDocumentDiscountAmount(BigDecimal amount) { this.documentDiscountAmount = amount != null ? amount : BigDecimal.ZERO; }
    @Override public boolean isPackagePriceEnabled() { return packagePriceEnabled; }
    @Override public void setPackagePriceEnabled(boolean enabled) { packagePriceEnabled = enabled; }
    @Override public BigDecimal getPackagePriceAmount() { return packagePriceAmount; }
    @Override public void setPackagePriceAmount(BigDecimal amount) { packagePriceAmount = amount != null ? amount : BigDecimal.ZERO; }

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
        BigDecimal sumExTax = BigDecimal.ZERO;
        BigDecimal sumTax = BigDecimal.ZERO;

        for (QuotationLine line : lines) {
            if (line == null) continue;
            line.recalculateTotal();
            if (line.getTotalPrice() != null) {
                sumExTax = sumExTax.add(line.getTotalPrice());
            }
            if (line.getTaxAmount() != null) {
                sumTax = sumTax.add(line.getTaxAmount());
            }
        }

        PackagePriceTotals.Totals packageTotals = PackagePriceTotals.apply(
                sumExTax, sumTax, packagePriceEnabled, packagePriceAmount);
        this.subtotal = packageTotals.subtotal();
        this.documentDiscountAmount = DocumentDiscount.calculateAmount(subtotal, documentDiscountType, documentDiscountValue);
        this.taxAmount = DocumentDiscount.calculateTaxAfterDiscount(subtotal, packageTotals.tax(), documentDiscountAmount);
        this.totalAmount = subtotal.subtract(documentDiscountAmount).add(taxAmount);
    }
    @Override
    public String toString() {
        return (quoteNumber != null ? quoteNumber : "Draft Quote") + 
               (customerName != null ? " - " + customerName : "");
    }
}