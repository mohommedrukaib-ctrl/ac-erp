package com.aceerp.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


public class ServiceJob {
    private int id;
    private String jobNumber;
    private int customerId;
    private String customerName;
    private Integer technicianId;
    private String technicianName;
    private String jobType = "REPAIR"; // INSTALLATION, REPAIR, MAINTENANCE, GAS_REFILL
    private String status = "PENDING"; // PENDING, IN_PROGRESS, COMPLETED, CANCELLED
    private LocalDate scheduledDate = LocalDate.now();
    private LocalDate completionDate;
    private String acModelInfo;
    private String reportedIssue;
    private String resolutionNotes;
    private BigDecimal laborCost = BigDecimal.ZERO;
    private BigDecimal partsTotal = BigDecimal.ZERO;
    private BigDecimal grandTotal = BigDecimal.ZERO;
    private LocalDateTime createdAt;
    private Integer createdBy;
    private List<ServiceJobLine> lines = new ArrayList<>();

    public ServiceJob() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getJobNumber() { return jobNumber; }
    public void setJobNumber(String jobNumber) { this.jobNumber = jobNumber; }

    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public Integer getTechnicianId() { return technicianId; }
    public void setTechnicianId(Integer technicianId) { this.technicianId = technicianId; }

    public String getTechnicianName() { return technicianName; }
    public void setTechnicianName(String technicianName) { this.technicianName = technicianName; }

    public String getJobType() { return jobType; }
    public void setJobType(String jobType) { this.jobType = jobType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }

    public LocalDate getCompletionDate() { return completionDate; }
    public void setCompletionDate(LocalDate completionDate) { this.completionDate = completionDate; }

    public String getAcModelInfo() { return acModelInfo; }
    public void setAcModelInfo(String acModelInfo) { this.acModelInfo = acModelInfo; }

    public String getReportedIssue() { return reportedIssue; }
    public void setReportedIssue(String reportedIssue) { this.reportedIssue = reportedIssue; }

    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }

    public BigDecimal getLaborCost() { return laborCost; }
    public void setLaborCost(BigDecimal laborCost) {
        this.laborCost = laborCost != null ? laborCost : BigDecimal.ZERO;
        recalculateTotal();
    }

    public BigDecimal getPartsTotal() { return partsTotal; }
    public void setPartsTotal(BigDecimal partsTotal) { this.partsTotal = partsTotal; }

    public BigDecimal getGrandTotal() { return grandTotal; }
    public void setGrandTotal(BigDecimal grandTotal) { this.grandTotal = grandTotal; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }

    public List<ServiceJobLine> getLines() { return lines; }
    public void setLines(List<ServiceJobLine> lines) {
        this.lines = lines;
        recalculateTotal();
    }

    public void recalculateTotal() {
        BigDecimal partsSum = BigDecimal.ZERO;
        if (lines != null) {
            for (ServiceJobLine line : lines) {
                line.recalculateTotal();
                partsSum = partsSum.add(line.getLineTotal());
            }
        }
        this.partsTotal = partsSum;
        this.grandTotal = this.laborCost.add(this.partsTotal);
    }
}