package com.aceerp.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ReconciliationItemDTO {
    private int journalEntryLineId;
    private int journalEntryId;
    private String entryNumber;
    private LocalDate entryDate;
    private String memo;
    private String accountCode;
    private String accountName;
    private BigDecimal debit = BigDecimal.ZERO;
    private BigDecimal credit = BigDecimal.ZERO;
    private BigDecimal amount = BigDecimal.ZERO;
    private boolean isDeposit;
    private String reference;
    private final BooleanProperty cleared = new SimpleBooleanProperty(false);

    public int getJournalEntryLineId() { return journalEntryLineId; }
    public void setJournalEntryLineId(int journalEntryLineId) { this.journalEntryLineId = journalEntryLineId; }

    public int getJournalEntryId() { return journalEntryId; }
    public void setJournalEntryId(int journalEntryId) { this.journalEntryId = journalEntryId; }

    public String getEntryNumber() { return entryNumber; }
    public void setEntryNumber(String entryNumber) { this.entryNumber = entryNumber; }

    public LocalDate getEntryDate() { return entryDate; }
    public void setEntryDate(LocalDate entryDate) { this.entryDate = entryDate; }

    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }

    public String getAccountCode() { return accountCode; }
    public void setAccountCode(String accountCode) { this.accountCode = accountCode; }

    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }

    public BigDecimal getDebit() { return debit; }
    public void setDebit(BigDecimal debit) { this.debit = debit; }

    public BigDecimal getCredit() { return credit; }
    public void setCredit(BigDecimal credit) { this.credit = credit; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public boolean isDeposit() { return isDeposit; }
    public void setDeposit(boolean deposit) { isDeposit = deposit; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public boolean isCleared() { return cleared.get(); }
    public void setCleared(boolean cleared) { this.cleared.set(cleared); }
    public BooleanProperty clearedProperty() { return cleared; }
}
