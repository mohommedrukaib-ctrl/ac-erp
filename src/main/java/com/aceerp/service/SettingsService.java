package com.aceerp.service;

import com.aceerp.model.UserRole;
import com.aceerp.model.UserSession;
import com.aceerp.repository.SettingsRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for reading and writing application settings.
 * 
 * CACHING STRATEGY:
 * Settings are read very frequently (currency symbol on every invoice line, 
 * company name on every report, etc.) but changed rarely.
 * 
 * We cache all settings in memory. When a setting is updated, we invalidate 
 * the cache so the next read re-fetches from the database.
 * 
 * ConcurrentHashMap is thread-safe: multiple parts of the UI can read 
 * settings simultaneously without conflicts.
 */
public class SettingsService {

    private static final Logger logger = LoggerFactory.getLogger(SettingsService.class);

    // Singleton
    private static SettingsService instance;

    private final SettingsRepository repository;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private volatile boolean cacheLoaded = false;

    private SettingsService() {
        this.repository = new SettingsRepository();
    }

    public static synchronized SettingsService getInstance() {
        if (instance == null) {
            instance = new SettingsService();
        }
        return instance;
    }

    /**
     * Load all settings into the cache. Called at startup and after bulk updates.
     */
    public synchronized void reload() {
        cache.clear();
        cache.putAll(repository.getAll());
        cacheLoaded = true;
        logger.info("Loaded {} settings into cache.", cache.size());
    }

    private void ensureLoaded() {
        if (!cacheLoaded) {
            reload();
        }
    }

    /**
     * Get a setting value. Returns null if not found.
     */
    public String get(String key) {
        ensureLoaded();
        return cache.get(key);
    }

    /**
     * Get a setting with a default fallback.
     */
    public String get(String key, String defaultValue) {
        String value = get(key);
        return (value != null) ? value : defaultValue;
    }

    public int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getBool(String key, boolean defaultValue) {
        String value = get(key);
        if (value == null) return defaultValue;
        return "true".equalsIgnoreCase(value.trim());
    }

    /**
     * Get all settings for a given category.
     */
    public Map<String, String> getCategory(String category) {
        return repository.getAllByCategory(category);
    }

    /**
     * Update a single setting. Requires ADMIN permission.
     */
    public void set(String key, String value) throws SQLException {
        requireAdmin();
        int userId = UserSession.getInstance().getCurrentUser().getId();
        repository.set(key, value, userId);
        cache.put(key, value);
    }

    /**
     * Update multiple settings atomically. Requires ADMIN permission.
     */
    public void saveAll(Map<String, String> values) throws SQLException {
        requireAdmin();
        int userId = UserSession.getInstance().getCurrentUser().getId();
        repository.setMany(values, userId);
        cache.putAll(values);
        logger.info("User {} updated {} settings.", userId, values.size());
    }

    /**
     * Save settings during the setup wizard, when no user is logged in.
     * Only works before any user exists.
     */
    public void saveAllDuringSetup(Map<String, String> values) throws SQLException {
        repository.setMany(values, null);
        cache.putAll(values);
        logger.info("Settings saved during setup wizard: {} values.", values.size());
    }

    private void requireAdmin() {
        UserSession session = UserSession.getInstance();
        if (!session.isLoggedIn() || !session.hasRole(UserRole.ADMIN)) {
            throw new SecurityException("Only administrators can change settings.");
        }
    }
}