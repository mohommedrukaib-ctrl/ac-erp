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
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.GridPane;
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
    @FXML private TableColumn<PurchaseOrderLine, BigDecimal> colDiscount;
    @FXML private TableColumn<PurchaseOrderLine, Boolean> colTaxable;
    @FXML private TableColumn<PurchaseOrderLine, BigDecimal> colTaxRate;
    @FXML private TableColumn<PurchaseOrderLine, BigDecimal> colTaxAmt;
    @FXML private TableColumn<PurchaseOrderLine, String> colNotes;
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
    
    private boolean editMode = false;

    @FXML
    public void initialize() {
        loadDefaultTaxRate();
        setupSmartSearch();
        setupTable();
        setupListView();
        com.aceerp.util.QuickBooksContextMenus.installListView(poListView, order -> java.util.List.of(
            com.aceerp.util.QuickBooksContextMenus.action("Edit", this::handleEdit),
            com.aceerp.util.QuickBooksContextMenus.action("Print", this::handlePrint)
        ));
        
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
                defaultTaxRate = new BigDecimal("15.00");
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
        com.aceerp.util.SmartSearchHelper.setupEntitySearch(
                supplierSearchField,
                query -> supplierService.listAll().stream()
                        .filter(s -> s.getName() != null
                                && s.getName().toLowerCase().contains(query.toLowerCase().trim()))
                        .collect(Collectors.toList()),
                Supplier::getName,
                this::selectSupplier,
                typed -> com.aceerp.util.GlobalQuickAdd.showSupplierDialog(typed, this::selectSupplier),
                typed -> {
                    if (selectedSupplier != null && (selectedSupplier.getName() == null
                            || !selectedSupplier.getName().equalsIgnoreCase(typed.trim()))) {
                        selectedSupplier = null;
                    }
                });
    }

    private void selectSupplier(Supplier supplier) {
        if (supplier == null) return;
        supplierSearchField.setText(supplier.getName());
        selectedSupplier = supplier;
    }

    private void setupTable() {
        colProduct.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getDescription()));
        colProduct.setCellFactory(TextFieldTableCell.forTableColumn());
        colProduct.setOnEditCommit(e -> {
            if (!editMode) { linesTable.refresh(); return; }
            e.getRowValue().setDescription(e.getNewValue());
            linesTable.refresh();
        });

        colQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colQty.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
        colQty.setOnEditCommit(e -> {
            if (!editMode) { linesTable.refresh(); return; }
            e.getRowValue().setQuantity(e.getNewValue());
            updateTotals();
        });

        colPrice.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
        colPrice.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
        colPrice.setOnEditCommit(e -> {
            if (!editMode) { linesTable.refresh(); return; }
            e.getRowValue().setUnitPrice(e.getNewValue());
            updateTotals();
        });

        if (colDiscount != null) {
            colDiscount.setCellValueFactory(new PropertyValueFactory<>("discountPercent"));
            colDiscount.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
            colDiscount.setOnEditCommit(e -> {
                if (!editMode) { linesTable.refresh(); return; }
                e.getRowValue().setDiscountPercent(e.getNewValue() != null ? e.getNewValue() : BigDecimal.ZERO);
                updateTotals();
            });
        }

        colTaxable.setCellValueFactory(cellData -> cellData.getValue().taxableProperty());
        colTaxable.setCellFactory(CheckBoxTableCell.forTableColumn(colTaxable));
        linesTable.getItems().addListener((javafx.collections.ListChangeListener.Change<? extends PurchaseOrderLine> c) -> updateTotals());

        colTaxRate.setCellValueFactory(new PropertyValueFactory<>("taxRate"));
        colTaxRate.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
        colTaxRate.setOnEditCommit(e -> {
            if (!editMode) { linesTable.refresh(); return; }
            e.getRowValue().setTaxRate(e.getNewValue());
            updateTotals();
        });

        colTaxAmt.setCellValueFactory(new PropertyValueFactory<>("taxAmount"));

        if (colNotes != null) {
            colNotes.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getNotes() != null ? d.getValue().getNotes() : ""
            ));
            colNotes.setCellFactory(TextFieldTableCell.forTableColumn());
            colNotes.setOnEditCommit(e -> {
                if (!editMode) { linesTable.refresh(); return; }
                e.getRowValue().setNotes(e.getNewValue());
                linesTable.refresh();
            });
        }

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

    @FXML
    private void handleMoveUp() {
        if (!editMode) {
            showAlert("Locked", "Click Edit first.");
            return;
        }
        int idx = linesTable.getSelectionModel().getSelectedIndex();
        if (idx > 0) {
            PurchaseOrderLine line = lines.remove(idx);
            lines.add(idx - 1, line);
            linesTable.getSelectionModel().select(idx - 1);
        }
    }

    @FXML
    private void handleMoveDown() {
        if (!editMode) {
            showAlert("Locked", "Click Edit first.");
            return;
        }
        int idx = linesTable.getSelectionModel().getSelectedIndex();
        if (idx >= 0 && idx < lines.size() - 1) {
            PurchaseOrderLine line = lines.remove(idx);
            lines.add(idx + 1, line);
            linesTable.getSelectionModel().select(idx + 1);
        }
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
        
        supplierSearchField.setText(po.getSupplierName() != null ? po.getSupplierName() : "");
        selectedSupplier = po.getSupplier();
        
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
            line.setDiscountPercent(BigDecimal.ZERO);
            line.setNotes("");
            
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
        line.setDiscountPercent(BigDecimal.ZERO);
        line.setNotes("");
        
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
            // Net of discount, before tax
            BigDecimal qty = line.getQuantity() != null ? line.getQuantity() : BigDecimal.ZERO;
            BigDecimal price = line.getUnitPrice() != null ? line.getUnitPrice() : BigDecimal.ZERO;
            BigDecimal disc = line.getDiscountPercent() != null ? line.getDiscountPercent() : BigDecimal.ZERO;
            BigDecimal gross = qty.multiply(price);
            BigDecimal net = gross.subtract(gross.multiply(disc).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP));
            subtotal = subtotal.add(net);
            tax = tax.add(line.getTaxAmount() != null ? line.getTaxAmount() : BigDecimal.ZERO);
            total = total.add(line.getLineTotal() != null ? line.getLineTotal() : BigDecimal.ZERO);
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
        com.aceerp.util.GlobalQuickAdd.showSupplierDialog(supplierSearchField.getText(), this::selectSupplier);
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