package com.aceerp.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class JournalEntry {

    private int id;
    private String entryNumber;
    private LocalDate entryDate;
    private String reference;
    private String description;
    private String status; // DRAFT, POSTED, VOID
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private List<JournalEntryLine> lines = new ArrayList<>();

    public JournalEntry() {}

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    // ---- Entry Number ----
    public String getEntryNumber() {
        return entryNumber;
    }

    public void setEntryNumber(String entryNumber) {
        this.entryNumber = entryNumber;
    }

    public String getEntryNo() {
        return entryNumber;
    }

    public void setEntryNo(String entryNo) {
        this.entryNumber = entryNo;
    }

    // ---- Entry Date ----
    public LocalDate getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(LocalDate entryDate) {
        this.entryDate = entryDate;
    }

    public LocalDate getDate() {
        return entryDate;
    }

    public void setDate(LocalDate date) {
        this.entryDate = date;
    }

    // ---- Reference ----
    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getRef() {
        return reference;
    }

    public void setRef(String ref) {
        this.reference = ref;
    }

    // ---- Description / Memo ----
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMemo() {
        return description;
    }

    public void setMemo(String memo) {
        this.description = memo;
    }

    public String getNotes() {
        return description;
    }

    public void setNotes(String notes) {
        this.description = notes;
    }

    // ---- Status ----
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    // ---- Total Amount ----
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount != null ? totalAmount : BigDecimal.ZERO;
    }

    public BigDecimal getTotal() {
        return totalAmount;
    }

    public void setTotal(BigDecimal total) {
        this.totalAmount = total != null ? total : BigDecimal.ZERO;
    }

    public BigDecimal getAmount() {
        return totalAmount;
    }

    public void setAmount(BigDecimal amount) {
        this.totalAmount = amount != null ? amount : BigDecimal.ZERO;
    }

    // ---- Lines ----
    public List<JournalEntryLine> getLines() {
        return lines;
    }

    public void setLines(List<JournalEntryLine> lines) {
        this.lines = lines != null ? lines : new ArrayList<>();
    }
}