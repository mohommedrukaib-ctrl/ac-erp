package com.aceerp.model;

import java.math.BigDecimal;

public class Account {

    private int id;
    private String accountCode;
    private String accountName;
    private String accountType; // ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE
    private BigDecimal currentBalance = BigDecimal.ZERO;
    private Integer parentId;
    private String normalBalance = "DEBIT"; // DEBIT or CREDIT
    private boolean systemAccount;
    private boolean active = true;
    private String description;

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
    public void setCurrentBalance(BigDecimal currentBalance) {
        this.currentBalance = currentBalance != null ? currentBalance : BigDecimal.ZERO;
    }
    public BigDecimal getBalance() { return currentBalance; }
    public void setBalance(BigDecimal balance) { setCurrentBalance(balance); }

    public Integer getParentId() { return parentId; }
    public void setParentId(Integer parentId) { this.parentId = parentId; }

    public String getNormalBalance() { return normalBalance; }
    public void setNormalBalance(String normalBalance) { this.normalBalance = normalBalance; }

    public boolean isSystemAccount() { return systemAccount; }
    public void setSystemAccount(boolean systemAccount) { this.systemAccount = systemAccount; }
    public boolean getIsSystem() { return systemAccount; }
    public void setIsSystem(boolean isSystem) { this.systemAccount = isSystem; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /** True if this is a debit-normal account (Asset/Expense). */
    public boolean isDebitNormal() {
        return "DEBIT".equalsIgnoreCase(normalBalance)
                || "ASSET".equalsIgnoreCase(accountType)
                || "EXPENSE".equalsIgnoreCase(accountType);
    }

    @Override
    public String toString() {
        return (accountCode != null ? accountCode : "") + " — " + (accountName != null ? accountName : "");
    }
}