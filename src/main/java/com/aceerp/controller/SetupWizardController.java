package com.aceerp.controller;

import com.aceerp.config.AppConfig;
import com.aceerp.config.DatabaseConfig;
import com.aceerp.repository.DatabaseMigrator;
import com.aceerp.service.SettingsService;
import com.aceerp.service.UserService;
import com.aceerp.util.SceneRouter;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class SetupWizardController {

    private static final Logger log = LoggerFactory.getLogger(SetupWizardController.class);

    @FXML private Label lblStep1;
    @FXML private Label lblStep2;
    @FXML private Label lblStep3;

    @FXML private VBox paneWelcome;
    @FXML private VBox paneDatabase;
    @FXML private VBox paneAdmin;
    @FXML private VBox paneCompany;

    @FXML private TextField txtDbHost;
    @FXML private TextField txtDbPort;
    @FXML private TextField txtDbName;
    @FXML private TextField txtDbUser;
    @FXML private PasswordField txtDbPassword;
    @FXML private Button btnTestConnection;
    @FXML private Label lblDbStatus;

    @FXML private TextField txtAdminFullName;
    @FXML private TextField txtAdminUsername;
    @FXML private PasswordField txtAdminPassword;
    @FXML private PasswordField txtAdminConfirmPassword;
    @FXML private Label lblAdminStatus;

    @FXML private TextField txtCompanyName;
    @FXML private TextField txtCurrencyCode;
    @FXML private TextField txtCurrencySymbol;
    @FXML private Label lblCompanyStatus;

    @FXML private Button btnBack;
    @FXML private Button btnNext;

    private int currentStep = 0;
    private boolean dbConnected = false;

    @FXML
    public void initialize() {
        txtCurrencySymbol.setPromptText("e.g. $ or Rs");

        AppConfig config = AppConfig.getInstance();
        txtDbHost.setText(config.getDbHost());
        txtDbPort.setText(String.valueOf(config.getDbPort()));
        txtDbName.setText(config.getDbName());
        txtDbUser.setText(config.getDbUser());
        txtDbPassword.setText(config.getDbPassword());

        if (DatabaseConfig.getInstance().isInitialized()) {
            dbConnected = true;
            setDbStatus("Connected successfully.", true);
        }

        showStep(0);
    }

    private void showStep(int step) {
        currentStep = step;

        paneWelcome.setVisible(false);
        paneWelcome.setManaged(false);
        paneDatabase.setVisible(false);
        paneDatabase.setManaged(false);
        paneAdmin.setVisible(false);
        paneAdmin.setManaged(false);
        paneCompany.setVisible(false);
        paneCompany.setManaged(false);

        switch (step) {
            case 0 -> { paneWelcome.setVisible(true); paneWelcome.setManaged(true); }
            case 1 -> { paneDatabase.setVisible(true); paneDatabase.setManaged(true); }
            case 2 -> { paneAdmin.setVisible(true); paneAdmin.setManaged(true); }
            case 3 -> { paneCompany.setVisible(true); paneCompany.setManaged(true); }
        }

        String active = "-fx-text-fill: white; -fx-font-weight: bold;";
        String done = "-fx-text-fill: #2ecc71; -fx-font-weight: bold;";
        String inactive = "-fx-text-fill: rgba(255,255,255,0.5);";

        lblStep1.setStyle(step == 1 ? active : (step > 1 ? done : inactive));
        lblStep2.setStyle(step == 2 ? active : (step > 2 ? done : inactive));
        lblStep3.setStyle(step == 3 ? active : inactive);

        btnBack.setVisible(step > 0);
        btnNext.setText(step == 3 ? "Finish" : "Next");
    }

    @FXML
    private void handleNext() {
        switch (currentStep) {
            case 0 -> showStep(1);
            case 1 -> {
                if (dbConnected && DatabaseConfig.getInstance().isInitialized()) {
                    showStep(2);
                } else {
                    setupDatabase();
                }
            }
            case 2 -> {
                if (createAdmin()) {
                    showStep(3);
                }
            }
            case 3 -> {
                if (saveCompanyInfo()) {
                    finishSetup();
                }
            }
        }
    }

    @FXML
    private void handleBack() {
        if (currentStep > 0) {
            showStep(currentStep - 1);
        }
    }

    @FXML
    private void handleTestConnection() {
        String host = txtDbHost.getText().trim();
        String user = txtDbUser.getText().trim();
        if (host.isEmpty() || user.isEmpty()) {
            setDbStatus("Fill in Server and Username first.", false);
            return;
        }

        int port;
        try {
            port = Integer.parseInt(txtDbPort.getText().trim());
        } catch (NumberFormatException e) {
            setDbStatus("Invalid port number.", false);
            return;
        }

        String dbName = txtDbName.getText().trim();
        String password = txtDbPassword.getText();

        setDbStatus("Testing...", true);
        btnTestConnection.setDisable(true);
        final int fPort = port;

        new Thread(() -> {
            DatabaseConfig.ConnectionResult result =
                    DatabaseConfig.testConnection(host, fPort, dbName, user, password);
            Platform.runLater(() -> {
                btnTestConnection.setDisable(false);
                switch (result) {
                    case SUCCESS -> setDbStatus("Connection successful!", true);
                    case DATABASE_NOT_FOUND -> setDbStatus(
                            "Server OK but database not found. It will be created on Next.", true);
                    case AUTH_FAILED -> setDbStatus("Wrong username or password.", false);
                    case SERVER_UNREACHABLE -> setDbStatus("Cannot reach server.", false);
                    case OTHER_ERROR -> setDbStatus("Connection failed.", false);
                }
            });
        }).start();
    }

    private void setupDatabase() {
        String host = txtDbHost.getText().trim();
        String dbName = txtDbName.getText().trim();
        String user = txtDbUser.getText().trim();
        String password = txtDbPassword.getText();

        if (host.isEmpty() || dbName.isEmpty() || user.isEmpty() || password.isEmpty()) {
            setDbStatus("All fields are required.", false);
            return;
        }

        int port;
        try {
            port = Integer.parseInt(txtDbPort.getText().trim());
        } catch (NumberFormatException e) {
            setDbStatus("Invalid port.", false);
            return;
        }

        if (!dbName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            setDbStatus("Database name: letters, digits, underscores only.", false);
            return;
        }

        setDbStatus("Connecting...", true);
        btnNext.setDisable(true);
        btnTestConnection.setDisable(true);
        final int fPort = port;

        new Thread(() -> {
            DatabaseConfig.ConnectionResult result =
                    DatabaseConfig.testConnection(host, fPort, dbName, user, password);

            if (result == DatabaseConfig.ConnectionResult.DATABASE_NOT_FOUND) {
                DatabaseConfig.createDatabaseIfNotExists(host, fPort, dbName, user, password);
                result = DatabaseConfig.testConnection(host, fPort, dbName, user, password);
            }

            final DatabaseConfig.ConnectionResult finalResult = result;
            Platform.runLater(() -> {
                btnNext.setDisable(false);
                btnTestConnection.setDisable(false);

                if (finalResult == DatabaseConfig.ConnectionResult.SUCCESS) {
                    setDbStatus("Initializing...", true);
                    if (initDatabase(host, fPort, dbName, user, password)) {
                        setDbStatus("Database ready!", true);
                        dbConnected = true;
                        showStep(2);
                    }
                } else if (finalResult == DatabaseConfig.ConnectionResult.AUTH_FAILED) {
                    setDbStatus("Wrong username or password.", false);
                } else if (finalResult == DatabaseConfig.ConnectionResult.SERVER_UNREACHABLE) {
                    setDbStatus("Cannot reach server.", false);
                } else {
                    setDbStatus("Connection failed.", false);
                }
            });
        }).start();
    }

    private boolean initDatabase(String host, int port, String dbName,
                                  String user, String password) {
        try {
            AppConfig config = AppConfig.getInstance();
            config.setDbHost(host);
            config.setDbPort(port);
            config.setDbName(dbName);
            config.setDbUser(user);
            config.setDbPassword(password);
            config.save();

            DatabaseConfig.getInstance().initialize(host, port, dbName, user, password, 10, 2);
            new DatabaseMigrator().migrate();
            SettingsService.getInstance().reload();

            log.info("Database initialized from setup wizard.");
            return true;
        } catch (Exception e) {
            log.error("Failed to initialize database", e);
            setDbStatus("Error: " + e.getMessage(), false);
            return false;
        }
    }

    private void setDbStatus(String msg, boolean ok) {
        lblDbStatus.setText(msg);
        lblDbStatus.setStyle(ok
                ? "-fx-text-fill: #27ae60; -fx-font-size: 13px;"
                : "-fx-text-fill: #e74c3c; -fx-font-size: 13px;");
    }

    private boolean createAdmin() {
        lblAdminStatus.setText("");

        // FIX: If users already exist, skip admin creation silently.
        // This prevents the crash when re-running the wizard on an existing database.
        UserService userService = new UserService();
        if (!userService.needsInitialSetup()) {
            log.info("Users already exist in database, skipping admin creation step.");
            return true;
        }

        String fullName = txtAdminFullName.getText().trim();
        String username = txtAdminUsername.getText().trim();
        String password = txtAdminPassword.getText();
        String confirm = txtAdminConfirmPassword.getText();

        if (fullName.isEmpty()) {
            lblAdminStatus.setText("Full name is required.");
            return false;
        }
        if (username.length() < 3) {
            lblAdminStatus.setText("Username must be at least 3 characters.");
            return false;
        }
        if (password.isEmpty()) {
            lblAdminStatus.setText("Password is required.");
            return false;
        }
        if (!password.equals(confirm)) {
            lblAdminStatus.setText("Passwords do not match.");
            return false;
        }

        try {
            userService.createSuperAdmin(username, password, fullName);
            log.info("Super Admin created: {}", username);
            return true;
        } catch (Exception e) {
            log.error("Failed to create admin", e);
            lblAdminStatus.setText("Error: " + e.getMessage());
            return false;
        }
    }

    private boolean saveCompanyInfo() {
        lblCompanyStatus.setText("");

        String name = txtCompanyName.getText().trim();
        String code = txtCurrencyCode.getText().trim().toUpperCase();
        String symbol = txtCurrencySymbol.getText().trim();

        if (name.isEmpty()) {
            lblCompanyStatus.setText("Business name is required.");
            return false;
        }
        if (code.length() != 3) {
            lblCompanyStatus.setText("Currency code must be 3 letters (e.g. USD, INR).");
            return false;
        }
        if (symbol.isEmpty()) {
            lblCompanyStatus.setText("Currency symbol is required.");
            return false;
        }

        try {
            SettingsService.getInstance().saveAllDuringSetup(Map.of(
                    "company.name", name,
                    "currency.code", code,
                    "currency.symbol", symbol
            ));
            return true;
        } catch (Exception e) {
            log.error("Failed to save company info", e);
            lblCompanyStatus.setText("Error: " + e.getMessage());
            return false;
        }
    }

    private void finishSetup() {
        log.info("Setup wizard completed.");
        try {
            SceneRouter.getInstance().switchTo("login.fxml", "ACE ERP - Login", 480, 580);
        } catch (Exception e) {
            log.error("Failed to open login screen", e);
        }
    }
}   