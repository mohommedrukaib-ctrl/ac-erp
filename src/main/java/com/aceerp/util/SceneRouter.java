package com.aceerp.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;

public final class SceneRouter {

    private static final Logger logger = LoggerFactory.getLogger(SceneRouter.class);
    private static SceneRouter instance;
    private Stage primaryStage;
    private String cssPath;

    private SceneRouter() {}

    public static synchronized SceneRouter getInstance() {
        if (instance == null) {
            instance = new SceneRouter();
        }
        return instance;
    }

    public void initialize(Stage stage, String cssPath) {
        this.primaryStage = stage;
        this.cssPath = cssPath;
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public void switchTo(String fxmlFileName, String title, double width, double height)
            throws IOException {
        String path = "/com/aceerp/view/" + fxmlFileName;
        URL resource = getClass().getResource(path);
        if (resource == null) {
            throw new IOException("View not found: " + path);
        }

        FXMLLoader loader = new FXMLLoader(resource);
        Parent root = loader.load();

        Scene scene = (width > 0 && height > 0)
                ? new Scene(root, width, height)
                : new Scene(root);

        if (cssPath != null) {
            scene.getStylesheets().add(cssPath);
        }

        primaryStage.setScene(scene);
        primaryStage.setTitle(title);

        if (width > 0 && height > 0) {
            primaryStage.setWidth(width);
            primaryStage.setHeight(height);
            primaryStage.centerOnScreen();
        }

        primaryStage.show();
        logger.info("Switched scene to: {}", fxmlFileName);
    }
}