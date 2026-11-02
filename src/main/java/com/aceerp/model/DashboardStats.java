package com.aceerp.model;

import java.util.Collections;
import java.util.List;

/**
 * Record holding summary statistics and chart dataset DTOs for the Dashboard.
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
    List<MonthlySales> monthlySales,
    List<JobStatusCount> jobStatusCounts
) {
    public record MonthlySales(String monthLabel, double revenue) {}
    public record JobStatusCount(String status, int count) {}

    public static DashboardStats empty() {
        return new DashboardStats(
            0, 0, 0, 0.0, 0, 0.0, 0, 0,
            Collections.emptyList(),
            Collections.emptyList()
        );
    }
}