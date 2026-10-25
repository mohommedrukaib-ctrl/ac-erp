package com.aceerp.controller;

import com.aceerp.model.Supplier;
import com.aceerp.service.SupplierService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SupplierController {

    private static final Logger log = LoggerFactory.getLogger(SupplierController.class);
    private final SupplierService service = new SupplierService();

    @FXML private TableView<Supplier> tableView;
    @FXML private TableColumn<Supplier, String> colCode;
    @FXML private TableColumn<Supplier, String> colName;
    @FXML private TableColumn<Supplier, String> colContact;
    @FXML private TableColumn<Supplier, String> colPhone;
    @FXML private TableColumn<Supplier, String> colEmail;
    @FXML private TableColumn<Supplier, String> colStatus;
    @FXML private TextField searchField;

    private final ObservableList<Supplier> allData = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colCode.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSupplierCode()));
        colName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        colContact.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getContactPerson()));
        colPhone.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPhone()));
        colEmail.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEmail()));
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatusDisplay()));

        FilteredList<Supplier> filtered = new FilteredList<>(allData, p -> true);
        tableView.setItems(filtered);

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String f = newVal == null ? "" : newVal.toLowerCase();
            filtered.setPredicate(s ->
                    s.getName().toLowerCase().contains(f) ||
                    (s.getPhone() != null && s.getPhone().toLowerCase().contains(f)) ||
                    (s.getSupplierCode() != null && s.getSupplierCode().toLowerCase().contains(f))
            );
        });

        loadData();
    }

    private void loadData() {
        allData.setAll(service.listAll());
    }

    @FXML
    private void onAdd() { showAddEditDialog(null); }

    @FXML
    private void onEdit() {
        Supplier sel = tableView.getSelectionModel().getSelectedItem();
        if (sel == null) { showAlert("Select a supplier to edit."); return; }
        showAddEditDialog(sel);
    }

    @FXML
    private void onDeactivate() {
        Supplier sel = tableView.getSelectionModel().getSelectedItem();
        if (sel == null) { showAlert("Select a supplier."); return; }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Deactivate " + sel.getName() + "?", ButtonType.YES, ButtonType.NO);
        confirm.showAndWait().ifPresent(r -> {
            if (r == ButtonType.YES) {
                try { service.deactivate(sel.getId()); loadData(); }
                catch (Exception e) { showAlert("Error: " + e.getMessage()); }
            }
        });
    }

    private void showAddEditDialog(Supplier existing) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Add Supplier" : "Edit Supplier");

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(20));

        TextField nameField = new TextField();
        TextField contactField = new TextField();
        TextField phoneField = new TextField();
        TextField emailField = new TextField();
        TextField addressField = new TextField();
        TextField cityField = new TextField();
        TextField taxField = new TextField();
        TextArea notesField = new TextArea();
        notesField.setPrefRowCount(3);

        grid.add(new Label("Name *:"), 0, 0); grid.add(nameField, 1, 0);
        grid.add(new Label("Contact Person:"), 0, 1); grid.add(contactField, 1, 1);
        grid.add(new Label("Phone:"), 0, 2); grid.add(phoneField, 1, 2);
        grid.add(new Label("Email:"), 0, 3); grid.add(emailField, 1, 3);
        grid.add(new Label("Address:"), 0, 4); grid.add(addressField, 1, 4);
        grid.add(new Label("City:"), 0, 5); grid.add(cityField, 1, 5);
        grid.add(new Label("Tax Number:"), 0, 6); grid.add(taxField, 1, 6);
        grid.add(new Label("Notes:"), 0, 7); grid.add(notesField, 1, 7);

        if (existing != null) {
            nameField.setText(existing.getName());
            contactField.setText(existing.getContactPerson());
            phoneField.setText(existing.getPhone());
            emailField.setText(existing.getEmail());
            addressField.setText(existing.getAddress());
            cityField.setText(existing.getCity());
            taxField.setText(existing.getTaxNumber());
            notesField.setText(existing.getNotes());
        }

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    if (existing == null) {
                        service.addSupplier(nameField.getText(), contactField.getText(),
                                phoneField.getText(), emailField.getText(),
                                addressField.getText(), cityField.getText(),
                                taxField.getText(), notesField.getText());
                    } else {
                        existing.setName(nameField.getText());
                        existing.setContactPerson(contactField.getText());
                        existing.setPhone(phoneField.getText());
                        existing.setEmail(emailField.getText());
                        existing.setAddress(addressField.getText());
                        existing.setCity(cityField.getText());
                        existing.setTaxNumber(taxField.getText());
                        existing.setNotes(notesField.getText());
                        service.updateSupplier(existing);
                    }
                    loadData();
                } catch (Exception e) {
                    showAlert("Error: " + e.getMessage());
                }
            }
        });
    }

    private void showAlert(String msg) {
        new Alert(Alert.AlertType.WARNING, msg).showAndWait();
    }
}