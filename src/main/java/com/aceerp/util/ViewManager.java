package com.aceerp.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class ViewManager {

    private static final Logger logger = LoggerFactory.getLogger(ViewManager.class);

    private final Map<String, Parent> viewCache = new HashMap<>();
    private final Map<String, Object> controllerCache = new HashMap<>();

    private static final String VIEW_BASE_PATH = "/com/aceerp/view/";

    public Parent loadView(String fxmlFileName) throws IOException {
        if (viewCache.containsKey(fxmlFileName)) {
            logger.debug("Returning cached view: {}", fxmlFileName);
            return viewCache.get(fxmlFileName);
        }

        String path = VIEW_BASE_PATH + fxmlFileName;
        URL resource = getClass().getResource(path);

        if (resource == null) {
            logger.error("FXML file not found: {}", path);
            throw new IOException("View not found: " + fxmlFileName);
        }

        FXMLLoader loader = new FXMLLoader(resource);
        Parent view = loader.load();

        viewCache.put(fxmlFileName, view);
        Object controller = loader.getController();
        if (controller != null) {
            controllerCache.put(fxmlFileName, controller);
        }

        logger.debug("Loaded and cached view: {}", fxmlFileName);
        return view;
    }

    public Parent loadFreshView(String fxmlFileName) throws IOException {
        String path = VIEW_BASE_PATH + fxmlFileName;
        URL resource = getClass().getResource(path);

        if (resource == null) {
            throw new IOException("View not found: " + fxmlFileName);
        }

        FXMLLoader loader = new FXMLLoader(resource);
        return loader.load();
    }

    public Object getController(String fxmlFileName) {
        return controllerCache.get(fxmlFileName);
    }

    public void invalidate(String fxmlFileName) {
        viewCache.remove(fxmlFileName);
        controllerCache.remove(fxmlFileName);
        logger.debug("Invalidated cached view: {}", fxmlFileName);
    }

    public void evict(String fxmlFileName) {
        invalidate(fxmlFileName);
    }

    public void clearCache() {
        viewCache.clear();
        controllerCache.clear();
        logger.debug("View cache cleared.");
    }
}