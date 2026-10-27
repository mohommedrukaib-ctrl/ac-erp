package com.aceerp.model;

import java.time.LocalDateTime;

public class CompanyProfile {
    private int id = 1;
    private String companyName;
    private String tagline;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    private String phone;
    private String email;
    private String website;
    private String taxNumber;
    private String logoPath;
    private String invoiceFooter;
    private String defaultPrintTemplate = "CLASSIC";
    private LocalDateTime updatedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getTagline() { return tagline; }
    public void setTagline(String tagline) { this.tagline = tagline; }

    public String getAddressLine1() { return addressLine1; }
    public void setAddressLine1(String addressLine1) { this.addressLine1 = addressLine1; }

    public String getAddressLine2() { return addressLine2; }
    public void setAddressLine2(String addressLine2) { this.addressLine2 = addressLine2; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getWebsite() { return website; }
    public void setWebsite(String website) { this.website = website; }

    public String getTaxNumber() { return taxNumber; }
    public void setTaxNumber(String taxNumber) { this.taxNumber = taxNumber; }

    public String getLogoPath() { return logoPath; }
    public void setLogoPath(String logoPath) { this.logoPath = logoPath; }

    public String getInvoiceFooter() { return invoiceFooter; }
    public void setInvoiceFooter(String invoiceFooter) { this.invoiceFooter = invoiceFooter; }

    public String getDefaultPrintTemplate() { return defaultPrintTemplate; }
    public void setDefaultPrintTemplate(String defaultPrintTemplate) {
        this.defaultPrintTemplate = defaultPrintTemplate;
    }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getFullAddress() {
        StringBuilder sb = new StringBuilder();
        if (notBlank(addressLine1)) sb.append(addressLine1).append('\n');
        if (notBlank(addressLine2)) sb.append(addressLine2).append('\n');
        StringBuilder cityLine = new StringBuilder();
        if (notBlank(city)) cityLine.append(city);
        if (notBlank(state)) {
            if (cityLine.length() > 0) cityLine.append(", ");
            cityLine.append(state);
        }
        if (notBlank(postalCode)) {
            if (cityLine.length() > 0) cityLine.append(' ');
            cityLine.append(postalCode);
        }
        if (cityLine.length() > 0) sb.append(cityLine).append('\n');
        if (notBlank(country)) sb.append(country);
        return sb.toString().trim();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}