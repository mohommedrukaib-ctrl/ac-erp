package com.aceerp.controller;

import com.aceerp.model.Product;
import com.aceerp.model.PurchaseOrder;
import com.aceerp.model.PurchaseOrderLine;
import com.aceerp.model.Supplier;
import com.aceerp.service.ProductService;
import com.aceerp.service.PurchaseOrderService;
import com.aceerp.service.ReportService;
import com.aceerp.service.SettingsService;
import com.aceerp.service.SupplierService;
import com.aceerp.util.CurrencyFormatter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.GridPane;
import javafx.stage.Popup;
import javafx.util.converter.BigDecimalStringConverter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

public class PurchaseOrderController {

    @FXML private TextField searchField;
    @FXML private TextField supplierSearchField;
    @FXML private TextField poNumberField;
    @FXML private DatePicker orderDatePicker;
    @FXML private DatePicker expectedDatePicker;
    @FXML private ComboBox<String> statusCombo;
    @FXML private ListView<PurchaseOrder> poListView;
    
    @FXML private CheckBox globalTaxCheck;

    @FXML private TableView<PurchaseOrderLine> linesTable;
    @FXML private TableColumn<PurchaseOrderLine, String> colProduct;
    @FXML private TableColumn<PurchaseOrderLine, BigDecimal> colQty;
    @FXML private TableColumn<PurchaseOrderLine, BigDecimal> colPrice;
    @FXML private TableColumn<PurchaseOrderLine, Boolean> colTaxable;
    @FXML private TableColumn<PurchaseOrderLine, BigDecimal> colTaxRate;
    @FXML private TableColumn<PurchaseOrderLine, BigDecimal> colTaxAmt;
    @FXML private TableColumn<PurchaseOrderLine, BigDecimal> colTotal;
    
    @FXML private Label subtotalLabel;
    @FXML private Label taxLabel;
    @FXML private Label totalLabel;

    private final PurchaseOrderService poService = new PurchaseOrderService();
    private final SupplierService supplierService = new SupplierService();
    private final ProductService productService = new ProductService();
    
    private BigDecimal defaultTaxRate = BigDecimal.ZERO;

    private Supplier selectedSupplier;
    private final ObservableList<PurchaseOrderLine> lines = FXCollections.observableArrayList();
    private final ObservableList<PurchaseOrder> allPOs = FXCollections.observableArrayList();
    private PurchaseOrder currentPO;
    
    private Popup supplierPopup;
    private ListView<Supplier> supplierListView;
    private boolean editMode = false;

    @FXML
    public void initialize() {
        loadDefaultTaxRate();
        setupSmartSearch();
        setupTable();
        setupListView();
        
        statusCombo.setItems(FXCollections.observableArrayList("DRAFT", "APPROVED", "RECEIVED", "CANCELLED"));
        loadPOs();
        handleNewPO();
    }

    private void loadDefaultTaxRate() {
        String rateStr = SettingsService.getInstance().get("tax.default_rate");
        if (rateStr != null && !rateStr.isBlank()) {
            try {
                defaultTaxRate = new BigDecimal(rateStr.trim());
            } catch (Exception e) {
                defaultTaxRate = new BigDecimal("15.00"); // Fallback to 15%
            }
        } else {
            defaultTaxRate = new BigDecimal("15.00");
        }
    }

    private void setupListView() {
        poListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(PurchaseOrder po, boolean empty) {
                super.updateItem(po, empty);
                if (empty || po == null) setText(null);
                else setText(po.getPoNumber() + " | " + (po.getSupplierName() != null ? po.getSupplierName() : "Unknown") + " | " + po.getStatus());
            }
        });

        poListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) loadPOToForm(newVal);
        });

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isBlank()) poListView.setItems(allPOs);
            else {
                String q = newVal.toLowerCase();
                poListView.setItems(FXCollections.observableArrayList(allPOs.stream()
                    .filter(po -> (po.getPoNumber() != null && po.getPoNumber().toLowerCase().contains(q)) ||
                                  (po.getSupplierName() != null && po.getSupplierName().toLowerCase().contains(q)))
                    .collect(Collectors.toList())));
            }
        });
    }

    private void setupSmartSearch() {
        supplierPopup = new Popup();
        supplierPopup.setAutoHide(true);
        supplierListView = new ListView<>();
        supplierListView.setPrefHeight(150);
        
        supplierListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Supplier s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) {
                    setText(null);
                } else {
                    String code = s.getSupplierCode() != null ? s.getSupplierCode() + " | " : "";
                    setText(code + (s.getName() != null ? s.getName() : ("#" + s.getId())));
                }
            }
        });
        
        supplierPopup.getContent().add(supplierListView);

        supplierSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!editMode) return;
            if (newVal == null || newVal.trim().isEmpty()) {
                supplierPopup.hide();
                return;
            }
            String query = newVal.toLowerCase().trim();
            List<Supplier> results = supplierService.listAll().stream()
                .filter(s -> s.getName() != null && s.getName().toLowerCase().contains(query))
                .collect(Collectors.toList());

            if (results.isEmpty()) {
                supplierPopup.hide();
            } else {
                supplierListView.setItems(FXCollections.observableArrayList(results));
                if (!supplierPopup.isShowing() && supplierSearchField.getScene() != null) {
                    Bounds bounds = supplierSearchField.localToScreen(supplierSearchField.getBoundsInLocal());
                    if (bounds != null) {
                        supplierListView.setPrefWidth(bounds.getWidth());
                        supplierPopup.show(supplierSearchField, bounds.getMinX(), bounds.getMaxY());
                    }
                }
            }
        });

        supplierListView.setOnMouseClicked(e -> {
            Supplier s = supplierListView.getSelectionModel().getSelectedItem();
            if (s != null) {
                selectedSupplier = s;
                supplierSearchField.setText(s.getName());
                supplierPopup.hide();
            }
        });
    }

    private void setupTable() {
        colProduct.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getDescription()));
        colProduct.setCellFactory(TextFieldTableCell.forTableColumn());
        colProduct.setOnEditCommit(e -> { e.getRowValue().setDescription(e.getNewValue()); linesTable.refresh(); });

        colQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colQty.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
        colQty.setOnEditCommit(e -> { e.getRowValue().setQuantity(e.getNewValue()); updateTotals(); });

        colPrice.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
        colPrice.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
        colPrice.setOnEditCommit(e -> { e.getRowValue().setUnitPrice(e.getNewValue()); updateTotals(); });

        colTaxable.setCellValueFactory(cellData -> cellData.getValue().taxableProperty());
        colTaxable.setCellFactory(CheckBoxTableCell.forTableColumn(colTaxable));
        linesTable.getItems().addListener((javafx.collections.ListChangeListener.Change<? extends PurchaseOrderLine> c) -> updateTotals());

        colTaxRate.setCellValueFactory(new PropertyValueFactory<>("taxRate"));
        colTaxRate.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
        colTaxRate.setOnEditCommit(e -> { e.getRowValue().setTaxRate(e.getNewValue()); updateTotals(); });

        colTaxAmt.setCellValueFactory(new PropertyValueFactory<>("taxAmount"));
        colTotal.setCellValueFactory(new PropertyValueFactory<>("lineTotal"));
        
        linesTable.setItems(lines);
    }

    @FXML
    private void handleGlobalTaxToggle() {
        if (!editMode) {
            globalTaxCheck.setSelected(!globalTaxCheck.isSelected()); 
            showAlert("Locked", "Click Edit first.");
            return;
        }
        boolean applyTax = globalTaxCheck.isSelected();
        for (PurchaseOrderLine line : lines) {
            line.setTaxable(applyTax);
            if (applyTax) {
                line.setTaxRate(defaultTaxRate);
            }
        }
        linesTable.refresh();
        updateTotals();
    }

    private void setFormEditable(boolean editable) {
        this.editMode = editable;
        supplierSearchField.setDisable(!editable);
        orderDatePicker.setDisable(!editable);
        expectedDatePicker.setDisable(!editable);
        statusCombo.setDisable(!editable);
        linesTable.setEditable(editable);
        globalTaxCheck.setDisable(!editable);
    }

    private void loadPOToForm(PurchaseOrder po) {
        currentPO = po;
        poNumberField.setText(po.getPoNumber());
        orderDatePicker.setValue(po.getOrderDate());
        expectedDatePicker.setValue(po.getExpectedDate());
        statusCombo.setValue(po.getStatus());
        
        selectedSupplier = po.getSupplier();
        supplierSearchField.setText(po.getSupplierName() != null ? po.getSupplierName() : "");
        
        boolean allTaxed = po.getLines().stream().allMatch(PurchaseOrderLine::isTaxable);
        globalTaxCheck.setSelected(allTaxed);

        lines.setAll(po.getLines());
        updateTotals();
        setFormEditable(false);
    }

    @FXML
    private void handleEdit() {
        if (currentPO == null || currentPO.getId() <= 0) {
            showAlert("Notice", "Select a PO to edit."); return;
        }
        if ("RECEIVED".equalsIgnoreCase(currentPO.getStatus())) {
            showAlert("Locked", "Received POs cannot be edited. Create a GRN instead."); return;
        }
        setFormEditable(true);
    }

    @FXML
    private void handleNewPO() {
        currentPO = new PurchaseOrder();
        poNumberField.setText(poService.generateNextPoNumber());
        currentPO.setPoNumber(poNumberField.getText());
        orderDatePicker.setValue(LocalDate.now());
        expectedDatePicker.setValue(null);
        statusCombo.setValue("DRAFT");
        supplierSearchField.clear();
        selectedSupplier = null;
        globalTaxCheck.setSelected(true);
        lines.clear();
        updateTotals();
        
        setFormEditable(true);
        poListView.getSelectionModel().clearSelection();
    }

    @FXML
    private void handleAddLine() {
        if (!editMode) { showAlert("Locked", "Click Edit first."); return; }
        
        Dialog<Product> dialog = new Dialog<>();
        dialog.setTitle("Select Product");
        ButtonType addBtn = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addBtn, ButtonType.CANCEL);
        
        ListView<Product> lv = new ListView<>(FXCollections.observableArrayList(productService.listAll()));
        lv.setCellFactory(l -> new ListCell<>() {
            @Override protected void updateItem(Product p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : p.getProductCode() + " | " + p.getName());
            }
        });
        dialog.getDialogPane().setContent(lv);
        dialog.setResultConverter(btn -> btn == addBtn ? lv.getSelectionModel().getSelectedItem() : null);
        
        dialog.showAndWait().ifPresent(p -> {
            PurchaseOrderLine line = new PurchaseOrderLine();
            line.setProductId(p.getId());
            line.setDescription(p.getName());
            line.setQuantity(BigDecimal.ONE);
            line.setUnitPrice(p.getCostPrice() != null ? p.getCostPrice() : BigDecimal.ZERO);
            
            boolean applyTax = globalTaxCheck.isSelected();
            line.setTaxable(applyTax);
            line.setTaxRate(applyTax ? defaultTaxRate : BigDecimal.ZERO);
            
            line.recalculateTotal();
            lines.add(line);
            updateTotals();
        });
    }

    @FXML
    private void handleAddEmptyLine() {
        if (!editMode) { showAlert("Locked", "Click Edit first."); return; }
        PurchaseOrderLine line = new PurchaseOrderLine();
        line.setDescription("Custom Item");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(BigDecimal.ZERO);
        
        boolean applyTax = globalTaxCheck.isSelected();
        line.setTaxable(applyTax);
        line.setTaxRate(applyTax ? defaultTaxRate : BigDecimal.ZERO);
        
        line.recalculateTotal();
        lines.add(line);
        updateTotals();
    }

    @FXML
    private void handleRemoveLine() {
        if (!editMode) { showAlert("Locked", "Click Edit first."); return; }
        PurchaseOrderLine selected = linesTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            lines.remove(selected);
            updateTotals();
        }
    }

    private void updateTotals() {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;

        for (PurchaseOrderLine line : lines) {
            line.recalculateTotal();
            subtotal = subtotal.add(line.getQuantity().multiply(line.getUnitPrice()));
            tax = tax.add(line.getTaxAmount());
            total = total.add(line.getLineTotal());
        }

        subtotalLabel.setText("Subtotal: " + CurrencyFormatter.format(subtotal));
        taxLabel.setText("Tax: " + CurrencyFormatter.format(tax));
        totalLabel.setText("Grand Total: " + CurrencyFormatter.format(total));
        
        linesTable.refresh();
        if (currentPO != null) {
            currentPO.setSubtotal(subtotal);
            currentPO.setTaxAmount(tax);
            currentPO.setTotalAmount(total);
        }
    }

    @FXML
    private void handleSave() {
        if (!editMode) { showAlert("Locked", "Click Edit first."); return; }
        if (selectedSupplier == null) { showAlert("Error", "Select a supplier."); return; }
        if (lines.isEmpty()) { showAlert("Error", "Add at least one item."); return; }

        currentPO.setSupplier(selectedSupplier);
        currentPO.setSupplierId(selectedSupplier.getId());
        currentPO.setOrderDate(orderDatePicker.getValue());
        currentPO.setExpectedDate(expectedDatePicker.getValue());
        currentPO.setStatus(statusCombo.getValue());
        currentPO.setLines(lines);

        try {
            poService.savePurchaseOrder(currentPO);
            loadPOs();
            setFormEditable(false);
            showAlert("Success", "Purchase Order saved.");
        } catch (Exception e) {
            showAlert("Error", "Failed to save: " + e.getMessage());
        }
    }

    @FXML
    private void handleDelete() {
        if (currentPO == null || currentPO.getId() <= 0) return;
        if ("RECEIVED".equalsIgnoreCase(currentPO.getStatus())) {
            showAlert("Locked", "Cannot delete a Received PO."); return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete PO " + currentPO.getPoNumber() + "?");
        confirm.showAndWait().ifPresent(res -> {
            if (res == ButtonType.OK) {
                try {
                    poService.deletePurchaseOrder(currentPO.getId());
                    loadPOs();
                    handleNewPO();
                } catch (Exception e) {
                    showAlert("Error", "Failed to delete: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handlePrint() {
        if (currentPO == null || currentPO.getId() <= 0) {
            showAlert(Alert.AlertType.WARNING, "Not Saved", "Please save the purchase order first before printing.");
            return;
        }
        try {
            ReportService.printPurchaseOrder(currentPO.getId());
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Print Error", "Failed to generate purchase order report: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleQuickAddSupplier() {
        if (!editMode) { showAlert("Locked", "Click Edit first."); return; }
        
        Dialog<Supplier> dialog = new Dialog<>();
        dialog.setTitle("Quick Add Supplier");
        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(10));
        TextField nameField = new TextField(); nameField.setPromptText("Supplier Name *");
        grid.add(new Label("Name *:"), 0, 0); grid.add(nameField, 1, 0);
        dialog.getDialogPane().setContent(grid);
        
        dialog.setResultConverter(btn -> {
            if (btn != saveBtn || nameField.getText().trim().isEmpty()) return null;
            try {
                supplierService.addSupplier(nameField.getText().trim(), null, null, null, null, null, null, null);
                return supplierService.listAll().stream()
                        .filter(s -> s.getName().equalsIgnoreCase(nameField.getText().trim()))
                        .findFirst().orElse(null);
            } catch (Exception e) {
                showAlert("Error", e.getMessage()); return null;
            }
        });

        dialog.showAndWait().ifPresent(s -> {
            selectedSupplier = s;
            supplierSearchField.setText(s.getName());
        });
    }

    private void loadPOs() {
        allPOs.setAll(poService.getAllPurchaseOrders());
        poListView.setItems(allPOs);
    }

    private void showAlert(String title, String content) {
        showAlert(Alert.AlertType.INFORMATION, title, content);
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}