package com.aceerp.controller;

import com.aceerp.model.GoodsReceivedNote;
import com.aceerp.model.GrnLine;
import com.aceerp.model.Product;
import com.aceerp.model.PurchaseOrder;
import com.aceerp.model.PurchaseOrderLine;
import com.aceerp.model.Supplier;
import com.aceerp.service.GoodsReceivedNoteService;
import com.aceerp.service.ProductService;
import com.aceerp.service.PurchaseOrderService;
import com.aceerp.service.SupplierService;
import com.aceerp.util.CurrencyFormatter;
import com.aceerp.util.GlobalQuickAdd;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.VBox;
import javafx.util.converter.BigDecimalStringConverter;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

public class GrnController {

    @FXML private TextField searchField;
    @FXML private ListView<GoodsReceivedNote> grnListView;

    @FXML private TextField grnNumberField;
    @FXML private DatePicker receivedDatePicker;
    @FXML private TextField poNumberField;
    @FXML private TextField supplierNameField;
    @FXML private TextField deliveryNoteRefField;
    @FXML private TextField notesField;
    @FXML private Label lblStatus;

    @FXML private TableView<GrnLine> linesTable;
    @FXML private TableColumn<GrnLine, String> colProductCode;
    @FXML private TableColumn<GrnLine, String> colDescription;
    @FXML private TableColumn<GrnLine, BigDecimal> colQtyOrdered;
    @FXML private TableColumn<GrnLine, BigDecimal> colQtyReceived;
    @FXML private TableColumn<GrnLine, BigDecimal> colUnitCost;
    @FXML private TableColumn<GrnLine, BigDecimal> colLineTotal;

    @FXML private Label lblTotalQty;
    @FXML private Label lblTotalValue;

    private final GoodsReceivedNoteService grnService = new GoodsReceivedNoteService();
    private final PurchaseOrderService poService = new PurchaseOrderService();
    private final ProductService productService = new ProductService();
    private final SupplierService supplierService = new SupplierService();

    private final ObservableList<GoodsReceivedNote> allGrns = FXCollections.observableArrayList();
    private final ObservableList<GrnLine> lines = FXCollections.observableArrayList();

    private GoodsReceivedNote currentGrn;
    private PurchaseOrder selectedPo;
    private Supplier selectedSupplier;
    private boolean editMode = true;

    @FXML
    public void initialize() {
        setupTable();
        setupListAndSelection();
        loadGrns();
        handleNewGrn();
    }

    private void setupTable() {
        linesTable.setEditable(true);

        colProductCode.setCellValueFactory(new PropertyValueFactory<>("productCode"));
        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));

        colQtyOrdered.setCellValueFactory(new PropertyValueFactory<>("quantityOrdered"));
        colQtyOrdered.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
        colQtyOrdered.setOnEditCommit(e -> {
            if (!editMode) { linesTable.refresh(); return; }
            e.getRowValue().setQuantityOrdered(e.getNewValue());
            e.getRowValue().recalculateTotal();
            updateTotals();
        });

        colQtyReceived.setCellValueFactory(new PropertyValueFactory<>("quantityReceived"));
        colQtyReceived.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
        colQtyReceived.setOnEditCommit(e -> {
            if (!editMode) { linesTable.refresh(); return; }
            e.getRowValue().setQuantityReceived(e.getNewValue());
            e.getRowValue().recalculateTotal();
            updateTotals();
        });

        colUnitCost.setCellValueFactory(new PropertyValueFactory<>("unitCost"));
        colUnitCost.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
        colUnitCost.setOnEditCommit(e -> {
            if (!editMode) { linesTable.refresh(); return; }
            e.getRowValue().setUnitCost(e.getNewValue());
            e.getRowValue().recalculateTotal();
            updateTotals();
        });

        colLineTotal.setCellValueFactory(new PropertyValueFactory<>("lineTotal"));

        linesTable.setItems(lines);
    }

    private void setupListAndSelection() {
        grnListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(GoodsReceivedNote item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String supp = item.getSupplier() != null ? item.getSupplier().getName() : "Unknown Supplier";
                    String dateStr = item.getReceivedDate() != null ? item.getReceivedDate().toString() : "";
                    setText(item.getGrnNumber() + " | " + supp + " | " + dateStr + " | " + CurrencyFormatter.format(item.getTotalValue()));
                }
            }
        });

        grnListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadGrnToForm(newVal);
            }
        });

        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> filterGrns(newVal));
        }
    }

    private void filterGrns(String query) {
        if (query == null || query.isBlank()) {
            grnListView.setItems(allGrns);
            return;
        }
        String q = query.toLowerCase().trim();
        List<GoodsReceivedNote> filtered = allGrns.stream()
                .filter(g -> (g.getGrnNumber() != null && g.getGrnNumber().toLowerCase().contains(q)) ||
                             (g.getSupplier() != null && g.getSupplier().getName() != null && g.getSupplier().getName().toLowerCase().contains(q)) ||
                             (g.getDeliveryNoteRef() != null && g.getDeliveryNoteRef().toLowerCase().contains(q)))
                .collect(Collectors.toList());
        grnListView.setItems(FXCollections.observableArrayList(filtered));
    }

    private void loadGrns() {
        allGrns.setAll(grnService.getAllGrns());
        grnListView.setItems(allGrns);
    }

    private void loadGrnToForm(GoodsReceivedNote grn) {
        currentGrn = grn;
        selectedPo = null;
        selectedSupplier = grn.getSupplier();

        grnNumberField.setText(grn.getGrnNumber());
        receivedDatePicker.setValue(grn.getReceivedDate() != null ? grn.getReceivedDate() : LocalDate.now());
        poNumberField.setText(grn.getPoId() > 0 ? "PO #" + grn.getPoId() : "None (direct receipt)");
        supplierNameField.setText(grn.getSupplier() != null ? grn.getSupplier().getName() : "N/A");
        deliveryNoteRefField.setText(grn.getDeliveryNoteRef() != null ? grn.getDeliveryNoteRef() : "");
        notesField.setText(grn.getNotes() != null ? grn.getNotes() : "");

        lblStatus.setText(grn.getStatus() != null ? grn.getStatus() : "COMPLETED");

        lines.clear();
        if (grn.getLines() != null) {
            lines.addAll(grn.getLines());
        }

        updateTotals();
        setFormEditable(false);
    }

    private void setFormEditable(boolean editable) {
        this.editMode = editable;
        receivedDatePicker.setDisable(!editable);
        deliveryNoteRefField.setEditable(editable);
        notesField.setEditable(editable);
        linesTable.setEditable(editable);
    }

    private void applySupplier(Supplier supplier) {
        if (supplier == null) return;
        selectedSupplier = supplier;
        currentGrn.setSupplierId(supplier.getId());
        currentGrn.setSupplier(supplier);
        supplierNameField.setText(supplier.getName());
    }

    @FXML
    public void handleNewGrn() {
        currentGrn = new GoodsReceivedNote();
        currentGrn.setGrnNumber(grnService.generateNextGrnNumber());
        currentGrn.setReceivedDate(LocalDate.now());
        selectedPo = null;
        selectedSupplier = null;

        grnNumberField.setText(currentGrn.getGrnNumber());
        receivedDatePicker.setValue(currentGrn.getReceivedDate());
        poNumberField.setText("");
        poNumberField.setPromptText("Optional — none required");
        supplierNameField.clear();
        deliveryNoteRefField.clear();
        notesField.clear();
        lblStatus.setText("NEW");

        lines.clear();
        grnListView.getSelectionModel().clearSelection();

        setFormEditable(true);
        updateTotals();
    }

    @FXML
    public void handleRefresh() {
        loadGrns();
        handleNewGrn();
    }

    @FXML
    public void handleSelectPo() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click + New GRN to create a new stock receipt.");
            return;
        }

        Dialog<PurchaseOrder> dialog = new Dialog<>();
        dialog.setTitle("Select Purchase Order (Optional)");
        dialog.setHeaderText("Choose an open PO to receive against — or cancel and receive directly");

        ButtonType importBtn = new ButtonType("Import PO", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(importBtn, ButtonType.CANCEL);

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));
        TextField poSearch = new TextField();
        poSearch.setPromptText("Search PO number or supplier...");
        ListView<PurchaseOrder> poListView = new ListView<>();
        poListView.setPrefHeight(250);

        List<PurchaseOrder> openPos = poService.getAllPurchaseOrders().stream()
                .filter(po -> !"RECEIVED".equalsIgnoreCase(po.getStatus()) && !"CANCELLED".equalsIgnoreCase(po.getStatus()))
                .collect(Collectors.toList());

        poListView.setItems(FXCollections.observableArrayList(openPos));
        poListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(PurchaseOrder po, boolean empty) {
                super.updateItem(po, empty);
                if (empty || po == null) {
                    setText(null);
                } else {
                    String supp = po.getSupplier() != null ? po.getSupplier().getName() : "Unknown";
                    setText(po.getPoNumber() + " | " + supp + " | Status: " + po.getStatus());
                }
            }
        });

        poSearch.textProperty().addListener((obs, old, newVal) -> {
            if (newVal == null || newVal.isBlank()) {
                poListView.setItems(FXCollections.observableArrayList(openPos));
            } else {
                String q = newVal.toLowerCase().trim();
                poListView.setItems(FXCollections.observableArrayList(
                        openPos.stream().filter(p ->
                                (p.getPoNumber() != null && p.getPoNumber().toLowerCase().contains(q)) ||
                                (p.getSupplier() != null && p.getSupplier().getName() != null && p.getSupplier().getName().toLowerCase().contains(q))
                        ).collect(Collectors.toList())
                ));
            }
        });

        vbox.getChildren().addAll(poSearch, poListView);
        dialog.getDialogPane().setContent(vbox);
        dialog.setResultConverter(btn -> btn == importBtn ? poListView.getSelectionModel().getSelectedItem() : null);

        dialog.showAndWait().ifPresent(po -> {
            selectedPo = po;
            currentGrn.setPoId(po.getId());
            applySupplier(po.getSupplier());
            if (po.getSupplierId() > 0 && (selectedSupplier == null || selectedSupplier.getId() <= 0)) {
                currentGrn.setSupplierId(po.getSupplierId());
            }

            poNumberField.setText(po.getPoNumber());

            lines.clear();
            if (po.getLines() != null) {
                for (PurchaseOrderLine poLine : po.getLines()) {
                    GrnLine line = new GrnLine();
                    line.setPoLineId(poLine.getId());
                    if (poLine.getProduct() != null) {
                        line.setProduct(poLine.getProduct());
                    } else if (poLine.getProductId() != null && poLine.getProductId() > 0) {
                        line.setProductId(poLine.getProductId());
                        line.setDescription(poLine.getDescription());
                    } else {
                        line.setDescription(poLine.getDescription());
                    }
                    line.setQuantityOrdered(poLine.getQuantity() != null ? poLine.getQuantity() : BigDecimal.ONE);
                    line.setQuantityReceived(line.getQuantityOrdered());
                    line.setUnitCost(poLine.getUnitPrice() != null ? poLine.getUnitPrice() : BigDecimal.ZERO);
                    line.recalculateTotal();
                    lines.add(line);
                }
            }
            updateTotals();
        });
    }

    @FXML
    public void handleSelectSupplier() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click + New GRN to create a new stock receipt.");
            return;
        }

        Dialog<Supplier> dialog = new Dialog<>();
        dialog.setTitle("Select Supplier");
        dialog.setHeaderText("Choose supplier for this goods receipt (PO not required)");

        ButtonType selectBtn = new ButtonType("Select", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(selectBtn, ButtonType.CANCEL);

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));
        TextField search = new TextField();
        search.setPromptText("Search supplier name or phone...");
        ListView<Supplier> listView = new ListView<>();
        listView.setPrefHeight(250);

        List<Supplier> suppliers = supplierService.listAll();
        listView.setItems(FXCollections.observableArrayList(suppliers));
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Supplier s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) {
                    setText(null);
                } else {
                    String phone = s.getPhone() != null ? s.getPhone() : "";
                    setText(s.getName() + (phone.isBlank() ? "" : " | " + phone));
                }
            }
        });

        search.textProperty().addListener((obs, old, newVal) -> {
            if (newVal == null || newVal.isBlank()) {
                listView.setItems(FXCollections.observableArrayList(suppliers));
            } else {
                String q = newVal.toLowerCase().trim();
                listView.setItems(FXCollections.observableArrayList(
                        suppliers.stream().filter(s ->
                                (s.getName() != null && s.getName().toLowerCase().contains(q)) ||
                                (s.getPhone() != null && s.getPhone().toLowerCase().contains(q)) ||
                                (s.getEmail() != null && s.getEmail().toLowerCase().contains(q))
                        ).collect(Collectors.toList())
                ));
            }
        });

        vbox.getChildren().addAll(search, listView);
        dialog.getDialogPane().setContent(vbox);
        dialog.setResultConverter(btn -> btn == selectBtn ? listView.getSelectionModel().getSelectedItem() : null);

        dialog.showAndWait().ifPresent(this::applySupplier);
    }

    @FXML
    public void handleQuickAddSupplier() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click + New GRN to create a new stock receipt.");
            return;
        }

        GlobalQuickAdd.showSupplierDialog(() -> {
            List<Supplier> suppliers = supplierService.listAll();
            if (!suppliers.isEmpty()) {
                Supplier newest = suppliers.get(suppliers.size() - 1);
                for (Supplier s : suppliers) {
                    if (s.getId() > newest.getId()) {
                        newest = s;
                    }
                }
                applySupplier(newest);
            }
        });
    }

    @FXML
    public void handleAddLine() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click + New GRN to create a stock receipt.");
            return;
        }

        Dialog<Product> dialog = new Dialog<>();
        dialog.setTitle("Select Product");
        dialog.setHeaderText("Select product to receive into stock");
        ButtonType addBtn = new ButtonType("Add Line", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addBtn, ButtonType.CANCEL);

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));
        TextField itemSearch = new TextField();
        itemSearch.setPromptText("Search by code or name...");
        ListView<Product> itemListView = new ListView<>();
        itemListView.setPrefHeight(250);

        List<Product> products = productService.listAll();
        itemListView.setItems(FXCollections.observableArrayList(products));
        itemListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Product p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) setText(null);
                else setText(p.getProductCode() + " | " + p.getName() + " [Current Stock: " + p.getStockQuantity() + "]");
            }
        });

        itemSearch.textProperty().addListener((obs, old, newVal) -> {
            if (newVal == null || newVal.isBlank()) itemListView.setItems(FXCollections.observableArrayList(products));
            else {
                String q = newVal.toLowerCase().trim();
                itemListView.setItems(FXCollections.observableArrayList(
                        products.stream().filter(p ->
                                (p.getName() != null && p.getName().toLowerCase().contains(q)) ||
                                (p.getProductCode() != null && p.getProductCode().toLowerCase().contains(q))
                        ).collect(Collectors.toList())
                ));
            }
        });

        vbox.getChildren().addAll(itemSearch, itemListView);
        dialog.getDialogPane().setContent(vbox);
        dialog.setResultConverter(btn -> btn == addBtn ? itemListView.getSelectionModel().getSelectedItem() : null);

        dialog.showAndWait().ifPresent(product -> {
            GrnLine line = new GrnLine();
            line.setProduct(product);
            line.setQuantityOrdered(BigDecimal.ONE);
            line.setQuantityReceived(BigDecimal.ONE);

            BigDecimal cost = BigDecimal.ZERO;
            try {
                Method getCost = product.getClass().getMethod("getCostPrice");
                Object val = getCost.invoke(product);
                if (val instanceof BigDecimal b) cost = b;
            } catch (Exception ignored) {
                cost = product.getSellingPrice() != null ? product.getSellingPrice() : BigDecimal.ZERO;
            }
            line.setUnitCost(cost);
            line.recalculateTotal();

            lines.add(line);
            updateTotals();
        });
    }

    @FXML
    public void handleQuickAddProduct() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click + New GRN to create a stock receipt.");
            return;
        }

        GlobalQuickAdd.showProductDialog(() -> {
            List<Product> products = productService.listAll();
            if (!products.isEmpty()) {
                Product newest = products.get(products.size() - 1);
                for (Product p : products) {
                    if (p.getId() > newest.getId()) {
                        newest = p;
                    }
                }

                GrnLine line = new GrnLine();
                line.setProduct(newest);
                line.setQuantityOrdered(BigDecimal.ONE);
                line.setQuantityReceived(BigDecimal.ONE);

                BigDecimal cost = BigDecimal.ZERO;
                try {
                    Method getCost = newest.getClass().getMethod("getCostPrice");
                    Object val = getCost.invoke(newest);
                    if (val instanceof BigDecimal b) cost = b;
                } catch (Exception ignored) {
                    cost = newest.getSellingPrice() != null ? newest.getSellingPrice() : BigDecimal.ZERO;
                }
                line.setUnitCost(cost);
                line.recalculateTotal();

                lines.add(line);
                updateTotals();
            }
        });
    }

    @FXML
    public void handleRemoveLine() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Cannot edit posted GRN.");
            return;
        }
        GrnLine selected = linesTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            lines.remove(selected);
            updateTotals();
        } else {
            showAlert(Alert.AlertType.INFORMATION, "Selection Required", "Please select a line item from the table to remove.");
        }
    }

    @FXML
    public void handleEdit() {
        if (currentGrn != null && currentGrn.getId() > 0) {
            showAlert(Alert.AlertType.INFORMATION, "Posted GRN",
                    "Posted Goods Received Notes cannot be edited because inventory movements have already been committed.");
        }
    }

    @FXML
    public void handleSaveAndPost() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click + New GRN to receive stock.");
            return;
        }

        if (lines.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Validation Error", "Cannot post GRN with no line items. Use + Select Product or + Quick Add New Product.");
            return;
        }

        if (currentGrn.getSupplierId() <= 0 && selectedSupplier != null && selectedSupplier.getId() > 0) {
            currentGrn.setSupplierId(selectedSupplier.getId());
            currentGrn.setSupplier(selectedSupplier);
        }
        if (currentGrn.getSupplierId() <= 0 && selectedPo != null && selectedPo.getSupplierId() > 0) {
            currentGrn.setSupplierId(selectedPo.getSupplierId());
            currentGrn.setSupplier(selectedPo.getSupplier());
        }

        if (currentGrn.getSupplierId() <= 0) {
            showAlert(Alert.AlertType.WARNING, "Validation Error",
                    "Please select a Supplier (or Quick Add one). A Purchase Order is optional.");
            return;
        }

        currentGrn.setReceivedDate(receivedDatePicker.getValue() != null ? receivedDatePicker.getValue() : LocalDate.now());
        currentGrn.setDeliveryNoteRef(deliveryNoteRefField.getText() != null ? deliveryNoteRefField.getText().trim() : "");
        currentGrn.setNotes(notesField.getText() != null ? notesField.getText().trim() : "");
        currentGrn.setLines(lines);

        try {
            grnService.receiveStock(currentGrn);
            showAlert(Alert.AlertType.INFORMATION, "Stock Posted",
                    "Goods Received Note " + currentGrn.getGrnNumber() + " saved and stock quantities updated successfully!");
            loadGrns();
            setFormEditable(false);
            lblStatus.setText("COMPLETED");
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Post Failed", "Could not post GRN: " + ex.getMessage());
        }
    }

    @FXML
    public void handlePrint() {
        if (currentGrn == null || currentGrn.getId() <= 0) {
            showAlert(Alert.AlertType.WARNING, "Print Error", "Please select a saved, posted GRN to print.");
            return;
        }
        showAlert(Alert.AlertType.INFORMATION, "Print Preview", "Printing Goods Received Note #" + currentGrn.getGrnNumber());
    }

    private void updateTotals() {
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalVal = BigDecimal.ZERO;

        for (GrnLine line : lines) {
            line.recalculateTotal();
            if (line.getQuantityReceived() != null) {
                totalQty = totalQty.add(line.getQuantityReceived());
            }
            if (line.getLineTotal() != null) {
                totalVal = totalVal.add(line.getLineTotal());
            }
        }

        linesTable.refresh();
        if (lblTotalQty != null) lblTotalQty.setText(totalQty.setScale(2, java.math.RoundingMode.HALF_UP).toString());
        if (lblTotalValue != null) lblTotalValue.setText(CurrencyFormatter.format(totalVal));
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}