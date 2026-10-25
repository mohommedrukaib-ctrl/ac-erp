package com.aceerp.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);
    private static DatabaseConfig instance;
    private HikariDataSource dataSource;

    public enum ConnectionResult {
        SUCCESS, DATABASE_NOT_FOUND, AUTH_FAILED, SERVER_UNREACHABLE, OTHER_ERROR
    }

    private DatabaseConfig() {}

    public static synchronized DatabaseConfig getInstance() {
        if (instance == null) {
            instance = new DatabaseConfig();
        }
        return instance;
    }

    public void initialize() {
        AppConfig c = AppConfig.getInstance();
        initialize(c.getDbHost(), c.getDbPort(), c.getDbName(),
                   c.getDbUser(), c.getDbPassword(),
                   c.getPoolMaxSize(), c.getPoolMinIdle());
    }

    public void initialize(String host, int port, String dbName,
                           String user, String password,
                           int maxPool, int minIdle) {
        shutdown();
        String url = "jdbc:postgresql://" + host + ":" + port + "/" + dbName;
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(url);
        hc.setUsername(user);
        hc.setPassword(password);
        hc.setMaximumPoolSize(maxPool);
        hc.setMinimumIdle(minIdle);
        hc.setPoolName("ACE-ERP-Pool");
        hc.setConnectionTimeout(10000);
        dataSource = new HikariDataSource(hc);
        log.info("Database connection pool initialized. URL: {}", url);

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            log.info("Database connection test successful. Server version: {}",
                     meta.getDatabaseProductVersion());
        } catch (SQLException e) {
            log.error("Database connection test failed", e);
            shutdown();
            throw new RuntimeException("DB test failed: " + e.getMessage(), e);
        }
    }

    public boolean isInitialized() {
        return dataSource != null && !dataSource.isClosed();
    }

    public boolean isHealthy() {
        return isInitialized();
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Connection pool not initialized");
        }
        return dataSource.getConnection();
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            log.info("Shutting down database connection pool...");
            dataSource.close();
        }
        dataSource = null;
    }

    public static ConnectionResult testConnection(String host, int port,
                                                   String dbName, String user,
                                                   String password) {
        String url = "jdbc:postgresql://" + host + ":" + port + "/" + dbName;
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            return ConnectionResult.SUCCESS;
        } catch (SQLException e) {
            String state = e.getSQLState();
            if ("3D000".equals(state)) return ConnectionResult.DATABASE_NOT_FOUND;
            if ("28P01".equals(state) || "28000".equals(state)) return ConnectionResult.AUTH_FAILED;
            if (state != null && state.startsWith("08")) return ConnectionResult.SERVER_UNREACHABLE;
            return ConnectionResult.OTHER_ERROR;
        }
    }

    public static boolean createDatabaseIfNotExists(String host, int port,
                                                     String dbName, String user,
                                                     String password) {
        if (!dbName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) return false;
        String url = "jdbc:postgresql://" + host + ":" + port + "/postgres";
        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE DATABASE " + dbName + " WITH ENCODING = 'UTF8'");
            log.info("Database '{}' created.", dbName);
            return true;
        } catch (SQLException e) {
            log.error("Failed to create database: {}", e.getMessage());
            return false;
        }
    }
}