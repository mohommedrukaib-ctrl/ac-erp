package com.aceerp.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class DeliveryNote {
    private Integer id;
    private String dnNumber;
    private Integer soId;
    private String soNumber;
    private Integer customerId;
    private String customerName;
    private LocalDate dispatchDate;
    private String driverName;
    private String vehicleNo;
    private String deliveryAddress;
    private String status; // DRAFT, DISPATCHED, DELIVERED, CANCELLED
    private String notes;
    private Integer createdBy;
    private LocalDateTime createdAt;
    private List<DeliveryNoteLine> lines = new ArrayList<>();

    public DeliveryNote() {
        this.dispatchDate = LocalDate.now();
        this.status = "DISPATCHED";
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getDnNumber() { return dnNumber; }
    public void setDnNumber(String dnNumber) { this.dnNumber = dnNumber; }

    public Integer getSoId() { return soId; }
    public void setSoId(Integer soId) { this.soId = soId; }

    public String getSoNumber() { return soNumber; }
    public void setSoNumber(String soNumber) { this.soNumber = soNumber; }

    public Integer getCustomerId() { return customerId; }
    public void setCustomerId(Integer customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public LocalDate getDispatchDate() { return dispatchDate; }
    public void setDispatchDate(LocalDate dispatchDate) { this.dispatchDate = dispatchDate; }

    public String getDriverName() { return driverName; }
    public void setDriverName(String driverName) { this.driverName = driverName; }

    public String getVehicleNo() { return vehicleNo; }
    public void setVehicleNo(String vehicleNo) { this.vehicleNo = vehicleNo; }

    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<DeliveryNoteLine> getLines() { return lines; }
    public void setLines(List<DeliveryNoteLine> lines) { this.lines = lines; }

    @Override
    public String toString() {
        return (dnNumber != null ? dnNumber : "Draft DN") + 
               (customerName != null && !customerName.isEmpty() ? " - " + customerName : "");
    }
}