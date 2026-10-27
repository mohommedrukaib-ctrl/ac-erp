package com.aceerp.controller;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.SupplierBill;
import com.aceerp.model.SupplierPayment;
import com.aceerp.service.SupplierBillService;
import com.aceerp.service.SupplierPaymentService;
import com.aceerp.util.CurrencyFormatter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SupplierPaymentController {

    public static class SimpleSupplier {
        private final int id;
        private final String name;
        public SimpleSupplier(int id, String name) { this.id = id; this.name = name; }
        public int getId() { return id; }
        public String getName() { return name; }
        @Override public String toString() { return name; }
    }

    @FXML private TextField searchField;
    @FXML private ListView<SupplierPayment> paymentListView;

    @FXML private TextField paymentNumberField;
    @FXML private DatePicker paymentDatePicker;
    @FXML private ComboBox<SimpleSupplier> supplierCombo;
    @FXML private ComboBox<SupplierBill> billCombo;
    @FXML private TextField amountField;
    @FXML private ComboBox<String> methodCombo;
    @FXML private TextField referenceNumberField;
    @FXML private TextField notesField;

    private final SupplierPaymentService paymentService = new SupplierPaymentService();
    private final SupplierBillService billService = new SupplierBillService();

    private final ObservableList<SupplierPayment> allPayments = FXCollections.observableArrayList();
    private SupplierPayment currentPayment;

    @FXML
    public void initialize() {
        setupListView();
        loadSuppliers();

        methodCombo.setItems(FXCollections.observableArrayList("BANK_TRANSFER", "CASH", "CHEQUE", "CREDIT_CARD"));
        methodCombo.setValue("BANK_TRANSFER");

        supplierCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadUnpaidBillsForSupplier(newVal.getId());
            } else {
                billCombo.getItems().clear();
            }
        });

        loadPayments();
        handleNewPayment();
    }

    private void setupListView() {
        paymentListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(SupplierPayment item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String supp = item.getSupplierName() != null ? item.getSupplierName() : "Supplier";
                    setText(item.getPaymentNumber() + " | " + supp + " | " + CurrencyFormatter.format(item.getAmount()));
                }
            }
        });

        paymentListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadPaymentToForm(newVal);
            }
        });

        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> filterPayments(newVal));
        }
    }

    private void filterPayments(String query) {
        if (query == null || query.isBlank()) {
            paymentListView.setItems(allPayments);
            return;
        }
        String q = query.toLowerCase().trim();
        paymentListView.setItems(FXCollections.observableArrayList(
                allPayments.stream().filter(p ->
                        (p.getPaymentNumber() != null && p.getPaymentNumber().toLowerCase().contains(q)) ||
                        (p.getSupplierName() != null && p.getSupplierName().toLowerCase().contains(q)) ||
                        (p.getBillNumber() != null && p.getBillNumber().toLowerCase().contains(q))
                ).collect(Collectors.toList())
        ));
    }

    private void loadSuppliers() {
        List<SimpleSupplier> suppliers = new ArrayList<>();
        String sql = "SELECT id, name FROM suppliers ORDER BY name ASC";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                suppliers.add(new SimpleSupplier(rs.getInt("id"), rs.getString("name")));
            }
        } catch (SQLException ignored) {}
        supplierCombo.setItems(FXCollections.observableArrayList(suppliers));
    }

    private void loadUnpaidBillsForSupplier(int supplierId) {
        List<SupplierBill> unpaid = billService.getAllBills().stream()
                .filter(b -> b.getSupplierId() == supplierId && !"PAID".equalsIgnoreCase(b.getStatus()))
                .collect(Collectors.toList());
        billCombo.setItems(FXCollections.observableArrayList(unpaid));
    }

    private void loadPayments() {
        allPayments.setAll(paymentService.getAllPayments());
        paymentListView.setItems(allPayments);
    }

    private void loadPaymentToForm(SupplierPayment p) {
        currentPayment = p;
        paymentNumberField.setText(p.getPaymentNumber());
        paymentDatePicker.setValue(p.getPaymentDate());
        amountField.setText(p.getAmount() != null ? p.getAmount().toString() : "0.00");
        methodCombo.setValue(p.getPaymentMethod() != null ? p.getPaymentMethod() : "BANK_TRANSFER");
        referenceNumberField.setText(p.getReferenceNumber() != null ? p.getReferenceNumber() : "");
        notesField.setText(p.getNotes() != null ? p.getNotes() : "");

        supplierCombo.setDisable(true);
        billCombo.setDisable(true);
        amountField.setEditable(false);
    }

    @FXML
    private void handleNewPayment() {
        currentPayment = new SupplierPayment();
        currentPayment.setPaymentNumber(paymentService.generateNextPaymentNumber());
        paymentNumberField.setText(currentPayment.getPaymentNumber());
        paymentDatePicker.setValue(LocalDate.now());
        amountField.clear();
        referenceNumberField.clear();
        notesField.clear();
        supplierCombo.setValue(null);
        billCombo.getItems().clear();

        supplierCombo.setDisable(false);
        billCombo.setDisable(false);
        amountField.setEditable(true);

        paymentListView.getSelectionModel().clearSelection();
    }

    @FXML
    private void handleSave() {
        if (supplierCombo.getValue() == null) {
            showAlert(Alert.AlertType.WARNING, "Validation", "Please select a Supplier.");
            return;
        }

        try {
            BigDecimal amt = new BigDecimal(amountField.getText().trim());
            if (amt.compareTo(BigDecimal.ZERO) <= 0) {
                showAlert(Alert.AlertType.WARNING, "Validation", "Amount must be greater than zero.");
                return;
            }
            currentPayment.setAmount(amt);
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Validation", "Invalid payment amount.");
            return;
        }

        currentPayment.setSupplierId(supplierCombo.getValue().getId());
        currentPayment.setSupplierName(supplierCombo.getValue().getName());

        if (billCombo.getValue() != null) {
            currentPayment.setBillId(billCombo.getValue().getId());
        }

        currentPayment.setPaymentDate(paymentDatePicker.getValue());
        currentPayment.setPaymentMethod(methodCombo.getValue());
        currentPayment.setReferenceNumber(referenceNumberField.getText() != null ? referenceNumberField.getText().trim() : "");
        currentPayment.setNotes(notesField.getText() != null ? notesField.getText().trim() : "");

        try {
            paymentService.savePayment(currentPayment);
            showAlert(Alert.AlertType.INFORMATION, "Success", "Vendor Payment recorded successfully!");
            loadPayments();
            handleNewPayment();
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Error", ex.getMessage());
        }
    }

    @FXML
    private void handleRefresh() {
        loadPayments();
        handleNewPayment();
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}