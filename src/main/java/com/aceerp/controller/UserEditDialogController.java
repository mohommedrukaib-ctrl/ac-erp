package com.aceerp.controller;

import com.aceerp.model.User;
import com.aceerp.model.UserRole;
import com.aceerp.model.UserSession;
import com.aceerp.service.UserService;
import com.aceerp.util.AlertHelper;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Controller for the Add/Edit User dialog.
 * 
 * Reused for both creating new users and editing existing ones.
 * The 'editingUser' field is null when creating, non-null when editing.
 */
public class UserEditDialogController {

    private static final Logger logger = LoggerFactory.getLogger(UserEditDialogController.class);

    @FXML private Label titleLabel;
    @FXML private TextField usernameField;
    @FXML private TextField fullNameField;
    @FXML private TextField emailField;
    @FXML private TextField phoneField;
    @FXML private ChoiceBox<UserRole> roleChoice;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField passwordConfirmField;
    @FXML private Label passwordHintLabel;
    @FXML private CheckBox activeCheckBox;
    @FXML private Label errorLabel;

    private final UserService userService = new UserService();

    private User editingUser; // null = creating new, not-null = editing existing
    private boolean saved = false;

    @FXML
    public void initialize() {
        // Populate role choices based on current user's permissions
        boolean isSuperAdmin = UserSession.getInstance().getCurrentUser().getRole() == UserRole.SUPER_ADMIN;

        List<UserRole> availableRoles;
        if (isSuperAdmin) {
            availableRoles = Arrays.asList(UserRole.values());
        } else {
            // Admin can create any role EXCEPT super admin
            availableRoles = Arrays.stream(UserRole.values())
                .filter(r -> r != UserRole.SUPER_ADMIN)
                .collect(Collectors.toList());
        }

        roleChoice.setItems(FXCollections.observableArrayList(availableRoles));
        roleChoice.setValue(UserRole.USER);

        // Display role as its display name
        roleChoice.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(UserRole role) {
                return role == null ? "" : role.getDisplayName();
            }
            @Override
            public UserRole fromString(String s) {
                return null;
            }
        });

        errorLabel.setText("");
    }

    /**
     * Set up the dialog for creating a new user.
     */
    public void setupForNew() {
        this.editingUser = null;
        titleLabel.setText("Add New User");
        activeCheckBox.setSelected(true);
        passwordHintLabel.setText("Minimum 8 characters, one letter + one digit.");
    }

    /**
     * Set up the dialog for editing an existing user.
     */
    public void setupForEdit(User user) {
        this.editingUser = user;
        titleLabel.setText("Edit User");

        usernameField.setText(user.getUsername());
        usernameField.setDisable(true); // Username cannot be changed
        fullNameField.setText(user.getFullName());
        emailField.setText(user.getEmail());
        phoneField.setText(user.getPhone());
        roleChoice.setValue(user.getRole());
        activeCheckBox.setSelected(user.isActive());

        passwordHintLabel.setText("Leave blank to keep current password.");

        // Protect against editing someone with higher privileges
        boolean isSuperAdmin = UserSession.getInstance().getCurrentUser().getRole() == UserRole.SUPER_ADMIN;
        if (user.getRole() == UserRole.SUPER_ADMIN && !isSuperAdmin) {
            errorLabel.setText("Only a Super Administrator can modify this account.");
            roleChoice.setDisable(true);
            activeCheckBox.setDisable(true);
        }
    }

    @FXML
    private void onSave() {
        errorLabel.setText("");

        try {
            if (editingUser == null) {
                // Creating new user
                validateForCreate();

                userService.createUser(
                    usernameField.getText().trim(),
                    passwordField.getText(),
                    fullNameField.getText().trim(),
                    emailField.getText().trim(),
                    phoneField.getText().trim(),
                    roleChoice.getValue()
                );
                logger.info("User created: {}", usernameField.getText());

            } else {
                // Editing existing user
                validateForEdit();

                editingUser.setFullName(fullNameField.getText().trim());
                editingUser.setEmail(emptyToNull(emailField.getText()));
                editingUser.setPhone(emptyToNull(phoneField.getText()));
                editingUser.setRole(roleChoice.getValue());
                editingUser.setActive(activeCheckBox.isSelected());

                userService.updateUser(editingUser);

                // If password field is filled, change the password too
                String newPass = passwordField.getText();
                if (newPass != null && !newPass.isEmpty()) {
                    userService.changePassword(editingUser.getId(), newPass);
                }
                logger.info("User updated: {}", editingUser.getUsername());
            }

            saved = true;
            closeDialog();

        } catch (IllegalArgumentException | IllegalStateException | SecurityException e) {
            errorLabel.setText(e.getMessage());
        } catch (Exception e) {
            logger.error("Save failed: {}", e.getMessage(), e);
            errorLabel.setText("Could not save user: " + e.getMessage());
        }
    }

    @FXML
    private void onCancel() {
        saved = false;
        closeDialog();
    }

    public boolean isSaved() {
        return saved;
    }

    private void validateForCreate() {
        if (usernameField.getText() == null || usernameField.getText().trim().isEmpty()) {
            throw new IllegalArgumentException("Username is required.");
        }
        if (fullNameField.getText() == null || fullNameField.getText().trim().isEmpty()) {
            throw new IllegalArgumentException("Full name is required.");
        }
        if (passwordField.getText() == null || passwordField.getText().isEmpty()) {
            throw new IllegalArgumentException("Password is required for new users.");
        }
        if (!passwordField.getText().equals(passwordConfirmField.getText())) {
            throw new IllegalArgumentException("Passwords do not match.");
        }
    }

    private void validateForEdit() {
        if (fullNameField.getText() == null || fullNameField.getText().trim().isEmpty()) {
            throw new IllegalArgumentException("Full name is required.");
        }
        String newPass = passwordField.getText();
        if (newPass != null && !newPass.isEmpty()) {
            if (!newPass.equals(passwordConfirmField.getText())) {
                throw new IllegalArgumentException("Passwords do not match.");
            }
        }
    }

    private String emptyToNull(String s) {
        return (s == null || s.trim().isEmpty()) ? null : s.trim();
    }

    private void closeDialog() {
        Stage stage = (Stage) usernameField.getScene().getWindow();
        stage.close();
    }
}