package com.aceerp.model;

import java.math.BigDecimal;

public class ProfitAndLossDTO {
    private BigDecimal salesRevenue = BigDecimal.ZERO;
    private BigDecimal serviceRevenue = BigDecimal.ZERO;
    private BigDecimal totalRevenue = BigDecimal.ZERO;
    private BigDecimal costOfGoodsSold = BigDecimal.ZERO;
    private BigDecimal grossProfit = BigDecimal.ZERO;
    private BigDecimal purchaseExpenses = BigDecimal.ZERO;
    private BigDecimal netProfit = BigDecimal.ZERO;

    public BigDecimal getSalesRevenue() { return salesRevenue; }
    public void setSalesRevenue(BigDecimal v) { this.salesRevenue = v != null ? v : BigDecimal.ZERO; }

    public BigDecimal getServiceRevenue() { return serviceRevenue; }
    public void setServiceRevenue(BigDecimal v) { this.serviceRevenue = v != null ? v : BigDecimal.ZERO; }

    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal v) { this.totalRevenue = v != null ? v : BigDecimal.ZERO; }

    public BigDecimal getCostOfGoodsSold() { return costOfGoodsSold; }
    public void setCostOfGoodsSold(BigDecimal v) { this.costOfGoodsSold = v != null ? v : BigDecimal.ZERO; }

    public BigDecimal getGrossProfit() { return grossProfit; }
    public void setGrossProfit(BigDecimal v) { this.grossProfit = v != null ? v : BigDecimal.ZERO; }

    public BigDecimal getPurchaseExpenses() { return purchaseExpenses; }
    public void setPurchaseExpenses(BigDecimal v) { this.purchaseExpenses = v != null ? v : BigDecimal.ZERO; }

    public BigDecimal getNetProfit() { return netProfit; }
    public void setNetProfit(BigDecimal v) { this.netProfit = v != null ? v : BigDecimal.ZERO; }

    public void calculateTotals() {
        this.totalRevenue = salesRevenue.add(serviceRevenue);
        this.grossProfit = totalRevenue.subtract(costOfGoodsSold);
        this.netProfit = grossProfit.subtract(purchaseExpenses);
    }
}