package com.aceerp.controller;

import com.aceerp.model.Customer;
import com.aceerp.model.DeliveryNote;
import com.aceerp.model.DeliveryNoteLine;
import com.aceerp.model.Product;
import com.aceerp.service.CustomerService;
import com.aceerp.service.DeliveryNoteService;
import com.aceerp.service.ProductService;
import com.aceerp.util.PrintUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.LocalDate;
import java.util.List;

public class DeliveryNoteController {

    @FXML private ListView<DeliveryNote> dnListView;
    @FXML private TextField txtDnNumber;
    @FXML private DatePicker dpDispatchDate;
    @FXML private TextField txtCustomerSearch;
    @FXML private TextField txtPhone;
    @FXML private TextField txtDriverName;
    @FXML private TextField txtVehicleNo;
    @FXML private TextArea txtDeliveryAddress;
    @FXML private ComboBox<String> statusCombo;
    @FXML private TextArea txtNotes;

    @FXML private TableView<DeliveryNoteLine> linesTable;
    @FXML private TableColumn<DeliveryNoteLine, String> colProduct;
    @FXML private TableColumn<DeliveryNoteLine, Number> colOrderedQty;
    @FXML private TableColumn<DeliveryNoteLine, Number> colShippedQty;

    private final DeliveryNoteService dnService = new DeliveryNoteService();
    private final CustomerService customerService = new CustomerService();
    private final ProductService productService = new ProductService();

    private final ObservableList<DeliveryNote> dnObservableList = FXCollections.observableArrayList();
    private final ObservableList<DeliveryNoteLine> linesObservableList = FXCollections.observableArrayList();
    private DeliveryNote currentDN = new DeliveryNote();
    private Customer selectedCustomer;

    @FXML
    public void initialize() {
        setupListView();
        setupStatusCombo();
        setupTableColumns();
        loadDeliveryNotes();
        handleNewDN();
    }

    private void setupListView() {
        dnListView.setItems(dnObservableList);
        dnListView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) loadDNToForm(newV);
        });
    }

    private void setupStatusCombo() {
        statusCombo.setItems(FXCollections.observableArrayList("DRAFT", "DISPATCHED", "DELIVERED", "CANCELLED"));
    }

    private void setupTableColumns() {
        colProduct.setCellValueFactory(cell -> cell.getValue().productNameProperty());
        colOrderedQty.setCellValueFactory(cell -> cell.getValue().orderedQtyProperty());
        colShippedQty.setCellValueFactory(cell -> cell.getValue().shippedQtyProperty());
        linesTable.setItems(linesObservableList);
    }

    private void loadDeliveryNotes() {
        dnObservableList.setAll(dnService.getAllDeliveryNotes());
    }

    @FXML
    private void handleNewDN() {
        currentDN = new DeliveryNote();
        currentDN.setDnNumber(dnService.generateNextDNNumber());
        txtDnNumber.setText(currentDN.getDnNumber());
        dpDispatchDate.setValue(LocalDate.now());
        txtCustomerSearch.clear();
        txtPhone.clear();
        txtDriverName.clear();
        txtVehicleNo.clear();
        txtDeliveryAddress.clear();
        txtNotes.clear();
        statusCombo.setValue("DISPATCHED");
        linesObservableList.clear();
        selectedCustomer = null;
    }

    private void loadDNToForm(DeliveryNote dn) {
        currentDN = dn;
        txtDnNumber.setText(dn.getDnNumber());
        dpDispatchDate.setValue(dn.getDispatchDate());
        txtCustomerSearch.setText(dn.getCustomerName());
        txtDriverName.setText(dn.getDriverName());
        txtVehicleNo.setText(dn.getVehicleNo());
        txtDeliveryAddress.setText(dn.getDeliveryAddress());
        statusCombo.setValue(dn.getStatus());
        txtNotes.setText(dn.getNotes());
        linesObservableList.setAll(dn.getLines());
    }

    @FXML
    private void handleAddProduct() {
        List<Product> products = productService.listAll();
        if (products.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Products", "No products available in inventory.");
            return;
        }
        ChoiceDialog<Product> dialog = new ChoiceDialog<>(products.get(0), products);
        dialog.setTitle("Select Product for Delivery Note");
        dialog.setHeaderText("Choose item to ship:");
        dialog.showAndWait().ifPresent(p -> {
            DeliveryNoteLine line = new DeliveryNoteLine(p.getId(), p.getName(), p.getProductCode(), 1.0, 1.0);
            linesObservableList.add(line);
        });
    }

    @FXML
    private void handleRemoveLine() {
        DeliveryNoteLine selected = linesTable.getSelectionModel().getSelectedItem();
        if (selected != null) linesObservableList.remove(selected);
    }

    @FXML
    private void handleSaveDN() {
        try {
            if (txtCustomerSearch.getText().isBlank()) {
                showAlert(Alert.AlertType.WARNING, "Customer Required", "Please specify a customer.");
                return;
            }
            if (linesObservableList.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Empty Lines", "Please add at least one item to ship.");
                return;
            }

            currentDN.setDnNumber(txtDnNumber.getText());
            currentDN.setDispatchDate(dpDispatchDate.getValue());
            currentDN.setCustomerName(txtCustomerSearch.getText());
            if (selectedCustomer != null) currentDN.setCustomerId(selectedCustomer.getId());
            currentDN.setDriverName(txtDriverName.getText());
            currentDN.setVehicleNo(txtVehicleNo.getText());
            currentDN.setDeliveryAddress(txtDeliveryAddress.getText());
            currentDN.setStatus(statusCombo.getValue());
            currentDN.setNotes(txtNotes.getText());
            currentDN.setLines(linesObservableList);

            dnService.save(currentDN);
            showAlert(Alert.AlertType.INFORMATION, "Success", "Delivery Note saved & Inventory updated!");
            loadDeliveryNotes();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error Saving", e.getMessage());
        }
    }

    @FXML
    private void handleDeleteDN() {
        if (currentDN == null || currentDN.getId() == null) return;
        try {
            dnService.delete(currentDN.getId());
            loadDeliveryNotes();
            handleNewDN();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Delete Failed", e.getMessage());
        }
    }

    @FXML
    private void handlePrintPreview() {
        PrintUtils.showPrintPreviewDialog("Delivery Note - " + txtDnNumber.getText(), linesTable);
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}