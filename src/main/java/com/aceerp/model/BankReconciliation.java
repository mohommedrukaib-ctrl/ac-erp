package com.aceerp.model;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;

public class BankReconciliation {
    private int id;
    private int accountId;
    private String accountCode;
    private String accountName;
    private LocalDate statementDate;
    private BigDecimal beginningBalance = BigDecimal.ZERO;
    private BigDecimal statementEndingBalance = BigDecimal.ZERO;
    private int clearedDepositsCount;
    private BigDecimal clearedDepositsAmount = BigDecimal.ZERO;
    private int clearedChecksCount;
    private BigDecimal clearedChecksAmount = BigDecimal.ZERO;
    private BigDecimal clearedBalance = BigDecimal.ZERO;
    private BigDecimal difference = BigDecimal.ZERO;
    private String status = "IN_PROGRESS";
    private String notes;
    private Timestamp reconciledAt;
    private Integer reconciledBy;

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getAccountId() { return accountId; }
    public void setAccountId(int accountId) { this.accountId = accountId; }

    public String getAccountCode() { return accountCode; }
    public void setAccountCode(String accountCode) { this.accountCode = accountCode; }

    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }

    public LocalDate getStatementDate() { return statementDate; }
    public void setStatementDate(LocalDate statementDate) { this.statementDate = statementDate; }

    public BigDecimal getBeginningBalance() { return beginningBalance; }
    public void setBeginningBalance(BigDecimal beginningBalance) { this.beginningBalance = beginningBalance; }

    public BigDecimal getStatementEndingBalance() { return statementEndingBalance; }
    public void setStatementEndingBalance(BigDecimal statementEndingBalance) { this.statementEndingBalance = statementEndingBalance; }

    public int getClearedDepositsCount() { return clearedDepositsCount; }
    public void setClearedDepositsCount(int clearedDepositsCount) { this.clearedDepositsCount = clearedDepositsCount; }

    public BigDecimal getClearedDepositsAmount() { return clearedDepositsAmount; }
    public void setClearedDepositsAmount(BigDecimal clearedDepositsAmount) { this.clearedDepositsAmount = clearedDepositsAmount; }

    public int getClearedChecksCount() { return clearedChecksCount; }
    public void setClearedChecksCount(int clearedChecksCount) { this.clearedChecksCount = clearedChecksCount; }

    public BigDecimal getClearedChecksAmount() { return clearedChecksAmount; }
    public void setClearedChecksAmount(BigDecimal clearedChecksAmount) { this.clearedChecksAmount = clearedChecksAmount; }

    public BigDecimal getClearedBalance() { return clearedBalance; }
    public void setClearedBalance(BigDecimal clearedBalance) { this.clearedBalance = clearedBalance; }

    public BigDecimal getDifference() { return difference; }
    public void setDifference(BigDecimal difference) { this.difference = difference; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Timestamp getReconciledAt() { return reconciledAt; }
    public void setReconciledAt(Timestamp reconciledAt) { this.reconciledAt = reconciledAt; }

    public Integer getReconciledBy() { return reconciledBy; }
    public void setReconciledBy(Integer reconciledBy) { this.reconciledBy = reconciledBy; }
}
