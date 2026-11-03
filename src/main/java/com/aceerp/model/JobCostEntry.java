package com.aceerp.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public class JobCostEntry {
    private int id;
    private int serviceJobId;
    private String costCategory; // MATERIAL, LABOR, EQUIPMENT, SUBCONTRACTOR, OVERHEAD
    private LocalDate entryDate = LocalDate.now();
    private String description;
    private BigDecimal quantity = BigDecimal.ONE;
    private BigDecimal unitCost = BigDecimal.ZERO;
    private BigDecimal totalCost = BigDecimal.ZERO;
    private String sourceType; // SUPPLIER_BILL, STOCK_ISSUE, TIMESHEET, MANUAL
    private Integer sourceId;
    private Integer journalEntryId;

    public void recalculateTotal() {
        if (quantity == null) quantity = BigDecimal.ONE;
        if (unitCost == null) unitCost = BigDecimal.ZERO;
        totalCost = quantity.multiply(unitCost).setScale(2, RoundingMode.HALF_UP);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getServiceJobId() { return serviceJobId; }
    public void setServiceJobId(int serviceJobId) { this.serviceJobId = serviceJobId; }

    public String getCostCategory() { return costCategory; }
    public void setCostCategory(String costCategory) { this.costCategory = costCategory; }

    public LocalDate getEntryDate() { return entryDate; }
    public void setEntryDate(LocalDate entryDate) { this.entryDate = entryDate; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; recalculateTotal(); }

    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; recalculateTotal(); }

    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public Integer getSourceId() { return sourceId; }
    public void setSourceId(Integer sourceId) { this.sourceId = sourceId; }

    public Integer getJournalEntryId() { return journalEntryId; }
    public void setJournalEntryId(Integer journalEntryId) { this.journalEntryId = journalEntryId; }
}