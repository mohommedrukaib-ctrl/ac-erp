package com.aceerp.controller;

import com.aceerp.model.Product;
import com.aceerp.model.PurchaseReturn;
import com.aceerp.model.PurchaseReturnLine;
import com.aceerp.service.ProductService;
import com.aceerp.service.PurchaseReturnService;
import com.aceerp.service.SupplierService;
import com.aceerp.util.CurrencyFormatter;
import com.aceerp.util.GlobalQuickAdd;
import com.aceerp.util.SmartSearchHelper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.VBox;
import javafx.util.converter.BigDecimalStringConverter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

public class PurchaseReturnController {

    public static class SimpleSupplier {
        private final int id;
        private final String name;
        public SimpleSupplier(int id, String name) { this.id = id; this.name = name; }
        public int getId() { return id; }
        public String getName() { return name; }
        @Override public String toString() { return name; }
    }

    @FXML private TextField searchField;
    @FXML private ListView<PurchaseReturn> returnListView;

    @FXML private TextField returnNumberField;
    @FXML private DatePicker returnDatePicker;
    @FXML private TextField supplierSearchField;
    @FXML private TextField reasonField;
    @FXML private Label lblTotalValuation;

    @FXML private TableView<PurchaseReturnLine> linesTable;
    @FXML private TableColumn<PurchaseReturnLine, String> colDescription;
    @FXML private TableColumn<PurchaseReturnLine, BigDecimal> colQty;
    @FXML private TableColumn<PurchaseReturnLine, BigDecimal> colCost;
    @FXML private TableColumn<PurchaseReturnLine, BigDecimal> colSetPrice;
    @FXML private TableColumn<PurchaseReturnLine, String> colDiscountType;
    @FXML private TableColumn<PurchaseReturnLine, BigDecimal> colDiscount;
    @FXML private TableColumn<PurchaseReturnLine, Boolean> colPackagePrice;
    @FXML private TableColumn<PurchaseReturnLine, BigDecimal> colTotal;

    private final PurchaseReturnService returnService = new PurchaseReturnService();
    private final ProductService productService = new ProductService();
    private final SupplierService supplierService = new SupplierService();

    private final ObservableList<PurchaseReturn> allReturns = FXCollections.observableArrayList();
    private final ObservableList<PurchaseReturnLine> lines = FXCollections.observableArrayList();
    private PurchaseReturn currentReturn;
    private SimpleSupplier selectedSupplier;

    @FXML
    public void initialize() {
        setupTable();
        setupListView();
        setupSupplierSearch();
        loadReturns();
        handleNewReturn();
    }

    private void setupSupplierSearch() {
        SmartSearchHelper.setupEntitySearch(
                supplierSearchField,
                query -> supplierService.listAll().stream()
                        .filter(s -> s.getName() != null
                                && s.getName().toLowerCase().contains(query.toLowerCase().trim()))
                        .map(s -> new SimpleSupplier(s.getId(), s.getName()))
                        .collect(Collectors.toList()),
                SimpleSupplier::getName,
                this::selectSupplier,
                typed -> GlobalQuickAdd.showSupplierDialog(typed,
                        supplier -> selectSupplier(new SimpleSupplier(supplier.getId(), supplier.getName()))),
                typed -> {
                    if (selectedSupplier != null && !selectedSupplier.getName().equalsIgnoreCase(typed.trim())) {
                        selectedSupplier = null;
                    }
                });
    }

    private void selectSupplier(SimpleSupplier supplier) {
        if (supplier == null) return;
        supplierSearchField.setText(supplier.getName());
        selectedSupplier = supplier;
    }

    private void setupTable() {
        linesTable.setEditable(true);

        colDescription.setCellValueFactory(new PropertyValueFactory<>("description"));
        colDescription.setCellFactory(TextFieldTableCell.forTableColumn());
        colDescription.setOnEditCommit(e -> {
            e.getRowValue().setDescription(e.getNewValue());
            linesTable.refresh();
        });

        colQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colQty.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
        colQty.setOnEditCommit(e -> {
            e.getRowValue().setQuantity(e.getNewValue());
            updateTotal();
        });

        com.aceerp.util.LinePricingTableColumns.configureUnitAndSetPrice(
                colCost, colSetPrice, PurchaseReturnLine::getUnitCost, PurchaseReturnLine::setUnitCost,
                PurchaseReturnLine::isPackagePrice, () -> linesTable.isEditable(), this::updateTotal);

        com.aceerp.util.LinePricingTableColumns.configureDiscountType(
                colDiscountType, PurchaseReturnLine::getDiscountType, PurchaseReturnLine::setDiscountType, this::updateTotal);
        com.aceerp.util.LinePricingTableColumns.configurePackagePrice(
                colPackagePrice, PurchaseReturnLine::isPackagePrice, PurchaseReturnLine::setPackagePrice, this::updateTotal);
        colDiscount.setCellValueFactory(new PropertyValueFactory<>("discountValue"));
        colDiscount.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
        colDiscount.setOnEditCommit(e -> {
            e.getRowValue().setDiscountValue(e.getNewValue() != null ? e.getNewValue() : BigDecimal.ZERO);
            updateTotal();
        });

        colTotal.setCellValueFactory(new PropertyValueFactory<>("lineTotal"));
        linesTable.setItems(lines);
    }

    private void setupListView() {
        returnListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(PurchaseReturn item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String supp = item.getSupplierName() != null ? item.getSupplierName() : "Supplier";
                    setText(item.getReturnNumber() + " | " + supp + " | " + CurrencyFormatter.format(item.getTotalAmount()));
                }
            }
        });

        returnListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadReturnToForm(newVal);
            }
        });

        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> filterReturns(newVal));
        }
    }

    private void filterReturns(String query) {
        if (query == null || query.isBlank()) {
            returnListView.setItems(allReturns);
            return;
        }
        String q = query.toLowerCase().trim();
        returnListView.setItems(FXCollections.observableArrayList(
                allReturns.stream().filter(r ->
                        (r.getReturnNumber() != null && r.getReturnNumber().toLowerCase().contains(q)) ||
                        (r.getSupplierName() != null && r.getSupplierName().toLowerCase().contains(q))
                ).collect(Collectors.toList())
        ));
    }

    private void loadReturns() {
        allReturns.setAll(returnService.getAllReturns());
        returnListView.setItems(allReturns);
    }

    private void loadReturnToForm(PurchaseReturn pr) {
        currentReturn = pr;
        returnNumberField.setText(pr.getReturnNumber());
        returnDatePicker.setValue(pr.getReturnDate());
        reasonField.setText(pr.getReason() != null ? pr.getReason() : "");
        supplierSearchField.setText(pr.getSupplierName() != null ? pr.getSupplierName() : "");
        selectedSupplier = new SimpleSupplier(pr.getSupplierId(), pr.getSupplierName());

        lines.clear();
        if (pr.getLines() != null) {
            lines.addAll(pr.getLines());
        }

        updateTotal();
        linesTable.setEditable(false);
        supplierSearchField.setDisable(true);
    }

    @FXML
    private void handleNewReturn() {
        currentReturn = new PurchaseReturn();
        currentReturn.setReturnNumber(returnService.generateNextReturnNumber());
        returnNumberField.setText(currentReturn.getReturnNumber());
        returnDatePicker.setValue(LocalDate.now());
        reasonField.clear();
        supplierSearchField.clear();
        selectedSupplier = null;
        lines.clear();

        linesTable.setEditable(true);
        supplierSearchField.setDisable(false);
        returnListView.getSelectionModel().clearSelection();

        updateTotal();
    }

    @FXML
    private void handleAddLine() {
        Dialog<Product> dialog = new Dialog<>();
        dialog.setTitle("Select Product to Return");
        dialog.setHeaderText("Search products in inventory");
        ButtonType addBtn = new ButtonType("Add to Return", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addBtn, ButtonType.CANCEL);

        VBox vbox = new VBox(10); vbox.setPadding(new Insets(10));
        TextField itemSearch = new TextField(); itemSearch.setPromptText("Search product...");
        ListView<Product> itemListView = new ListView<>(); itemListView.setPrefHeight(250);

        List<Product> products = productService.listAll();
        itemListView.setItems(FXCollections.observableArrayList(products));
        itemListView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Product p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) setText(null);
                else setText(p.getProductCode() + " | " + p.getName() + " [Stock: " + p.getStockQuantity() + "]");
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
            PurchaseReturnLine line = new PurchaseReturnLine();
            line.setProductId(product.getId());
            line.setDescription(product.getName());
            line.setQuantity(BigDecimal.ONE);
            line.setUnitCost(product.getSellingPrice() != null ? product.getSellingPrice() : BigDecimal.ZERO);
            line.recalculateTotal();

            lines.add(line);
            updateTotal();
        });
    }

    @FXML
    private void handleRemoveLine() {
        PurchaseReturnLine selected = linesTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            lines.remove(selected);
            updateTotal();
        }
    }

    private void updateTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseReturnLine line : lines) {
            line.recalculateTotal();
            if (line.getLineTotal() != null) {
                total = total.add(line.getLineTotal());
            }
        }
        linesTable.refresh();
        if (lblTotalValuation != null) lblTotalValuation.setText(CurrencyFormatter.format(total));
    }

    @FXML
    private void handleProcessReturn() {
        if (selectedSupplier == null || selectedSupplier.getId() <= 0) {
            showAlert(Alert.AlertType.WARNING, "Validation", "Please select a Supplier.");
            return;
        }

        if (lines.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Validation", "Cannot process empty purchase return.");
            return;
        }

        currentReturn.setSupplierId(selectedSupplier.getId());
        currentReturn.setSupplierName(selectedSupplier.getName());
        currentReturn.setReturnDate(returnDatePicker.getValue());
        currentReturn.setReason(reasonField.getText() != null ? reasonField.getText().trim() : "");
        currentReturn.setLines(lines);

        try {
            returnService.processReturnAndDeductStock(currentReturn);
            showAlert(Alert.AlertType.INFORMATION, "Success", "Purchase Return / Debit Note " + currentReturn.getReturnNumber() + " processed! Stock updated.");
            loadReturns();
            handleNewReturn();
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Error", ex.getMessage());
        }
    }

    @FXML
    private void handleRefresh() {
        loadReturns();
        handleNewReturn();
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}