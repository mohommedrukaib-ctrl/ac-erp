package com.aceerp.service;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.WarrantyRegistration;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class WarrantyRegistrationService {

    public List<WarrantyRegistration> listAll() throws SQLException {
        String sql = """
                SELECT ps.id, ps.product_id, ps.customer_id, ps.serial_number,
                       ps.installation_date, ps.warranty_expiry,
                       p.product_code, p.name AS product_name, c.name AS customer_name
                FROM product_serials ps
                JOIN products p ON p.id = ps.product_id
                JOIN customers c ON c.id = ps.customer_id
                ORDER BY ps.id DESC
                """;
        List<WarrantyRegistration> registrations = new ArrayList<>();
        try (Connection connection = DatabaseConfig.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                WarrantyRegistration registration = new WarrantyRegistration();
                registration.setId(rows.getInt("id"));
                registration.setProductId(rows.getInt("product_id"));
                registration.setCustomerId(rows.getInt("customer_id"));
                registration.setSerialNumber(rows.getString("serial_number"));
                registration.setProductCode(rows.getString("product_code"));
                registration.setProductName(rows.getString("product_name"));
                registration.setCustomerName(rows.getString("customer_name"));
                Date installationDate = rows.getDate("installation_date");
                if (installationDate != null) registration.setInstallationDate(installationDate.toLocalDate());
                Date warrantyExpiry = rows.getDate("warranty_expiry");
                if (warrantyExpiry != null) registration.setWarrantyExpiry(warrantyExpiry.toLocalDate());
                registrations.add(registration);
            }
        }
        return registrations;
    }

    public int register(int productId, int customerId, String serialNumber,
                        java.time.LocalDate installationDate, java.time.LocalDate warrantyExpiry)
            throws SQLException {
        if (productId <= 0 || customerId <= 0) {
            throw new IllegalArgumentException("Select a product and customer.");
        }
        if (serialNumber == null || serialNumber.isBlank()) {
            throw new IllegalArgumentException("Serial number is required.");
        }
        if (installationDate == null || warrantyExpiry == null) {
            throw new IllegalArgumentException("Installation and warranty expiry dates are required.");
        }
        if (warrantyExpiry.isBefore(installationDate)) {
            throw new IllegalArgumentException("Warranty expiry cannot be before installation date.");
        }

        String sql = """
                INSERT INTO product_serials
                    (product_id, serial_number, status, customer_id, installation_date, warranty_expiry)
                VALUES (?, ?, 'SOLD', ?, ?, ?)
                """;
        try (Connection connection = DatabaseConfig.getInstance().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, productId);
            statement.setString(2, serialNumber.trim());
            statement.setInt(3, customerId);
            statement.setDate(4, Date.valueOf(installationDate));
            statement.setDate(5, Date.valueOf(warrantyExpiry));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        throw new SQLException("Warranty registration was saved but the database did not return its record ID.");
    }
}
