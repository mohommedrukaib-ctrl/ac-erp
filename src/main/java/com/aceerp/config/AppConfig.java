package com.aceerp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);
    private static AppConfig instance;

    private final Properties properties;
    private final String configPath;
    private boolean loaded;

    private AppConfig() {
        properties = new Properties();
        configPath = "config/application.properties";
        loaded = false;
    }

    public static synchronized AppConfig getInstance() {
        if (instance == null) {
            instance = new AppConfig();
        }
        return instance;
    }

    public void load() {
        File file = new File(configPath);
        if (file.exists()) {
            try (InputStream is = new FileInputStream(file)) {
                properties.load(is);
                loaded = true;
                log.info("Configuration loaded from {}", file.getAbsolutePath());
            } catch (IOException e) {
                log.error("Failed to load config", e);
                loaded = false;
            }
        } else {
            log.warn("Config file not found: {}", configPath);
            loaded = false;
        }
    }

    public void save() {
        try {
            Path path = Path.of(configPath);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (OutputStream os = new FileOutputStream(path.toFile())) {
                properties.store(os, "ACE ERP Configuration");
                log.info("Configuration saved to {}", path.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Failed to save config", e);
            throw new RuntimeException("Could not save configuration: " + e.getMessage(), e);
        }
    }

    public boolean hasDatabaseConfig() {
        return loaded
                && notBlank(getDbHost())
                && notBlank(getDbName())
                && notBlank(getDbUser())
                && notBlank(getDbPassword());
    }

    public String getDbHost() { return properties.getProperty("db.host", "localhost"); }

    public int getDbPort() {
        try { return Integer.parseInt(properties.getProperty("db.port", "5432")); }
        catch (NumberFormatException e) { return 5432; }
    }

    public String getDbName() { return properties.getProperty("db.name", "ace_erp"); }
    public String getDbUser() { return properties.getProperty("db.username", ""); }
    public String getDbPassword() { return properties.getProperty("db.password", ""); }

    public int getPoolMaxSize() {
        try { return Integer.parseInt(properties.getProperty("db.pool.maxSize", "10")); }
        catch (NumberFormatException e) { return 10; }
    }

    public int getPoolMinIdle() {
        try { return Integer.parseInt(properties.getProperty("db.pool.minIdle", "2")); }
        catch (NumberFormatException e) { return 2; }
    }

    public void setDbHost(String v) { properties.setProperty("db.host", v); }
    public void setDbPort(int v) { properties.setProperty("db.port", String.valueOf(v)); }
    public void setDbName(String v) { properties.setProperty("db.name", v); }
    public void setDbUser(String v) { properties.setProperty("db.username", v); }
    public void setDbPassword(String v) { properties.setProperty("db.password", v); }

    private boolean notBlank(String s) { return s != null && !s.trim().isEmpty(); }
}