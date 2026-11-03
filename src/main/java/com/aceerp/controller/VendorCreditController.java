package com.aceerp.controller;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.UserSession;
import com.aceerp.model.VendorCredit;
import com.aceerp.model.VendorCreditLine;
import com.aceerp.service.CreditService;
import com.aceerp.util.AlertHelper;
import com.aceerp.util.CurrencyFormatter;
import com.aceerp.util.GlobalQuickAdd;
import com.aceerp.util.SmartSearchHelper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.converter.BigDecimalStringConverter;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class VendorCreditController {

    public static class SimpleSupplier {
        private final int id;
        private final String name;
        public SimpleSupplier(int id, String name) { this.id = id; this.name = name; }
        public int getId() { return id; }
        public String getName() { return name; }
        @Override public String toString() { return name; }
    }

    @FXML private ListView<VendorCredit> vendorCreditListView;
    @FXML private TextField searchField;

    @FXML private TextField creditNumberField;
    @FXML private DatePicker issueDatePicker;
    @FXML private TextField supplierSearchField;
    @FXML private Label lblStatus;
    @FXML private TextField memoNotesField;

    @FXML private TableView<VendorCreditLine> linesTable;
    @FXML private TableColumn<VendorCreditLine, String> colProduct;
    @FXML private TableColumn<VendorCreditLine, BigDecimal> colQty;
    @FXML private TableColumn<VendorCreditLine, BigDecimal> colCost;
    @FXML private TableColumn<VendorCreditLine, BigDecimal> colTaxRate;
    @FXML private TableColumn<VendorCreditLine, BigDecimal> colTotal;

    @FXML private Label lblSubtotal;
    @FXML private Label lblTax;
    @FXML private Label lblTotal;
    @FXML private Label lblRemaining;

    private final CreditService creditService = new CreditService();

    private final ObservableList<VendorCredit> allCredits = FXCollections.observableArrayList();
    private final ObservableList<VendorCreditLine> lines = FXCollections.observableArrayList();

    private VendorCredit currentCredit;
    private SimpleSupplier selectedSupplier;

    @FXML
    public void initialize() {
        setupSmartSearch();
        setupTable();
        setupListView();
        loadVendorCredits();
        handleNewCredit();
    }

    private void setupSmartSearch() {
        SmartSearchHelper.setupEntitySearch(
                supplierSearchField,
                query -> listSuppliers().stream()
                        .filter(s -> s.getName().toLowerCase().contains(query.toLowerCase().trim()))
                        .collect(Collectors.toList()),
                SimpleSupplier::getName,
                this::selectSupplier,
                typed -> GlobalQuickAdd.showSupplierDialog(typed,
                        supp -> selectSupplier(new SimpleSupplier(supp.getId(), supp.getName()))),
                typed -> {
                    if (selectedSupplier != null && !selectedSupplier.getName().equalsIgnoreCase(typed.trim())) {
                        selectedSupplier = null;
                    }
                }
        );
    }

    private void selectSupplier(SimpleSupplier supplier) {
        supplierSearchField.setText(supplier.getName());
        selectedSupplier = supplier;
    }

    private List<SimpleSupplier> listSuppliers() {
        List<SimpleSupplier> list = new ArrayList<>();
        String sql = "SELECT id, name FROM suppliers ORDER BY name ASC";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(new SimpleSupplier(rs.getInt("id"), rs.getString("name")));
        } catch (Exception ignored) {}
        return list;
    }

    private void setupListView() {
        vendorCreditListView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(VendorCredit item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else setText(item.getVendorCreditNumber() + " | " + item.getSupplierName() + " | " + item.getStatus() + " | Rem: " + CurrencyFormatter.format(item.getRemainingAmount()));
            }
        });
        vendorCreditListView.getSelectionModel().selectedItemProperty().addListener((o, oldV, newV) -> {
            if (newV != null) loadCreditToForm(newV);
        });
        if (searchField != null) {
            searchField.textProperty().addListener((o, oldV, newV) -> filterCredits(newV));
        }
    }

    private void filterCredits(String q) {
        if (q == null || q.isBlank()) { vendorCreditListView.setItems(allCredits); return; }
        String search = q.toLowerCase().trim();
        vendorCreditListView.setItems(FXCollections.observableArrayList(
                allCredits.stream().filter(c -> c.getVendorCreditNumber().toLowerCase().contains(search) ||
                        (c.getSupplierName() != null && c.getSupplierName().toLowerCase().contains(search)))
                        .collect(Collectors.toList())
        ));
    }

    private void setupTable() {
        linesTable.setEditable(true);

        colProduct.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDescription()));
        colProduct.setCellFactory(TextFieldTableCell.forTableColumn());
        colProduct.setOnEditCommit(e -> { e.getRowValue().setDescription(e.getNewValue()); recalculateTotals(); });

        colQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colQty.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
        colQty.setOnEditCommit(e -> { e.getRowValue().setQuantity(e.getNewValue()); recalculateTotals(); });

        colCost.setCellValueFactory(new PropertyValueFactory<>("unitCost"));
        colCost.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
        colCost.setOnEditCommit(e -> { e.getRowValue().setUnitCost(e.getNewValue()); recalculateTotals(); });

        colTaxRate.setCellValueFactory(new PropertyValueFactory<>("taxRate"));
        colTaxRate.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
        colTaxRate.setOnEditCommit(e -> { e.getRowValue().setTaxRate(e.getNewValue()); recalculateTotals(); });

        colTotal.setCellValueFactory(new PropertyValueFactory<>("totalAmount"));
        linesTable.setItems(lines);
    }

    private void loadVendorCredits() {
        allCredits.clear();
        String sql = """
            SELECT v.id, v.vendor_credit_number, v.supplier_id, v.issue_date, v.status,
                   v.subtotal, v.tax_amount, v.total_amount, v.remaining_amount, v.memo,
                   s.name AS supplier_name
            FROM vendor_credits v
            JOIN suppliers s ON v.supplier_id = s.id
            ORDER BY v.id DESC
            """;
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                VendorCredit vc = new VendorCredit();
                vc.setId(rs.getInt("id"));
                vc.setVendorCreditNumber(rs.getString("vendor_credit_number"));
                vc.setSupplierId(rs.getInt("supplier_id"));
                vc.setSupplierName(rs.getString("supplier_name"));
                vc.setIssueDate(rs.getDate("issue_date").toLocalDate());
                vc.setStatus(rs.getString("status"));
                vc.setSubtotal(rs.getBigDecimal("subtotal"));
                vc.setTaxAmount(rs.getBigDecimal("tax_amount"));
                vc.setTotalAmount(rs.getBigDecimal("total_amount"));
                vc.setRemainingAmount(rs.getBigDecimal("remaining_amount"));
                vc.setMemo(rs.getString("memo"));
                allCredits.add(vc);
            }
        } catch (Exception ignored) {}
        vendorCreditListView.setItems(allCredits);
    }

    private void loadCreditToForm(VendorCredit credit) {
        currentCredit = credit;
        creditNumberField.setText(credit.getVendorCreditNumber());
        issueDatePicker.setValue(credit.getIssueDate());
        supplierSearchField.setText(credit.getSupplierName());
        lblStatus.setText(credit.getStatus());
        memoNotesField.setText(credit.getMemo() != null ? credit.getMemo() : "");

        selectedSupplier = new SimpleSupplier(credit.getSupplierId(), credit.getSupplierName());
        lines.clear();

        // Load Lines
        String sql = "SELECT * FROM vendor_credit_lines WHERE vendor_credit_id = ? ORDER BY line_no ASC";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, credit.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    VendorCreditLine l = new VendorCreditLine();
                    l.setId(rs.getInt("id"));
                    l.setVendorCreditId(rs.getInt("vendor_credit_id"));
                    l.setProductId((Integer) rs.getObject("product_id"));
                    l.setAccountId((Integer) rs.getObject("account_id"));
                    l.setDescription(rs.getString("description"));
                    l.setQuantity(rs.getBigDecimal("quantity"));
                    l.setUnitCost(rs.getBigDecimal("unit_cost"));
                    l.setTaxRate(rs.getBigDecimal("tax_rate"));
                    l.setTaxAmount(rs.getBigDecimal("tax_amount"));
                    l.setTotalAmount(rs.getBigDecimal("total_amount"));
                    lines.add(l);
                }
            }
        } catch (Exception ignored) {}
        recalculateTotals();
    }

    @FXML
    public void handleNewCredit() {
        currentCredit = new VendorCredit();
        creditNumberField.setText("VC-Auto");
        issueDatePicker.setValue(LocalDate.now());
        supplierSearchField.clear();
        lblStatus.setText("OPEN");
        memoNotesField.clear();
        selectedSupplier = null;
        lines.clear();
        vendorCreditListView.getSelectionModel().clearSelection();
        recalculateTotals();
    }

    @FXML
    public void handleAddLine() {
        VendorCreditLine l = new VendorCreditLine();
        l.setDescription("Returned Goods / Vendor Allowance");
        l.setQuantity(BigDecimal.ONE);
        l.setUnitCost(BigDecimal.ZERO);
        l.setTaxRate(BigDecimal.ZERO);
        l.recalculateTotal();
        lines.add(l);
        recalculateTotals();
    }

    @FXML
    public void handleRemoveLine() {
        VendorCreditLine sel = linesTable.getSelectionModel().getSelectedItem();
        if (sel != null) { lines.remove(sel); recalculateTotals(); }
    }

    private void recalculateTotals() {
        BigDecimal sub = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (VendorCreditLine l : lines) {
            l.recalculateTotal();
            sub = sub.add(l.getQuantity().multiply(l.getUnitCost()));
            tax = tax.add(l.getTaxAmount());
        }
        BigDecimal tot = sub.add(tax);
        lblSubtotal.setText(CurrencyFormatter.format(sub));
        lblTax.setText(CurrencyFormatter.format(tax));
        lblTotal.setText(CurrencyFormatter.format(tot));
        lblRemaining.setText(CurrencyFormatter.format(currentCredit != null && currentCredit.getId() > 0 ? currentCredit.getRemainingAmount() : tot));
        linesTable.refresh();
    }

    @FXML
    public void handleSave() {
        if (selectedSupplier == null) { AlertHelper.warning("Validation", "Please select a supplier."); return; }
        if (lines.isEmpty()) { AlertHelper.warning("Validation", "Add at least one line."); return; }

        currentCredit.setSupplierId(selectedSupplier.getId());
        currentCredit.setSupplierName(selectedSupplier.getName());
        currentCredit.setIssueDate(issueDatePicker.getValue());
        currentCredit.setMemo(memoNotesField.getText());
        currentCredit.setLines(new ArrayList<>(lines));

        try {
            int userId = UserSession.getInstance().getCurrentUser() != null ? UserSession.getInstance().getCurrentUser().getId() : 0;
            creditService.saveVendorCredit(currentCredit, userId);
            AlertHelper.info("Success", "Vendor Credit " + currentCredit.getVendorCreditNumber() + " issued and GL entry posted.");
            loadVendorCredits();
            handleNewCredit();
        } catch (Exception e) {
            AlertHelper.error("Error", e.getMessage());
        }
    }
}