package com.aceerp.model;

import java.util.Collections;
import java.util.List;

/**
 * Record holding summary statistics and chart dataset DTOs for the Dashboard.
 * Includes HVAC Enterprise metrics (AMC Contracts, Upcoming Visits, Pending Commissions).
 */
public record DashboardStats(
    int totalCustomers,
    int totalProducts,
    int totalInvoices,
    double monthlyRevenue,
    int openInvoices,
    double outstandingBalance,
    int pendingServiceJobs,
    int lowStockProducts,
    int activeAmcContracts,
    int upcomingAmcVisits,
    double pendingCommissions,
    List<MonthlySales> monthlySales,
    List<JobStatusCount> jobStatusCounts
) {
    public record MonthlySales(String monthLabel, double revenue) {}
    public record JobStatusCount(String status, int count) {}

    public static DashboardStats empty() {
        return new DashboardStats(
            0, 0, 0, 0.0, 0, 0.0, 0, 0,
            0, 0, 0.0,
            Collections.emptyList(),
            Collections.emptyList()
        );
    }
}