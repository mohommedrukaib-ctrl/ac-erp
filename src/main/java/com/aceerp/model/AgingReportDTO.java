package com.aceerp.model;

import java.math.BigDecimal;

public class AgingReportDTO {
    private String entityName;
    private BigDecimal current0To30 = BigDecimal.ZERO;
    private BigDecimal days31To60 = BigDecimal.ZERO;
    private BigDecimal days61To90 = BigDecimal.ZERO;
    private BigDecimal daysOver90 = BigDecimal.ZERO;
    private BigDecimal totalOutstanding = BigDecimal.ZERO;

    public String getEntityName() { return entityName; }
    public void setEntityName(String entityName) { this.entityName = entityName; }

    public BigDecimal getCurrent0To30() { return current0To30; }
    public void setCurrent0To30(BigDecimal v) {
        this.current0To30 = v != null ? v : BigDecimal.ZERO;
        calc();
    }

    public BigDecimal getDays31To60() { return days31To60; }
    public void setDays31To60(BigDecimal v) {
        this.days31To60 = v != null ? v : BigDecimal.ZERO;
        calc();
    }

    public BigDecimal getDays61To90() { return days61To90; }
    public void setDays61To90(BigDecimal v) {
        this.days61To90 = v != null ? v : BigDecimal.ZERO;
        calc();
    }

    public BigDecimal getDaysOver90() { return daysOver90; }
    public void setDaysOver90(BigDecimal v) {
        this.daysOver90 = v != null ? v : BigDecimal.ZERO;
        calc();
    }

    public BigDecimal getTotalOutstanding() { return totalOutstanding; }

    private void calc() {
        totalOutstanding = current0To30.add(days31To60).add(days61To90).add(daysOver90);
    }
}