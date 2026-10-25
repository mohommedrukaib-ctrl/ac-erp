package com.aceerp.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;



public class SalesOrder {
    private int id;
    private String soNumber;
    private Integer quotationId;
    private int customerId;
    private Customer customer;
    private String customerName;
    private LocalDate orderDate = LocalDate.now();
    private LocalDate expectedDeliveryDate;
    private String status = "DRAFT"; // DRAFT, APPROVED, SHIPPED, INVOICED, CANCELLED
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal taxAmount = BigDecimal.ZERO;
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<SalesOrderLine> lines = new ArrayList<>();

    public SalesOrder() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getSoNumber() { return soNumber; }
    public void setSoNumber(String soNumber) { this.soNumber = soNumber; }

    public Integer getQuotationId() { return quotationId; }
    public void setQuotationId(Integer quotationId) { this.quotationId = quotationId; }

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

    public LocalDate getOrderDate() { return orderDate; }
    public void setOrderDate(LocalDate orderDate) { this.orderDate = orderDate; }

    public LocalDate getExpectedDeliveryDate() { return expectedDeliveryDate; }
    public void setExpectedDeliveryDate(LocalDate expectedDeliveryDate) { this.expectedDeliveryDate = expectedDeliveryDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<SalesOrderLine> getLines() { return lines; }
    public void setLines(List<SalesOrderLine> lines) {
        this.lines = lines != null ? lines : new ArrayList<>();
        recalculateTotals();
    }

    public void recalculateTotals() {
        BigDecimal sum = BigDecimal.ZERO;
        for (SalesOrderLine line : lines) {
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
        return (soNumber != null ? soNumber : "Draft SO") + (customerName != null ? " - " + customerName : "");
    }
}