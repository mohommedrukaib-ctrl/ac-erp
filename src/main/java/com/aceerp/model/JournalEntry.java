package com.aceerp.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class JournalEntry {

    private int id;
    private String entryNumber;
    private LocalDate entryDate;
    private String reference;          // display / legacy
    private String referenceType;      // INVOICE, BILL, PAYMENT, MANUAL, etc.
    private Integer referenceId;
    private String description;        // memo
    private String status = "DRAFT";   // DRAFT, POSTED, VOID
    private BigDecimal totalAmount = BigDecimal.ZERO;
    private BigDecimal totalDebit = BigDecimal.ZERO;
    private BigDecimal totalCredit = BigDecimal.ZERO;
    private boolean autoPosted;
    private Integer createdBy;
    private List<JournalEntryLine> lines = new ArrayList<>();

    public JournalEntry() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getEntryNumber() { return entryNumber; }
    public void setEntryNumber(String entryNumber) { this.entryNumber = entryNumber; }
    public String getEntryNo() { return entryNumber; }
    public void setEntryNo(String entryNo) { this.entryNumber = entryNo; }

    public LocalDate getEntryDate() { return entryDate; }
    public void setEntryDate(LocalDate entryDate) { this.entryDate = entryDate; }
    public LocalDate getDate() { return entryDate; }
    public void setDate(LocalDate date) { this.entryDate = date; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getRef() { return reference; }
    public void setRef(String ref) { this.reference = ref; }

    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }

    public Integer getReferenceId() { return referenceId; }
    public void setReferenceId(Integer referenceId) { this.referenceId = referenceId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getMemo() { return description; }
    public void setMemo(String memo) { this.description = memo; }
    public String getNotes() { return description; }
    public void setNotes(String notes) { this.description = notes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount != null ? totalAmount : BigDecimal.ZERO;
    }
    public BigDecimal getTotal() { return totalAmount; }
    public void setTotal(BigDecimal total) { setTotalAmount(total); }
    public BigDecimal getAmount() { return totalAmount; }
    public void setAmount(BigDecimal amount) { setTotalAmount(amount); }

    public BigDecimal getTotalDebit() { return totalDebit; }
    public void setTotalDebit(BigDecimal totalDebit) {
        this.totalDebit = totalDebit != null ? totalDebit : BigDecimal.ZERO;
    }

    public BigDecimal getTotalCredit() { return totalCredit; }
    public void setTotalCredit(BigDecimal totalCredit) {
        this.totalCredit = totalCredit != null ? totalCredit : BigDecimal.ZERO;
    }

    public boolean isAutoPosted() { return autoPosted; }
    public void setAutoPosted(boolean autoPosted) { this.autoPosted = autoPosted; }

    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }

    public List<JournalEntryLine> getLines() { return lines; }
    public void setLines(List<JournalEntryLine> lines) {
        this.lines = lines != null ? lines : new ArrayList<>();
    }

    /** Recalculate debit/credit totals from lines. */
    public void recalculateTotals() {
        BigDecimal dr = BigDecimal.ZERO;
        BigDecimal cr = BigDecimal.ZERO;
        if (lines != null) {
            for (JournalEntryLine line : lines) {
                if (line.getDebit() != null) dr = dr.add(line.getDebit());
                if (line.getCredit() != null) cr = cr.add(line.getCredit());
            }
        }
        this.totalDebit = dr.setScale(2, RoundingMode.HALF_UP);
        this.totalCredit = cr.setScale(2, RoundingMode.HALF_UP);
        this.totalAmount = this.totalDebit;
    }

    /** True only when DR == CR and both > 0. */
    public boolean isBalanced() {
        recalculateTotals();
        return totalDebit.compareTo(BigDecimal.ZERO) > 0
                && totalDebit.compareTo(totalCredit) == 0;
    }
}