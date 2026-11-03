package com.aceerp.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class JobProfitabilityDTO {
    private int serviceJobId;
    private String jobRefNumber;
    private String customerName;
    private String jobTitle;
    private String status;

    private BigDecimal contractPrice = BigDecimal.ZERO;
    private BigDecimal budgetedCost = BigDecimal.ZERO;
    private BigDecimal actualCost = BigDecimal.ZERO;
    private BigDecimal costVariance = BigDecimal.ZERO; // Budgeted - Actual
    private BigDecimal netProfit = BigDecimal.ZERO;    // Contract Price - Actual Cost
    private BigDecimal grossMarginPercent = BigDecimal.ZERO;

    public void calculate() {
        if (contractPrice == null) contractPrice = BigDecimal.ZERO;
        if (budgetedCost == null) budgetedCost = BigDecimal.ZERO;
        if (actualCost == null) actualCost = BigDecimal.ZERO;

        costVariance = budgetedCost.subtract(actualCost).setScale(2, RoundingMode.HALF_UP);
        netProfit = contractPrice.subtract(actualCost).setScale(2, RoundingMode.HALF_UP);

        if (contractPrice.compareTo(BigDecimal.ZERO) > 0) {
            grossMarginPercent = netProfit.divide(contractPrice, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        } else {
            grossMarginPercent = BigDecimal.ZERO;
        }
    }

    public int getServiceJobId() { return serviceJobId; }
    public void setServiceJobId(int serviceJobId) { this.serviceJobId = serviceJobId; }

    public String getJobRefNumber() { return jobRefNumber; }
    public void setJobRefNumber(String jobRefNumber) { this.jobRefNumber = jobRefNumber; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String jobTitle) { this.jobTitle = jobTitle; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getContractPrice() { return contractPrice; }
    public void setContractPrice(BigDecimal contractPrice) { this.contractPrice = contractPrice; calculate(); }

    public BigDecimal getBudgetedCost() { return budgetedCost; }
    public void setBudgetedCost(BigDecimal budgetedCost) { this.budgetedCost = budgetedCost; calculate(); }

    public BigDecimal getActualCost() { return actualCost; }
    public void setActualCost(BigDecimal actualCost) { this.actualCost = actualCost; calculate(); }

    public BigDecimal getCostVariance() { return costVariance; }
    public BigDecimal getNetProfit() { return netProfit; }
    public BigDecimal getGrossMarginPercent() { return grossMarginPercent; }
}