package com.aceerp.controller;

import com.aceerp.model.Customer;
import com.aceerp.model.Product;
import com.aceerp.model.WarrantyRegistration;
import com.aceerp.service.AuditLogService;
import com.aceerp.service.CustomerService;
import com.aceerp.service.ProductService;
import com.aceerp.service.ReportService;
import com.aceerp.service.WarrantyRegistrationService;
import com.aceerp.util.AlertHelper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

public class WarrantyCertificateController {

    @FXML private ComboBox<Product> productSelector;
    @FXML private ComboBox<Customer> customerSelector;
    @FXML private TextField serialNumberField;
    @FXML private DatePicker installationDatePicker;
    @FXML private DatePicker expiryDatePicker;
    @FXML private TableView<WarrantyRegistration> registrationsTable;
    @FXML private TableColumn<WarrantyRegistration, String> serialColumn;
    @FXML private TableColumn<WarrantyRegistration, String> productColumn;
    @FXML private TableColumn<WarrantyRegistration, String> customerColumn;
    @FXML private TableColumn<WarrantyRegistration, String> installedColumn;
    @FXML private TableColumn<WarrantyRegistration, String> expiryColumn;

    private final ProductService productService = new ProductService();
    private final CustomerService customerService = new CustomerService();
    private final WarrantyRegistrationService warrantyService = new WarrantyRegistrationService();

    @FXML
    public void initialize() {
        productSelector.setItems(FXCollections.observableArrayList(
                productService.listAll().stream().filter(Product::isActive).toList()));
        customerSelector.setItems(FXCollections.observableArrayList(
                customerService.listAll().stream().filter(Customer::isActive).toList()));
        installationDatePicker.setValue(LocalDate.now());

        productSelector.valueProperty().addListener((observable, oldProduct, newProduct) -> updateExpiryDate());
        installationDatePicker.valueProperty().addListener(
                (observable, oldDate, newDate) -> updateExpiryDate());

        serialColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getSerialNumber()));
        productColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getProductCode() + " - " + cell.getValue().getProductName()));
        customerColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getCustomerName()));
        installedColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(formatDate(cell.getValue().getInstallationDate())));
        expiryColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(formatDate(cell.getValue().getWarrantyExpiry())));

        loadRegistrations();
    }

    private void updateExpiryDate() {
        Product product = productSelector.getValue();
        LocalDate installationDate = installationDatePicker.getValue();
        if (product != null && installationDate != null && product.getWarrantyMonths() > 0) {
            expiryDatePicker.setValue(installationDate.plusMonths(product.getWarrantyMonths()));
        }
    }

    @FXML
    private void handleRegister() {
        Product product = productSelector.getValue();
        Customer customer = customerSelector.getValue();
        if (product == null || customer == null) {
            AlertHelper.warning("Missing Information", "Select both a product and customer.");
            return;
        }

        try {
            int registrationId = warrantyService.register(product.getId(), customer.getId(),
                    serialNumberField.getText(), installationDatePicker.getValue(), expiryDatePicker.getValue());
            AuditLogService.log("CREATE", "WARRANTY_CERTIFICATE", registrationId,
                    "Registered serial number " + serialNumberField.getText().trim());
            serialNumberField.clear();
            loadRegistrations();
            AlertHelper.info("Saved", "Warranty registration saved. Select it below and print its certificate.");
        } catch (IllegalArgumentException ex) {
            AlertHelper.warning("Invalid Warranty Registration", ex.getMessage());
        } catch (SQLException ex) {
            AlertHelper.error("Could Not Save", "Warranty registration failed: " + ex.getMessage());
        }
    }

    @FXML
    private void handleRefresh() {
        loadRegistrations();
    }

    @FXML
    private void handlePrintCertificate() {
        WarrantyRegistration selected = registrationsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertHelper.warning("No Selection", "Select a registered serial number to print its warranty certificate.");
            return;
        }
        ReportService.printWarrantyCertificate(selected.getId());
        AuditLogService.log("PRINT", "WARRANTY_CERTIFICATE", selected.getId(),
                "Printed certificate for serial number " + selected.getSerialNumber());
    }

    private void loadRegistrations() {
        try {
            List<WarrantyRegistration> registrations = warrantyService.listAll();
            registrationsTable.setItems(FXCollections.observableArrayList(registrations));
        } catch (SQLException ex) {
            AlertHelper.error("Could Not Load", "Warranty registrations could not be loaded: " + ex.getMessage());
        }
    }

    private static String formatDate(LocalDate date) {
        return date == null ? "" : date.toString();
    }
}
