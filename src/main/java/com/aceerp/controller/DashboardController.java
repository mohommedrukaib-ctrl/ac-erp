package com.aceerp.controller;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.DashboardStats;
import com.aceerp.service.DashboardService;
import com.aceerp.util.AlertHelper;
import com.aceerp.util.GlobalQuickAdd;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the Dashboard view.
 * Displays real-time business metrics and quick action shortcuts.
 */
public class DashboardController {

    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);

    @FXML private Label customerCountLabel;
    @FXML private Label productCountLabel;
    @FXML private Label invoiceCountLabel;
    @FXML private Label revenueLabel;
    @FXML private Label dbStatusLabel;

    private final DashboardService dashboardService = new DashboardService();

    @FXML
    public void initialize() {
        logger.info("Dashboard initializing...");
        loadDashboardStats();
    }

    /**
     * Refresh the dashboard metrics.
     */
    public void refresh() {
        loadDashboardStats();
    }

    /**
     * Asynchronously loads summary statistics from DashboardService.
     */
    private void loadDashboardStats() {
        new Thread(() -> {
            DashboardStats stats = dashboardService.getDashboardStats();
            boolean isHealthy = DatabaseConfig.getInstance().isHealthy();

            Platform.runLater(() -> {
                customerCountLabel.setText(String.valueOf(stats.totalCustomers()));
                productCountLabel.setText(String.valueOf(stats.totalProducts()));
                invoiceCountLabel.setText(String.valueOf(stats.totalInvoices()));
                revenueLabel.setText(String.format("%.2f", stats.monthlyRevenue()));

                if (isHealthy) {
                    dbStatusLabel.setText("Connected");
                    dbStatusLabel.setStyle("-fx-text-fill: #27ae60;");
                } else {
                    dbStatusLabel.setText("Disconnected");
                    dbStatusLabel.setStyle("-fx-text-fill: #e74c3c;");
                }
            });
        }).start();
    }

    // ======================= Quick Actions Handlers =======================

    @FXML
    public void handleQuickNewInvoice() {
        AlertHelper.info("Quick Action", "To create an invoice, please navigate to the Invoices section from the sidebar.");
    }

    @FXML
    public void handleQuickNewCustomer() {
        GlobalQuickAdd.showCustomerDialog(this::loadDashboardStats);
    }

    @FXML
    public void handleQuickNewProduct() {
        GlobalQuickAdd.showProductDialog(this::loadDashboardStats);
    }

    @FXML
    public void handleQuickNewServiceJob() {
        AlertHelper.info("Quick Action", "To create a service job, please navigate to the Services section from the sidebar.");
    }
}