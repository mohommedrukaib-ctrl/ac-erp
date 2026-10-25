package com.aceerp.controller;

import com.aceerp.model.User;
import com.aceerp.model.UserRole;
import com.aceerp.model.UserSession;
import com.aceerp.service.UserService;
import com.aceerp.util.AlertHelper;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.format.DateTimeFormatter;

/**
 * Controller for the User Management screen.
 * 
 * Shows a table of all users with Add, Edit, Change Password, and Deactivate buttons.
 */
public class UserManagementController {

    private static final Logger logger = LoggerFactory.getLogger(UserManagementController.class);

    @FXML private TableView<User> usersTable;
    @FXML private TableColumn<User, String> usernameCol;
    @FXML private TableColumn<User, String> fullNameCol;
    @FXML private TableColumn<User, String> emailCol;
    @FXML private TableColumn<User, String> roleCol;
    @FXML private TableColumn<User, String> statusCol;
    @FXML private TableColumn<User, String> lastLoginCol;
    @FXML private Button addButton;
    @FXML private Button editButton;
    @FXML private Button deactivateButton;
    @FXML private Label infoLabel;

    private final UserService userService = new UserService();
    private final ObservableList<User> users = FXCollections.observableArrayList();

    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML
    public void initialize() {
        setupTable();
        refreshUsers();
        setupSelectionListener();
    }

    private void setupTable() {
        // Link columns to User's getters
        usernameCol.setCellValueFactory(new PropertyValueFactory<>("username"));
        fullNameCol.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        emailCol.setCellValueFactory(new PropertyValueFactory<>("email"));
        roleCol.setCellValueFactory(new PropertyValueFactory<>("roleDisplayName"));
        statusCol.setCellValueFactory(new PropertyValueFactory<>("statusDisplay"));

        // Last login requires formatting
        lastLoginCol.setCellValueFactory(cellData -> {
            if (cellData.getValue().getLastLogin() == null) {
                return new javafx.beans.property.SimpleStringProperty("Never");
            }
            return new javafx.beans.property.SimpleStringProperty(
                cellData.getValue().getLastLogin().format(DATE_FORMAT)
            );
        });

        usersTable.setItems(users);

        // Double-click to edit
        usersTable.setRowFactory(tv -> {
            javafx.scene.control.TableRow<User> row = new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    onEditUser();
                }
            });
            return row;
        });
    }

    private void setupSelectionListener() {
        usersTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            boolean hasSelection = newVal != null;
            editButton.setDisable(!hasSelection);
            deactivateButton.setDisable(!hasSelection ||
                !newVal.isActive() ||
                newVal.getId() == UserSession.getInstance().getCurrentUser().getId());
        });
    }

    private void refreshUsers() {
        try {
            users.setAll(userService.listAllUsers());
            infoLabel.setText("Total users: " + users.size());
        } catch (SecurityException e) {
            AlertHelper.error("Access Denied", e.getMessage());
            infoLabel.setText("");
        } catch (Exception e) {
            logger.error("Failed to load users: {}", e.getMessage(), e);
            AlertHelper.error("Error", "Could not load users: " + e.getMessage());
        }
    }

    @FXML
    private void onAddUser() {
        showUserDialog(null);
    }

    @FXML
    private void onEditUser() {
        User selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        showUserDialog(selected);
    }

    @FXML
    private void onDeactivateUser() {
        User selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        boolean confirmed = AlertHelper.confirm(
            "Deactivate User",
            "Deactivate user '" + selected.getUsername() + "'?\n\n" +
            "They will not be able to log in until you reactivate the account."
        );

        if (!confirmed) return;

        try {
            userService.deactivateUser(selected.getId());
            refreshUsers();
            AlertHelper.info("User Deactivated",
                "User '" + selected.getUsername() + "' has been deactivated.");
        } catch (Exception e) {
            logger.error("Deactivate failed: {}", e.getMessage(), e);
            AlertHelper.error("Error", e.getMessage());
        }
    }

    @FXML
    private void onRefresh() {
        refreshUsers();
    }

    /**
     * Open the Add/Edit user dialog modally.
     */
    private void showUserDialog(User userToEdit) {
        try {
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/aceerp/view/user-edit-dialog.fxml"));
            Parent root = loader.load();

            UserEditDialogController controller = loader.getController();
            if (userToEdit == null) {
                controller.setupForNew();
            } else {
                controller.setupForEdit(userToEdit);
            }

            Stage dialog = new Stage();
            dialog.setTitle(userToEdit == null ? "Add User" : "Edit User");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(usersTable.getScene().getWindow());

            Scene scene = new Scene(root);
            // Share the main stylesheet
            if (usersTable.getScene() != null && !usersTable.getScene().getStylesheets().isEmpty()) {
                scene.getStylesheets().addAll(usersTable.getScene().getStylesheets());
            }
            dialog.setScene(scene);
            dialog.setResizable(false);
            dialog.showAndWait();

            if (controller.isSaved()) {
                refreshUsers();
            }

        } catch (IOException e) {
            logger.error("Could not open user dialog: {}", e.getMessage(), e);
            AlertHelper.error("Error", "Could not open user editor: " + e.getMessage());
        }
    }
}