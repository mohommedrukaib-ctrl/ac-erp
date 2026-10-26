package com.aceerp.controller;

import com.aceerp.model.Customer;
import com.aceerp.model.DeliveryNote;
import com.aceerp.model.DeliveryNoteLine;
import com.aceerp.model.Product;
import com.aceerp.service.CustomerService;
import com.aceerp.service.DeliveryNoteService;
import com.aceerp.service.ProductService;
import com.aceerp.util.AlertHelper;
import com.aceerp.util.PrintUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.util.converter.NumberStringConverter;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

public class DeliveryNoteController {

    @FXML private TextField searchField;
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

    @FXML private Button btnAddProduct;
    @FXML private Button btnRemoveLine;
    @FXML private Button btnSaveDN;

    private final DeliveryNoteService dnService = new DeliveryNoteService();
    private final CustomerService customerService = new CustomerService();
    private final ProductService productService = new ProductService();

    private final ObservableList<DeliveryNote> dnObservableList = FXCollections.observableArrayList();
    private final ObservableList<DeliveryNoteLine> linesObservableList = FXCollections.observableArrayList();
    private List<DeliveryNote> allDeliveryNotes = List.of();

    private DeliveryNote currentDN = new DeliveryNote();
    private Customer selectedCustomer;
    private boolean editMode = false;

    private Popup customerPopup;
    private ListView<Customer> customerListView;

    @FXML
    public void initialize() {
        setupSmartCustomerSearch();
        setupListView();
        setupStatusCombo();
        setupTableColumns();
        loadDeliveryNotes();
        handleNewDN();
    }

    private void setupListView() {
        dnListView.setItems(dnObservableList);

        dnListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(DeliveryNote item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String name = item.getCustomerName() != null ? item.getCustomerName() : "Unknown";
                    String status = item.getStatus() != null ? item.getStatus() : "DRAFT";
                    setText(item.getDnNumber() + " | " + name + " | " + status);
                }
            }
        });

        dnListView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                loadDNToForm(newV);
            }
        });

        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> filterDeliveryNotes(newVal));
        }
    }

    private void filterDeliveryNotes(String query) {
        if (query == null || query.isBlank()) {
            dnObservableList.setAll(allDeliveryNotes);
            return;
        }
        String q = query.toLowerCase().trim();
        dnObservableList.setAll(allDeliveryNotes.stream()
                .filter(dn -> (dn.getDnNumber() != null && dn.getDnNumber().toLowerCase().contains(q))
                        || (dn.getCustomerName() != null && dn.getCustomerName().toLowerCase().contains(q))
                        || (dn.getStatus() != null && dn.getStatus().toLowerCase().contains(q)))
                .collect(Collectors.toList()));
    }

    private void setupSmartCustomerSearch() {
        customerPopup = new Popup();
        customerPopup.setAutoHide(true);
        customerListView = new ListView<>();
        customerListView.setPrefHeight(150);
        customerPopup.getContent().add(customerListView);

        txtCustomerSearch.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!editMode) return;
            if (newVal == null || newVal.trim().isEmpty()) {
                customerPopup.hide();
                return;
            }
            String query = newVal.toLowerCase().trim();
            List<Customer> results = customerService.listAll().stream()
                    .filter(c -> c.getName() != null && c.getName().toLowerCase().contains(query))
                    .collect(Collectors.toList());

            if (results.isEmpty()) {
                customerPopup.hide();
            } else {
                customerListView.setItems(FXCollections.observableArrayList(results));
                if (!customerPopup.isShowing() && txtCustomerSearch.getScene() != null) {
                    Bounds bounds = txtCustomerSearch.localToScreen(txtCustomerSearch.getBoundsInLocal());
                    if (bounds != null) {
                        customerListView.setPrefWidth(bounds.getWidth());
                        customerPopup.show(txtCustomerSearch, bounds.getMinX(), bounds.getMaxY());
                    }
                }
            }
        });

        customerListView.setOnMouseClicked(e -> {
            Customer c = customerListView.getSelectionModel().getSelectedItem();
            if (c != null) {
                selectCustomer(c);
            }
        });
    }

    private void selectCustomer(Customer c) {
        selectedCustomer = c;
        txtCustomerSearch.setText(c.getName());
        txtPhone.setText(c.getPhone() != null && !c.getPhone().isEmpty() ? c.getPhone() : "N/A");
        customerPopup.hide();
    }

    private void setupStatusCombo() {
        statusCombo.setItems(FXCollections.observableArrayList("DRAFT", "DISPATCHED", "DELIVERED", "CANCELLED"));
    }

    private void setupTableColumns() {
        colProduct.setCellValueFactory(cell -> cell.getValue().productNameProperty());
        colOrderedQty.setCellValueFactory(cell -> cell.getValue().orderedQtyProperty());
        colShippedQty.setCellValueFactory(cell -> cell.getValue().shippedQtyProperty());

        colShippedQty.setCellFactory(TextFieldTableCell.forTableColumn(new NumberStringConverter()));
        colShippedQty.setOnEditCommit(e -> {
            if (!editMode) {
                linesTable.refresh();
                return;
            }
            Number val = e.getNewValue();
            if (val != null) {
                e.getRowValue().setShippedQty(val.doubleValue());
            }
            linesTable.refresh();
        });

        linesTable.setItems(linesObservableList);
    }

    private void loadDeliveryNotes() {
        allDeliveryNotes = dnService.getAllDeliveryNotes();
        dnObservableList.setAll(allDeliveryNotes);
    }

    private void setFormEditable(boolean editable) {
        editMode = editable;
        dpDispatchDate.setDisable(!editable);
        txtCustomerSearch.setEditable(editable);
        txtDriverName.setEditable(editable);
        txtVehicleNo.setEditable(editable);
        txtDeliveryAddress.setEditable(editable);
        statusCombo.setDisable(!editable);
        txtNotes.setEditable(editable);
        linesTable.setEditable(editable);
        if (btnAddProduct != null) btnAddProduct.setDisable(!editable);
        if (btnRemoveLine != null) btnRemoveLine.setDisable(!editable);
        if (btnSaveDN != null) btnSaveDN.setDisable(!editable);
    }

    @FXML
    private void handleEdit() {
        if (currentDN == null || currentDN.getId() == null || currentDN.getId() <= 0) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a Delivery Note from the list first.");
            return;
        }
        setFormEditable(true);
        showAlert(Alert.AlertType.INFORMATION, "Edit Mode", "Delivery Note is now editable. Make changes and click Dispatch & Save.");
    }

    @FXML
    private void handleNewDN() {
        dnListView.getSelectionModel().clearSelection();
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
        setFormEditable(true);
    }

    private void loadDNToForm(DeliveryNote dn) {
        currentDN = dn;
        txtDnNumber.setText(dn.getDnNumber());
        dpDispatchDate.setValue(dn.getDispatchDate() != null ? dn.getDispatchDate() : LocalDate.now());
        txtCustomerSearch.setText(dn.getCustomerName() != null ? dn.getCustomerName() : "");
        txtDriverName.setText(dn.getDriverName() != null ? dn.getDriverName() : "");
        txtVehicleNo.setText(dn.getVehicleNo() != null ? dn.getVehicleNo() : "");
        txtDeliveryAddress.setText(dn.getDeliveryAddress() != null ? dn.getDeliveryAddress() : "");
        statusCombo.setValue(dn.getStatus() != null ? dn.getStatus() : "DRAFT");
        txtNotes.setText(dn.getNotes() != null ? dn.getNotes() : "");

        if (dn.getCustomerId() > 0) {
            selectedCustomer = customerService.getById(dn.getCustomerId()).orElse(null);
            if (selectedCustomer != null) {
                txtPhone.setText(selectedCustomer.getPhone() != null ? selectedCustomer.getPhone() : "N/A");
            } else {
                txtPhone.setText("N/A");
            }
        } else {
            selectedCustomer = null;
            txtPhone.setText("N/A");
        }

        linesObservableList.clear();
        if (dn.getLines() != null) {
            linesObservableList.addAll(dn.getLines());
        }

        setFormEditable(false);
    }

    @FXML
    private void handleAddProduct() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click Edit first to modify this Delivery Note.");
            return;
        }
        List<Product> products = productService.listAll();
        if (products.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Products", "No products available in inventory.");
            return;
        }

        Dialog<Product> dialog = new Dialog<>();
        dialog.setTitle("Select Product");
        dialog.setHeaderText("Search products to ship");
        ButtonType addBtn = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addBtn, ButtonType.CANCEL);

        TextField itemSearch = new TextField();
        itemSearch.setPromptText("Search product...");
        ListView<Product> itemListView = new ListView<>();
        itemListView.setPrefHeight(280);
        itemListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Product p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) setText(null);
                else setText(p.getProductCode() + " | " + p.getName()
                        + (p.getStockQuantity() <= 0 ? " [OUT OF STOCK]" : " [Stock: " + p.getStockQuantity() + "]"));
            }
        });
        itemListView.setItems(FXCollections.observableArrayList(products));
        itemSearch.textProperty().addListener((obs, o, n) -> {
            if (n == null || n.isBlank()) {
                itemListView.setItems(FXCollections.observableArrayList(products));
            } else {
                String q = n.toLowerCase().trim();
                itemListView.setItems(FXCollections.observableArrayList(products.stream()
                        .filter(p -> (p.getName() != null && p.getName().toLowerCase().contains(q))
                                || (p.getProductCode() != null && p.getProductCode().toLowerCase().contains(q)))
                        .collect(Collectors.toList())));
            }
        });

        VBox box = new VBox(10, itemSearch, itemListView);
        box.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(box);
        dialog.setResultConverter(btn -> btn == addBtn ? itemListView.getSelectionModel().getSelectedItem() : null);

        dialog.showAndWait().ifPresent(p -> {
            DeliveryNoteLine line = new DeliveryNoteLine(p.getId(), p.getName(), p.getProductCode(), 1.0, 1.0);
            linesObservableList.add(line);
        });
    }

    @FXML
    private void handleRemoveLine() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click Edit first to modify this Delivery Note.");
            return;
        }
        DeliveryNoteLine selected = linesTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            linesObservableList.remove(selected);
        } else {
            showAlert(Alert.AlertType.INFORMATION, "Notice", "Please select a line item to remove.");
        }
    }

    @FXML
    private void handleSaveDN() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click Edit first to modify this Delivery Note.");
            return;
        }
        try {
            if (txtCustomerSearch.getText() == null || txtCustomerSearch.getText().isBlank()) {
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
            if (selectedCustomer != null) {
                currentDN.setCustomerId(selectedCustomer.getId());
            }
            currentDN.setDriverName(txtDriverName.getText());
            currentDN.setVehicleNo(txtVehicleNo.getText());
            currentDN.setDeliveryAddress(txtDeliveryAddress.getText());
            currentDN.setStatus(statusCombo.getValue());
            currentDN.setNotes(txtNotes.getText());
            currentDN.setLines(linesObservableList);

            dnService.save(currentDN);
            showAlert(Alert.AlertType.INFORMATION, "Success", "Delivery Note saved & Inventory updated!");
            loadDeliveryNotes();
            setFormEditable(false);
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error Saving", e.getMessage());
        }
    }

    @FXML
    private void handleDeleteDN() {
        if (currentDN == null || currentDN.getId() == null || currentDN.getId() <= 0) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a Delivery Note to delete.");
            return;
        }
        boolean confirmed = AlertHelper.confirm("Delete Delivery Note",
                "Are you sure you want to delete DN #" + currentDN.getDnNumber() + "?");
        if (!confirmed) return;

        try {
            dnService.delete(currentDN.getId());
            loadDeliveryNotes();
            handleNewDN();
            showAlert(Alert.AlertType.INFORMATION, "Deleted", "Delivery Note deleted.");
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
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}