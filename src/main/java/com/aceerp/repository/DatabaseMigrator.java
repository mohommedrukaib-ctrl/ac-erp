package com.aceerp.repository;

import com.aceerp.config.DatabaseConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DatabaseMigrator {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseMigrator.class);

    private static final String[] MIGRATION_FILES = {
        "V001__initial_schema.sql",
        "V002__add_audit_columns.sql",
        "V003__add_stock_movements.sql",
        "V004__add_sales_module.sql",
        "V005__sales_order_delivery_workflow.sql",
        "V006__fix_sales_columns.sql",
        "V007__fix_line_columns.sql",
        "V008__delivery_notes_and_payments.sql",
        "V009__add_driver_fields.sql",
        "V010__add_delivery_address.sql",
        "V011__add_delivery_note_created_by.sql",
        "V012__fix_delivery_note_trigger.sql",
        "V013__recreate_delivery_notes_schema.sql",
        "V014__add_stock_movement_reference_columns.sql",
        "V015__purchase_orders_and_vat.sql",
        "V016__vat_columns_all_docs.sql",
        "V017__create_goods_received_notes.sql",
        "V018__po_line_total.sql",
        "V019__create_supplier_bills.sql",
        "V020__supplier_payments_and_purchase_returns.sql",
        "V021__accounting_schema.sql",
        "V022__service_management.sql",
        "V023__company_profile.sql"
        
    };

    public void migrate() throws SQLException {
        logger.info("Starting database migration check...");

        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            Set<String> applied = getAppliedMigrations(conn);
            logger.info("Previously applied migrations: {}", applied);

            int newMigrations = 0;
            for (String fileName : MIGRATION_FILES) {
                String version = extractVersion(fileName);

                if (applied.contains(version)) {
                    logger.debug("Migration {} already applied, skipping.", version);
                    continue;
                }

                logger.info("Applying migration: {}", fileName);
                runMigration(conn, fileName);
                recordMigration(conn, fileName);

                newMigrations++;
                logger.info("Migration {} applied successfully.", version);
            }

            if (newMigrations == 0) {
                logger.info("Database is up to date. No new migrations.");
            } else {
                logger.info("{} migration(s) applied successfully.", newMigrations);
            }
        }
    }

    private Set<String> getAppliedMigrations(Connection conn) {
        Set<String> applied = new HashSet<>();

        try (ResultSet rs = conn.getMetaData().getTables(
                null, null, "schema_migrations", null)) {
            if (!rs.next()) {
                logger.info("schema_migrations table not found. This appears to be a fresh database.");
                return applied;
            }
        } catch (SQLException e) {
            logger.warn("Could not check for schema_migrations table: {}", e.getMessage());
            return applied;
        }

        String sql = "SELECT version FROM schema_migrations";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                applied.add(rs.getString("version"));
            }
        } catch (SQLException e) {
            logger.warn("Could not read applied migrations: {}", e.getMessage());
        }

        return applied;
    }

    private void runMigration(Connection conn, String fileName) throws SQLException {
        String sql = loadMigrationFile(fileName);

        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);

        try (Statement stmt = conn.createStatement()) {
            List<String> statements = splitSqlStatements(sql);

            for (String singleStatement : statements) {
                String trimmed = singleStatement.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }

            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            logger.error("Migration {} failed. Changes rolled back. Error: {}",
                        fileName, e.getMessage());
            throw new SQLException("Migration " + fileName + " failed: " + e.getMessage(), e);
        } finally {
            conn.setAutoCommit(originalAutoCommit);
        }
    }

    private void recordMigration(Connection conn, String fileName) throws SQLException {
        String version = extractVersion(fileName);
        String description = extractDescription(fileName);

        String sql = """
            INSERT INTO schema_migrations (version, description, applied_at)
            VALUES (?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (version) DO NOTHING
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, version);
            stmt.setString(2, description);
            stmt.executeUpdate();
            logger.debug("Recorded migration: {} — {}", version, description);
        }
    }

    private String loadMigrationFile(String fileName) throws SQLException {
        String resourcePath = "/db/migration/" + fileName;

        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new SQLException("Migration file not found: " + resourcePath);
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            throw new SQLException("Cannot read migration file: " + resourcePath, e);
        }
    }

    /**
     * Split a SQL string into individual statements.
     * Splits on semicolons while respecting single quotes (') AND dollar quotes ($$).
     */
    private List<String> splitSqlStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDollarQuote = false;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);

            // Handle $$ dollar quotes for PostgreSQL PL/pgSQL functions
            if (c == '$' && i + 1 < sql.length() && sql.charAt(i + 1) == '$' && !inSingleQuote) {
                inDollarQuote = !inDollarQuote;
                current.append("$$");
                i++; // skip second dollar sign
                continue;
            }

            if (c == '\'' && !inDollarQuote) {
                if (inSingleQuote && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    current.append(c);
                    current.append(sql.charAt(i + 1));
                    i++;
                    continue;
                }
                inSingleQuote = !inSingleQuote;
            }

            if (c == ';' && !inSingleQuote && !inDollarQuote) {
                String stmt = current.toString().trim();
                if (!stmt.isEmpty() && !isOnlyComments(stmt)) {
                    statements.add(stmt);
                }
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }

        String last = current.toString().trim();
        if (!last.isEmpty() && !isOnlyComments(last)) {
            statements.add(last);
        }

        return statements;
    }

    private boolean isOnlyComments(String sql) {
        String[] lines = sql.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                return false;
            }
        }
        return true;
    }

    private String extractVersion(String fileName) {
        int underscorePos = fileName.indexOf("__");
        if (underscorePos > 0) {
            return fileName.substring(0, underscorePos);
        }
        return fileName.replace(".sql", "");
    }

    private String extractDescription(String fileName) {
        int doubleUnderscore = fileName.indexOf("__");
        int dotSql = fileName.lastIndexOf(".sql");

        if (doubleUnderscore > 0 && dotSql > doubleUnderscore) {
            String raw = fileName.substring(doubleUnderscore + 2, dotSql);
            return raw.replace("_", " ");
        }

        return fileName.replace(".sql", "").replace("_", " ");
    }
}