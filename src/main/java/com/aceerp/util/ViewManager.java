package com.aceerp.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages loading and caching of FXML views.
 * 
 * WHAT THIS DOES:
 * When the user clicks "Customers" in the sidebar, we need to load
 * the customers.fxml view and display it in the center of the window.
 * 
 * ViewManager:
 * 1. Loads FXML files by name
 * 2. Caches loaded views so we don't reload them every time the user 
 *    switches back and forth (performance optimization)
 * 3. Provides the loaded view's controller for interaction
 * 
 * CACHING EXPLAINED:
 * First click on "Customers":  Load customers.fxml from disk → slow (~50ms)
 * Second click on "Customers": Return cached view → instant (~0ms)
 */
public class ViewManager {

    private static final Logger logger = LoggerFactory.getLogger(ViewManager.class);

    // Cache of already-loaded views: fxmlName → loaded Parent node
    private final Map<String, Parent> viewCache = new HashMap<>();

    // Cache of controllers: fxmlName → controller object
    private final Map<String, Object> controllerCache = new HashMap<>();

    // The base path where FXML files are located (in the resources folder)
    private static final String VIEW_BASE_PATH = "/com/aceerp/view/";

    /**
     * Load a view by FXML filename. Returns cached version if available.
     * 
     * @param fxmlFileName the FXML file name (e.g., "dashboard.fxml")
     * @return the loaded Parent node, ready to display
     * @throws IOException if the FXML file cannot be loaded
     */
    public Parent loadView(String fxmlFileName) throws IOException {
        // Check cache first
        if (viewCache.containsKey(fxmlFileName)) {
            logger.debug("Returning cached view: {}", fxmlFileName);
            return viewCache.get(fxmlFileName);
        }

        // Load from FXML file
        String path = VIEW_BASE_PATH + fxmlFileName;
        URL resource = getClass().getResource(path);

        if (resource == null) {
            logger.error("FXML file not found: {}", path);
            throw new IOException("View not found: " + fxmlFileName);
        }

        FXMLLoader loader = new FXMLLoader(resource);
        Parent view = loader.load();

        // Cache both the view and its controller
        viewCache.put(fxmlFileName, view);
        Object controller = loader.getController();
        if (controller != null) {
            controllerCache.put(fxmlFileName, controller);
        }

        logger.debug("Loaded and cached view: {}", fxmlFileName);
        return view;
    }

    /**
     * Load a view WITHOUT caching. Use this when you need a fresh instance
     * every time (e.g., a dialog or form that should reset).
     * 
     * @param fxmlFileName the FXML file name
     * @return the loaded Parent node
     * @throws IOException if the FXML file cannot be loaded
     */
    public Parent loadFreshView(String fxmlFileName) throws IOException {
        String path = VIEW_BASE_PATH + fxmlFileName;
        URL resource = getClass().getResource(path);

        if (resource == null) {
            throw new IOException("View not found: " + fxmlFileName);
        }

        FXMLLoader loader = new FXMLLoader(resource);
        return loader.load();
    }

    /**
     * Get the controller of a previously loaded view.
     * 
     * @param fxmlFileName the FXML file name
     * @return the controller object, or null if not loaded yet
     */
    public Object getController(String fxmlFileName) {
        return controllerCache.get(fxmlFileName);
    }

    /**
     * Remove a view from the cache, forcing it to reload next time.
     * Useful when data changes and the view needs to refresh.
     */
    public void invalidate(String fxmlFileName) {
        viewCache.remove(fxmlFileName);
        controllerCache.remove(fxmlFileName);
        logger.debug("Invalidated cached view: {}", fxmlFileName);
    }

    /**
     * Clear the entire cache. Use when switching users or resetting the app.
     */
    public void clearCache() {
        viewCache.clear();
        controllerCache.clear();
        logger.debug("View cache cleared.");
    }
}