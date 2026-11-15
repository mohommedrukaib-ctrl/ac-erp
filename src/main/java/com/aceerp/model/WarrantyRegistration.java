package com.aceerp.model;

import java.time.LocalDate;

public class WarrantyRegistration {
    private int id;
    private int productId;
    private int customerId;
    private String serialNumber;
    private String productName;
    private String productCode;
    private String customerName;
    private LocalDate installationDate;
    private LocalDate warrantyExpiry;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public LocalDate getInstallationDate() { return installationDate; }
    public void setInstallationDate(LocalDate installationDate) { this.installationDate = installationDate; }

    public LocalDate getWarrantyExpiry() { return warrantyExpiry; }
    public void setWarrantyExpiry(LocalDate warrantyExpiry) { this.warrantyExpiry = warrantyExpiry; }
}
