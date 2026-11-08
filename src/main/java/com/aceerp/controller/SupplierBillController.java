package com.aceerp.controller;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.GoodsReceivedNote;
import com.aceerp.model.GrnLine;
import com.aceerp.model.Product;
import com.aceerp.model.SupplierBill;
import com.aceerp.model.SupplierBillLine;
import com.aceerp.service.AuditLogService;
import com.aceerp.service.GoodsReceivedNoteService;
import com.aceerp.service.ProductService;
import com.aceerp.service.SupplierBillService;
import com.aceerp.util.AlertHelper;
import com.aceerp.util.CurrencyFormatter;
import com.aceerp.util.DocumentDiscount;
import com.aceerp.util.GlobalQuickAdd;
import com.aceerp.util.SmartSearchHelper;
import com.aceerp.util.TaxUtil;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.VBox;
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
        private final int id;
        private final String name;
        private final String phone;

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
    @FXML private TableColumn<SupplierBillLine, BigDecimal> colDiscount;
    @FXML private TableColumn<SupplierBillLine, Boolean> colTaxable;
    @FXML private TableColumn<SupplierBillLine, String> colNotes;
    @FXML private TableColumn<SupplierBillLine, BigDecimal> colTotal;

    @FXML private Label totalLabel;
    @FXML private Label lblSubtotal;
    @FXML private Label lblTax;
    @FXML private Label lblTaxTitle;
    @FXML private ComboBox<String> cmbDocumentDiscountType;
    @FXML private TextField txtDocumentDiscountValue;
    @FXML private Label lblDocumentDiscountAmount;
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

    @FXML
    public void initialize() {
        DocumentDiscount.configureControls(cmbDocumentDiscountType, txtDocumentDiscountValue,
                () -> { if (editMode) updateTotal(); });
        setupSmartSearch();
        setupTable();
        setupListView();
        com.aceerp.util.QuickBooksContextMenus.installListView(supplierBillListView, bill -> java.util.List.of(
            com.aceerp.util.QuickBooksContextMenus.action("Edit", this::handleEdit),
            com.aceerp.util.QuickBooksContextMenus.action("Print", this::handlePrint)
        ));

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
        SmartSearchHelper.setupEntitySearch(
                supplierSearchField,
                query -> listSuppliers().stream()
                        .filter(s -> s.getName() != null && s.getName().toLowerCase().contains(query.toLowerCase().trim()))
                        .collect(Collectors.toList()),
                SimpleSupplier::getName,
                this::selectSupplier,
                typedText -> GlobalQuickAdd.showSupplierDialog(typedText,
                        s -> selectSupplier(new SimpleSupplier(s.getId(), s.getName(), s.getPhone()))),
                typed -> {
                    if (selectedSupplier != null && !selectedSupplier.getName().equalsIgnoreCase(typed.trim())) {
                        selectedSupplier = null;
                        supplierPhoneField.clear();
                    }
                }
        );
    }

    private void selectSupplier(SimpleSupplier s) {
        if (s == null) return;
        supplierSearchField.setText(s.getName());
        supplierPhoneField.setText(s.getPhone() != null && !s.getPhone().isBlank() ? s.getPhone() : "N/A");
        selectedSupplier = s;
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

        colProduct.setCellValueFactory(d -> new SimpleStringProperty(
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

        if (colDiscount != null) {
            colDiscount.setCellValueFactory(new PropertyValueFactory<>("discountPercent"));
            colDiscount.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
            colDiscount.setOnEditCommit(e -> {
                if (!editMode) { linesTable.refresh(); return; }
                e.getRowValue().setDiscountPercent(e.getNewValue() != null ? e.getNewValue() : BigDecimal.ZERO);
                updateTotal();
            });
        }

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

        if (colNotes != null) {
            colNotes.setCellValueFactory(d -> new SimpleStringProperty(
                    d.getValue().getNotes() != null ? d.getValue().getNotes() : ""
            ));
            colNotes.setCellFactory(TextFieldTableCell.forTableColumn());
            colNotes.setOnEditCommit(e -> {
                if (!editMode) { linesTable.refresh(); return; }
                e.getRowValue().setNotes(e.getNewValue());
                linesTable.refresh();
            });
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
        setFormEditable(false);
        billNumberField.setText(bill.getBillNumber());
        DocumentDiscount.setControls(cmbDocumentDiscountType, txtDocumentDiscountValue,
                bill.getDocumentDiscountType(), bill.getDocumentDiscountValue());
        billDatePicker.setValue(bill.getBillDate() != null ? bill.getBillDate() : LocalDate.now());
        dueDatePicker.setValue(bill.getDueDate() != null ? bill.getDueDate() : LocalDate.now().plusDays(30));
        supplierInvoiceRefField.setText(bill.getSupplierInvoiceRef() != null ? bill.getSupplierInvoiceRef() : "");
        statusCombo.setValue(bill.getStatus() != null ? bill.getStatus() : "UNPAID");
        notesField.setText(bill.getNotes() != null ? bill.getNotes() : "");

        if (lblStatus != null) {
            lblStatus.setText(bill.getStatus() != null ? bill.getStatus() : "UNPAID");
        }

        SimpleSupplier loadedSupplier = bill.getSupplierId() > 0
                ? listSuppliers().stream().filter(s -> s.getId() == bill.getSupplierId()).findFirst().orElse(null)
                : null;
        if (loadedSupplier != null) {
            supplierSearchField.setText(loadedSupplier.getName());
            supplierPhoneField.setText(loadedSupplier.getPhone() != null ? loadedSupplier.getPhone() : "N/A");
            selectedSupplier = loadedSupplier;
        } else if (bill.getSupplierName() != null) {
            supplierSearchField.setText(bill.getSupplierName());
            supplierPhoneField.setText("N/A");
            selectedSupplier = null;
        } else {
            supplierSearchField.clear();
            supplierPhoneField.clear();
            selectedSupplier = null;
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
        cmbDocumentDiscountType.setDisable(!editMode);
        txtDocumentDiscountValue.setDisable(!editMode);
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
        setFormEditable(false);
        currentBill = new SupplierBill();
        DocumentDiscount.setControls(cmbDocumentDiscountType, txtDocumentDiscountValue,
                DocumentDiscount.PERCENT, BigDecimal.ZERO);
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
    private void handleMoveUp() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click Edit first to modify this Bill.");
            return;
        }
        int idx = linesTable.getSelectionModel().getSelectedIndex();
        if (idx > 0) {
            SupplierBillLine line = lines.remove(idx);
            lines.add(idx - 1, line);
            linesTable.getSelectionModel().select(idx - 1);
        }
    }

    @FXML
    private void handleMoveDown() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click Edit first to modify this Bill.");
            return;
        }
        int idx = linesTable.getSelectionModel().getSelectedIndex();
        if (idx >= 0 && idx < lines.size() - 1) {
            SupplierBillLine line = lines.remove(idx);
            lines.add(idx + 1, line);
            linesTable.getSelectionModel().select(idx + 1);
        }
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
                    bLine.setDiscountPercent(BigDecimal.ZERO);
                    bLine.setNotes("");

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
        GlobalQuickAdd.showSupplierDialog(supplierSearchField.getText(),
                s -> selectSupplier(new SimpleSupplier(s.getId(), s.getName(), s.getPhone())));
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
            line.setDiscountPercent(BigDecimal.ZERO);
            line.setNotes("");

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
        line.setDiscountPercent(BigDecimal.ZERO);
        line.setNotes("");

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
            if (line.getTotalPrice() != null) subtotal = subtotal.add(line.getTotalPrice());
            if (line.getTaxAmount() != null) taxTotal = taxTotal.add(line.getTaxAmount());
        }

        BigDecimal discountAmount = DocumentDiscount.calculateAmount(
                subtotal, DocumentDiscount.selectedType(cmbDocumentDiscountType),
                DocumentDiscount.valueForCalculation(txtDocumentDiscountValue));
        taxTotal = DocumentDiscount.calculateTaxAfterDiscount(subtotal, taxTotal, discountAmount);
        BigDecimal grand = subtotal.subtract(discountAmount).add(taxTotal);
        linesTable.refresh();

        if (lblSubtotal != null) lblSubtotal.setText(CurrencyFormatter.format(subtotal));
        if (lblTax != null) lblTax.setText(CurrencyFormatter.format(taxTotal));
        if (lblDocumentDiscountAmount != null) lblDocumentDiscountAmount.setText(CurrencyFormatter.format(discountAmount));
        if (lblTaxTitle != null) lblTaxTitle.setText(TaxUtil.getTaxName() + ":");
        if (totalLabel != null) totalLabel.setText("Total: " + CurrencyFormatter.format(grand));

        if (currentBill != null) {
            currentBill.setSubtotal(subtotal);
            currentBill.setDocumentDiscountType(DocumentDiscount.selectedType(cmbDocumentDiscountType));
            currentBill.setDocumentDiscountValue(DocumentDiscount.valueForCalculation(txtDocumentDiscountValue));
            currentBill.setDocumentDiscountAmount(discountAmount);
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

        // Resolve typed name to an existing supplier if not picked from the dropdown
        if (selectedSupplier == null && supplierSearchField.getText() != null && !supplierSearchField.getText().isBlank()) {
            String typed = supplierSearchField.getText().trim();
            selectedSupplier = listSuppliers().stream()
                    .filter(s -> s.getName() != null && s.getName().equalsIgnoreCase(typed))
                    .findFirst().orElse(null);
        }

        if (selectedSupplier == null || selectedSupplier.getId() <= 0) {
            showAlert(Alert.AlertType.WARNING, "Validation", "Please select a valid supplier from the list or use Quick Add (+).");
            return;
        }
        if (lines.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Validation", "Please add at least one line item.");
            return;
        }

        if (currentBill == null) {
            currentBill = new SupplierBill();
        }
        updateTotal();
        String discountError = DocumentDiscount.validate(
                DocumentDiscount.selectedType(cmbDocumentDiscountType),
                txtDocumentDiscountValue.getText(),
                currentBill.getSubtotal());
        if (discountError != null) {
            showAlert(Alert.AlertType.WARNING, "Invalid Discount", discountError);
            return;
        }
        boolean isNew = currentBill.getId() <= 0;

        currentBill.setSupplierId(selectedSupplier.getId());
        currentBill.setSupplierName(selectedSupplier.getName());
        currentBill.setBillDate(billDatePicker.getValue());
        currentBill.setDueDate(dueDatePicker.getValue());
        currentBill.setSupplierInvoiceRef(supplierInvoiceRefField.getText() != null ? supplierInvoiceRefField.getText().trim() : "");
        currentBill.setStatus(statusCombo.getValue() != null ? statusCombo.getValue() : "UNPAID");
        currentBill.setNotes(notesField.getText() != null ? notesField.getText().trim() : "");
        currentBill.setLines(new ArrayList<>(lines));

        try {
            billService.saveBill(currentBill);
            AuditLogService.log(isNew ? "CREATE" : "UPDATE", "SUPPLIER_BILL", currentBill.getId(),
                    (isNew ? "Created" : "Updated") + " Supplier Bill #" + currentBill.getBillNumber() + " for " + selectedSupplier.getName());
            loadBills();
            setFormEditable(false);
            showAlert(Alert.AlertType.INFORMATION, "Success", "Supplier Bill saved successfully!");
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Save Failed", e.getMessage());
        }
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

        int deletedId = currentBill.getId();
        String deletedNum = currentBill.getBillNumber();

        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmtLines = conn.prepareStatement("DELETE FROM supplier_bill_lines WHERE bill_id = ?");
                 PreparedStatement stmtBill = conn.prepareStatement("DELETE FROM supplier_bills WHERE id = ?")) {

                stmtLines.setInt(1, deletedId);
                stmtLines.executeUpdate();
                stmtBill.setInt(1, deletedId);
                stmtBill.executeUpdate();

                conn.commit();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
            AuditLogService.log("DELETE", "SUPPLIER_BILL", deletedId, "Deleted Supplier Bill #" + deletedNum);
            showAlert(Alert.AlertType.INFORMATION, "Success", "Supplier Bill deleted successfully.");
            handleNewBill();
            loadBills();
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

                try (Connection conn = DatabaseConfig.getInstance().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                             "UPDATE supplier_bills SET paid_amount = ?, status = ? WHERE id = ?")) {
                    stmt.setBigDecimal(1, newTotalPaid);
                    stmt.setString(2, newStatus);
                    stmt.setInt(3, currentBill.getId());
                    stmt.executeUpdate();
                }

                AuditLogService.log("PAYMENT", "SUPPLIER_BILL", currentBill.getId(),
                        "Recorded payment of " + CurrencyFormatter.format(amountPaid) + " on Bill #" + currentBill.getBillNumber());
                showAlert(Alert.AlertType.INFORMATION, "Payment Recorded",
                        "Successfully recorded vendor payment of " + CurrencyFormatter.format(amountPaid));
                loadBills();
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

    @FXML
    private void handleApplyVendorCredit() {
        if (currentBill == null || currentBill.getId() <= 0) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select an open bill to apply credit.");
            return;
        }

        BigDecimal total = currentBill.getTotalAmount() != null ? currentBill.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal paid = currentBill.getPaidAmount() != null ? currentBill.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal outstanding = total.subtract(paid);

        if (outstanding.compareTo(BigDecimal.ZERO) <= 0 || "PAID".equalsIgnoreCase(currentBill.getStatus())) {
            showAlert(Alert.AlertType.INFORMATION, "Fully Paid", "This bill is already fully paid.");
            return;
        }

        // Query available vendor credits for this supplier
        List<com.aceerp.model.VendorCredit> availableCredits = new ArrayList<>();
        String sql = "SELECT id, vendor_credit_number, remaining_amount FROM vendor_credits WHERE supplier_id = ? AND remaining_amount > 0 AND status != 'VOID'";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, currentBill.getSupplierId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    com.aceerp.model.VendorCredit vc = new com.aceerp.model.VendorCredit();
                    vc.setId(rs.getInt("id"));
                    vc.setVendorCreditNumber(rs.getString("vendor_credit_number"));
                    vc.setRemainingAmount(rs.getBigDecimal("remaining_amount"));
                    availableCredits.add(vc);
                }
            }
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Database Error", e.getMessage());
            return;
        }

        if (availableCredits.isEmpty()) {
            showAlert(Alert.AlertType.INFORMATION, "No Credits", "No active vendor credits found for " + currentBill.getSupplierName());
            return;
        }

        ChoiceDialog<com.aceerp.model.VendorCredit> dialog = new ChoiceDialog<>(availableCredits.get(0), availableCredits);
        dialog.setTitle("Apply Vendor Credit");
        dialog.setHeaderText("Select Vendor Credit to apply to Bill #" + currentBill.getBillNumber());
        dialog.setContentText("Available Credits:");

        dialog.showAndWait().ifPresent(vc -> {
            BigDecimal maxApply = vc.getRemainingAmount().min(outstanding);
            TextInputDialog amtDialog = new TextInputDialog(maxApply.setScale(2, RoundingMode.HALF_UP).toString());
            amtDialog.setTitle("Credit Amount");
            amtDialog.setHeaderText("Credit: " + vc.getVendorCreditNumber() + " (Max: " + CurrencyFormatter.format(maxApply) + ")");
            amtDialog.setContentText("Enter amount to apply:");

            amtDialog.showAndWait().ifPresent(amtStr -> {
                try {
                    BigDecimal applyAmt = new BigDecimal(amtStr.trim());
                    if (applyAmt.compareTo(BigDecimal.ZERO) <= 0 || applyAmt.compareTo(maxApply) > 0) {
                        showAlert(Alert.AlertType.WARNING, "Invalid Amount", "Amount must be between $0.01 and " + CurrencyFormatter.format(maxApply));
                        return;
                    }

                    // Perform application
                    try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
                        conn.setAutoCommit(false);
                        try {
                            // Insert credit application
                            try (PreparedStatement ps = conn.prepareStatement(
                                    "INSERT INTO credit_applications (credit_type, vendor_credit_id, supplier_bill_id, applied_amount, applied_date) VALUES ('VENDOR', ?, ?, ?, CURRENT_DATE)")) {
                                ps.setInt(1, vc.getId());
                                ps.setInt(2, currentBill.getId());
                                ps.setBigDecimal(3, applyAmt);
                                ps.executeUpdate();
                            }

                            // Deduct vendor credit remaining
                            BigDecimal newVcRem = vc.getRemainingAmount().subtract(applyAmt);
                            String vcStatus = newVcRem.compareTo(BigDecimal.ZERO) == 0 ? "FULLY_APPLIED" : "PARTIALLY_APPLIED";
                            try (PreparedStatement ps = conn.prepareStatement("UPDATE vendor_credits SET remaining_amount = ?, status = ? WHERE id = ?")) {
                                ps.setBigDecimal(1, newVcRem);
                                ps.setString(2, vcStatus);
                                ps.setInt(3, vc.getId());
                                ps.executeUpdate();
                            }

                            // Increase bill paid_amount
                            BigDecimal newBillPaid = paid.add(applyAmt);
                            String billStatus = newBillPaid.compareTo(total) >= 0 ? "PAID" : "PARTIALLY_PAID";
                            try (PreparedStatement ps = conn.prepareStatement("UPDATE supplier_bills SET paid_amount = ?, status = ? WHERE id = ?")) {
                                ps.setBigDecimal(1, newBillPaid);
                                ps.setString(2, billStatus);
                                ps.setInt(3, currentBill.getId());
                                ps.executeUpdate();
                            }

                            conn.commit();
                            showAlert(Alert.AlertType.INFORMATION, "Applied", "Applied " + CurrencyFormatter.format(applyAmt) + " credit to Bill #" + currentBill.getBillNumber());
                            loadBills();
                        } catch (Exception ex) {
                            conn.rollback();
                            throw ex;
                        }
                    }
                } catch (Exception ex) {
                    showAlert(Alert.AlertType.ERROR, "Apply Error", ex.getMessage());
                }
            });
        });
    }
}