package com.aceerp.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class JobBudget {
    private int id;
    private int serviceJobId;
    private BigDecimal budgetedMaterials = BigDecimal.ZERO;
    private BigDecimal budgetedLabor = BigDecimal.ZERO;
    private BigDecimal budgetedEquipment = BigDecimal.ZERO;
    private BigDecimal budgetedSubcontractor = BigDecimal.ZERO;
    private BigDecimal budgetedOverhead = BigDecimal.ZERO;
    private BigDecimal totalBudgetedCost = BigDecimal.ZERO;

    public void recalculateTotal() {
        budgetedMaterials = nz(budgetedMaterials);
        budgetedLabor = nz(budgetedLabor);
        budgetedEquipment = nz(budgetedEquipment);
        budgetedSubcontractor = nz(budgetedSubcontractor);
        budgetedOverhead = nz(budgetedOverhead);
        totalBudgetedCost = budgetedMaterials
                .add(budgetedLabor)
                .add(budgetedEquipment)
                .add(budgetedSubcontractor)
                .add(budgetedOverhead)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getServiceJobId() { return serviceJobId; }
    public void setServiceJobId(int serviceJobId) { this.serviceJobId = serviceJobId; }

    public BigDecimal getBudgetedMaterials() { return budgetedMaterials; }
    public void setBudgetedMaterials(BigDecimal budgetedMaterials) { this.budgetedMaterials = nz(budgetedMaterials); }

    public BigDecimal getBudgetedLabor() { return budgetedLabor; }
    public void setBudgetedLabor(BigDecimal budgetedLabor) { this.budgetedLabor = nz(budgetedLabor); }

    public BigDecimal getBudgetedEquipment() { return budgetedEquipment; }
    public void setBudgetedEquipment(BigDecimal budgetedEquipment) { this.budgetedEquipment = nz(budgetedEquipment); }

    public BigDecimal getBudgetedSubcontractor() { return budgetedSubcontractor; }
    public void setBudgetedSubcontractor(BigDecimal budgetedSubcontractor) { this.budgetedSubcontractor = nz(budgetedSubcontractor); }

    public BigDecimal getBudgetedOverhead() { return budgetedOverhead; }
    public void setBudgetedOverhead(BigDecimal budgetedOverhead) { this.budgetedOverhead = nz(budgetedOverhead); }

    public BigDecimal getTotalBudgetedCost() { return totalBudgetedCost; }
    public void setTotalBudgetedCost(BigDecimal totalBudgetedCost) { this.totalBudgetedCost = nz(totalBudgetedCost); }
}