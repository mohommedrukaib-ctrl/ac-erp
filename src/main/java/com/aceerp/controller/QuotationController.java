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
    @FXML private TableColumn<QuotationLine, BigDecimal> colTotal;
    @FXML private Label totalLabel;
    @FXML private VBox detailsPane;

    private final QuotationService quotationService = new QuotationService();
    private final CustomerService customerService = new CustomerService();
    private final ProductService productService = new ProductService();

    private Customer selectedCustomer;
    private final ObservableList<QuotationLine> lines = FXCollections.observableArrayList();
    private Quotation currentQuotation;
    
    private Popup customerPopup;
    private ListView<Customer> customerListView;

    @FXML
    public void initialize() {
        setupSmartSearch();
        setupTable();
        loadQuotations();
        statusCombo.setItems(FXCollections.observableArrayList("DRAFT", "SENT", "ACCEPTED", "EXPIRED"));
        handleNewQuotation();
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
        linesTable.setEditable(true);

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
            QuotationLine line = new QuotationLine();
            line.setProduct(product);
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
        QuotationLine line = new QuotationLine();
        line.setDescription("Custom Item / Service");
        line.setQuantity(BigDecimal.ONE);
        line.setUnitPrice(BigDecimal.ZERO);
        line.setTaxRate(BigDecimal.ZERO);
        line.recalculateTotal();
        lines.add(line);
        linesTable.getSelectionModel().select(line);
        updateTotal();
    }

    @FXML
    private void handleRemoveLine() {
        QuotationLine selected = linesTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            lines.remove(selected);
            updateTotal();
        } else {
            showAlert("Notice", "Please select a line item from the table to remove.");
        }
    }

    private void updateTotal() {
    BigDecimal total = BigDecimal.ZERO;

    for (QuotationLine line : lines) {
        line.recalculateTotal();
        if (line.getTotalPrice() != null) {
            total = total.add(line.getTotalPrice());
        }
    }

    linesTable.refresh();
    totalLabel.setText("Total: " + CurrencyFormatter.format(total));
}

    @FXML
    private void handleSave() {
        if (selectedCustomer == null) {
            showAlert("Validation", "Please select a customer."); return;
        }
        currentQuotation.setCustomer(selectedCustomer);
        currentQuotation.setCustomerId(selectedCustomer.getId());
        currentQuotation.setQuoteDate(quoteDatePicker.getValue());
        currentQuotation.setStatus(statusCombo.getValue());
        currentQuotation.setLines(lines);

        quotationService.saveQuotation(currentQuotation);
        loadQuotations();
        showAlert("Success", "Quotation saved successfully!");
    }

    @FXML
    private void handleNewQuotation() {
        currentQuotation = new Quotation();
        currentQuotation.setQuoteNumber(quotationService.generateNextQuoteNumber());
        quoteNumberField.setText(currentQuotation.getQuoteNumber());
        quoteDatePicker.setValue(LocalDate.now());
        statusCombo.setValue("DRAFT");
        lines.clear(); customerSearchField.clear(); customerPhoneField.clear(); selectedCustomer = null;
        updateTotal();
    }

    private void loadQuotations() {
        quotationListView.setItems(FXCollections.observableArrayList(quotationService.getAllQuotations()));
    }

    @FXML
    private void handlePrint() {
        Dialog<Void> previewDialog = new Dialog<>();
        previewDialog.setTitle("Print Preview");
        previewDialog.setHeaderText("Preview Quotation: " + quoteNumberField.getText());
        ButtonType printBtnType = new ButtonType("Confirm Print", ButtonBar.ButtonData.OK_DONE);
        previewDialog.getDialogPane().getButtonTypes().addAll(printBtnType, ButtonType.CANCEL);

        VBox printDoc = new VBox(20);
        printDoc.setPadding(new Insets(40));
        printDoc.setStyle("-fx-background-color: white;");
        printDoc.setPrefSize(595, 842);

        Label header = new Label("QUOTATION: " + quoteNumberField.getText());
        header.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        Label customerInfo = new Label("Customer: " + (selectedCustomer != null ? selectedCustomer.getName() : "N/A") + 
                                     "\nPhone: " + customerPhoneField.getText());
        Label dateInfo = new Label("Date: " + (quoteDatePicker.getValue() != null ? quoteDatePicker.getValue().toString() : ""));

        GridPane table = new GridPane();
        table.setHgap(30); table.setVgap(10);
        table.setStyle("-fx-border-color: #ddd; -fx-border-width: 1; -fx-padding: 10;");
        table.add(new Label("Product"), 0, 0); table.add(new Label("Qty"), 1, 0); table.add(new Label("Price"), 2, 0); table.add(new Label("Total"), 3, 0);
        
        int row = 1;
        for (QuotationLine line : lines) {
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
    private void handleConvertToSO() {
        if (currentQuotation == null || currentQuotation.getId() <= 0) {
            showAlert(Alert.AlertType.WARNING, "No Quotation Selected", "Please select or save a quotation first.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Convert to Sales Order");
        confirm.setHeaderText("Create Sales Order from Quotation " + currentQuotation.getQuoteNumber() + "?");
        confirm.setContentText("This will convert the quote into an active Sales Order.");

        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
                    String nextSoNum = "SO-" + (System.currentTimeMillis() / 1000);
                    
                    String insertSo = "INSERT INTO sales_orders (order_number, quotation_id, customer_id, order_date, status, subtotal, tax_amount, total_amount, notes) " +
                                      "VALUES (?, ?, ?, CURRENT_DATE, 'APPROVED', ?, ?, ?, ?) RETURNING id";
                    
                    int newSoId = 0;
                    try (PreparedStatement stmt = conn.prepareStatement(insertSo)) {
                        stmt.setString(1, nextSoNum);
                        stmt.setInt(2, currentQuotation.getId());
                        stmt.setInt(3, currentQuotation.getCustomerId());
                        stmt.setBigDecimal(4, currentQuotation.getSubtotal() != null ? currentQuotation.getSubtotal() : BigDecimal.ZERO);
                        stmt.setBigDecimal(5, currentQuotation.getTaxAmount() != null ? currentQuotation.getTaxAmount() : BigDecimal.ZERO);
                        stmt.setBigDecimal(6, currentQuotation.getTotalAmount() != null ? currentQuotation.getTotalAmount() : BigDecimal.ZERO);
                        stmt.setString(7, "Converted from Quote " + currentQuotation.getQuoteNumber());
                        ResultSet rs = stmt.executeQuery();
                        if (rs.next()) newSoId = rs.getInt(1);
                    }

                    // Copy line items
                    String copyLines = "INSERT INTO sales_order_lines (so_id, product_id, description, quantity, unit_price, line_total) " +
                                       "SELECT ?, product_id, description, quantity, unit_price, total_price FROM quotation_lines WHERE quotation_id = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(copyLines)) {
                        stmt.setInt(1, newSoId);
                        stmt.setInt(2, currentQuotation.getId());
                        stmt.executeUpdate();
                    }

                    // Mark Quote as ACCEPTED
                    try (PreparedStatement stmt = conn.prepareStatement("UPDATE quotations SET status = 'ACCEPTED' WHERE id = ?")) {
                        stmt.setInt(1, currentQuotation.getId());
                        stmt.executeUpdate();
                    }

                    showAlert(Alert.AlertType.INFORMATION, "Success", "Sales Order " + nextSoNum + " created successfully!");
                    loadQuotations(); // Refresh list
                } catch (Exception e) {
                    showAlert(Alert.AlertType.ERROR, "Conversion Error", e.getMessage());
                }
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