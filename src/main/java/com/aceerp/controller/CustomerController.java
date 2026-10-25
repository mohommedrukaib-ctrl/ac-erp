package com.aceerp.controller;

import com.aceerp.model.Customer;
import com.aceerp.service.CustomerService;
import com.aceerp.util.AlertHelper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.net.URL;
import java.util.ResourceBundle;

public class CustomerController implements Initializable {

    @FXML private TextField searchField;
    @FXML private TableView<Customer> customersTable;
    @FXML private TableColumn<Customer, String> colCode;
    @FXML private TableColumn<Customer, String> colName;
    @FXML private TableColumn<Customer, String> colPhone;
    @FXML private TableColumn<Customer, String> colEmail;
    @FXML private TableColumn<Customer, String> colCity;
    @FXML private TableColumn<Customer, String> colTax;
    @FXML private TableColumn<Customer, String> colStatus;

    private final CustomerService customerService = new CustomerService();
    private final ObservableList<Customer> customerList = FXCollections.observableArrayList();
    private FilteredList<Customer> filteredCustomers;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupColumns();
        setupSearch();
        loadCustomers();
    }

    private void setupColumns() {
        colCode.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getCustomerCode()));
        colName.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getName()));
        colPhone.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getPhone() != null ? c.getValue().getPhone() : ""));
        colEmail.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getEmail() != null ? c.getValue().getEmail() : ""));
        colCity.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getCity() != null ? c.getValue().getCity() : ""));
        colTax.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getTaxNumber() != null ? c.getValue().getTaxNumber() : ""));
        colStatus.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getStatusDisplay()));
    }

    private void setupSearch() {
        filteredCustomers = new FilteredList<>(customerList, p -> true);
        customersTable.setItems(filteredCustomers);

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String query = newVal == null ? "" : newVal.toLowerCase().trim();
            filteredCustomers.setPredicate(c -> {
                if (query.isEmpty()) return true;
                return (c.getName() != null && c.getName().toLowerCase().contains(query))
                    || (c.getCustomerCode() != null && c.getCustomerCode().toLowerCase().contains(query))
                    || (c.getPhone() != null && c.getPhone().contains(query))
                    || (c.getEmail() != null && c.getEmail().toLowerCase().contains(query));
            });
        });
    }

    private void loadCustomers() {
        try {
            customerList.setAll(customerService.listAll());
        } catch (Exception e) {
            AlertHelper.error("Error", "Failed to load customers: " + e.getMessage());
        }
    }

    @FXML
    private void handleAdd() {
        showCustomerDialog(null);
    }

    @FXML
    private void handleEdit() {
        Customer selected = customersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertHelper.warning("No Selection", "Please select a customer to edit.");
            return;
        }
        showCustomerDialog(selected);
    }

    @FXML
    private void handleDeactivate() {
        Customer selected = customersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertHelper.warning("No Selection", "Please select a customer.");
            return;
        }
        if (AlertHelper.confirm("Deactivate Customer", "Are you sure you want to deactivate '" + selected.getName() + "'?")) {
            try {
                customerService.deactivate(selected.getId());
                loadCustomers();
            } catch (Exception e) {
                AlertHelper.error("Error", "Failed to deactivate customer: " + e.getMessage());
            }
        }
    }

    private void showCustomerDialog(Customer existing) {
        boolean isEdit = (existing != null);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(isEdit ? "Edit Customer" : "Add Customer");
        dialog.setHeaderText(isEdit ? "Edit: " + existing.getName() : "Enter customer details");

        TextField nameField = new TextField();
        nameField.setPromptText("Full Name / Company Name *");

        TextField phoneField = new TextField();
        phoneField.setPromptText("Phone Number");

        TextField emailField = new TextField();
        emailField.setPromptText("Email Address");

        TextField addressField = new TextField();
        addressField.setPromptText("Address");

        TextField cityField = new TextField();
        cityField.setPromptText("City");

        TextField taxField = new TextField();
        taxField.setPromptText("Tax Registration / TRN / VAT No");

        TextArea notesArea = new TextArea();
        notesArea.setPromptText("Optional notes...");
        notesArea.setPrefRowCount(3);

        if (isEdit) {
            nameField.setText(existing.getName());
            phoneField.setText(existing.getPhone() != null ? existing.getPhone() : "");
            emailField.setText(existing.getEmail() != null ? existing.getEmail() : "");
            addressField.setText(existing.getAddress() != null ? existing.getAddress() : "");
            cityField.setText(existing.getCity() != null ? existing.getCity() : "");
            taxField.setText(existing.getTaxNumber() != null ? existing.getTaxNumber() : "");
            notesArea.setText(existing.getNotes() != null ? existing.getNotes() : "");
        }

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(15));

        grid.add(new Label("Customer Name:*"), 0, 0); grid.add(nameField, 1, 0);
        grid.add(new Label("Phone:"), 0, 1);          grid.add(phoneField, 1, 1);
        grid.add(new Label("Email:"), 0, 2);          grid.add(emailField, 1, 2);
        grid.add(new Label("Address:"), 0, 3);        grid.add(addressField, 1, 3);
        grid.add(new Label("City:"), 0, 4);           grid.add(cityField, 1, 4);
        grid.add(new Label("Tax / TRN ID:"), 0, 5);   grid.add(taxField, 1, 5);
        grid.add(new Label("Notes:"), 0, 6);          grid.add(notesArea, 1, 6);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                String name = nameField.getText().trim();
                if (name.isEmpty()) {
                    AlertHelper.warning("Missing Name", "Customer name is required.");
                    return null;
                }
                try {
                    if (isEdit) {
                        existing.setName(name);
                        existing.setPhone(phoneField.getText().trim());
                        existing.setEmail(emailField.getText().trim());
                        existing.setAddress(addressField.getText().trim());
                        existing.setCity(cityField.getText().trim());
                        existing.setTaxNumber(taxField.getText().trim());
                        existing.setNotes(notesArea.getText().trim());
                        customerService.updateCustomer(existing);
                        AlertHelper.info("Success", "Customer updated.");
                    } else {
                        customerService.addCustomer(
                            name,
                            phoneField.getText().trim(),
                            emailField.getText().trim(),
                            addressField.getText().trim(),
                            cityField.getText().trim(),
                            taxField.getText().trim(),
                            notesArea.getText().trim()
                        );
                        AlertHelper.info("Success", "Customer created.");
                    }
                    loadCustomers();
                } catch (Exception e) {
                    AlertHelper.error("Error", "Failed to save customer: " + e.getMessage());
                    return null;
                }
            }
            return button;
        });

        dialog.showAndWait();
    }
}