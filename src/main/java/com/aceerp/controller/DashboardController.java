package com.aceerp.controller;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.DashboardStats;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Controller for the Dashboard view.
 * 
 * The dashboard is the "home screen" of the application.
 * It shows summary statistics and quick actions.
 */
public class DashboardController {

    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);

    @FXML private Label customerCountLabel;
    @FXML private Label productCountLabel;
    @FXML private Label invoiceCountLabel;
    @FXML private Label revenueLabel;
    @FXML private Label dbStatusLabel;

    /**
     * Called automatically after FXML is loaded.
     * Loads dashboard data from the database.
     */
    @FXML
    public void initialize() {
        logger.info("Dashboard initializing...");
        loadDashboardStats();
    }

    /**
     * Refresh the dashboard data. Can be called when navigating back to dashboard.
     */
    public void refresh() {
        loadDashboardStats();
    }

    /**
     * Load summary statistics from the database and update the labels.
     */
    private void loadDashboardStats() {
        DashboardStats stats = fetchStats();

        customerCountLabel.setText(String.valueOf(stats.totalCustomers()));
        productCountLabel.setText(String.valueOf(stats.totalProducts()));
        invoiceCountLabel.setText(String.valueOf(stats.totalInvoices()));
        revenueLabel.setText(String.format("%.2f", stats.monthlyRevenue()));

        // Check database connection status
        if (DatabaseConfig.getInstance().isHealthy()) {
            dbStatusLabel.setText("Connected");
            dbStatusLabel.setStyle("-fx-text-fill: #27ae60;");
        } else {
            dbStatusLabel.setText("Disconnected");
            dbStatusLabel.setStyle("-fx-text-fill: #e74c3c;");
        }
    }

    /**
     * Query the database for summary counts.
     * Uses simple COUNT queries — fast even with large tables.
     */
    private DashboardStats fetchStats() {
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

            // Count all invoices
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM invoices")) {
                if (rs.next()) invoices = rs.getInt(1);
            }

            // Sum revenue for current month (paid/partial invoices only)
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COALESCE(SUM(total_amount), 0) FROM invoices " +
                    "WHERE status IN ('PAID', 'PARTIAL') " +
                    "AND invoice_date >= date_trunc('month', CURRENT_DATE)")) {
                if (rs.next()) revenue = rs.getDouble(1);
            }

        } catch (SQLException e) {
            logger.error("Failed to load dashboard stats: {}", e.getMessage());
            return DashboardStats.empty();
        }

        return new DashboardStats(customers, products, invoices, revenue);
    }
}