package com.aceerp;

import com.aceerp.config.AppConfig;
import com.aceerp.config.DatabaseConfig;
import com.aceerp.repository.DatabaseMigrator;
import com.aceerp.service.SettingsService;
import com.aceerp.service.UserService;
import com.aceerp.util.SceneRouter;
import javafx.application.Application;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App extends Application {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    @Override
    public void start(Stage primaryStage) {
        log.info("=== ACE ERP Starting ===");
        try {
            AppConfig config = AppConfig.getInstance();
            config.load();

            String cssPath = getClass().getResource("/com/aceerp/css/style.css").toExternalForm();
            SceneRouter.getInstance().initialize(primaryStage, cssPath);

            boolean dbReady = false;
            if (config.hasDatabaseConfig()) {
                try {
                    DatabaseConfig.getInstance().initialize();
                    new DatabaseMigrator().migrate();
                    SettingsService.getInstance().reload();
                    dbReady = true;
                } catch (Exception e) {
                    log.warn("Could not connect to database: {}", e.getMessage());
                }
            }

            if (dbReady) {
                if (new UserService().needsInitialSetup()) {
                    log.info("No users found. Starting setup wizard.");
                    SceneRouter.getInstance().switchTo(
                            "setup-wizard.fxml", "ACE ERP - Setup", 720, 680);
                } else {
                    log.info("Users found. Showing login.");
                    SceneRouter.getInstance().switchTo(
                            "login.fxml", "ACE ERP - Login", 480, 580);
                }
            } else {
                log.info("Database not ready. Starting setup wizard.");
                SceneRouter.getInstance().switchTo(
                        "setup-wizard.fxml", "ACE ERP - Setup", 720, 680);
            }
        } catch (Exception e) {
            log.error("Fatal startup error", e);
            System.exit(1);
        }
    }

    @Override
    public void stop() {
        log.info("=== ACE ERP Shutting Down ===");
        DatabaseConfig.getInstance().shutdown();
        log.info("=== ACE ERP Shutdown Complete ===");
    }

    public static void main(String[] args) {
        launch(args);
    }
}