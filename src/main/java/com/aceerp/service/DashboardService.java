package com.aceerp.service;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.DashboardStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Service to supply business metrics and statistics to the Dashboard.
 */
public class DashboardService {

    private static final Logger logger = LoggerFactory.getLogger(DashboardService.class);

    /**
     * Fetches summary statistics from the database.
     */
    public DashboardStats getDashboardStats() {
        int customers = 0;
        int products = 0;
        int invoices = 0;
        double revenue = 0.0;

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             Statement stmt = conn.createStatement()) {

            // Count active customers
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM customers WHERE is_active = TRUE")) {
                if (rs.next()) customers = rs.getInt(1);
            }

            // Count active products
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM products WHERE is_active = TRUE")) {
                if (rs.next()) products = rs.getInt(1);
            }

            // Count all non-cancelled invoices
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM invoices WHERE status != 'CANCELLED'")) {
                if (rs.next()) invoices = rs.getInt(1);
            }

            // Sum revenue for current month (paid / partial invoices)
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COALESCE(SUM(total_amount), 0) FROM invoices " +
                    "WHERE status IN ('PAID', 'PARTIAL') " +
                    "AND COALESCE(invoice_date, CURRENT_DATE) >= DATE_TRUNC('month', CURRENT_DATE)")) {
                if (rs.next()) revenue = rs.getDouble(1);
            }

        } catch (SQLException e) {
            logger.error("Failed to fetch dashboard stats: {}", e.getMessage(), e);
            return DashboardStats.empty();
        }

        return new DashboardStats(customers, products, invoices, revenue);
    }
}