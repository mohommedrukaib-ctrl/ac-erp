package com.aceerp.controller;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.Customer;
import com.aceerp.model.Product;
import com.aceerp.model.Quotation;
import com.aceerp.model.QuotationLine;
import com.aceerp.service.CustomerService;
import com.aceerp.service.ProductService;
import com.aceerp.service.QuotationService;
import com.aceerp.util.CurrencyFormatter;
import com.aceerp.util.TaxUtil;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.print.PrinterJob;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.util.converter.BigDecimalStringConverter;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

public class QuotationController {

    @FXML private TextField searchField;
    @FXML private TextField customerSearchField;
    @FXML private TextField customerPhoneField;
    @FXML private TextField quoteNumberField;
    @FXML private ListView<Quotation> quotationListView;
    @FXML private DatePicker quoteDatePicker;
    @FXML private ComboBox<String> statusCombo;
    @FXML private TableView<QuotationLine> linesTable;
    @FXML private TableColumn<QuotationLine, String> colProduct;
    @FXML private TableColumn<QuotationLine, BigDecimal> colQty;
    @FXML private TableColumn<QuotationLine, BigDecimal> colPrice;
    @FXML private TableColumn<QuotationLine, Boolean> colTaxable;
    @FXML private TableColumn<QuotationLine, BigDecimal> colTotal;
    @FXML private Label totalLabel;
    @FXML private Label lblSubtotal;
    @FXML private Label lblTax;
    @FXML private Label lblTaxTitle;
    @FXML private CheckBox globalTaxCheck;
    @FXML private VBox detailsPane;

    private final QuotationService quotationService = new QuotationService();
    private final CustomerService customerService = new CustomerService();
    private final ProductService productService = new ProductService();

    private Customer selectedCustomer;
    private final ObservableList<QuotationLine> lines = FXCollections.observableArrayList();
    private ObservableList<Quotation> allQuotations = FXCollections.observableArrayList();
    private Quotation currentQuotation;
    
    private Popup customerPopup;
    private ListView<Customer> customerListView;
    
    private boolean editMode = false;

    @FXML
    public void initialize() {
        setupSmartSearch();
        setupTable();
        setupListView();
        
        statusCombo.setItems(FXCollections.observableArrayList("DRAFT", "SENT", "ACCEPTED", "EXPIRED", "INVOICED"));
        
        if (globalTaxCheck != null) {
            globalTaxCheck.setSelected(TaxUtil.isTaxEnabled());
        }
        if (lblTaxTitle != null) {
            lblTaxTitle.setText(TaxUtil.getTaxName() + ":");
        }
        
        loadQuotations();
        handleNewQuotation();
    }
    
    private void setupListView() {
        quotationListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Quotation q, boolean empty) {
                super.updateItem(q, empty);
                if (empty || q == null) {
                    setText(null);
                } else {
                    String custName = (q.getCustomer() != null) ? q.getCustomer().getName() : "Unknown";
                    setText(q.getQuoteNumber() + " | " + custName + " | " + q.getStatus() + " | " + CurrencyFormatter.format(q.getTotalAmount()));
                }
            }
        });

        quotationListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadQuotationToForm(newVal);
            }
        });

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isBlank()) {
                quotationListView.setItems(allQuotations);
            } else {
                String lower = newVal.toLowerCase();
                quotationListView.setItems(FXCollections.observableArrayList(
                    allQuotations.stream().filter(q -> 
                        (q.getQuoteNumber() != null && q.getQuoteNumber().toLowerCase().contains(lower)) ||
                        (q.getCustomer() != null && q.getCustomer().getName().toLowerCase().contains(lower)) ||
                        (q.getStatus() != null && q.getStatus().toLowerCase().contains(lower))
                    ).collect(Collectors.toList())
                ));
            }
        });
    }

    private void setupSmartSearch() {
        customerPopup = new Popup();
        customerPopup.setAutoHide(true);
        customerListView = new ListView<>();
        customerListView.setPrefHeight(150);
        customerPopup.getContent().add(customerListView);

        customerSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
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
                if (!customerPopup.isShowing() && customerSearchField.getScene() != null) {
                    Bounds bounds = customerSearchField.localToScreen(customerSearchField.getBoundsInLocal());
                    if (bounds != null) {
                        customerListView.setPrefWidth(bounds.getWidth());
                        customerPopup.show(customerSearchField, bounds.getMinX(), bounds.getMaxY());
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
        customerSearchField.setText(c.getName());
        customerPhoneField.setText(c.getPhone() != null && !c.getPhone().isEmpty() ? c.getPhone() : "N/A");
        customerPopup.hide();
    }

    private void setFormEditable(boolean editable) {
        this.editMode = editable;
        customerSearchField.setDisable(!editable);
        quoteDatePicker.setDisable(!editable);
        statusCombo.setDisable(!editable);
        linesTable.setEditable(editable);
        if (globalTaxCheck != null) {
            globalTaxCheck.setDisable(!editable);
        }
    }

    private void loadQuotationToForm(Quotation q) {
        currentQuotation = q;
        quoteNumberField.setText(q.getQuoteNumber());
        quoteDatePicker.setValue(q.getQuoteDate());
        statusCombo.setValue(q.getStatus());
        
        selectedCustomer = q.getCustomer();
        if (selectedCustomer != null) {
            customerSearchField.setText(selectedCustomer.getName());
            customerPhoneField.setText(selectedCustomer.getPhone() != null ? selectedCustomer.getPhone() : "N/A");
        } else {
            customerSearchField.clear();
            customerPhoneField.clear();
        }
        
        lines.setAll(q.getLines());
        
        if (globalTaxCheck != null && !lines.isEmpty()) {
            boolean allTaxable = lines.stream().allMatch(QuotationLine::isTaxable);
            globalTaxCheck.setSelected(allTaxable);
        }
        
        updateTotal();
        setFormEditable(false);
    }

    @FXML
    private void handleEdit() {
        if (currentQuotation == null || currentQuotation.getId() <= 0) {
            showAlert(Alert.AlertType.WARNING, "Edit", "Please select a saved quotation from the list first.");
            return;
        }
        if ("ACCEPTED".equalsIgnoreCase(currentQuotation.getStatus()) || "INVOICED".equalsIgnoreCase(currentQuotation.getStatus())) {
            showAlert(Alert.AlertType.WARNING, "Locked", "Converted/Accepted quotations cannot be modified.");
            return;
        }
        setFormEditable(true);
    }

    @FXML
    private void handleQuickAddCustomer() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click Edit first to modify the document.");
            return;
        }
        Dialog<Customer> dialog = new Dialog<>();
        dialog.setTitle("Quick Add Customer");
        dialog.setHeaderText("Enter new customer details");

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(10));
        TextField nameField = new TextField(); nameField.setPromptText("Company / Person Name *");
        TextField phoneField = new TextField(); phoneField.setPromptText("Phone");
        TextField emailField = new TextField(); emailField.setPromptText("Email");

        grid.add(new Label("Name *:"), 0, 0); grid.add(nameField, 1, 0);
        grid.add(new Label("Phone:"), 0, 1); grid.add(phoneField, 1, 1);
        grid.add(new Label("Email:"), 0, 2); grid.add(emailField, 1, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn -> {
            if (btn != saveBtn) return null;
            if (nameField.getText().trim().isEmpty()) {
                showAlert("Validation", "Customer name is required.");
                return null;
            }
            try {
                int newId = customerService.addCustomer(nameField.getText().trim(), phoneField.getText(), emailField.getText(), "", "", null, null);
                return customerService.getById(newId).orElseGet(() -> {
                    Customer fallback = new Customer();
                    fallback.setId(newId);
                    fallback.setName(nameField.getText().trim());
                    fallback.setPhone(phoneField.getText());
                    return fallback;
                });
            } catch (SQLException ex) {
                showAlert("Error", ex.getMessage());
                return null;
            }
        });

        dialog.showAndWait().ifPresent(this::selectCustomer);
    }

    private void setupTable() {
        colProduct.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(
            d.getValue().getDescription() != null ? d.getValue().getDescription() :
            (d.getValue().getProduct() != null ? d.getValue().getProduct().getName() : "Custom Item")
        ));
        colProduct.setCellFactory(TextFieldTableCell.forTableColumn());
        colProduct.setOnEditCommit(e -> {
            e.getRowValue().setDescription(e.getNewValue());
            linesTable.refresh();
        });

        colQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colQty.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
        colQty.setOnEditCommit(e -> { e.getRowValue().setQuantity(e.getNewValue()); updateTotal(); });

        colPrice.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
        colPrice.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
        colPrice.setOnEditCommit(e -> { e.getRowValue().setUnitPrice(e.getNewValue()); updateTotal(); });

        if (colTaxable != null) {
            colTaxable.setCellValueFactory(cellData -> {
                QuotationLine line = cellData.getValue();
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

    @FXML
    private void handleGlobalTaxToggle() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click Edit first to modify the document.");
            return;
        }
        boolean apply = globalTaxCheck != null && globalTaxCheck.isSelected();
        for (QuotationLine line : lines) {
            line.setTaxable(apply);
        }
        updateTotal();
        linesTable.refresh();
    }

    @FXML
    private void handleAddLine() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click Edit first to modify the document.");
            return;
        }
        Dialog<Product> dialog = new Dialog<>();
        dialog.setTitle("Select Item");
        dialog.setHeaderText("Search products");
        ButtonType addBtn = new ButtonType("Add to Document", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addBtn, ButtonType.CANCEL);

        VBox vbox = new VBox(10); vbox.setPadding(new Insets(10));
        TextField itemSearch = new TextField(); itemSearch.setPromptText("Search product...");
        ListView<Product> itemListView = new ListView<>(); itemListView.setPrefHeight(280);

        itemListView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Product p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) setText(null);
                else setText(p.getProductCode() + " | " + p.getName() + (p.getStockQuantity() <= 0 ? " [OUT OF STOCK]" : " [Stock: " + p.getStockQuantity() + "]"));
            }
        });

        List<Product> products = productService.listAll();
        itemListView.setItems(FXCollections.observableArrayList(products));

        itemSearch.textProperty().addListener((obs, old, newVal) -> {
            if (newVal == null || newVal.isBlank()) itemListView.setItems(FXCollections.observableArrayList(products));
            else {
                String q = newVal.toLowerCase().trim();
                itemListView.setItems(FXCollections.observableArrayList(products.stream()
                    .filter(p -> (p.getName() != null && p.getName().toLowerCase().contains(q)) || 
                                 (p.getProductCode() != null && p.getProductCode().toLowerCase().contains(q)))
                    .collect(Collectors.toList())));
            }
        });

        vbox.getChildren().addAll(itemSearch, itemListView);
        dialog.getDialogPane().setContent(vbox);
        dialog.setResultConverter(btn -> btn == addBtn ? itemListView.getSelectionModel().getSelectedItem() : null);

        dialog.showAndWait().ifPresent(product -> {
            QuotationLine line = new QuotationLine();
            line.setProduct(product);
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
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click Edit first to modify the document.");
            return;
        }
        QuotationLine line = new QuotationLine();
        line.setDescription("Custom Item / Service");
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
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click Edit first to modify the document.");
            return;
        }
        QuotationLine selected = linesTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            lines.remove(selected);
            updateTotal();
        } else {
            showAlert("Notice", "Please select a line item from the table to remove.");
        }
    }

    private void updateTotal() {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;

        for (QuotationLine line : lines) {
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

        if (currentQuotation != null) {
            currentQuotation.setSubtotal(subtotal);
            currentQuotation.setTaxAmount(taxTotal);
            currentQuotation.setTotalAmount(grand);
        }
    }

    @FXML
    private void handleSave() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click Edit first to modify the document.");
            return;
        }
        if (selectedCustomer == null) {
            showAlert("Validation", "Please select a customer."); return;
        }
        if (lines.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Validation", "Cannot save a quotation with empty line items.");
            return;
        }
        
        currentQuotation.setCustomer(selectedCustomer);
        currentQuotation.setCustomerId(selectedCustomer.getId());
        currentQuotation.setQuoteDate(quoteDatePicker.getValue());
        currentQuotation.setStatus(statusCombo.getValue());
        currentQuotation.setLines(lines);

        quotationService.saveQuotation(currentQuotation);
        loadQuotations();
        
        showAlert("Success", "Quotation saved successfully!");
        setFormEditable(false);
    }
    
    @FXML
    private void handleDelete() {
        if (currentQuotation == null || currentQuotation.getId() <= 0) {
            showAlert(Alert.AlertType.WARNING, "Delete", "Please select a saved quotation to delete.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to permanently delete Quotation " + currentQuotation.getQuoteNumber() + "?");
        confirm.showAndWait().ifPresent(res -> {
            if (res == ButtonType.OK) {
                try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
                    conn.setAutoCommit(false);
                    try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM quotation_lines WHERE quotation_id = ?")) {
                        stmt.setInt(1, currentQuotation.getId());
                        stmt.executeUpdate();
                    }
                    try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM quotations WHERE id = ?")) {
                        stmt.setInt(1, currentQuotation.getId());
                        stmt.executeUpdate();
                    }
                    conn.commit();
                    
                    loadQuotations();
                    handleNewQuotation();
                    showAlert("Success", "Quotation deleted successfully.");
                } catch (Exception e) {
                    showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to delete: " + e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleNewQuotation() {
        currentQuotation = new Quotation();
        currentQuotation.setQuoteNumber(quotationService.generateNextQuoteNumber());
        quoteNumberField.setText(currentQuotation.getQuoteNumber());
        quoteDatePicker.setValue(LocalDate.now());
        statusCombo.setValue("DRAFT");
        lines.clear(); 
        customerSearchField.clear(); 
        customerPhoneField.clear(); 
        selectedCustomer = null;
        
        if (globalTaxCheck != null) {
            globalTaxCheck.setSelected(TaxUtil.isTaxEnabled());
        }
        
        updateTotal();
        setFormEditable(true);
        quotationListView.getSelectionModel().clearSelection();
    }

    private void loadQuotations() {
        allQuotations.setAll(quotationService.getAllQuotations());
        searchField.setText("");
        quotationListView.setItems(allQuotations);
    }

        @FXML
    private void handlePrint() {
        if (currentQuotation == null || currentQuotation.getId() <= 0) {
            showAlert(Alert.AlertType.WARNING, "Not Saved", "Please save the quotation first before printing.");
            return;
        }
        try {
            // This calls the JasperReports engine we built in Option A
            com.aceerp.service.ReportService.printQuotation(currentQuotation.getId());
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Print Error", "Failed to generate quotation report: " + e.getMessage());
            e.printStackTrace();
        }
    }

    
    @FXML
    private void handleConvertToSO() {
        if (currentQuotation == null || currentQuotation.getId() <= 0) {
            showAlert(Alert.AlertType.WARNING, "No Quotation Selected", "Please select a saved quotation first.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Convert to Sales Order");
        confirm.setHeaderText("Create Sales Order from Quotation " + currentQuotation.getQuoteNumber() + "?");
        confirm.setContentText("This will convert the quote into an active Sales Order.");

        confirm.showAndWait().ifPresent(response -> {
            if (response != ButtonType.OK) return;

            try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
                conn.setAutoCommit(false);
                try {
                    String nextSoNum = generateNextSoNumber(conn);

                    boolean hasOrderNumber = columnExists(conn, "sales_orders", "order_number");
                    String insertSo = hasOrderNumber
                            ? "INSERT INTO sales_orders (so_number, order_number, quotation_id, customer_id, order_date, status, subtotal, tax_amount, total_amount, notes) VALUES (?, ?, ?, ?, CURRENT_DATE, 'APPROVED', ?, ?, ?, ?) RETURNING id"
                            : "INSERT INTO sales_orders (so_number, quotation_id, customer_id, order_date, status, subtotal, tax_amount, total_amount, notes) VALUES (?, ?, ?, CURRENT_DATE, 'APPROVED', ?, ?, ?, ?) RETURNING id";

                    int newSoId;
                    try (PreparedStatement stmt = conn.prepareStatement(insertSo)) {
                        int i = 1;
                        stmt.setString(i++, nextSoNum);
                        if (hasOrderNumber) stmt.setString(i++, nextSoNum); // order_number alias
                        stmt.setInt(i++, currentQuotation.getId());
                        stmt.setInt(i++, currentQuotation.getCustomerId());
                        stmt.setBigDecimal(i++, nz(currentQuotation.getSubtotal()));
                        stmt.setBigDecimal(i++, nz(currentQuotation.getTaxAmount()));
                        stmt.setBigDecimal(i++, nz(currentQuotation.getTotalAmount()));
                        stmt.setString(i, "Converted from Quote " + currentQuotation.getQuoteNumber());
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (!rs.next()) throw new SQLException("Sales order insert returned no id");
                            newSoId = rs.getInt(1);
                        }
                    }

                    // Copy lines to both total columns to be fully compatible
                    String copyLines =
                            "INSERT INTO sales_order_lines (" +
                            "so_id, product_id, description, quantity, unit_price, tax_rate, tax_amount, line_total, total_price" +
                            ") SELECT ?, product_id, description, quantity, unit_price, " +
                            "COALESCE(tax_rate, 0), COALESCE(tax_amount, 0), " +
                            "COALESCE(line_total, total_price, quantity * unit_price, 0), " +
                            "COALESCE(total_price, line_total, quantity * unit_price, 0) " +
                            "FROM quotation_lines WHERE quotation_id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(copyLines)) {
                        stmt.setInt(1, newSoId);
                        stmt.setInt(2, currentQuotation.getId());
                        stmt.executeUpdate();
                    }

                    try (PreparedStatement stmt = conn.prepareStatement("UPDATE quotations SET status = 'ACCEPTED' WHERE id = ?")) {
                        stmt.setInt(1, currentQuotation.getId());
                        stmt.executeUpdate();
                    }

                    conn.commit();
                    showAlert(Alert.AlertType.INFORMATION, "Success", "Sales Order " + nextSoNum + " created successfully!");
                    loadQuotations();
                    setFormEditable(false);
                } catch (Exception ex) {
                    conn.rollback();
                    throw ex;
                }
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Conversion Error", e.getMessage());
            }
        });
    }

    // ADDED FEATURE: Direct conversion from Quotation to Invoice
    @FXML
    private void handleConvertToInvoice() {
        if (currentQuotation == null || currentQuotation.getId() <= 0) {
            showAlert(Alert.AlertType.WARNING, "No Quotation Selected", "Please select a saved quotation first.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Convert to Invoice");
        confirm.setHeaderText("Create Sales Invoice from Quotation " + currentQuotation.getQuoteNumber() + "?");
        confirm.setContentText("This will create an active Sales Invoice.");

        confirm.showAndWait().ifPresent(response -> {
            if (response != ButtonType.OK) return;

            try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
                conn.setAutoCommit(false);
                try {
                    String nextInvNum = generateNextInvoiceNumber(conn);

                    String insertInv = 
                            "INSERT INTO invoices (invoice_number, quotation_id, customer_id, invoice_date, status, subtotal, tax_amount, total_amount, paid_amount, notes) " +
                            "VALUES (?, ?, ?, CURRENT_DATE, 'DRAFT', ?, ?, ?, 0, ?) RETURNING id";

                    int newInvId;
                    try (PreparedStatement stmt = conn.prepareStatement(insertInv)) {
                        stmt.setString(1, nextInvNum);
                        stmt.setInt(2, currentQuotation.getId());
                        stmt.setInt(3, currentQuotation.getCustomerId());
                        stmt.setBigDecimal(4, nz(currentQuotation.getSubtotal()));
                        stmt.setBigDecimal(5, nz(currentQuotation.getTaxAmount()));
                        stmt.setBigDecimal(6, nz(currentQuotation.getTotalAmount()));
                        stmt.setString(7, "Billed from Quote " + currentQuotation.getQuoteNumber());
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (!rs.next()) throw new SQLException("Invoice insert returned no id");
                            newInvId = rs.getInt(1);
                        }
                    }

                    boolean hasTotalPrice = columnExists(conn, "invoice_lines", "total_price");
                    boolean hasLineTotal = columnExists(conn, "invoice_lines", "line_total");
                    
                    String cols = "invoice_id, product_id, description, quantity, unit_price, tax_rate, tax_amount";
                    String vals = "?, product_id, description, quantity, unit_price, COALESCE(tax_rate, 0), COALESCE(tax_amount, 0)";
                    
                    if (hasLineTotal && hasTotalPrice) {
                        cols += ", line_total, total_price";
                        vals += ", COALESCE(line_total, total_price, quantity * unit_price, 0), COALESCE(total_price, line_total, quantity * unit_price, 0)";
                    } else if (hasTotalPrice) {
                        cols += ", total_price";
                        vals += ", COALESCE(total_price, line_total, quantity * unit_price, 0)";
                    } else {
                        cols += ", line_total";
                        vals += ", COALESCE(line_total, total_price, quantity * unit_price, 0)";
                    }

                    String copyLines = "INSERT INTO invoice_lines (" + cols + ") SELECT " + vals + " FROM quotation_lines WHERE quotation_id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(copyLines)) {
                        stmt.setInt(1, newInvId);
                        stmt.setInt(2, currentQuotation.getId());
                        stmt.executeUpdate();
                    }

                    try (PreparedStatement stmt = conn.prepareStatement("UPDATE quotations SET status = 'INVOICED' WHERE id = ?")) {
                        stmt.setInt(1, currentQuotation.getId());
                        stmt.executeUpdate();
                    }

                    conn.commit();
                    showAlert(Alert.AlertType.INFORMATION, "Success", "Invoice " + nextInvNum + " created successfully!");
                    loadQuotations();
                    setFormEditable(false);
                } catch (Exception ex) {
                    conn.rollback();
                    throw ex;
                }
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Conversion Error", e.getMessage());
            }
        });
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private String generateNextSoNumber(Connection conn) {
        String sql = "SELECT COALESCE(so_number, order_number) AS num FROM sales_orders WHERE COALESCE(so_number, order_number) IS NOT NULL ORDER BY id DESC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String last = rs.getString(1);
                if (last != null && last.toUpperCase().startsWith("SO-")) {
                    String digits = last.substring(3).replaceAll("[^0-9]", "");
                    if (!digits.isEmpty()) return String.format("SO-%05d", Integer.parseInt(digits) + 1);
                }
            }
        } catch (SQLException e) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT so_number FROM sales_orders ORDER BY id DESC LIMIT 1"); ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String last = rs.getString(1);
                    if (last != null && last.toUpperCase().startsWith("SO-")) {
                        String digits = last.substring(3).replaceAll("[^0-9]", "");
                        if (!digits.isEmpty()) return String.format("SO-%05d", Integer.parseInt(digits) + 1);
                    }
                }
            } catch (SQLException ignored) {}
        }
        return "SO-00001";
    }

    private String generateNextInvoiceNumber(Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT invoice_number FROM invoices ORDER BY id DESC LIMIT 1"); ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                String last = rs.getString(1);
                if (last != null && last.toUpperCase().startsWith("INV-")) {
                    String digits = last.substring(4).replaceAll("[^0-9]", "");
                    if (!digits.isEmpty()) return String.format("INV-%05d", Integer.parseInt(digits) + 1);
                }
            }
        } catch (SQLException ignored) {}
        return "INV-00001";
    }

    private boolean columnExists(Connection conn, String table, String column) {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, table, column)) {
            if (rs.next()) return true;
        } catch (SQLException ignored) {}
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, table.toLowerCase(), column.toLowerCase())) {
            if (rs.next()) return true;
        } catch (SQLException ignored) {}
        return false;
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