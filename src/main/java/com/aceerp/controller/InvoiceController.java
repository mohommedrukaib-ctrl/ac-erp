package com.aceerp.controller;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.Customer;
import com.aceerp.model.Invoice;
import com.aceerp.model.InvoiceLine;
import com.aceerp.model.Product;
import com.aceerp.service.CustomerService;
import com.aceerp.service.InvoiceService;
import com.aceerp.service.ProductService;
import com.aceerp.util.CurrencyFormatter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.print.PrinterJob;
import javafx.scene.control.*;
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
    @FXML private TableColumn<InvoiceLine, BigDecimal> colTotal;
    @FXML private Label totalLabel;
    @FXML private VBox detailsPane;

    private final InvoiceService invoiceService = new InvoiceService();
    private final CustomerService customerService = new CustomerService();
    private final ProductService productService = new ProductService();

    private Customer selectedCustomer;
    private final ObservableList<InvoiceLine> lines = FXCollections.observableArrayList();
    private Invoice currentInvoice;

    private Popup customerPopup;
    private ListView<Customer> customerListView;

    @FXML
    public void initialize() {
        setupSmartSearch();
        setupTable();
        loadInvoices();
        statusCombo.setItems(FXCollections.observableArrayList("DRAFT", "SENT", "PARTIALLY_PAID", "PAID", "OVERDUE", "CANCELLED"));
        handleNewInvoice();
    }

    private void setupSmartSearch() {
        customerPopup = new Popup();
        customerPopup.setAutoHide(true);
        customerListView = new ListView<>();
        customerListView.setPrefHeight(150);
        customerPopup.getContent().add(customerListView);

        customerSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
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
    private void handleQuickAddCustomer() {
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
                showAlert("Validation", "Customer name is required."); return null;
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
                showAlert("Error", ex.getMessage()); return null;
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
            e.getRowValue().setDescription(e.getNewValue());
            linesTable.refresh();
        });

        colQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colQty.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
        colQty.setOnEditCommit(e -> { e.getRowValue().setQuantity(e.getNewValue()); updateTotal(); });

        colPrice.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
        colPrice.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
        colPrice.setOnEditCommit(e -> { e.getRowValue().setUnitPrice(e.getNewValue()); updateTotal(); });

        colTotal.setCellValueFactory(new PropertyValueFactory<>("totalPrice"));
        linesTable.setItems(lines);
    }

    @FXML
    private void handleAddLine() {
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
            lines.add(line);
            updateTotal();
        });
    }

    @FXML
    private void handleAddEmptyLine() {
        InvoiceLine line = new InvoiceLine();
        line.setDescription("Custom Item / Service");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(BigDecimal.ZERO);
        line.recalculateTotal();
        lines.add(line);
        linesTable.getSelectionModel().select(line);
        updateTotal();
    }

    @FXML
    private void handleRemoveLine() {
        InvoiceLine selected = linesTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            lines.remove(selected);
            updateTotal();
        } else {
            showAlert("Notice", "Please select a line item from the table to remove.");
        }
    }

    private void updateTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (InvoiceLine line : lines) {
            line.recalculateTotal();
            if (line.getTotalPrice() != null) total = total.add(line.getTotalPrice());
        }
        linesTable.refresh();
        totalLabel.setText("Total: " + CurrencyFormatter.format(total));
    }

    @FXML
    private void handleSave() {
        if (selectedCustomer == null) {
            showAlert("Validation", "Please select a customer."); return;
        }
        currentInvoice.setCustomerId(selectedCustomer.getId());
        currentInvoice.setCustomerName(selectedCustomer.getName());
        currentInvoice.setInvoiceDate(invoiceDatePicker.getValue());
        currentInvoice.setStatus(statusCombo.getValue() != null ? statusCombo.getValue() : "DRAFT");
        currentInvoice.setLines(lines);

        invoiceService.saveInvoice(currentInvoice);
        loadInvoices();
        showAlert("Success", "Invoice saved successfully!");
    }

    @FXML
    private void handleNewInvoice() {
        currentInvoice = new Invoice();
        currentInvoice.setInvoiceNumber(invoiceService.generateNextInvoiceNumber());
        invoiceNumberField.setText(currentInvoice.getInvoiceNumber());
        invoiceDatePicker.setValue(LocalDate.now());
        statusCombo.setValue("DRAFT");
        lines.clear(); customerSearchField.clear(); customerPhoneField.clear(); selectedCustomer = null;
        updateTotal();
    }

    private void loadInvoices() {
        invoiceListView.setItems(FXCollections.observableArrayList(invoiceService.listAll()));
    }

    @FXML
    private void handlePrint() {
        Dialog<Void> previewDialog = new Dialog<>();
        previewDialog.setTitle("Print Preview");
        previewDialog.setHeaderText("Preview Tax Invoice: " + invoiceNumberField.getText());
        ButtonType printBtnType = new ButtonType("Confirm Print", ButtonBar.ButtonData.OK_DONE);
        previewDialog.getDialogPane().getButtonTypes().addAll(printBtnType, ButtonType.CANCEL);

        VBox printDoc = new VBox(20);
        printDoc.setPadding(new Insets(40));
        printDoc.setStyle("-fx-background-color: white;");
        printDoc.setPrefSize(595, 842);

        Label header = new Label("TAX INVOICE: " + invoiceNumberField.getText());
        header.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        Label customerInfo = new Label("Customer: " + (selectedCustomer != null ? selectedCustomer.getName() : "N/A") + 
                                     "\nPhone: " + customerPhoneField.getText());
        Label dateInfo = new Label("Date: " + (invoiceDatePicker.getValue() != null ? invoiceDatePicker.getValue().toString() : ""));

        GridPane table = new GridPane();
        table.setHgap(30); table.setVgap(10);
        table.setStyle("-fx-border-color: #ddd; -fx-border-width: 1; -fx-padding: 10;");
        table.add(new Label("Product"), 0, 0); table.add(new Label("Qty"), 1, 0); table.add(new Label("Price"), 2, 0); table.add(new Label("Total"), 3, 0);
        
        int row = 1;
        for (InvoiceLine line : lines) {
            table.add(new Label(line.getDescription()), 0, row);
            table.add(new Label(String.valueOf(line.getQuantity())), 1, row);
            table.add(new Label(CurrencyFormatter.format(line.getUnitPrice())), 2, row);
            table.add(new Label(CurrencyFormatter.format(line.getTotalPrice())), 3, row);
            row++;
        }

        Label totalLabelView = new Label("Grand Total: " + totalLabel.getText().replace("Total: ", ""));
        totalLabelView.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        printDoc.getChildren().addAll(header, customerInfo, dateInfo, table, totalLabelView);

        ScrollPane scroll = new ScrollPane(printDoc);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(500);
        previewDialog.getDialogPane().setContent(scroll);

        Button printButton = (Button) previewDialog.getDialogPane().lookupButton(printBtnType);
        printButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            PrinterJob job = PrinterJob.createPrinterJob();
            if (job != null && job.showPrintDialog(previewDialog.getOwner())) {
                if (job.printPage(printDoc)) {
                    job.endJob();
                    previewDialog.close();
                }
            }
        });

        previewDialog.showAndWait();
    }

    @FXML
    private void handleReceivePayment() {
        if (currentInvoice == null || currentInvoice.getId() <= 0) {
            showAlert(Alert.AlertType.WARNING, "No Invoice Selected", "Please select an invoice to record payment.");
            return;
        }

        BigDecimal total = currentInvoice.getTotalAmount() != null ? currentInvoice.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal paid = currentInvoice.getPaidAmount() != null ? currentInvoice.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal outstanding = total.subtract(paid);

        if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
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

                    // 1. Record Payment Log
                    String paySql = "INSERT INTO payments (payment_number, invoice_id, customer_id, payment_date, amount, notes) VALUES (?, ?, ?, CURRENT_DATE, ?, ?)";
                    try (PreparedStatement stmt = conn.prepareStatement(paySql)) {
                        stmt.setString(1, "PAY-" + System.currentTimeMillis() / 1000);
                        stmt.setInt(2, currentInvoice.getId());
                        stmt.setInt(3, currentInvoice.getCustomerId());
                        stmt.setBigDecimal(4, amountPaid);
                        stmt.setString(5, "Payment for Invoice " + currentInvoice.getInvoiceNumber());
                        stmt.executeUpdate();
                    }

                    // 2. Update Invoice Status & Balance
                    String invSql = "UPDATE invoices SET paid_amount = ?, status = ? WHERE id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(invSql)) {
                        stmt.setBigDecimal(1, newTotalPaid);
                        stmt.setString(2, newStatus);
                        stmt.setInt(3, currentInvoice.getId());
                        stmt.executeUpdate();
                    }

                    conn.commit();
                    showAlert(Alert.AlertType.INFORMATION, "Payment Recorded", "Successfully logged payment of " + CurrencyFormatter.format(amountPaid));
                    loadInvoices(); // Refresh UI
                }
            } catch (NumberFormatException e) {
                showAlert(Alert.AlertType.ERROR, "Invalid Input", "Please enter a valid numeric amount.");
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Payment Error", e.getMessage());
            }
        });
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