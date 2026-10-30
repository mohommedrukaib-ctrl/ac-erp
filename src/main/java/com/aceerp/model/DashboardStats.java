package com.aceerp.model;

/**
 * Holds summary statistics for the dashboard display.
 * 
 * These numbers are shown on the dashboard cards:
 *   - Total customers
 *   - Total products
 *   - Total invoices
 *   - Recent revenue (this month)
 */ 
public record DashboardStats(
    int totalCustomers,
    int totalProducts,
    int totalInvoices,
    double monthlyRevenue
) {
    /**
     * Returns a DashboardStats with all zeros. Used before the database
     * is connected or when data hasn't loaded yet.
     */
    public static DashboardStats empty() {
        return new DashboardStats(0, 0, 0, 0.0);
    }
}           