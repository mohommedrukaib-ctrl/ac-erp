package com.aceerp.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Customer {
    private int id;
    private String customerCode;
    private String name;
    private String phone;
    private String email;
    private String address;
    private String city;
    private String taxNumber;
    private String notes;
    private BigDecimal balance = BigDecimal.ZERO;
    private boolean active = true;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Customer() {}

    public Customer(String customerCode, String name, String phone, String email) {
        this.customerCode = customerCode;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.active = true;
    }

    public String getDisplayString() {
        return name + (phone != null && !phone.isEmpty() ? " (" + phone + ")" : "");
    }

    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getCustomerCode() { return customerCode; }
    public void setCustomerCode(String customerCode) { this.customerCode = customerCode; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getTaxNumber() { return taxNumber; }
    public void setTaxNumber(String taxNumber) { this.taxNumber = taxNumber; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() { return getDisplayString(); }

        public String getStatusDisplay() {
        return active ? "Active" : "Inactive";
    }
}