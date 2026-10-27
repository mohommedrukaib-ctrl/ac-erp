package com.aceerp.model;

import java.math.BigDecimal;

public class JournalEntryLine {

    private int id;
    private int journalEntryId;
    private int accountId;
    private String accountCode;
    private String accountName;
    private String description;
    private BigDecimal debit = BigDecimal.ZERO;
    private BigDecimal credit = BigDecimal.ZERO;

    public JournalEntryLine() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getJournalEntryId() { return journalEntryId; }
    public void setJournalEntryId(int journalEntryId) { this.journalEntryId = journalEntryId; }

    public int getAccountId() { return accountId; }
    public void setAccountId(int accountId) { this.accountId = accountId; }

    public String getAccountCode() { return accountCode; }
    public void setAccountCode(String accountCode) { this.accountCode = accountCode; }
    public String getCode() { return accountCode; }
    public void setCode(String code) { this.accountCode = code; }

    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public String getName() { return accountName; }
    public void setName(String name) { this.accountName = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getMemo() { return description; }
    public void setMemo(String memo) { this.description = memo; }

    public BigDecimal getDebit() { return debit; }
    public void setDebit(BigDecimal debit) { this.debit = debit != null ? debit : BigDecimal.ZERO; }

    public BigDecimal getCredit() { return credit; }
    public void setCredit(BigDecimal credit) { this.credit = credit != null ? credit : BigDecimal.ZERO; }
}