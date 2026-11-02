package com.aceerp.model;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public class FixedAsset {
    private int id;
    private String assetCode;
    private String assetName;
    private String category = "VEHICLE"; // VEHICLE, EQUIPMENT, TOOLS, OFFICE
    private LocalDate acquisitionDate = LocalDate.now();
    private BigDecimal acquisitionCost = BigDecimal.ZERO;
    private BigDecimal salvageValue = BigDecimal.ZERO;
    private int usefulLifeYears = 5;
    private String depreciationMethod = "STRAIGHT_LINE"; // STRAIGHT_LINE, DECLINING_BALANCE
    private BigDecimal accumulatedDepreciation = BigDecimal.ZERO;
    private BigDecimal bookValue = BigDecimal.ZERO;
    private Integer assetAccountId;
    private Integer accumulatedDepAccountId;
    private Integer depExpenseAccountId;
    private String status = "ACTIVE"; // ACTIVE, DISPOSED, FULLY_DEPRECIATED
    private String notes;

    public void recalculateBookValue() {
        if (acquisitionCost == null) acquisitionCost = BigDecimal.ZERO;
        if (accumulatedDepreciation == null) accumulatedDepreciation = BigDecimal.ZERO;
        this.bookValue = acquisitionCost.subtract(accumulatedDepreciation).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateMonthlyDepreciation() {
        if (acquisitionCost == null || salvageValue == null || usefulLifeYears <= 0) return BigDecimal.ZERO;
        
        BigDecimal depreciableBase = acquisitionCost.subtract(salvageValue);
        if (depreciableBase.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;

        if ("STRAIGHT_LINE".equalsIgnoreCase(depreciationMethod)) {
            int totalMonths = usefulLifeYears * 12;
            return depreciableBase.divide(BigDecimal.valueOf(totalMonths), 2, RoundingMode.HALF_UP);
        } else if ("DECLINING_BALANCE".equalsIgnoreCase(depreciationMethod)) {
            // Double declining balance rate = (2 / usefulLifeYears)
            BigDecimal annualRate = BigDecimal.valueOf(2.0 / usefulLifeYears);
            BigDecimal currentBook = bookValue != null && bookValue.compareTo(BigDecimal.ZERO) > 0 ? bookValue : acquisitionCost;
            BigDecimal monthlyDep = currentBook.multiply(annualRate).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            
            // Ensure we do not depreciate below salvage value
            if (currentBook.subtract(monthlyDep).compareTo(salvageValue) < 0) {
                monthlyDep = currentBook.subtract(salvageValue);
            }
            return monthlyDep.compareTo(BigDecimal.ZERO) > 0 ? monthlyDep : BigDecimal.ZERO;
        }
        return BigDecimal.ZERO;
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getAssetCode() { return assetCode; }
    public void setAssetCode(String assetCode) { this.assetCode = assetCode; }

    public String getAssetName() { return assetName; }
    public void setAssetName(String assetName) { this.assetName = assetName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public LocalDate getAcquisitionDate() { return acquisitionDate; }
    public void setAcquisitionDate(LocalDate acquisitionDate) { this.acquisitionDate = acquisitionDate; }

    public BigDecimal getAcquisitionCost() { return acquisitionCost; }
    public void setAcquisitionCost(BigDecimal acquisitionCost) { this.acquisitionCost = acquisitionCost; recalculateBookValue(); }

    public BigDecimal getSalvageValue() { return salvageValue; }
    public void setSalvageValue(BigDecimal salvageValue) { this.salvageValue = salvageValue; }

    public int getUsefulLifeYears() { return usefulLifeYears; }
    public void setUsefulLifeYears(int usefulLifeYears) { this.usefulLifeYears = usefulLifeYears; }

    public String getDepreciationMethod() { return depreciationMethod; }
    public void setDepreciationMethod(String depreciationMethod) { this.depreciationMethod = depreciationMethod; }

    public BigDecimal getAccumulatedDepreciation() { return accumulatedDepreciation; }
    public void setAccumulatedDepreciation(BigDecimal accumulatedDepreciation) { this.accumulatedDepreciation = accumulatedDepreciation; recalculateBookValue(); }

    public BigDecimal getBookValue() { return bookValue; }
    public void setBookValue(BigDecimal bookValue) { this.bookValue = bookValue; }

    public Integer getAssetAccountId() { return assetAccountId; }
    public void setAssetAccountId(Integer assetAccountId) { this.assetAccountId = assetAccountId; }

    public Integer getAccumulatedDepAccountId() { return accumulatedDepAccountId; }
    public void setAccumulatedDepAccountId(Integer accumulatedDepAccountId) { this.accumulatedDepAccountId = accumulatedDepAccountId; }

    public Integer getDepExpenseAccountId() { return depExpenseAccountId; }
    public void setDepExpenseAccountId(Integer depExpenseAccountId) { this.depExpenseAccountId = depExpenseAccountId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}