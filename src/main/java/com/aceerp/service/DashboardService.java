package com.aceerp.service;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.DashboardStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Service to supply real-time business metrics and chart datasets for the ACE ERP Dashboard.
 * Includes HVAC enterprise metrics: Active AMC Contracts, Upcoming AMC Visits, Pending Commissions.
 */
public class DashboardService {

    private static final Logger logger = LoggerFactory.getLogger(DashboardService.class);

    /**
     * Fetches summary statistics from PostgreSQL.
     */
    public DashboardStats getDashboardStats() {
        int customers = 0;
        int products = 0;
        int invoices = 0;
        double revenue = 0.0;
        int openInvoices = 0;
        double outstandingBalance = 0.0;
        int pendingServiceJobs = 0;
        int lowStockProducts = 0;
        int activeAmcContracts = 0;
        int upcomingAmcVisits = 0;
        double pendingCommissions = 0.0;
        List<DashboardStats.MonthlySales> monthlySalesList = new ArrayList<>();
        List<DashboardStats.JobStatusCount> jobStatusList = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. Total active customers
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM customers WHERE is_active = TRUE")) {
                if (rs.next()) customers = rs.getInt(1);
            }

            // 2. Total active products
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM products WHERE is_active = TRUE")) {
                if (rs.next()) products = rs.getInt(1);
            }

            // 3. Total non-cancelled invoices
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM invoices WHERE status != 'CANCELLED'")) {
                if (rs.next()) invoices = rs.getInt(1);
            }

            // 4. Monthly revenue
            String revenueSql = """
                SELECT COALESCE(SUM(total_amount), 0) FROM invoices
                WHERE status IN ('PAID', 'PARTIAL')
                  AND COALESCE(invoice_date, CURRENT_DATE) >= DATE_TRUNC('month', CURRENT_DATE)
            """;
            try (ResultSet rs = stmt.executeQuery(revenueSql)) {
                if (rs.next()) revenue = rs.getDouble(1);
            }

            // 5. Open Invoices
            String openInvoicesSql = """
                SELECT COUNT(*), COALESCE(SUM(total_amount - COALESCE(paid_amount, 0)), 0)
                FROM invoices
                WHERE status NOT IN ('PAID', 'CANCELLED', 'DRAFT')
            """;
            try (ResultSet rs = stmt.executeQuery(openInvoicesSql)) {
                if (rs.next()) {
                    openInvoices = rs.getInt(1);
                    outstandingBalance = rs.getDouble(2);
                }
            }

            // 6. Pending Service Jobs
            String activeJobsSql = "SELECT COUNT(*) FROM service_jobs WHERE status IN ('PENDING', 'IN_PROGRESS', 'SCHEDULED')";
            try (ResultSet rs = stmt.executeQuery(activeJobsSql)) {
                if (rs.next()) pendingServiceJobs = rs.getInt(1);
            }

            // 7. Low Stock
            String lowStockSql = "SELECT COUNT(*) FROM products WHERE is_active = TRUE AND stock_quantity <= 5";
            try (ResultSet rs = stmt.executeQuery(lowStockSql)) {
                if (rs.next()) lowStockProducts = rs.getInt(1);
            }

            // 8. 6-Month Sales Trend
            String salesTrendSql = """
                SELECT TO_CHAR(COALESCE(invoice_date, CURRENT_DATE), 'Mon YY') AS month_label,
                       DATE_TRUNC('month', COALESCE(invoice_date, CURRENT_DATE)) AS month_date,
                       COALESCE(SUM(total_amount), 0) AS total_val
                FROM invoices
                WHERE status NOT IN ('CANCELLED', 'DRAFT')
                  AND COALESCE(invoice_date, CURRENT_DATE) >= CURRENT_DATE - INTERVAL '5 months'
                GROUP BY month_label, month_date
                ORDER BY month_date ASC
            """;
            try (ResultSet rs = stmt.executeQuery(salesTrendSql)) {
                while (rs.next()) {
                    monthlySalesList.add(new DashboardStats.MonthlySales(
                        rs.getString("month_label"),
                        rs.getDouble("total_val")
                    ));
                }
            }

            // 9. Service Job Status Breakdown
            String jobStatusSql = "SELECT status, COUNT(*) FROM service_jobs GROUP BY status";
            try (ResultSet rs = stmt.executeQuery(jobStatusSql)) {
                while (rs.next()) {
                    jobStatusList.add(new DashboardStats.JobStatusCount(
                        rs.getString(1),
                        rs.getInt(2)
                    ));
                }
            }

            // 10. Active AMC Contracts
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM amc_contracts WHERE status = 'ACTIVE'")) {
                if (rs.next()) activeAmcContracts = rs.getInt(1);
            }

            // 11. Upcoming AMC Visits (next 30 days)
            String upcomingAmcSql = """
                SELECT COUNT(*) FROM amc_schedules
                WHERE status = 'SCHEDULED'
                  AND scheduled_date BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '30 days'
            """;
            try (ResultSet rs = stmt.executeQuery(upcomingAmcSql)) {
                if (rs.next()) upcomingAmcVisits = rs.getInt(1);
            }

            // 12. Pending Technician Commissions
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COALESCE(SUM(commission_amount), 0) FROM technician_commissions WHERE status = 'PENDING'")) {
                if (rs.next()) pendingCommissions = rs.getDouble(1);
            }

        } catch (SQLException e) {
            logger.error("Failed to fetch dashboard stats: {}", e.getMessage(), e);
            return DashboardStats.empty();
        }

        return new DashboardStats(
            customers, products, invoices, revenue,
            openInvoices, outstandingBalance, pendingServiceJobs, lowStockProducts,
            activeAmcContracts, upcomingAmcVisits, pendingCommissions,
            monthlySalesList, jobStatusList
        );
    }
}