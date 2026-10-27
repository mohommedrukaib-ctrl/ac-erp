package com.aceerp.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SupplierBill {
    private int id;
    private String billNumber;
    private int supplierId;
    private String supplierName;
    private Supplier supplier;
    private Integer poId;
    private Integer grnId;
    private LocalDate billDate = LocalDate.now();
    private LocalDate dueDate = LocalDate.now().plusDays(30);
    private String supplierInvoiceRef;
    private String status = "UNPAID";
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal taxAmount = BigDecimal.ZERO;
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private BigDecimal paidAmount = BigDecimal.ZERO;
    private String notes;
    private LocalDateTime createdAt;
    private List<SupplierBillLine> lines = new ArrayList<>();

    public SupplierBill() {}

    public void recalculateTotal() {
        subtotal = BigDecimal.ZERO;
        taxAmount = BigDecimal.ZERO;
        if (lines != null) {
            for (SupplierBillLine line : lines) {
                line.recalculateTotal();
                if (line.getTotalPrice() != null) {
                    subtotal = subtotal.add(line.getTotalPrice());
                }
                if (line.getTaxAmount() != null) {
                    taxAmount = taxAmount.add(line.getTaxAmount());
                }
            }
        }
        totalAmount = subtotal.add(taxAmount);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getBillNumber() { return billNumber; }
    public void setBillNumber(String billNumber) { this.billNumber = billNumber; }

    public int getSupplierId() { return supplierId; }
    public void setSupplierId(int supplierId) { this.supplierId = supplierId; }

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier supplier) {
        this.supplier = supplier;
        if (supplier != null) {
            this.supplierId = supplier.getId();
            this.supplierName = supplier.getName();
        }
    }

    public Integer getPoId() { return poId; }
    public void setPoId(Integer poId) { this.poId = poId; }

    public Integer getGrnId() { return grnId; }
    public void setGrnId(Integer grnId) { this.grnId = grnId; }

    public LocalDate getBillDate() { return billDate; }
    public void setBillDate(LocalDate billDate) { this.billDate = billDate; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public String getSupplierInvoiceRef() { return supplierInvoiceRef; }
    public void setSupplierInvoiceRef(String supplierInvoiceRef) { this.supplierInvoiceRef = supplierInvoiceRef; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getTaxAmount() { return taxAmount; }
    public void setTaxAmount(BigDecimal taxAmount) { this.taxAmount = taxAmount; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getPaidAmount() { return paidAmount; }
    public void setPaidAmount(BigDecimal paidAmount) { this.paidAmount = paidAmount; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<SupplierBillLine> getLines() { return lines; }
    public void setLines(List<SupplierBillLine> lines) { this.lines = lines; }
}