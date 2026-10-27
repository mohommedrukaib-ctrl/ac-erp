package com.aceerp.controller;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.GoodsReceivedNote;
import com.aceerp.model.GrnLine;
import com.aceerp.model.Product;
import com.aceerp.model.SupplierBill;
import com.aceerp.model.SupplierBillLine;
import com.aceerp.service.GoodsReceivedNoteService;
import com.aceerp.service.ProductService;
import com.aceerp.service.SupplierBillService;
import com.aceerp.util.AlertHelper;
import com.aceerp.util.CurrencyFormatter;
import com.aceerp.util.TaxUtil;
import javafx.beans.property.SimpleBooleanProperty;
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
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.util.converter.BigDecimalStringConverter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SupplierBillController {

    public static class SimpleSupplier {
        private int id;
        private String name;
        private String phone;

        public SimpleSupplier(int id, String name, String phone) {
            this.id = id;
            this.name = name;
            this.phone = phone;
        }

        public int getId() { return id; }
        public String getName() { return name; }
        public String getPhone() { return phone; }
        @Override public String toString() { return name; }
    }

    @FXML private TextField searchField;
    @FXML private ListView<SupplierBill> supplierBillListView;

    @FXML private TextField billNumberField;
    @FXML private DatePicker billDatePicker;
    @FXML private DatePicker dueDatePicker;
    @FXML private TextField supplierSearchField;
    @FXML private TextField supplierPhoneField;
    @FXML private TextField supplierInvoiceRefField;
    @FXML private ComboBox<String> statusCombo;
    @FXML private TextField notesField;
    @FXML private Label lblStatus;

    @FXML private TableView<SupplierBillLine> linesTable;
    @FXML private TableColumn<SupplierBillLine, String> colProduct;
    @FXML private TableColumn<SupplierBillLine, BigDecimal> colQty;
    @FXML private TableColumn<SupplierBillLine, BigDecimal> colPrice;
    @FXML private TableColumn<SupplierBillLine, Boolean> colTaxable;
    @FXML private TableColumn<SupplierBillLine, BigDecimal> colTotal;

    @FXML private Label totalLabel;
    @FXML private Label lblSubtotal;
    @FXML private Label lblTax;
    @FXML private Label lblTaxTitle;
    @FXML private CheckBox globalTaxCheck;
    @FXML private VBox detailsPane;

    private final SupplierBillService billService = new SupplierBillService();
    private final ProductService productService = new ProductService();
    private final GoodsReceivedNoteService grnService = new GoodsReceivedNoteService();

    private final ObservableList<SupplierBill> allBills = FXCollections.observableArrayList();
    private final ObservableList<SupplierBillLine> lines = FXCollections.observableArrayList();

    private SupplierBill currentBill;
    private SimpleSupplier selectedSupplier;
    private boolean editMode = true;

    private Popup supplierPopup;
    private ListView<SimpleSupplier> supplierListView;

    @FXML
    public void initialize() {
        setupSmartSearch();
        setupTable();
        setupListView();

        statusCombo.setItems(FXCollections.observableArrayList("UNPAID", "PARTIALLY_PAID", "PAID", "CANCELLED"));

        if (globalTaxCheck != null) {
            globalTaxCheck.setSelected(TaxUtil.isTaxEnabled());
        }
        if (lblTaxTitle != null) {
            lblTaxTitle.setText(TaxUtil.getTaxName() + ":");
        }

        loadBills();
        handleNewBill();
    }

    private void setupListView() {
        supplierBillListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(SupplierBill item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String suppName = item.getSupplierName() != null ? item.getSupplierName() : "Unknown Supplier";
                    String status = item.getStatus() != null ? item.getStatus() : "UNPAID";
                    setText(item.getBillNumber() + " | " + suppName + " | " + status + " | " + CurrencyFormatter.format(item.getTotalAmount()));
                }
            }
        });

        supplierBillListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadBillToForm(newVal);
            }
        });

        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> filterBills(newVal));
        }
    }

    private void filterBills(String query) {
        if (query == null || query.isBlank()) {
            supplierBillListView.setItems(allBills);
            return;
        }
        String q = query.toLowerCase().trim();
        List<SupplierBill> filtered = allBills.stream()
                .filter(b -> (b.getBillNumber() != null && b.getBillNumber().toLowerCase().contains(q)) ||
                             (b.getSupplierName() != null && b.getSupplierName().toLowerCase().contains(q)) ||
                             (b.getSupplierInvoiceRef() != null && b.getSupplierInvoiceRef().toLowerCase().contains(q)))
                .collect(Collectors.toList());
        supplierBillListView.setItems(FXCollections.observableArrayList(filtered));
    }

    private void setupSmartSearch() {
        supplierPopup = new Popup();
        supplierPopup.setAutoHide(true);
        supplierListView = new ListView<>();
        supplierListView.setPrefHeight(150);
        supplierPopup.getContent().add(supplierListView);

        supplierSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!editMode) return;
            if (newVal == null || newVal.trim().isEmpty()) {
                supplierPopup.hide();
                return;
            }
            String query = newVal.toLowerCase().trim();
            List<SimpleSupplier> results = listSuppliers().stream()
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
            SimpleSupplier s = supplierListView.getSelectionModel().getSelectedItem();
            if (s != null) {
                selectSupplier(s);
            }
        });
    }

    private void selectSupplier(SimpleSupplier s) {
        selectedSupplier = s;
        supplierSearchField.setText(s.getName());
        supplierPhoneField.setText(s.getPhone() != null && !s.getPhone().isBlank() ? s.getPhone() : "N/A");
        supplierPopup.hide();
    }

    private List<SimpleSupplier> listSuppliers() {
        List<SimpleSupplier> list = new ArrayList<>();
        String sql = "SELECT id, name, phone FROM suppliers ORDER BY name ASC";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(new SimpleSupplier(rs.getInt("id"), rs.getString("name"), rs.getString("phone")));
            }
        } catch (SQLException ignored) {}
        return list;
    }

    private void setupTable() {
        linesTable.setEditable(true);

        colProduct.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
                d.getValue().getDescription() != null ? d.getValue().getDescription() : "Custom Item"
        ));
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
            updateTotal();
        });

        colPrice.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
        colPrice.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
        colPrice.setOnEditCommit(e -> {
            if (!editMode) { linesTable.refresh(); return; }
            e.getRowValue().setUnitPrice(e.getNewValue());
            updateTotal();
        });

        if (colTaxable != null) {
            colTaxable.setCellValueFactory(cellData -> {
                SupplierBillLine line = cellData.getValue();
                SimpleBooleanProperty prop = new SimpleBooleanProperty(line.isTaxable());
                prop.addListener((obs, was, isNow) -> {
                    if (!editMode) {
                        linesTable.refresh();
                        return;
                    }
                    line.setTaxable(isNow);
                    updateTotal();
                    linesTable.refresh();
                });
                return prop;
            });
            colTaxable.setCellFactory(CheckBoxTableCell.forTableColumn(colTaxable));
            colTaxable.setEditable(true);
        }

        colTotal.setCellValueFactory(new PropertyValueFactory<>("totalPrice"));
        linesTable.setItems(lines);
    }

    private void loadBills() {
        allBills.setAll(billService.listAll());
        supplierBillListView.setItems(allBills);
    }

    private void loadBillToForm(SupplierBill bill) {
        currentBill = bill;
        billNumberField.setText(bill.getBillNumber());
        billDatePicker.setValue(bill.getBillDate() != null ? bill.getBillDate() : LocalDate.now());
        dueDatePicker.setValue(bill.getDueDate() != null ? bill.getDueDate() : LocalDate.now().plusDays(30));
        supplierInvoiceRefField.setText(bill.getSupplierInvoiceRef() != null ? bill.getSupplierInvoiceRef() : "");
        statusCombo.setValue(bill.getStatus() != null ? bill.getStatus() : "UNPAID");
        notesField.setText(bill.getNotes() != null ? bill.getNotes() : "");

        if (lblStatus != null) {
            lblStatus.setText(bill.getStatus() != null ? bill.getStatus() : "UNPAID");
        }

        if (bill.getSupplierId() > 0) {
            selectedSupplier = listSuppliers().stream().filter(s -> s.getId() == bill.getSupplierId()).findFirst().orElse(null);
        } else {
            selectedSupplier = null;
        }

        if (selectedSupplier != null) {
            supplierSearchField.setText(selectedSupplier.getName());
            supplierPhoneField.setText(selectedSupplier.getPhone() != null ? selectedSupplier.getPhone() : "N/A");
        } else if (bill.getSupplierName() != null) {
            supplierSearchField.setText(bill.getSupplierName());
            supplierPhoneField.setText("N/A");
        } else {
            supplierSearchField.clear();
            supplierPhoneField.clear();
        }

        lines.clear();
        if (bill.getLines() != null) {
            lines.addAll(bill.getLines());
        }

        if (globalTaxCheck != null && !lines.isEmpty()) {
            boolean allTaxable = lines.stream().allMatch(SupplierBillLine::isTaxable);
            globalTaxCheck.setSelected(allTaxable);
        }

        updateTotal();
        setFormEditable(false);
    }

    private boolean isBillPaid(SupplierBill bill) {
        if (bill == null) return false;
        if ("PAID".equalsIgnoreCase(bill.getStatus())) return true;
        if (bill.getPaidAmount() != null && bill.getTotalAmount() != null && bill.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
            return bill.getPaidAmount().compareTo(bill.getTotalAmount()) >= 0;
        }
        return false;
    }

    private void setFormEditable(boolean editable) {
        if (editable && isBillPaid(currentBill)) {
            editMode = false;
        } else {
            editMode = editable;
        }
        supplierSearchField.setDisable(!editMode);
        billDatePicker.setDisable(!editMode);
        dueDatePicker.setDisable(!editMode);
        supplierInvoiceRefField.setEditable(editMode);
        statusCombo.setDisable(!editMode);
        notesField.setEditable(editMode);
        linesTable.setEditable(editMode);
        if (globalTaxCheck != null) {
            globalTaxCheck.setDisable(!editMode);
        }
    }

    @FXML
    private void handleNewBill() {
        currentBill = new SupplierBill();
        currentBill.setBillNumber(billService.generateNextBillNumber());
        billNumberField.setText(currentBill.getBillNumber());
        billDatePicker.setValue(LocalDate.now());
        dueDatePicker.setValue(LocalDate.now().plusDays(30));
        statusCombo.setValue("UNPAID");
        supplierInvoiceRefField.clear();
        notesField.clear();
        supplierSearchField.clear();
        supplierPhoneField.clear();
        selectedSupplier = null;

        lines.clear();
        supplierBillListView.getSelectionModel().clearSelection();

        if (globalTaxCheck != null) {
            globalTaxCheck.setSelected(TaxUtil.isTaxEnabled());
        }

        setFormEditable(true);
        updateTotal();
    }

    @FXML
    private void handleRefresh() {
        loadBills();
        handleNewBill();
    }

    @FXML
    private void handleEdit() {
        if (currentBill == null || currentBill.getId() <= 0) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a Supplier Bill from the list first.");
            return;
        }
        if (isBillPaid(currentBill)) {
            showAlert(Alert.AlertType.WARNING, "Bill Locked", "This bill is PAID and cannot be edited.");
            setFormEditable(false);
            return;
        }
        setFormEditable(true);
        showAlert(Alert.AlertType.INFORMATION, "Edit Mode", "Bill is now editable. Make changes and click Save.");
    }

    @FXML
    private void handleImportGrn() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click Edit or + New Bill first.");
            return;
        }

        Dialog<GoodsReceivedNote> dialog = new Dialog<>();
        dialog.setTitle("Import Goods Received Note");
        dialog.setHeaderText("Select a GRN to convert into a Supplier Bill");
        ButtonType importBtn = new ButtonType("Import GRN", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(importBtn, ButtonType.CANCEL);

        VBox vbox = new VBox(10); vbox.setPadding(new Insets(10));
        TextField grnSearch = new TextField(); grnSearch.setPromptText("Search GRN number or supplier...");
        ListView<GoodsReceivedNote> grnListViewModal = new ListView<>(); grnListViewModal.setPrefHeight(250);

        List<GoodsReceivedNote> grnList = grnService.getAllGrns();
        grnListViewModal.setItems(FXCollections.observableArrayList(grnList));
        grnListViewModal.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(GoodsReceivedNote g, boolean empty) {
                super.updateItem(g, empty);
                if (empty || g == null) setText(null);
                else {
                    String supp = g.getSupplier() != null ? g.getSupplier().getName() : "Supplier";
                    setText(g.getGrnNumber() + " | " + supp + " | Date: " + g.getReceivedDate() + " | " + CurrencyFormatter.format(g.getTotalValue()));
                }
            }
        });

        grnSearch.textProperty().addListener((obs, old, newVal) -> {
            if (newVal == null || newVal.isBlank()) grnListViewModal.setItems(FXCollections.observableArrayList(grnList));
            else {
                String q = newVal.toLowerCase().trim();
                grnListViewModal.setItems(FXCollections.observableArrayList(
                        grnList.stream().filter(g ->
                                (g.getGrnNumber() != null && g.getGrnNumber().toLowerCase().contains(q)) ||
                                (g.getSupplier() != null && g.getSupplier().getName() != null && g.getSupplier().getName().toLowerCase().contains(q))
                        ).collect(Collectors.toList())
                ));
            }
        });

        vbox.getChildren().addAll(grnSearch, grnListViewModal);
        dialog.getDialogPane().setContent(vbox);
        dialog.setResultConverter(btn -> btn == importBtn ? grnListViewModal.getSelectionModel().getSelectedItem() : null);

        dialog.showAndWait().ifPresent(grn -> {
            currentBill.setGrnId(grn.getId());
            currentBill.setPoId(grn.getPoId());
            currentBill.setSupplierId(grn.getSupplierId());

            if (grn.getSupplier() != null) {
                selectSupplier(new SimpleSupplier(grn.getSupplier().getId(), grn.getSupplier().getName(), grn.getSupplier().getPhone()));
            }

            if (grn.getDeliveryNoteRef() != null && !grn.getDeliveryNoteRef().isBlank()) {
                supplierInvoiceRefField.setText(grn.getDeliveryNoteRef());
            }

            lines.clear();
            if (grn.getLines() != null) {
                for (GrnLine gLine : grn.getLines()) {
                    SupplierBillLine bLine = new SupplierBillLine();
                    bLine.setProductId(gLine.getProductId() != null ? gLine.getProductId() : 0);
                    bLine.setDescription(gLine.getDescription());
                    bLine.setQuantity(gLine.getQuantityReceived() != null ? gLine.getQuantityReceived() : BigDecimal.ONE);
                    bLine.setUnitPrice(gLine.getUnitCost() != null ? gLine.getUnitCost() : BigDecimal.ZERO);

                    boolean applyTax = globalTaxCheck == null || globalTaxCheck.isSelected();
                    bLine.setTaxable(applyTax);
                    bLine.recalculateTotal();
                    lines.add(bLine);
                }
            }
            updateTotal();
        });
    }

    @FXML
    private void handleQuickAddSupplier() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click Edit first to modify this Bill.");
            return;
        }

        Dialog<SimpleSupplier> dialog = new Dialog<>();
        dialog.setTitle("Quick Add Supplier");
        dialog.setHeaderText("Enter new supplier details");

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(10));
        TextField nameField = new TextField(); nameField.setPromptText("Company / Supplier Name *");
        TextField phoneField = new TextField(); phoneField.setPromptText("Phone");
        TextField emailField = new TextField(); emailField.setPromptText("Email");

        grid.add(new Label("Name *:"), 0, 0); grid.add(nameField, 1, 0);
        grid.add(new Label("Phone:"), 0, 1); grid.add(phoneField, 1, 1);
        grid.add(new Label("Email:"), 0, 2); grid.add(emailField, 1, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn -> {
            if (btn != saveBtn) return null;
            if (nameField.getText().trim().isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Validation", "Supplier name is required.");
                return null;
            }
            String sql = "INSERT INTO suppliers (name, phone, email) VALUES (?, ?, ?) RETURNING id";
            try (Connection conn = DatabaseConfig.getInstance().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, nameField.getText().trim());
                stmt.setString(2, phoneField.getText());
                stmt.setString(3, emailField.getText());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return new SimpleSupplier(rs.getInt(1), nameField.getText().trim(), phoneField.getText());
                }
            } catch (SQLException ex) {
                showAlert(Alert.AlertType.ERROR, "Error", ex.getMessage());
            }
            return null;
        });

        dialog.showAndWait().ifPresent(this::selectSupplier);
    }

    @FXML
    private void handleGlobalTaxToggle() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click Edit first to modify this Bill.");
            return;
        }
        boolean apply = globalTaxCheck != null && globalTaxCheck.isSelected();
        for (SupplierBillLine line : lines) {
            line.setTaxable(apply);
        }
        updateTotal();
        linesTable.refresh();
    }

    @FXML
    private void handleAddLine() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click Edit first to modify this Bill.");
            return;
        }

        Dialog<Product> dialog = new Dialog<>();
        dialog.setTitle("Select Product");
        dialog.setHeaderText("Search product catalog");
        ButtonType addBtn = new ButtonType("Add to Bill", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addBtn, ButtonType.CANCEL);

        VBox vbox = new VBox(10); vbox.setPadding(new Insets(10));
        TextField itemSearch = new TextField(); itemSearch.setPromptText("Search product...");
        ListView<Product> itemListView = new ListView<>(); itemListView.setPrefHeight(280);

        itemListView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Product p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) setText(null);
                else setText(p.getProductCode() + " | " + p.getName() + " [Cost: " + CurrencyFormatter.format(p.getSellingPrice()) + "]");
            }
        });

        List<Product> allProducts = productService.listAll();
        itemListView.setItems(FXCollections.observableArrayList(allProducts));

        itemSearch.textProperty().addListener((obs, old, newVal) -> {
            if (newVal == null || newVal.isBlank()) itemListView.setItems(FXCollections.observableArrayList(allProducts));
            else {
                String q = newVal.toLowerCase().trim();
                itemListView.setItems(FXCollections.observableArrayList(allProducts.stream()
                        .filter(p -> (p.getName() != null && p.getName().toLowerCase().contains(q)) ||
                                     (p.getProductCode() != null && p.getProductCode().toLowerCase().contains(q)))
                        .collect(Collectors.toList())));
            }
        });

        vbox.getChildren().addAll(itemSearch, itemListView);
        dialog.getDialogPane().setContent(vbox);
        dialog.setResultConverter(btn -> btn == addBtn ? itemListView.getSelectionModel().getSelectedItem() : null);

        dialog.showAndWait().ifPresent(product -> {
            SupplierBillLine line = new SupplierBillLine();
            line.setProductId(product.getId());
            line.setDescription(product.getName());
            line.setQuantity(BigDecimal.ONE);
            line.setUnitPrice(product.getSellingPrice() != null ? product.getSellingPrice() : BigDecimal.ZERO);

            boolean applyTax = globalTaxCheck == null || globalTaxCheck.isSelected();
            line.setTaxable(applyTax);
            line.recalculateTotal();

            lines.add(line);
            updateTotal();
        });
    }

    @FXML
    private void handleAddEmptyLine() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click Edit first to modify this Bill.");
            return;
        }
        SupplierBillLine line = new SupplierBillLine();
        line.setDescription("Custom Service / Vendor Expense");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(BigDecimal.ZERO);

        boolean applyTax = globalTaxCheck == null || globalTaxCheck.isSelected();
        line.setTaxable(applyTax);
        line.recalculateTotal();

        lines.add(line);
        linesTable.getSelectionModel().select(line);
        updateTotal();
    }

    @FXML
    private void handleRemoveLine() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click Edit first to modify this Bill.");
            return;
        }
        SupplierBillLine selected = linesTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            lines.remove(selected);
            updateTotal();
        } else {
            showAlert(Alert.AlertType.INFORMATION, "Notice", "Please select a line item from the table to remove.");
        }
    }

    private void updateTotal() {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;

        for (SupplierBillLine line : lines) {
            line.recalculateTotal();
            if (line.getTotalPrice() != null) {
                subtotal = subtotal.add(line.getTotalPrice());
            }
            if (line.getTaxAmount() != null) {
                taxTotal = taxTotal.add(line.getTaxAmount());
            }
        }

        BigDecimal grand = subtotal.add(taxTotal);
        linesTable.refresh();

        if (lblSubtotal != null) lblSubtotal.setText(CurrencyFormatter.format(subtotal));
        if (lblTax != null) lblTax.setText(CurrencyFormatter.format(taxTotal));
        if (lblTaxTitle != null) lblTaxTitle.setText(TaxUtil.getTaxName() + ":");
        if (totalLabel != null) totalLabel.setText("Total: " + CurrencyFormatter.format(grand));

        if (currentBill != null) {
            currentBill.setSubtotal(subtotal);
            currentBill.setTaxAmount(taxTotal);
            currentBill.setTotalAmount(grand);
        }
    }

    @FXML
    private void handleSave() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click Edit first to modify this Bill.");
            return;
        }
        if (isBillPaid(currentBill)) {
            showAlert(Alert.AlertType.WARNING, "Bill Locked", "Paid bills cannot be edited or modified.");
            return;
        }
        if (selectedSupplier == null && supplierSearchField.getText().isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Validation", "Please select a supplier.");
            return;
        }
        if (currentBill == null) {
            currentBill = new SupplierBill();
        }

        if (selectedSupplier != null) {
            currentBill.setSupplierId(selectedSupplier.getId());
            currentBill.setSupplierName(selectedSupplier.getName());
        } else {
            currentBill.setSupplierName(supplierSearchField.getText().trim());
        }

        currentBill.setBillDate(billDatePicker.getValue());
        currentBill.setDueDate(dueDatePicker.getValue());
        currentBill.setSupplierInvoiceRef(supplierInvoiceRefField.getText() != null ? supplierInvoiceRefField.getText().trim() : "");
        currentBill.setStatus(statusCombo.getValue() != null ? statusCombo.getValue() : "UNPAID");
        currentBill.setNotes(notesField.getText() != null ? notesField.getText().trim() : "");
        currentBill.setLines(lines);

        billService.saveBill(currentBill);
        loadBills();
        setFormEditable(false);
        showAlert(Alert.AlertType.INFORMATION, "Success", "Supplier Bill saved successfully!");
    }

    @FXML
    private void handleDelete() {
        if (currentBill == null || currentBill.getId() <= 0) {
            showAlert(Alert.AlertType.WARNING, "Notice", "Please select an existing bill to delete.");
            return;
        }

        if (isBillPaid(currentBill)) {
            showAlert(Alert.AlertType.WARNING, "Bill Locked", "Paid supplier bills cannot be deleted.");
            return;
        }

        boolean confirmed = AlertHelper.confirm("Delete Supplier Bill",
                "Are you sure you want to delete Supplier Bill #" + currentBill.getBillNumber() + "?");
        if (!confirmed) return;

        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmtLines = conn.prepareStatement("DELETE FROM supplier_bill_lines WHERE bill_id = ?");
                 PreparedStatement stmtBill = conn.prepareStatement("DELETE FROM supplier_bills WHERE id = ?")) {

                stmtLines.setInt(1, currentBill.getId());
                stmtLines.executeUpdate();

                stmtBill.setInt(1, currentBill.getId());
                stmtBill.executeUpdate();

                conn.commit();
                showAlert(Alert.AlertType.INFORMATION, "Success", "Supplier Bill deleted successfully.");
                handleNewBill();
                loadBills();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Could not delete bill: " + e.getMessage());
        }
    }

    @FXML
    private void handlePayBill() {
        if (currentBill == null || currentBill.getId() <= 0) {
            showAlert(Alert.AlertType.WARNING, "No Bill Selected", "Please select a bill to record payment.");
            return;
        }

        BigDecimal total = currentBill.getTotalAmount() != null ? currentBill.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal paid = currentBill.getPaidAmount() != null ? currentBill.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal outstanding = total.subtract(paid);

        if (outstanding.compareTo(BigDecimal.ZERO) <= 0 || "PAID".equalsIgnoreCase(currentBill.getStatus())) {
            showAlert(Alert.AlertType.INFORMATION, "Already Paid", "This bill is already fully paid.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog(outstanding.setScale(2, RoundingMode.HALF_UP).toString());
        dialog.setTitle("Record Supplier Payment");
        dialog.setHeaderText("Bill #" + currentBill.getBillNumber() + "\nOutstanding: " + CurrencyFormatter.format(outstanding));
        dialog.setContentText("Enter Payment Amount:");

        dialog.showAndWait().ifPresent(amountStr -> {
            try {
                BigDecimal amountPaid = new BigDecimal(amountStr.trim());
                if (amountPaid.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Amount must be greater than zero.");
                }

                BigDecimal newTotalPaid = paid.add(amountPaid);
                String newStatus = newTotalPaid.compareTo(total) >= 0 ? "PAID" : "PARTIALLY_PAID";

                try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
                    conn.setAutoCommit(false);

                    String updateSql = "UPDATE supplier_bills SET paid_amount = ?, status = ? WHERE id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                        stmt.setBigDecimal(1, newTotalPaid);
                        stmt.setString(2, newStatus);
                        stmt.setInt(3, currentBill.getId());
                        stmt.executeUpdate();
                    }

                    conn.commit();
                    showAlert(Alert.AlertType.INFORMATION, "Payment Recorded", "Successfully recorded vendor payment of " + CurrencyFormatter.format(amountPaid));
                    loadBills();
                }
            } catch (NumberFormatException e) {
                showAlert(Alert.AlertType.ERROR, "Invalid Input", "Please enter a valid numeric amount.");
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Payment Error", e.getMessage());
            }
        });
    }

    @FXML
    private void handlePrint() {
        if (currentBill == null || currentBill.getId() <= 0) {
            showAlert(Alert.AlertType.WARNING, "Print", "Please select a saved bill to print.");
            return;
        }
        showAlert(Alert.AlertType.INFORMATION, "Print Preview", "Printing Supplier Bill #" + currentBill.getBillNumber());
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}