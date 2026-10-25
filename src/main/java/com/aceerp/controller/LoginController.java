package com.aceerp.controller;

import com.aceerp.service.AuthService;
import com.aceerp.service.AuthService.LoginResult;
import com.aceerp.util.SceneRouter;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the login screen.
 */
public class LoginController {

    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Label errorLabel;

    private final AuthService authService = new AuthService();

    @FXML
    public void initialize() {
        errorLabel.setText("");

        // Focus username field when the screen opens
        Platform.runLater(() -> usernameField.requestFocus());
    }

    @FXML
    private void onLogin() {
        errorLabel.setText("");
        loginButton.setDisable(true);

        String username = usernameField.getText();
        String password = passwordField.getText();

        try {
            LoginResult result = authService.login(username, password);

            if (result.success()) {
                logger.info("Login successful for: {}", result.user().getUsername());
                // Switch to main application
                SceneRouter.getInstance().switchTo(
                    "main.fxml",
                    "ACE ERP — " + result.user().getFullName(),
                    1280, 750
                );
            } else {
                errorLabel.setText(result.errorMessage());
                passwordField.clear();
                passwordField.requestFocus();
            }
        } catch (Exception e) {
            logger.error("Login error: {}", e.getMessage(), e);
            errorLabel.setText("Unable to log in. Please try again.");
        } finally {
            loginButton.setDisable(false);
        }
    }
}