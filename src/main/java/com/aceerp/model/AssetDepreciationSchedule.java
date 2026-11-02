package com.aceerp.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class AssetDepreciationSchedule {
    private int id;
    private int fixedAssetId;
    private LocalDate depreciationDate;
    private int periodYear;
    private int periodMonth;
    private BigDecimal depreciationAmount = BigDecimal.ZERO;
    private Integer journalEntryId;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getFixedAssetId() { return fixedAssetId; }
    public void setFixedAssetId(int fixedAssetId) { this.fixedAssetId = fixedAssetId; }

    public LocalDate getDepreciationDate() { return depreciationDate; }
    public void setDepreciationDate(LocalDate depreciationDate) { this.depreciationDate = depreciationDate; }

    public int getPeriodYear() { return periodYear; }
    public void setPeriodYear(int periodYear) { this.periodYear = periodYear; }

    public int getPeriodMonth() { return periodMonth; }
    public void setPeriodMonth(int periodMonth) { this.periodMonth = periodMonth; }

    public BigDecimal getDepreciationAmount() { return depreciationAmount; }
    public void setDepreciationAmount(BigDecimal depreciationAmount) { this.depreciationAmount = depreciationAmount; }

    public Integer getJournalEntryId() { return journalEntryId; }
    public void setJournalEntryId(Integer journalEntryId) { this.journalEntryId = journalEntryId; }
}