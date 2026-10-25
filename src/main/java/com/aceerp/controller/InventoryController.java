package com.aceerp.controller;

import com.aceerp.model.Product;
import com.aceerp.model.StockMovement;
import com.aceerp.model.UserSession;
import com.aceerp.service.InventoryService;
import com.aceerp.util.AlertHelper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller for the Inventory page.
 * Shows stock movement history and provides buttons to record
 * Stock In, Stock Out, and Stock Adjustments.
 * 
 * The "smart product picker" lets you type to search products
 * instead of scrolling through a long dropdown.
 */
public class InventoryController implements Initializable {

    // --- FXML fields (must match inventory.fxml exactly) ---
    @FXML private TextField searchField;
    @FXML private TableView<StockMovement> movementsTable;
    @FXML private TableColumn<StockMovement, String> colDate;
    @FXML private TableColumn<StockMovement, String> colProduct;
    @FXML private TableColumn<StockMovement, String> colType;
    @FXML private TableColumn<StockMovement, String> colQty;
    @FXML private TableColumn<StockMovement, String> colBefore;
    @FXML private TableColumn<StockMovement, String> colAfter;
    @FXML private TableColumn<StockMovement, String> colRef;
    @FXML private TableColumn<StockMovement, String> colUser;
    @FXML private TableColumn<StockMovement, String> colNotes;
    @FXML private Label lowStockLabel;
    @FXML private ToggleButton btnAll;
    @FXML private ToggleButton btnIn;
    @FXML private ToggleButton btnOut;
    @FXML private ToggleButton btnAdjust;

    private final InventoryService inventoryService = new InventoryService();
    private ObservableList<StockMovement> allMovements = FXCollections.observableArrayList();
    private FilteredList<StockMovement> filteredMovements;
    private String currentFilter = "ALL";
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTableColumns();
        setupSearch();
        setupFilterButtons();
        loadMovements();
        loadLowStockAlerts();
    }

    // ==================== TABLE SETUP ====================

    /** Configures what each column shows */
    private void setupTableColumns() {
        colDate.setCellValueFactory(c -> {
            if (c.getValue().getCreatedAt() != null) {
                return new javafx.beans.property.SimpleStringProperty(
                    dateFormat.format(c.getValue().getCreatedAt()));
            }
            return new javafx.beans.property.SimpleStringProperty("");
        });
        colProduct.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().getProductDisplay()));
        colType.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().getTypeDisplay()));
        colQty.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().getQuantityDisplay()));
        colBefore.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(String.valueOf(c.getValue().getPreviousStock())));
        colAfter.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(String.valueOf(c.getValue().getNewStock())));
        colRef.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().getReferenceDisplay()));
        colUser.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(
                c.getValue().getCreatedByName() != null ? c.getValue().getCreatedByName() : ""));
        colNotes.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(
                c.getValue().getNotes() != null ? c.getValue().getNotes() : ""));
    }

    /** Filters the table as you type in the search box */
    private void setupSearch() {
        filteredMovements = new FilteredList<>(allMovements, m -> true);
        movementsTable.setItems(filteredMovements);

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            applyFilters();
        });
    }

    /** Makes filter buttons work as a group (only one selected at a time) */
    private void setupFilterButtons() {
        ToggleGroup filterGroup = new ToggleGroup();
        btnAll.setToggleGroup(filterGroup);
        btnIn.setToggleGroup(filterGroup);
        btnOut.setToggleGroup(filterGroup);
        btnAdjust.setToggleGroup(filterGroup);

        btnAll.setOnAction(e -> { currentFilter = "ALL"; applyFilters(); });
        btnIn.setOnAction(e -> { currentFilter = "IN"; applyFilters(); });
        btnOut.setOnAction(e -> { currentFilter = "OUT"; applyFilters(); });
        btnAdjust.setOnAction(e -> { currentFilter = "ADJUSTMENT"; applyFilters(); });
    }

    /** Applies both the type filter and the text search */
    private void applyFilters() {
        String searchText = searchField.getText().toLowerCase().trim();

        filteredMovements.setPredicate(m -> {
            // Type filter
            if (!"ALL".equals(currentFilter) && !currentFilter.equals(m.getMovementType())) {
                return false;
            }
            // Text search
            if (!searchText.isEmpty()) {
                String productDisplay = m.getProductDisplay().toLowerCase();
                String ref = m.getReferenceDisplay().toLowerCase();
                String notes = m.getNotes() != null ? m.getNotes().toLowerCase() : "";
                if (!productDisplay.contains(searchText) &&
                    !ref.contains(searchText) &&
                    !notes.contains(searchText)) {
                    return false;
                }
            }
            return true;
        });
    }

    // ==================== DATA LOADING ====================

    private void loadMovements() {
        try {
            List<StockMovement> movements = inventoryService.getRecentMovements("ALL", 500);
            allMovements.setAll(movements);
        } catch (Exception e) {
            AlertHelper.error("Load Error", "Failed to load stock movements");
        }
    }

    private void loadLowStockAlerts() {
        try {
            List<Product> lowStock = inventoryService.getLowStockProducts();
            if (lowStock.isEmpty()) {
                lowStockLabel.setText("All products are well stocked.");
            } else {
                StringBuilder sb = new StringBuilder();
                for (Product p : lowStock) {
                    sb.append("- ").append(p.getProductCode()).append(" ")
                      .append(p.getName())
                      .append(" (").append(p.getStockQuantity())
                      .append(" left, min: ").append(p.getMinStockLevel()).append(")\n");
                }
                lowStockLabel.setText(sb.toString().trim());
            }
        } catch (Exception e) {
            lowStockLabel.setText("Could not load alerts.");
        }
    }

    // ==================== ACTION HANDLERS ====================

    @FXML
    private void handleStockIn() {
        showStockDialog("Stock In", "IN");
    }

    @FXML
    private void handleStockOut() {
        showStockDialog("Stock Out", "OUT");
    }

    @FXML
    private void handleAdjust() {
        showAdjustDialog();
    }

    @FXML
    private void handleFilterAll() { /* handled by setupFilterButtons */ }
    @FXML
    private void handleFilterIn() { /* handled by setupFilterButtons */ }
    @FXML
    private void handleFilterOut() { /* handled by setupFilterButtons */ }
    @FXML
    private void handleFilterAdjust() { /* handled by setupFilterButtons */ }

    // ==================== SMART PRODUCT PICKER DIALOG ====================

    /**
     * Shows a dialog for Stock In or Stock Out.
     * Includes a smart product picker: type to search, click to select.
     */
    private void showStockDialog(String title, String movementType) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText("Record " + title);

        // --- Smart Product Picker ---
        Label productLabel = new Label("Search Product:");
        TextField productSearch = new TextField();
        productSearch.setPromptText("Type product name or code...");

        ListView<Product> productList = new ListView<>();
        productList.setPrefHeight(120);

        Label selectedLabel = new Label("No product selected");
        selectedLabel.setStyle("-fx-font-weight: bold;");

        final Product[] selected = {null};

        // SMART SEARCH: as you type, filter the product list
        productSearch.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.trim().length() >= 1) {
                List<Product> results = inventoryService.searchProducts(newVal.trim());
                productList.getItems().setAll(results);
                if (results.isEmpty()) {
                    selectedLabel.setText("No products found. Add in Products page first.");
                    selectedLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: red;");
                }
            } else {
                productList.getItems().clear();
                selectedLabel.setText("No product selected");
                selectedLabel.setStyle("-fx-font-weight: bold;");
            }
            selected[0] = null;
        });

        // When user clicks a product in the list, select it
        productList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selected[0] = newVal;
                selectedLabel.setText("Selected: " + newVal.getProductCode() +
                    " - " + newVal.getName() +
                    " (Current stock: " + newVal.getStockQuantity() + ")");
                selectedLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: green;");
            }
        });

        // --- Other fields ---
        Label qtyLabel = new Label("Quantity:");
        TextField qtyField = new TextField();
        qtyField.setPromptText("Enter number of units");

        Label refLabel = new Label("Reference:");
        TextField refField = new TextField();
        refField.setPromptText("e.g. PO-001, JOB-001");

        Label notesLabel = new Label("Notes:");
        TextField notesField = new TextField();
        notesField.setPromptText("Optional notes");

        // --- Layout ---
        VBox content = new VBox(8);
        content.setPadding(new Insets(10));
        content.getChildren().addAll(
            productLabel, productSearch, productList, selectedLabel,
            new Separator(),
            qtyLabel, qtyField,
            refLabel, refField,
            notesLabel, notesField
        );

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // --- Handle OK ---
        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                // Validate
                if (selected[0] == null) {
                    AlertHelper.warning("Missing Product", "Please select a product from the search results.");
                    return null; // keeps dialog open
                }
                int qty;
                try {
                    qty = Integer.parseInt(qtyField.getText().trim());
                } catch (NumberFormatException e) {
                    AlertHelper.warning("Invalid Quantity", "Please enter a valid number.");
                    return null;
                }
                if (qty <= 0) {
                    AlertHelper.warning("Invalid Quantity", "Quantity must be greater than 0.");
                    return null;
                }

                // Execute
                try {
                    int userId = UserSession.getInstance().getCurrentUser() != null ?
                        UserSession.getInstance().getCurrentUser().getId() : 0;
                    String refType = movementType.equals("IN") ? "PURCHASE" : "SALE";
                    String refNum = refField.getText().trim();
                    if (refNum.isEmpty()) refNum = null;
                    String notes = notesField.getText().trim();
                    if (notes.isEmpty()) notes = null;

                    if ("IN".equals(movementType)) {
                        inventoryService.stockIn(selected[0].getId(), qty,
                            refType, refNum, notes, userId);
                    } else {
                        inventoryService.stockOut(selected[0].getId(), qty,
                            refType, refNum, notes, userId);
                    }

                    AlertHelper.info("Success",
                        title + " recorded: " + qty + " units of " + selected[0].getName());
                    loadMovements();
                    loadLowStockAlerts();
                } catch (IllegalArgumentException e) {
                    AlertHelper.warning("Validation Error", e.getMessage());
                    return null; // keeps dialog open
                } catch (Exception e) {
                    AlertHelper.error("Error", "Failed: " + e.getMessage());
                }
            }
            return button;
        });

        dialog.showAndWait();
    }

    /**
     * Shows a dialog for Stock Adjustment.
     * User enters the ACTUAL count (not the difference).
     */
    private void showAdjustDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Adjust Stock");
        dialog.setHeaderText("Enter the actual physical count");

        // --- Smart Product Picker (same pattern) ---
        Label productLabel = new Label("Search Product:");
        TextField productSearch = new TextField();
        productSearch.setPromptText("Type product name or code...");

        ListView<Product> productList = new ListView<>();
        productList.setPrefHeight(120);

        Label selectedLabel = new Label("No product selected");
        selectedLabel.setStyle("-fx-font-weight: bold;");

        final Product[] selected = {null};

        productSearch.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.trim().length() >= 1) {
                List<Product> results = inventoryService.searchProducts(newVal.trim());
                productList.getItems().setAll(results);
                if (results.isEmpty()) {
                    selectedLabel.setText("No products found.");
                    selectedLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: red;");
                }
            } else {
                productList.getItems().clear();
                selectedLabel.setText("No product selected");
                selectedLabel.setStyle("-fx-font-weight: bold;");
            }
            selected[0] = null;
        });

        productList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selected[0] = newVal;
                selectedLabel.setText("Selected: " + newVal.getProductCode() +
                    " - " + newVal.getName() +
                    " (Current stock: " + newVal.getStockQuantity() + ")");
                selectedLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: green;");
            }
        });

        // --- Adjustment fields ---
        Label countLabel = new Label("Actual Count:");
        TextField countField = new TextField();
        countField.setPromptText("Enter the real physical count");

        Label reasonLabel = new Label("Reason:");
        TextField reasonField = new TextField();
        reasonField.setPromptText("e.g. Physical count, Damaged, Lost");

        VBox content = new VBox(8);
        content.setPadding(new Insets(10));
        content.getChildren().addAll(
            productLabel, productSearch, productList, selectedLabel,
            new Separator(),
            countLabel, countField,
            reasonLabel, reasonField
        );

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                if (selected[0] == null) {
                    AlertHelper.warning("Missing Product", "Please select a product.");
                    return null;
                }
                int actualCount;
                try {
                    actualCount = Integer.parseInt(countField.getText().trim());
                } catch (NumberFormatException e) {
                    AlertHelper.warning("Invalid Count", "Please enter a valid number.");
                    return null;
                }
                String reason = reasonField.getText().trim();
                if (reason.isEmpty()) reason = "Manual adjustment";

                try {
                    int userId = UserSession.getInstance().getCurrentUser() != null ?
                        UserSession.getInstance().getCurrentUser().getId() : 0;
                    inventoryService.adjustStock(selected[0].getId(), actualCount, reason, userId);

                    AlertHelper.info("Success",
                        "Stock adjusted for " + selected[0].getName() +
                        " to " + actualCount + " units.");
                    loadMovements();
                    loadLowStockAlerts();
                } catch (IllegalArgumentException e) {
                    AlertHelper.warning("Validation Error", e.getMessage());
                    return null;
                } catch (Exception e) {
                    AlertHelper.error("Error", "Failed: " + e.getMessage());
                }
            }
            return button;
        });

        dialog.showAndWait();
    }
}