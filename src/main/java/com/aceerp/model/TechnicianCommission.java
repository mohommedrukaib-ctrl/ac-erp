package com.aceerp.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class TechnicianCommission {
    private int id;
    private int technicianId;
    private String technicianName;
    private int serviceJobId;
    private String jobNumber;
    private BigDecimal commissionAmount = BigDecimal.ZERO;
    private String status = "PENDING";
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getTechnicianId() { return technicianId; }
    public void setTechnicianId(int technicianId) { this.technicianId = technicianId; }

    public String getTechnicianName() { return technicianName; }
    public void setTechnicianName(String technicianName) { this.technicianName = technicianName; }

    public int getServiceJobId() { return serviceJobId; }
    public void setServiceJobId(int serviceJobId) { this.serviceJobId = serviceJobId; }

    public String getJobNumber() { return jobNumber; }
    public void setJobNumber(String jobNumber) { this.jobNumber = jobNumber; }

    public BigDecimal getCommissionAmount() { return commissionAmount; }
    public void setCommissionAmount(BigDecimal commissionAmount) {
        this.commissionAmount = commissionAmount != null ? commissionAmount : BigDecimal.ZERO;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}