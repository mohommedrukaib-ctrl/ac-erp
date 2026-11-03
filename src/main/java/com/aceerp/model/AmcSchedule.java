package com.aceerp.model;

import java.time.LocalDate;

public class AmcSchedule {
    private int id;
    private int contractId;
    private LocalDate scheduledDate;
    private String status = "PENDING";
    private Integer serviceJobId;
    private String notes;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getContractId() { return contractId; }
    public void setContractId(int contractId) { this.contractId = contractId; }

    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getServiceJobId() { return serviceJobId; }
    public void setServiceJobId(Integer serviceJobId) { this.serviceJobId = serviceJobId; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}