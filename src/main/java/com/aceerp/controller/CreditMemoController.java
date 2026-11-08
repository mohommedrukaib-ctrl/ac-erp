package com.aceerp.controller;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.CreditMemoLine;
import com.aceerp.model.CustomerCreditMemo;
import com.aceerp.model.Product;
import com.aceerp.model.UserSession;
import com.aceerp.service.CreditService;
import com.aceerp.service.ProductService;
import com.aceerp.util.AlertHelper;
import com.aceerp.util.CurrencyFormatter;
import com.aceerp.util.GlobalQuickAdd;
import com.aceerp.util.SmartSearchHelper;
import javafx.beans.property.SimpleStringProperty;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CreditMemoController {

    public static class SimpleCustomer {
        private final int id;
        private final String name;
        public SimpleCustomer(int id, String name) { this.id = id; this.name = name; }
        public int getId() { return id; }
        public String getName() { return name; }
        @Override public String toString() { return name; }
    }

    @FXML private ListView<CustomerCreditMemo> creditMemoListView;
    @FXML private TextField searchField;

    @FXML private TextField memoNumberField;
    @FXML private DatePicker issueDatePicker;
    @FXML private TextField customerSearchField;
    @FXML private Label lblStatus;
    @FXML private TextField memoNotesField;

    @FXML private TableView<CreditMemoLine> linesTable;
    @FXML private TableColumn<CreditMemoLine, String> colProduct;
    @FXML private TableColumn<CreditMemoLine, BigDecimal> colQty;
    @FXML private TableColumn<CreditMemoLine, BigDecimal> colPrice;
    @FXML private TableColumn<CreditMemoLine, BigDecimal> colTaxRate;
    @FXML private TableColumn<CreditMemoLine, BigDecimal> colTotal;

    @FXML private Label lblSubtotal;
    @FXML private Label lblTax;
    @FXML private Label lblTotal;
    @FXML private Label lblRemaining;

    private final CreditService creditService = new CreditService();
    private final ProductService productService = new ProductService();

    private final ObservableList<CustomerCreditMemo> allMemos = FXCollections.observableArrayList();
    private final ObservableList<CreditMemoLine> lines = FXCollections.observableArrayList();

    private CustomerCreditMemo currentMemo;
    private SimpleCustomer selectedCustomer;

    @FXML
    public void initialize() {
        setupSmartSearch();
        setupTable();
        setupListView();
        loadCreditMemos();
        handleNewMemo();
    }

    private void setupSmartSearch() {
        SmartSearchHelper.setupEntitySearch(
                customerSearchField,
                query -> listCustomers().stream()
                        .filter(c -> c.getName().toLowerCase().contains(query.toLowerCase().trim()))
                        .collect(Collectors.toList()),
                SimpleCustomer::getName,
                this::selectCustomer,
                typed -> GlobalQuickAdd.showCustomerDialog(typed,
                        cust -> selectCustomer(new SimpleCustomer(cust.getId(), cust.getName()))),
                typed -> {
                    if (selectedCustomer != null && !selectedCustomer.getName().equalsIgnoreCase(typed.trim())) {
                        selectedCustomer = null;
                    }
                }
        );
    }

    private void selectCustomer(SimpleCustomer customer) {
        SmartSearchHelper.setTextWithoutSuggestions(customerSearchField, customer.getName());
        selectedCustomer = customer;
    }

    private List<SimpleCustomer> listCustomers() {
        List<SimpleCustomer> list = new ArrayList<>();
        String sql = "SELECT id, name FROM customers ORDER BY name ASC";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(new SimpleCustomer(rs.getInt("id"), rs.getString("name")));
        } catch (Exception ignored) {}
        return list;
    }

    private void setupListView() {
        creditMemoListView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(CustomerCreditMemo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else setText(item.getCreditMemoNumber() + " | " + item.getCustomerName() + " | " + item.getStatus() + " | Rem: " + CurrencyFormatter.format(item.getRemainingAmount()));
            }
        });
        creditMemoListView.getSelectionModel().selectedItemProperty().addListener((o, oldV, newV) -> {
            if (newV != null) loadMemoToForm(newV);
        });
        if (searchField != null) {
            searchField.textProperty().addListener((o, oldV, newV) -> filterMemos(newV));
        }
    }

    private void filterMemos(String q) {
        if (q == null || q.isBlank()) { creditMemoListView.setItems(allMemos); return; }
        String search = q.toLowerCase().trim();
        creditMemoListView.setItems(FXCollections.observableArrayList(
                allMemos.stream().filter(m -> m.getCreditMemoNumber().toLowerCase().contains(search) ||
                        (m.getCustomerName() != null && m.getCustomerName().toLowerCase().contains(search)))
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

        colPrice.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
        colPrice.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
        colPrice.setOnEditCommit(e -> { e.getRowValue().setUnitPrice(e.getNewValue()); recalculateTotals(); });

        colTaxRate.setCellValueFactory(new PropertyValueFactory<>("taxRate"));
        colTaxRate.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
        colTaxRate.setOnEditCommit(e -> { e.getRowValue().setTaxRate(e.getNewValue()); recalculateTotals(); });

        colTotal.setCellValueFactory(new PropertyValueFactory<>("totalAmount"));
        linesTable.setItems(lines);
    }

    private void loadCreditMemos() {
        allMemos.clear();
        String sql = """
            SELECT m.id, m.credit_memo_number, m.customer_id, m.issue_date, m.status,
                   m.subtotal, m.tax_amount, m.total_amount, m.remaining_amount, m.memo,
                   c.name AS customer_name
            FROM customer_credit_memos m
            JOIN customers c ON m.customer_id = c.id
            ORDER BY m.id DESC
            """;
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                CustomerCreditMemo cm = new CustomerCreditMemo();
                cm.setId(rs.getInt("id"));
                cm.setCreditMemoNumber(rs.getString("credit_memo_number"));
                cm.setCustomerId(rs.getInt("customer_id"));
                cm.setCustomerName(rs.getString("customer_name"));
                cm.setIssueDate(rs.getDate("issue_date").toLocalDate());
                cm.setStatus(rs.getString("status"));
                cm.setSubtotal(rs.getBigDecimal("subtotal"));
                cm.setTaxAmount(rs.getBigDecimal("tax_amount"));
                cm.setTotalAmount(rs.getBigDecimal("total_amount"));
                cm.setRemainingAmount(rs.getBigDecimal("remaining_amount"));
                cm.setMemo(rs.getString("memo"));
                allMemos.add(cm);
            }
        } catch (Exception ignored) {}
        creditMemoListView.setItems(allMemos);
    }

    private void loadMemoToForm(CustomerCreditMemo memo) {
        currentMemo = memo;
        memoNumberField.setText(memo.getCreditMemoNumber());
        issueDatePicker.setValue(memo.getIssueDate());
        SmartSearchHelper.setTextWithoutSuggestions(customerSearchField, memo.getCustomerName());
        lblStatus.setText(memo.getStatus());
        memoNotesField.setText(memo.getMemo() != null ? memo.getMemo() : "");

        selectedCustomer = new SimpleCustomer(memo.getCustomerId(), memo.getCustomerName());
        lines.clear();

        // Load Lines
        String sql = "SELECT * FROM credit_memo_lines WHERE credit_memo_id = ? ORDER BY line_no ASC";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, memo.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CreditMemoLine l = new CreditMemoLine();
                    l.setId(rs.getInt("id"));
                    l.setCreditMemoId(rs.getInt("credit_memo_id"));
                    l.setProductId((Integer) rs.getObject("product_id"));
                    l.setDescription(rs.getString("description"));
                    l.setQuantity(rs.getBigDecimal("quantity"));
                    l.setUnitPrice(rs.getBigDecimal("unit_price"));
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
    public void handleNewMemo() {
        currentMemo = new CustomerCreditMemo();
        memoNumberField.setText("CM-Auto");
        issueDatePicker.setValue(LocalDate.now());
        customerSearchField.clear();
        lblStatus.setText("OPEN");
        memoNotesField.clear();
        selectedCustomer = null;
        lines.clear();
        creditMemoListView.getSelectionModel().clearSelection();
        recalculateTotals();
    }

    @FXML
    public void handleAddLine() {
        CreditMemoLine l = new CreditMemoLine();
        l.setDescription("Returned Goods / Allowance");
        l.setQuantity(BigDecimal.ONE);
        l.setUnitPrice(BigDecimal.ZERO);
        l.setTaxRate(BigDecimal.ZERO);
        l.recalculateTotal();
        lines.add(l);
        recalculateTotals();
    }

    @FXML
    public void handleRemoveLine() {
        CreditMemoLine sel = linesTable.getSelectionModel().getSelectedItem();
        if (sel != null) { lines.remove(sel); recalculateTotals(); }
    }

    private void recalculateTotals() {
        BigDecimal sub = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (CreditMemoLine l : lines) {
            l.recalculateTotal();
            sub = sub.add(l.getQuantity().multiply(l.getUnitPrice()));
            tax = tax.add(l.getTaxAmount());
        }
        BigDecimal tot = sub.add(tax);
        lblSubtotal.setText(CurrencyFormatter.format(sub));
        lblTax.setText(CurrencyFormatter.format(tax));
        lblTotal.setText(CurrencyFormatter.format(tot));
        lblRemaining.setText(CurrencyFormatter.format(currentMemo != null && currentMemo.getId() > 0 ? currentMemo.getRemainingAmount() : tot));
        linesTable.refresh();
    }

    @FXML
    public void handleSave() {
        if (selectedCustomer == null) { AlertHelper.warning("Validation", "Please select a customer."); return; }
        if (lines.isEmpty()) { AlertHelper.warning("Validation", "Add at least one line."); return; }

        currentMemo.setCustomerId(selectedCustomer.getId());
        currentMemo.setCustomerName(selectedCustomer.getName());
        currentMemo.setIssueDate(issueDatePicker.getValue());
        currentMemo.setMemo(memoNotesField.getText());
        currentMemo.setLines(new ArrayList<>(lines));

        try {
            int userId = UserSession.getInstance().getCurrentUser() != null ? UserSession.getInstance().getCurrentUser().getId() : 0;
            creditService.saveCustomerCreditMemo(currentMemo, userId);
            AlertHelper.info("Success", "Credit Memo " + currentMemo.getCreditMemoNumber() + " issued and GL entry posted.");
            loadCreditMemos();
            handleNewMemo();
        } catch (Exception e) {
            AlertHelper.error("Error", e.getMessage());
        }
    }
}