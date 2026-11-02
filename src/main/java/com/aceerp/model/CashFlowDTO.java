
package com.aceerp.model;

import java.math.BigDecimal;

public class CashFlowDTO {

    private BigDecimal netIncome = BigDecimal.ZERO;
    private BigDecimal arChange = BigDecimal.ZERO;
    private BigDecimal apChange = BigDecimal.ZERO;
    private BigDecimal inventoryChange = BigDecimal.ZERO;
    private BigDecimal operatingCashFlow = BigDecimal.ZERO;

    private BigDecimal investingCashFlow = BigDecimal.ZERO;
    private BigDecimal financingCashFlow = BigDecimal.ZERO;

    private BigDecimal beginningCash = BigDecimal.ZERO;
    private BigDecimal netCashChange = BigDecimal.ZERO;
    private BigDecimal endingCash = BigDecimal.ZERO;

    public CashFlowDTO() {}

    public BigDecimal getNetIncome() { return netIncome; }
    public void setNetIncome(BigDecimal netIncome) { this.netIncome = netIncome; }

    public BigDecimal getArChange() { return arChange; }
    public void setArChange(BigDecimal arChange) { this.arChange = arChange; }

    public BigDecimal getApChange() { return apChange; }
    public void setApChange(BigDecimal apChange) { this.apChange = apChange; }

    public BigDecimal getInventoryChange() { return inventoryChange; }
    public void setInventoryChange(BigDecimal inventoryChange) { this.inventoryChange = inventoryChange; }

    public BigDecimal getOperatingCashFlow() { return operatingCashFlow; }
    public void setOperatingCashFlow(BigDecimal operatingCashFlow) { this.operatingCashFlow = operatingCashFlow; }

    public BigDecimal getInvestingCashFlow() { return investingCashFlow; }
    public void setInvestingCashFlow(BigDecimal investingCashFlow) { this.investingCashFlow = investingCashFlow; }

    public BigDecimal getFinancingCashFlow() { return financingCashFlow; }
    public void setFinancingCashFlow(BigDecimal financingCashFlow) { this.financingCashFlow = financingCashFlow; }

    public BigDecimal getBeginningCash() { return beginningCash; }
    public void setBeginningCash(BigDecimal beginningCash) { this.beginningCash = beginningCash; }

    public BigDecimal getNetCashChange() { return netCashChange; }
    public void setNetCashChange(BigDecimal netCashChange) { this.netCashChange = netCashChange; }

    public BigDecimal getEndingCash() { return endingCash; }
    public void setEndingCash(BigDecimal endingCash) { this.endingCash = endingCash; }
}
