package com.aceerp.controller;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.Customer;
import com.aceerp.model.Invoice;
import com.aceerp.model.InvoiceLine;
import com.aceerp.model.Product;
import com.aceerp.service.CustomerService;
import com.aceerp.service.InvoiceService;
import com.aceerp.service.ProductService;
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
import java.util.List;
import java.util.stream.Collectors;

public class InvoiceController {

    @FXML private TextField searchField;
    @FXML private TextField customerSearchField;
    @FXML private TextField customerPhoneField;
    @FXML private TextField invoiceNumberField;
    @FXML private ListView<Invoice> invoiceListView;
    @FXML private DatePicker invoiceDatePicker;
    @FXML private ComboBox<String> statusCombo;
    @FXML private TableView<InvoiceLine> linesTable;
    @FXML private TableColumn<InvoiceLine, String> colProduct;
    @FXML private TableColumn<InvoiceLine, BigDecimal> colQty;
    @FXML private TableColumn<InvoiceLine, BigDecimal> colPrice;
    @FXML private TableColumn<InvoiceLine, BigDecimal> colDiscount;
    @FXML private TableColumn<InvoiceLine, Boolean> colTaxable;
    @FXML private TableColumn<InvoiceLine, String> colNotes;
    @FXML private TableColumn<InvoiceLine, BigDecimal> colTotal;
    @FXML private Label totalLabel;
    @FXML private Label lblSubtotal;
    @FXML private Label lblTax;
    @FXML private Label lblTaxTitle;
    @FXML private CheckBox globalTaxCheck;
    @FXML private VBox detailsPane;

    private final InvoiceService invoiceService = new InvoiceService();
    private final CustomerService customerService = new CustomerService();
    private final ProductService productService = new ProductService();

    private Customer selectedCustomer;
    private final ObservableList<InvoiceLine> lines = FXCollections.observableArrayList();
    private Invoice currentInvoice;
    private List<Invoice> allInvoices = List.of();
    private boolean editMode = false;

    private Popup customerPopup;
    private ListView<Customer> customerListView;

    @FXML
    public void initialize() {
        setupSmartSearch();
        setupTable();
        setupListAndSelection();
        loadInvoices();
        statusCombo.setItems(FXCollections.observableArrayList("DRAFT", "SENT", "PARTIALLY_PAID", "PAID", "OVERDUE", "CANCELLED"));
        
        if (globalTaxCheck != null) {
            globalTaxCheck.setSelected(TaxUtil.isTaxEnabled());
        }
        if (lblTaxTitle != null) {
            lblTaxTitle.setText(TaxUtil.getTaxName() + ":");
        }
        
        handleNewInvoice();
    }

    private void setupListAndSelection() {
        invoiceListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Invoice item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String custName = item.getCustomerName() != null ? item.getCustomerName() : "Unknown Customer";
                    BigDecimal total = item.getTotalAmount() != null ? item.getTotalAmount() : BigDecimal.ZERO;
                    String status = item.getStatus() != null ? item.getStatus() : "DRAFT";
                    setText(item.getInvoiceNumber() + " | " + custName + " | " + status + " | " + CurrencyFormatter.format(total));
                }
            }
        });

        invoiceListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadInvoiceToForm(newVal);
            }
        });

        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> filterInvoices(newVal));
        }
    }

    private void loadInvoiceToForm(Invoice inv) {
        currentInvoice = inv;
        invoiceNumberField.setText(inv.getInvoiceNumber());
        invoiceDatePicker.setValue(inv.getInvoiceDate() != null ? inv.getInvoiceDate() : LocalDate.now());

        BigDecimal total = inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal paid = inv.getPaidAmount() != null ? inv.getPaidAmount() : BigDecimal.ZERO;
        
        String calculatedStatus = inv.getStatus();
        if (paid.compareTo(BigDecimal.ZERO) > 0) {
            if (total.compareTo(BigDecimal.ZERO) > 0 && paid.compareTo(total) >= 0) {
                calculatedStatus = "PAID";
            } else if (!"CANCELLED".equalsIgnoreCase(calculatedStatus)) {
                calculatedStatus = "PARTIALLY_PAID";
            }
        }
        if (calculatedStatus == null || calculatedStatus.isBlank()) {
            calculatedStatus = "DRAFT";
        }
        inv.setStatus(calculatedStatus);
        statusCombo.setValue(calculatedStatus);

        if (inv.getCustomerId() > 0) {
            selectedCustomer = customerService.getById(inv.getCustomerId()).orElse(null);
        } else {
            selectedCustomer = null;
        }

        if (selectedCustomer != null) {
            customerSearchField.setText(selectedCustomer.getName());
            customerPhoneField.setText(selectedCustomer.getPhone() != null ? selectedCustomer.getPhone() : "N/A");
        } else if (inv.getCustomerName() != null) {
            customerSearchField.setText(inv.getCustomerName());
            customerPhoneField.setText("N/A");
        } else {
            customerSearchField.clear();
            customerPhoneField.clear();
        }

        lines.clear();
        if (inv.getLines() != null) {
            lines.addAll(inv.getLines());
        }
        
        if (globalTaxCheck != null && !lines.isEmpty()) {
            boolean allTaxable = lines.stream().allMatch(InvoiceLine::isTaxable);
            globalTaxCheck.setSelected(allTaxable);
        }
        
        updateTotal();
        setFormEditable(false);
    }

    private boolean isInvoicePaid(Invoice inv) {
        if (inv == null) return false;
        if ("PAID".equalsIgnoreCase(inv.getStatus())) return true;
        if (inv.getPaidAmount() != null && inv.getTotalAmount() != null && inv.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
            return inv.getPaidAmount().compareTo(inv.getTotalAmount()) >= 0;
        }
        return false;
    }

    private void setFormEditable(boolean editable) {
        if (editable && isInvoicePaid(currentInvoice)) {
            editMode = false;
        } else {
            editMode = editable;
        }
        customerSearchField.setDisable(!editMode);
        invoiceDatePicker.setDisable(!editMode);
        statusCombo.setDisable(!editMode);
        linesTable.setEditable(editMode);
        if (globalTaxCheck != null) {
            globalTaxCheck.setDisable(!editMode);
        }
    }

    @FXML
    public void handleEdit() {
        if (currentInvoice == null || currentInvoice.getId() <= 0) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Please select an Invoice from the list first.");
            return;
        }
        if (isInvoicePaid(currentInvoice)) {
            showAlert(Alert.AlertType.WARNING, "Invoice Locked",
                    "This invoice is PAID. You cannot edit, delete, or mark it unpaid.");
            setFormEditable(false);
            return;
        }
        setFormEditable(true);
        showAlert(Alert.AlertType.INFORMATION, "Edit Mode", "Invoice is now editable. Make changes and click Save.");
    }

    private void filterInvoices(String query) {
        if (query == null || query.isBlank()) {
            invoiceListView.setItems(FXCollections.observableArrayList(allInvoices));
            return;
        }
        String q = query.toLowerCase().trim();
        List<Invoice> filtered = allInvoices.stream()
                .filter(inv -> (inv.getInvoiceNumber() != null && inv.getInvoiceNumber().toLowerCase().contains(q)) ||
                               (inv.getCustomerName() != null && inv.getCustomerName().toLowerCase().contains(q)) ||
                               (inv.getStatus() != null && inv.getStatus().toLowerCase().contains(q)))
                .collect(Collectors.toList());
        invoiceListView.setItems(FXCollections.observableArrayList(filtered));
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

    @FXML
    public void handleQuickAddCustomer() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click Edit first to modify this Invoice.");
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
                showAlert(Alert.AlertType.WARNING, "Validation", "Customer name is required."); return null;
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
                showAlert(Alert.AlertType.ERROR, "Error", ex.getMessage()); return null;
            }
        });

        dialog.showAndWait().ifPresent(this::selectCustomer);
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

        if (colDiscount != null) {
            colDiscount.setCellValueFactory(new PropertyValueFactory<>("discountPercent"));
            colDiscount.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
            colDiscount.setOnEditCommit(e -> {
                if (!editMode) { linesTable.refresh(); return; }
                e.getRowValue().setDiscountPercent(e.getNewValue());
                updateTotal();
            });
        }

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

        if (colTaxable != null) {
            colTaxable.setCellValueFactory(cellData -> {
                InvoiceLine line = cellData.getValue();
                SimpleBooleanProperty prop = new SimpleBooleanProperty(line.isTaxable());
                prop.addListener((obs, was, isNow) -> {
                    if (!editMode) { linesTable.refresh(); return; }
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

    // ================= Reorder Lines ↑↓ =================
    @FXML
    public void handleMoveUp() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click Edit first to adjust lines.");
            return;
        }
        int index = linesTable.getSelectionModel().getSelectedIndex();
        if (index > 0) {
            InvoiceLine line = lines.remove(index);
            lines.add(index - 1, line);
            linesTable.getSelectionModel().select(index - 1);
        }
    }

    @FXML
    public void handleMoveDown() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click Edit first to adjust lines.");
            return;
        }
        int index = linesTable.getSelectionModel().getSelectedIndex();
        if (index >= 0 && index < lines.size() - 1) {
            InvoiceLine line = lines.remove(index);
            lines.add(index + 1, line);
            linesTable.getSelectionModel().select(index + 1);
        }
    }

    @FXML
    public void handleGlobalTaxToggle() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click Edit first to modify this Invoice.");
            return;
        }
        boolean apply = globalTaxCheck != null && globalTaxCheck.isSelected();
        for (InvoiceLine line : lines) {
            line.setTaxable(apply);
        }
        updateTotal();
        linesTable.refresh();
    }

    @FXML
    public void handleAddLine() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click Edit first to modify this Invoice.");
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
            InvoiceLine line = new InvoiceLine();
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
    public void handleAddEmptyLine() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click Edit first to modify this Invoice.");
            return;
        }
        InvoiceLine line = new InvoiceLine();
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
    public void handleRemoveLine() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click Edit first to modify this Invoice.");
            return;
        }
        InvoiceLine selected = linesTable.getSelectionModel().getSelectedItem();
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

        for (InvoiceLine line : lines) {
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

        if (currentInvoice != null) {
            currentInvoice.setSubtotal(subtotal);
            currentInvoice.setTaxAmount(taxTotal);
            currentInvoice.setTotalAmount(grand);
        }
    }

    @FXML
    public void handleSave() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click Edit first to modify this Invoice.");
            return;
        }
        if (isInvoicePaid(currentInvoice)) {
            showAlert(Alert.AlertType.WARNING, "Invoice Locked", "Paid invoices cannot be edited or modified.");
            return;
        }
        if (selectedCustomer == null) {
            showAlert(Alert.AlertType.WARNING, "Validation", "Please select a customer."); return;
        }
        if (currentInvoice == null) {
            currentInvoice = new Invoice();
        }

        currentInvoice.setCustomerId(selectedCustomer.getId());
        currentInvoice.setCustomerName(selectedCustomer.getName());
        currentInvoice.setInvoiceDate(invoiceDatePicker.getValue());
        currentInvoice.setLines(lines);

        BigDecimal total = currentInvoice.getTotalAmount() != null ? currentInvoice.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal paid = currentInvoice.getPaidAmount() != null ? currentInvoice.getPaidAmount() : BigDecimal.ZERO;

        String chosenStatus = statusCombo.getValue();
        if (paid.compareTo(BigDecimal.ZERO) > 0) {
            if (total.compareTo(BigDecimal.ZERO) > 0 && paid.compareTo(total) >= 0) {
                chosenStatus = "PAID";
            } else {
                chosenStatus = "PARTIALLY_PAID";
            }
        } else if (chosenStatus == null || chosenStatus.isBlank()) {
            chosenStatus = "DRAFT";
        }

        currentInvoice.setStatus(chosenStatus);
        invoiceService.saveInvoice(currentInvoice);

        int savedId = currentInvoice.getId();
        loadInvoicesAndSelect(savedId);
        setFormEditable(false);

        showAlert(Alert.AlertType.INFORMATION, "Success", "Invoice saved successfully!");
    }

    @FXML
    public void handleDelete() {
        if (currentInvoice == null || currentInvoice.getId() <= 0) {
            showAlert(Alert.AlertType.WARNING, "Notice", "Please select an existing invoice to delete.");
            return;
        }

        if (isInvoicePaid(currentInvoice)) {
            showAlert(Alert.AlertType.WARNING, "Invoice Locked", "Paid invoices cannot be deleted.");
            return;
        }

        boolean confirmed = AlertHelper.confirm("Delete Invoice",
            "Are you sure you want to delete Invoice #" + currentInvoice.getInvoiceNumber() + "?");
        if (!confirmed) return;

        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmtLines = conn.prepareStatement("DELETE FROM invoice_lines WHERE invoice_id = ?");
                 PreparedStatement stmtPay = conn.prepareStatement("DELETE FROM payments WHERE invoice_id = ?");
                 PreparedStatement stmtInv = conn.prepareStatement("DELETE FROM invoices WHERE id = ?")) {

                stmtLines.setInt(1, currentInvoice.getId());
                stmtLines.executeUpdate();

                stmtPay.setInt(1, currentInvoice.getId());
                stmtPay.executeUpdate();

                stmtInv.setInt(1, currentInvoice.getId());
                stmtInv.executeUpdate();

                conn.commit();
                showAlert(Alert.AlertType.INFORMATION, "Success", "Invoice deleted successfully.");
                handleNewInvoice();
                loadInvoices();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Could not delete invoice: " + e.getMessage());
        }
    }

    @FXML
    public void handleNewInvoice() {
        currentInvoice = new Invoice();
        currentInvoice.setInvoiceNumber(invoiceService.generateNextInvoiceNumber());
        invoiceNumberField.setText(currentInvoice.getInvoiceNumber());
        invoiceDatePicker.setValue(LocalDate.now());
        statusCombo.setValue("DRAFT");
        lines.clear(); customerSearchField.clear(); customerPhoneField.clear(); selectedCustomer = null;
        invoiceListView.getSelectionModel().clearSelection();
        
        if (globalTaxCheck != null) {
            globalTaxCheck.setSelected(TaxUtil.isTaxEnabled());
        }
        
        setFormEditable(true);
        updateTotal();
    }

    @FXML
    public void loadInvoices() {
        allInvoices = invoiceService.listAll();
        invoiceListView.setItems(FXCollections.observableArrayList(allInvoices));
    }

    private void loadInvoicesAndSelect(int targetId) {
        allInvoices = invoiceService.listAll();
        invoiceListView.setItems(FXCollections.observableArrayList(allInvoices));
        if (targetId > 0) {
            for (Invoice inv : allInvoices) {
                if (inv.getId() == targetId) {
                    invoiceListView.getSelectionModel().select(inv);
                    loadInvoiceToForm(inv);
                    return;
                }
            }
        }
    }

    @FXML
    public void handlePrint() {
        if (currentInvoice == null || currentInvoice.getId() <= 0) {
            showAlert(Alert.AlertType.WARNING, "Print", "Please select a saved invoice to print.");
            return;
        }
        
        com.aceerp.service.ReportService.printInvoice(currentInvoice.getId());
    }

    @FXML
    public void handleReceivePayment() {
        if (currentInvoice == null || currentInvoice.getId() <= 0) {
            showAlert(Alert.AlertType.WARNING, "No Invoice Selected", "Please select an invoice to record payment.");
            return;
        }

        BigDecimal total = currentInvoice.getTotalAmount() != null ? currentInvoice.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal paid = currentInvoice.getPaidAmount() != null ? currentInvoice.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal outstanding = total.subtract(paid);

        if (outstanding.compareTo(BigDecimal.ZERO) <= 0 || "PAID".equalsIgnoreCase(currentInvoice.getStatus())) {
            showAlert(Alert.AlertType.INFORMATION, "Already Paid", "This invoice is already fully paid.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog(outstanding.setScale(2, RoundingMode.HALF_UP).toString());
        dialog.setTitle("Receive Customer Payment");
        dialog.setHeaderText("Invoice #" + currentInvoice.getInvoiceNumber() + "\nOutstanding: " + CurrencyFormatter.format(outstanding));
        dialog.setContentText("Enter Amount Received:");

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

                    boolean hasPaymentType = columnExists(conn, "payments", "payment_type");
                    boolean hasPaymentMethod = columnExists(conn, "payments", "payment_method");
                    boolean hasCreatedBy = columnExists(conn, "payments", "created_by");

                    StringBuilder cols = new StringBuilder("payment_number, invoice_id, customer_id, payment_date, amount, notes");
                    StringBuilder vals = new StringBuilder("?, ?, ?, CURRENT_DATE, ?, ?");

                    if (hasPaymentType) {
                        cols.append(", payment_type");
                        vals.append(", 'CASH'");
                    }
                    if (hasPaymentMethod) {
                        cols.append(", payment_method");
                        vals.append(", 'CASH'");
                    }
                    if (hasCreatedBy && com.aceerp.model.UserSession.getInstance().isLoggedIn()) {
                        cols.append(", created_by");
                        vals.append(", ?");
                    }

                    String paySql = "INSERT INTO payments (" + cols + ") VALUES (" + vals + ")";
                    try (PreparedStatement stmt = conn.prepareStatement(paySql)) {
                        int idx = 1;
                        stmt.setString(idx++, "PAY-" + System.currentTimeMillis() / 1000);
                        stmt.setInt(idx++, currentInvoice.getId());
                        stmt.setInt(idx++, currentInvoice.getCustomerId());
                        stmt.setBigDecimal(idx++, amountPaid);
                        stmt.setString(idx++, "Payment for Invoice " + currentInvoice.getInvoiceNumber());
                        if (hasCreatedBy && com.aceerp.model.UserSession.getInstance().isLoggedIn()) {
                            stmt.setInt(idx++, com.aceerp.model.UserSession.getInstance().getCurrentUser().getId());
                        }
                        stmt.executeUpdate();
                    }

                    String invSql = "UPDATE invoices SET paid_amount = ?, status = ? WHERE id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(invSql)) {
                        stmt.setBigDecimal(1, newTotalPaid);
                        stmt.setString(2, newStatus);
                        stmt.setInt(3, currentInvoice.getId());
                        stmt.executeUpdate();
                    }

                    conn.commit();

                    currentInvoice.setPaidAmount(newTotalPaid);
                    currentInvoice.setStatus(newStatus);
                    int activeId = currentInvoice.getId();

                    showAlert(Alert.AlertType.INFORMATION, "Payment Recorded", "Successfully logged payment of " + CurrencyFormatter.format(amountPaid));
                    loadInvoicesAndSelect(activeId);
                }
            } catch (NumberFormatException e) {
                showAlert(Alert.AlertType.ERROR, "Invalid Input", "Please enter a valid numeric amount.");
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Payment Error", e.getMessage());
            }
        });
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

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}