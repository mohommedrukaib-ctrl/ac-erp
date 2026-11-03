package com.aceerp.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class AssemblyBuild {
    private int id;
    private String buildNumber;
    private int bomId;
    private String bomCode;
    private int assemblyProductId;
    private String assemblyProductCode;
    private String assemblyProductName;
    private int quantityToBuild = 1;
    private BigDecimal totalCost = BigDecimal.ZERO;
    private LocalDate buildDate = LocalDate.now();
    private Integer journalEntryId;
    private String notes;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getBuildNumber() { return buildNumber; }
    public void setBuildNumber(String buildNumber) { this.buildNumber = buildNumber; }

    public int getBomId() { return bomId; }
    public void setBomId(int bomId) { this.bomId = bomId; }

    public String getBomCode() { return bomCode; }
    public void setBomCode(String bomCode) { this.bomCode = bomCode; }

    public int getAssemblyProductId() { return assemblyProductId; }
    public void setAssemblyProductId(int assemblyProductId) { this.assemblyProductId = assemblyProductId; }

    public String getAssemblyProductCode() { return assemblyProductCode; }
    public void setAssemblyProductCode(String assemblyProductCode) { this.assemblyProductCode = assemblyProductCode; }

    public String getAssemblyProductName() { return assemblyProductName; }
    public void setAssemblyProductName(String assemblyProductName) { this.assemblyProductName = assemblyProductName; }

    public int getQuantityToBuild() { return quantityToBuild; }
    public void setQuantityToBuild(int quantityToBuild) { this.quantityToBuild = quantityToBuild; }

    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }

    public LocalDate getBuildDate() { return buildDate; }
    public void setBuildDate(LocalDate buildDate) { this.buildDate = buildDate; }

    public Integer getJournalEntryId() { return journalEntryId; }
    public void setJournalEntryId(Integer journalEntryId) { this.journalEntryId = journalEntryId; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
