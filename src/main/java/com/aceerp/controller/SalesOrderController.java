package com.aceerp.controller;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.Customer;
import com.aceerp.model.Product;
import com.aceerp.model.SalesOrder;
import com.aceerp.model.SalesOrderLine;
import com.aceerp.service.CustomerService;
import com.aceerp.service.ProductService;
import com.aceerp.service.SalesOrderService;
import com.aceerp.util.AlertHelper;
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
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

public class SalesOrderController {

    @FXML private TextField searchField;
    @FXML private TextField customerSearchField;
    @FXML private TextField customerPhoneField;
    @FXML private TextField soNumberField;
    @FXML private ListView<SalesOrder> salesOrderListView;
    @FXML private DatePicker orderDatePicker;
    @FXML private ComboBox<String> statusCombo;
    @FXML private TableView<SalesOrderLine> linesTable;
    @FXML private TableColumn<SalesOrderLine, String> colProduct;
    @FXML private TableColumn<SalesOrderLine, BigDecimal> colQty;
    @FXML private TableColumn<SalesOrderLine, BigDecimal> colPrice;
    @FXML private TableColumn<SalesOrderLine, BigDecimal> colTotal;
    @FXML private Label totalLabel;
    @FXML private VBox detailsPane;

    private final SalesOrderService salesOrderService = new SalesOrderService();
    private final CustomerService customerService = new CustomerService();
    private final ProductService productService = new ProductService();

    private Customer selectedCustomer;
    private final ObservableList<SalesOrderLine> lines = FXCollections.observableArrayList();
    private SalesOrder currentSalesOrder;
    private List<SalesOrder> allSalesOrders = List.of();
    private boolean editMode = false;

    private Popup customerPopup;
    private ListView<Customer> customerListView;

    @FXML
    public void initialize() {
        setupSmartSearch();
        setupTable();
        setupListAndSelection();
        loadSalesOrders();
        statusCombo.setItems(FXCollections.observableArrayList("DRAFT", "APPROVED", "SHIPPED", "INVOICED", "CANCELLED"));
        handleNewSalesOrder();
    }

    private void setupListAndSelection() {
        salesOrderListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(SalesOrder item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String custName = item.getCustomer() != null ? item.getCustomer().getName() : "Unknown Customer";
                    BigDecimal total = item.getTotalAmount() != null ? item.getTotalAmount() : BigDecimal.ZERO;
                    setText(item.getSoNumber() + " | " + custName + " | " + item.getStatus() + " | " + CurrencyFormatter.format(total));
                }
            }
        });

        salesOrderListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadSalesOrderToForm(newVal);
            }
        });

        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> filterSalesOrders(newVal));
        }
    }

    private void loadSalesOrderToForm(SalesOrder so) {
        currentSalesOrder = so;
        soNumberField.setText(so.getSoNumber());
        orderDatePicker.setValue(so.getOrderDate() != null ? so.getOrderDate() : LocalDate.now());
        statusCombo.setValue(so.getStatus() != null ? so.getStatus() : "DRAFT");

        selectedCustomer = so.getCustomer();
        if (selectedCustomer != null) {
            customerSearchField.setText(selectedCustomer.getName());
            customerPhoneField.setText(selectedCustomer.getPhone() != null ? selectedCustomer.getPhone() : "N/A");
        } else {
            customerSearchField.clear();
            customerPhoneField.clear();
        }

        lines.clear();
        if (so.getLines() != null) {
            lines.addAll(so.getLines());
        }
        updateTotal();
        setFormEditable(false);
    }

    private void filterSalesOrders(String query) {
        if (query == null || query.isBlank()) {
            salesOrderListView.setItems(FXCollections.observableArrayList(allSalesOrders));
            return;
        }
        String q = query.toLowerCase().trim();
        List<SalesOrder> filtered = allSalesOrders.stream()
                .filter(so -> (so.getSoNumber() != null && so.getSoNumber().toLowerCase().contains(q)) ||
                              (so.getCustomer() != null && so.getCustomer().getName() != null && so.getCustomer().getName().toLowerCase().contains(q)) ||
                              (so.getStatus() != null && so.getStatus().toLowerCase().contains(q)))
                .collect(Collectors.toList());
        salesOrderListView.setItems(FXCollections.observableArrayList(filtered));
    }

    private void setFormEditable(boolean editable) {
        editMode = editable;
        customerSearchField.setDisable(!editable);
        orderDatePicker.setDisable(!editable);
        statusCombo.setDisable(!editable);
        linesTable.setEditable(editable);
    }

    @FXML
    private void handleEdit() {
        if (currentSalesOrder == null || currentSalesOrder.getId() <= 0) {
            showAlert("No Selection", "Please select a Sales Order from the list first.");
            return;
        }
        setFormEditable(true);
        showAlert("Edit Mode", "Sales Order is now editable. Make your changes and click Save.");
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
    private void handleQuickAddCustomer() {
        if (!editMode) {
            showAlert("View Mode", "Click Edit first to modify this Sales Order.");
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

        colTotal.setCellValueFactory(new PropertyValueFactory<>("totalPrice"));
        linesTable.setItems(lines);
    }

    @FXML
    private void handleAddLine() {
        if (!editMode) {
            showAlert("View Mode", "Click Edit first to modify this Sales Order.");
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
            SalesOrderLine line = new SalesOrderLine();
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
        if (!editMode) {
            showAlert("View Mode", "Click Edit first to modify this Sales Order.");
            return;
        }
        SalesOrderLine line = new SalesOrderLine();
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
        if (!editMode) {
            showAlert("View Mode", "Click Edit first to modify this Sales Order.");
            return;
        }
        SalesOrderLine selected = linesTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            lines.remove(selected);
            updateTotal();
        } else {
            showAlert("Notice", "Please select a line item from the table to remove.");
        }
    }

    private void updateTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (SalesOrderLine line : lines) {
            line.recalculateTotal();
            if (line.getTotalPrice() != null) total = total.add(line.getTotalPrice());
        }
        linesTable.refresh();
        totalLabel.setText("Total: " + CurrencyFormatter.format(total));
    }

    @FXML
    private void handleSave() {
        if (!editMode) {
            showAlert("View Mode", "Click Edit first to modify this Sales Order.");
            return;
        }
        if (selectedCustomer == null) {
            showAlert("Validation", "Please select a customer."); return;
        }
        if (currentSalesOrder == null) {
            currentSalesOrder = new SalesOrder();
        }
        currentSalesOrder.setCustomer(selectedCustomer);
        currentSalesOrder.setOrderDate(orderDatePicker.getValue());
        currentSalesOrder.setStatus(statusCombo.getValue() != null ? statusCombo.getValue() : "DRAFT");
        currentSalesOrder.setLines(lines);

        salesOrderService.save(currentSalesOrder);
        loadSalesOrders();
        setFormEditable(false);
        showAlert("Success", "Sales Order saved successfully!");
    }

    @FXML
    private void handleDelete() {
        if (currentSalesOrder == null || currentSalesOrder.getId() <= 0) {
            showAlert("Notice", "Please select an existing Sales Order to delete.");
            return;
        }

        boolean confirmed = AlertHelper.confirm("Delete Sales Order",
            "Are you sure you want to delete Sales Order #" + currentSalesOrder.getSoNumber() + "?");
        if (!confirmed) return;

        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmtLines = conn.prepareStatement("DELETE FROM sales_order_lines WHERE sales_order_id = ?");
                 PreparedStatement stmtSO = conn.prepareStatement("DELETE FROM sales_orders WHERE id = ?")) {

                stmtLines.setInt(1, currentSalesOrder.getId());
                stmtLines.executeUpdate();

                stmtSO.setInt(1, currentSalesOrder.getId());
                stmtSO.executeUpdate();

                conn.commit();
                showAlert("Success", "Sales Order deleted successfully.");
                handleNewSalesOrder();
                loadSalesOrders();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException e) {
            showAlert("Error", "Could not delete sales order: " + e.getMessage());
        }
    }

    @FXML
    private void handleNewSalesOrder() {
        currentSalesOrder = new SalesOrder();
        currentSalesOrder.setSoNumber(salesOrderService.generateNextSONumber());
        soNumberField.setText(currentSalesOrder.getSoNumber());
        orderDatePicker.setValue(LocalDate.now());
        statusCombo.setValue("DRAFT");
        lines.clear(); customerSearchField.clear(); customerPhoneField.clear(); selectedCustomer = null;
        salesOrderListView.getSelectionModel().clearSelection();
        setFormEditable(true);
        updateTotal();
    }

    private void loadSalesOrders() {
        allSalesOrders = salesOrderService.listAll();
        salesOrderListView.setItems(FXCollections.observableArrayList(allSalesOrders));
    }

    @FXML
    private void handlePrint() {
        Dialog<Void> previewDialog = new Dialog<>();
        previewDialog.setTitle("Print Preview");
        previewDialog.setHeaderText("Preview Sales Order: " + soNumberField.getText());
        ButtonType printBtnType = new ButtonType("Confirm Print", ButtonBar.ButtonData.OK_DONE);
        previewDialog.getDialogPane().getButtonTypes().addAll(printBtnType, ButtonType.CANCEL);

        VBox printDoc = new VBox(20);
        printDoc.setPadding(new Insets(40));
        printDoc.setStyle("-fx-background-color: white;");
        printDoc.setPrefSize(595, 842);

        Label header = new Label("SALES ORDER: " + soNumberField.getText());
        header.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");
        Label customerInfo = new Label("Customer: " + (selectedCustomer != null ? selectedCustomer.getName() : "N/A") +
                                     "\nPhone: " + customerPhoneField.getText());
        Label dateInfo = new Label("Date: " + (orderDatePicker.getValue() != null ? orderDatePicker.getValue().toString() : ""));

        GridPane table = new GridPane();
        table.setHgap(30); table.setVgap(10);
        table.setStyle("-fx-border-color: #ddd; -fx-border-width: 1; -fx-padding: 10;");
        table.add(new Label("Product"), 0, 0); table.add(new Label("Qty"), 1, 0); table.add(new Label("Price"), 2, 0); table.add(new Label("Total"), 3, 0);

        int row = 1;
        for (SalesOrderLine line : lines) {
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

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title); alert.setHeaderText(null); alert.setContentText(content); alert.showAndWait();
    }
}