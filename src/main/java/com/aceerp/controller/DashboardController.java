package com.aceerp.controller;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.DashboardStats;
import com.aceerp.service.DashboardService;
import com.aceerp.util.AlertHelper;
import com.aceerp.util.CurrencyFormatter;
import com.aceerp.util.GlobalQuickAdd;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the Dashboard view.
 * Displays real-time business metrics, interactive sales charts, and quick actions.
 */
public class DashboardController {

    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);

    // Metric Labels
    @FXML private Label customerCountLabel;
    @FXML private Label productCountLabel;
    @FXML private Label invoiceCountLabel;
    @FXML private Label revenueLabel;
    @FXML private Label dbStatusLabel;

    @FXML private Label lblOutstandingBalance;
    @FXML private Label lblOpenInvoicesCount;
    @FXML private Label lblActiveJobs;
    @FXML private Label lblLowStock;

    // Visual Charts
    @FXML private BarChart<String, Number> salesChart;
    @FXML private PieChart jobStatusChart;

    private final DashboardService dashboardService = new DashboardService();

    @FXML
    public void initialize() {
        logger.info("Dashboard initializing...");
        loadDashboardStats();
    }

    /**
     * Refresh the dashboard metrics.
     */
    @FXML
    public void refresh() {
        loadDashboardStats();
    }

    /**
     * Asynchronously loads summary statistics and renders visual charts.
     */
    private void loadDashboardStats() {
        new Thread(() -> {
            DashboardStats stats = dashboardService.getDashboardStats();
            boolean isHealthy = DatabaseConfig.getInstance().isHealthy();

            Platform.runLater(() -> {
                // Populate Core Counts & Currency Metrics
                customerCountLabel.setText(String.valueOf(stats.totalCustomers()));
                productCountLabel.setText(String.valueOf(stats.totalProducts()));
                invoiceCountLabel.setText(String.valueOf(stats.totalInvoices()));
                revenueLabel.setText(CurrencyFormatter.format(stats.monthlyRevenue()));

                if (lblOutstandingBalance != null) {
                    lblOutstandingBalance.setText(CurrencyFormatter.format(stats.outstandingBalance()));
                }
                if (lblOpenInvoicesCount != null) {
                    lblOpenInvoicesCount.setText(stats.openInvoices() + " Unpaid");
                }
                if (lblActiveJobs != null) {
                    lblActiveJobs.setText(String.valueOf(stats.pendingServiceJobs()));
                }
                if (lblLowStock != null) {
                    lblLowStock.setText(String.valueOf(stats.lowStockProducts()));
                }

                // Database Status Badge
                if (isHealthy) {
                    dbStatusLabel.setText("● System Online");
                    dbStatusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                } else {
                    dbStatusLabel.setText("● System Offline");
                    dbStatusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                }

                // Render Sales BarChart
                if (salesChart != null) {
                    salesChart.getData().clear();
                    XYChart.Series<String, Number> series = new XYChart.Series<>();
                    series.setName("Revenue");
                    for (DashboardStats.MonthlySales ms : stats.monthlySales()) {
                        series.getData().add(new XYChart.Data<>(ms.monthLabel(), ms.revenue()));
                    }
                    salesChart.getData().add(series);
                }

                // Render Job Status PieChart
                if (jobStatusChart != null) {
                    ObservableList<PieChart.Data> pieData = FXCollections.observableArrayList();
                    for (DashboardStats.JobStatusCount jsc : stats.jobStatusCounts()) {
                        pieData.add(new PieChart.Data(jsc.status() + " (" + jsc.count() + ")", jsc.count()));
                    }
                    jobStatusChart.setData(pieData);
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