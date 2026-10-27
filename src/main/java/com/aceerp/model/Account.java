package com.aceerp.model;

import java.math.BigDecimal;

public class Account {

    private int id;
    private String accountCode;
    private String accountName;
    private String accountType; // ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE
    private BigDecimal currentBalance = BigDecimal.ZERO;

    public Account() {}

    public Account(int id, String accountCode, String accountName, String accountType, BigDecimal currentBalance) {
        this.id = id;
        this.accountCode = accountCode;
        this.accountName = accountName;
        this.accountType = accountType;
        this.currentBalance = currentBalance != null ? currentBalance : BigDecimal.ZERO;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getAccountCode() { return accountCode; }
    public void setAccountCode(String accountCode) { this.accountCode = accountCode; }
    public String getCode() { return accountCode; }
    public void setCode(String code) { this.accountCode = code; }

    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public String getName() { return accountName; }
    public void setName(String name) { this.accountName = name; }

    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }
    public String getType() { return accountType; }
    public void setType(String type) { this.accountType = type; }

    public BigDecimal getCurrentBalance() { return currentBalance; }
    public void setCurrentBalance(BigDecimal currentBalance) { this.currentBalance = currentBalance; }
    public BigDecimal getBalance() { return currentBalance; }
    public void setBalance(BigDecimal balance) { this.currentBalance = balance; }

    @Override
    public String toString() {
        return (accountCode != null ? accountCode : "") + " - " + (accountName != null ? accountName : "");
    }
}