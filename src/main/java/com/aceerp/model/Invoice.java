package com.aceerp.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Invoice {

    private int id;
    private String invoiceNumber;
    private int customerId;
    private String customerName;
    private Customer customer;
    private Integer soId;
    private Integer dnId;
    private LocalDate invoiceDate = LocalDate.now();
    private LocalDate dueDate = LocalDate.now().plusDays(30);
    private String status = "UNPAID"; // UNPAID, PARTIAL, PAID, CANCELLED
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal taxAmount = BigDecimal.ZERO;
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private BigDecimal paidAmount = BigDecimal.ZERO;
    private String notes;
    private int createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<InvoiceLine> lines = new ArrayList<>();

    public Invoice() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) {
        this.customer = customer;
        if (customer != null) {
            this.customerId = customer.getId();
            this.customerName = customer.getName();
        }
    }

    public Integer getSoId() { return soId; }
    public void setSoId(Integer soId) { this.soId = soId; }

    public Integer getDnId() { return dnId; }
    public void setDnId(Integer dnId) { this.dnId = dnId; }

    // Date Getters and Aliases
    public LocalDate getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(LocalDate invoiceDate) { this.invoiceDate = invoiceDate; }

    public LocalDate getIssueDate() { return invoiceDate; }
    public void setIssueDate(LocalDate issueDate) { this.invoiceDate = issueDate; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    // Amount Getters and Aliases
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

    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }

    public BigDecimal getAmountPaid() { return paidAmount; }
    public void setAmountPaid(BigDecimal amountPaid) { this.paidAmount = amountPaid; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<InvoiceLine> getLines() { return lines; }
    public void setLines(List<InvoiceLine> lines) { this.lines = lines != null ? lines : new ArrayList<>(); }

    @Override
    public String toString() {
        return (invoiceNumber != null ? invoiceNumber : "Draft Invoice") + 
               (customerName != null && !customerName.isEmpty() ? " - " + customerName : "");
    }
}