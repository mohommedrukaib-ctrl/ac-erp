
package com.aceerp.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class BalanceSheetDTO {

    public record BalanceRow(String accountCode, String accountName, String accountType, BigDecimal balance) {}

    private List<BalanceRow> assets = new ArrayList<>();
    private List<BalanceRow> liabilities = new ArrayList<>();
    private List<BalanceRow> equity = new ArrayList<>();

    private BigDecimal totalAssets = BigDecimal.ZERO;
    private BigDecimal totalLiabilities = BigDecimal.ZERO;
    private BigDecimal totalEquity = BigDecimal.ZERO;
    private BigDecimal currentYearNetIncome = BigDecimal.ZERO;
    private BigDecimal totalLiabilitiesAndEquity = BigDecimal.ZERO;

    public BalanceSheetDTO() {}

    public List<BalanceRow> getAssets() { return assets; }
    public void setAssets(List<BalanceRow> assets) { this.assets = assets; }

    public List<BalanceRow> getLiabilities() { return liabilities; }
    public void setLiabilities(List<BalanceRow> liabilities) { this.liabilities = liabilities; }

    public List<BalanceRow> getEquity() { return equity; }
    public void setEquity(List<BalanceRow> equity) { this.equity = equity; }

    public BigDecimal getTotalAssets() { return totalAssets; }
    public void setTotalAssets(BigDecimal totalAssets) { this.totalAssets = totalAssets; }

    public BigDecimal getTotalLiabilities() { return totalLiabilities; }
    public void setTotalLiabilities(BigDecimal totalLiabilities) { this.totalLiabilities = totalLiabilities; }

    public BigDecimal getTotalEquity() { return totalEquity; }
    public void setTotalEquity(BigDecimal totalEquity) { this.totalEquity = totalEquity; }

    public BigDecimal getCurrentYearNetIncome() { return currentYearNetIncome; }
    public void setCurrentYearNetIncome(BigDecimal currentYearNetIncome) { this.currentYearNetIncome = currentYearNetIncome; }

    public BigDecimal getTotalLiabilitiesAndEquity() { return totalLiabilitiesAndEquity; }
    public void setTotalLiabilitiesAndEquity(BigDecimal totalLiabilitiesAndEquity) { this.totalLiabilitiesAndEquity = totalLiabilitiesAndEquity; }

    public boolean isBalanced() {
        return totalAssets.compareTo(totalLiabilitiesAndEquity) == 0;
    }
}
