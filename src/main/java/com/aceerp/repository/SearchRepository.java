package com.aceerp.repository;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.SearchResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository for universal search queries.
 * 
 * Searches across multiple database tables and returns unified results.
 * 
 * WHY PREPARED STATEMENTS?
 * 
 * BAD (SQL Injection vulnerability):
 *   "SELECT * FROM customers WHERE name LIKE '%" + userInput + "%'"
 *   If userInput = "'; DROP TABLE customers; --", your table is deleted!
 * 
 * GOOD (PreparedStatement):
 *   "SELECT * FROM customers WHERE name ILIKE ?"
 *   stmt.setString(1, "%" + userInput + "%")
 *   The database treats the parameter as DATA, never as SQL code.
 *   The user can type anything and it can never break out of the query.
 * 
 * ILIKE vs LIKE:
 *   LIKE is case-sensitive:  'Ahmed' LIKE '%ahmed%'  → no match
 *   ILIKE is case-insensitive: 'Ahmed' ILIKE '%ahmed%' → matches!
 *   ILIKE is a PostgreSQL extension, very useful for search.
 */
public class SearchRepository {

    private static final Logger logger = LoggerFactory.getLogger(SearchRepository.class);

    // Maximum results per category (don't flood the dropdown)
    private static final int MAX_RESULTS_PER_CATEGORY = 5;

    /**
     * Search across all registered tables for the given query text.
     * Returns a combined list of results from all categories.
     * 
     * @param queryText the user's search text
     * @return list of matching results, empty list if nothing found
     */
    public List<SearchResult> searchAll(String queryText) {
        List<SearchResult> results = new ArrayList<>();

        if (queryText == null || queryText.trim().length() < 2) {
            // Don't search for very short strings (too many results, too slow)
            return results;
        }

        String searchPattern = "%" + queryText.trim() + "%";

        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            results.addAll(searchCustomers(conn, searchPattern));
            results.addAll(searchSuppliers(conn, searchPattern));
            results.addAll(searchProducts(conn, searchPattern));
            results.addAll(searchInvoices(conn, searchPattern));
        } catch (SQLException e) {
            logger.error("Search query failed: {}", e.getMessage());
        }

        return results;
    }

    /**
     * Search customers by name, phone, email, or customer code.
     */
    private List<SearchResult> searchCustomers(Connection conn, String pattern)
            throws SQLException {

        String sql = """
            SELECT id, customer_code, name, phone, email
            FROM customers
            WHERE is_active = TRUE
              AND (name ILIKE ? OR phone ILIKE ? OR email ILIKE ? OR customer_code ILIKE ?)
            ORDER BY name
            LIMIT ?
            """;

        List<SearchResult> results = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, pattern);
            stmt.setString(2, pattern);
            stmt.setString(3, pattern);
            stmt.setString(4, pattern);
            stmt.setInt(5, MAX_RESULTS_PER_CATEGORY);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String subtitle = buildCustomerSubtitle(
                        rs.getString("customer_code"),
                        rs.getString("phone"),
                        rs.getString("email")
                    );

                    results.add(new SearchResult(
                        "CUSTOMER",
                        rs.getString("name"),
                        subtitle,
                        "customers",
                        rs.getInt("id")
                    ));
                }
            }
        }

        return results;
    }

    /**
     * Search suppliers by name, phone, email, or supplier code.
     */
    private List<SearchResult> searchSuppliers(Connection conn, String pattern)
            throws SQLException {

        String sql = """
            SELECT id, supplier_code, name, phone, email
            FROM suppliers
            WHERE is_active = TRUE
              AND (name ILIKE ? OR phone ILIKE ? OR email ILIKE ? OR supplier_code ILIKE ?)
            ORDER BY name
            LIMIT ?
            """;

        List<SearchResult> results = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, pattern);
            stmt.setString(2, pattern);
            stmt.setString(3, pattern);
            stmt.setString(4, pattern);
            stmt.setInt(5, MAX_RESULTS_PER_CATEGORY);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String subtitle = buildSupplierSubtitle(
                        rs.getString("supplier_code"),
                        rs.getString("phone")
                    );

                    results.add(new SearchResult(
                        "SUPPLIER",
                        rs.getString("name"),
                        subtitle,
                        "suppliers",
                        rs.getInt("id")
                    ));
                }
            }
        }

        return results;
    }

    /**
     * Search products by name, code, brand, or model.
     */
    private List<SearchResult> searchProducts(Connection conn, String pattern)
            throws SQLException {

        String sql = """
            SELECT id, product_code, name, product_type, brand, selling_price
            FROM products
            WHERE is_active = TRUE
              AND (name ILIKE ? OR product_code ILIKE ? OR brand ILIKE ? OR model ILIKE ?)
            ORDER BY name
            LIMIT ?
            """;

        List<SearchResult> results = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, pattern);
            stmt.setString(2, pattern);
            stmt.setString(3, pattern);
            stmt.setString(4, pattern);
            stmt.setInt(5, MAX_RESULTS_PER_CATEGORY);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String subtitle = String.format("%s | %s | Price: %.2f",
                        rs.getString("product_code"),
                        rs.getString("product_type"),
                        rs.getDouble("selling_price")
                    );

                    results.add(new SearchResult(
                        "PRODUCT",
                        rs.getString("name"),
                        subtitle,
                        "products",
                        rs.getInt("id")
                    ));
                }
            }
        }

        return results;
    }

    /**
     * Search invoices by invoice number or customer name.
     */
    private List<SearchResult> searchInvoices(Connection conn, String pattern)
            throws SQLException {

        String sql = """
            SELECT i.id, i.invoice_number, i.invoice_date, i.total_amount, 
                   i.status, c.name AS customer_name
            FROM invoices i
            JOIN customers c ON i.customer_id = c.id
            WHERE i.invoice_number ILIKE ? OR c.name ILIKE ?
            ORDER BY i.invoice_date DESC
            LIMIT ?
            """;

        List<SearchResult> results = new ArrayList<>();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, pattern);
            stmt.setString(2, pattern);
            stmt.setInt(3, MAX_RESULTS_PER_CATEGORY);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String subtitle = String.format("%s | %s | %.2f | %s",
                        rs.getString("customer_name"),
                        rs.getString("invoice_date"),
                        rs.getDouble("total_amount"),
                        rs.getString("status")
                    );

                    results.add(new SearchResult(
                        "INVOICE",
                        rs.getString("invoice_number"),
                        subtitle,
                        "invoices",
                        rs.getInt("id")
                    ));
                }
            }
        }

        return results;
    }

    /**
     * Build a subtitle string for customer search results.
     */
    private String buildCustomerSubtitle(String code, String phone, String email) {
        StringBuilder sb = new StringBuilder();
        if (code != null && !code.isEmpty()) {
            sb.append(code);
        }
        if (phone != null && !phone.isEmpty()) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(phone);
        }
        if (email != null && !email.isEmpty()) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(email);
        }
        return sb.toString();
    }

    /**
     * Build a subtitle string for supplier search results.
     */
    private String buildSupplierSubtitle(String code, String phone) {
        StringBuilder sb = new StringBuilder();
        if (code != null && !code.isEmpty()) {
            sb.append(code);
        }
        if (phone != null && !phone.isEmpty()) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append(phone);
        }
        return sb.toString();
    }
}