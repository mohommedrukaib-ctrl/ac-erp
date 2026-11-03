package com.aceerp.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class BillOfMaterials {
    private int id;
    private int assemblyProductId;
    private String assemblyProductCode;
    private String assemblyProductName;
    private String bomCode;
    private String description;
    private BigDecimal totalCost = BigDecimal.ZERO;
    private List<BomComponent> components = new ArrayList<>();

    public void recalculateTotalCost() {
        BigDecimal sum = BigDecimal.ZERO;
        if (components != null) {
            for (BomComponent comp : components) {
                sum = sum.add(comp.getTotalCost());
            }
        }
        this.totalCost = sum.setScale(2, RoundingMode.HALF_UP);
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getAssemblyProductId() { return assemblyProductId; }
    public void setAssemblyProductId(int assemblyProductId) { this.assemblyProductId = assemblyProductId; }

    public String getAssemblyProductCode() { return assemblyProductCode; }
    public void setAssemblyProductCode(String assemblyProductCode) { this.assemblyProductCode = assemblyProductCode; }

    public String getAssemblyProductName() { return assemblyProductName; }
    public void setAssemblyProductName(String assemblyProductName) { this.assemblyProductName = assemblyProductName; }

    public String getBomCode() { return bomCode; }
    public void setBomCode(String bomCode) { this.bomCode = bomCode; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getTotalCost() { return totalCost; }
    public void setTotalCost(BigDecimal totalCost) { this.totalCost = totalCost; }

    public List<BomComponent> getComponents() { return components; }
    public void setComponents(List<BomComponent> components) {
        this.components = components;
        recalculateTotalCost();
    }
}