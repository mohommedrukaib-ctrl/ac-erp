package com.aceerp.controller;

import com.aceerp.model.Product;
import com.aceerp.model.StockMovement;
import com.aceerp.model.UserSession;
import com.aceerp.service.InventoryService;
import com.aceerp.service.ProductService;
import com.aceerp.util.AlertHelper;
import com.aceerp.util.SmartSearchHelper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Combined controller for Products AND Stock Management.
 * 
 * Tab 1 - Product List: CRUD operations with smart search fields
 * Tab 2 - Stock Movements: Stock In, Out, Adjust with full history
 * 
 * Both tabs share the same product data, so changes in one
 * are immediately visible in the other.
 */
public class ProductController implements Initializable {

    // ==================== TAB 1: PRODUCT LIST FIELDS ====================
    @FXML private TabPane mainTabs;
    @FXML private TextField searchField;
    @FXML private TableView<Product> productsTable;
    @FXML private TableColumn<Product, String> colCode;
    @FXML private TableColumn<Product, String> colName;
    @FXML private TableColumn<Product, String> colType;
    @FXML private TableColumn<Product, String> colBrand;
    @FXML private TableColumn<Product, String> colPrice;
    @FXML private TableColumn<Product, String> colStock;
    @FXML private TableColumn<Product, String> colStatus;

    // ==================== TAB 2: STOCK MOVEMENT FIELDS ====================
    @FXML private TextField movementSearchField;
    @FXML private TableView<StockMovement> movementsTable;
    @FXML private TableColumn<StockMovement, String> colDate;
    @FXML private TableColumn<StockMovement, String> colProduct;
    @FXML private TableColumn<StockMovement, String> colMvType;
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

    // ==================== SERVICES ====================
    private final ProductService productService = new ProductService();
    private final InventoryService inventoryService = new InventoryService();

    // ==================== DATA ====================
    private ObservableList<Product> allProducts = FXCollections.observableArrayList();
    private FilteredList<Product> filteredProducts;

    private ObservableList<StockMovement> allMovements = FXCollections.observableArrayList();
    private FilteredList<StockMovement> filteredMovements;
    private String currentFilter = "ALL";
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    // ==================== INITIALIZATION ====================

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Tab 1 setup
        setupProductColumns();
        setupProductSearch();
        loadProducts();

        // Tab 2 setup
        setupMovementColumns();
        setupMovementSearch();
        setupFilterButtons();
        loadMovements();
        loadLowStockAlerts();

        // Refresh stock data when switching tabs
        mainTabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null && newTab.getText().equals("Stock Movements")) {
                loadMovements();
                loadLowStockAlerts();
            } else if (newTab != null && newTab.getText().equals("Product List")) {
                loadProducts();
            }
        });
    }

    // ================================================================
    //  TAB 1: PRODUCT LIST
    // ================================================================

    private void setupProductColumns() {
        colCode.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().getProductCode()));
        colName.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().getName()));
        colType.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().getTypeDisplay()));
        colBrand.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(
                c.getValue().getBrand() != null ? c.getValue().getBrand() : ""));
        colPrice.setCellValueFactory(c -> {
            BigDecimal sp = c.getValue().getSellingPrice();
            return new javafx.beans.property.SimpleStringProperty(
                sp != null ? sp.toPlainString() : "0");
        });
        colStock.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(
                String.valueOf(c.getValue().getStockQuantity())));
        colStatus.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().getStatusDisplay()));
    }

    private void setupProductSearch() {
        filteredProducts = new FilteredList<>(allProducts, p -> true);
        productsTable.setItems(filteredProducts);

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String query = newVal.toLowerCase().trim();
            filteredProducts.setPredicate(p -> {
                if (query.isEmpty()) return true;
                String name = p.getName() != null ? p.getName().toLowerCase() : "";
                String code = p.getProductCode() != null ? p.getProductCode().toLowerCase() : "";
                String brand = p.getBrand() != null ? p.getBrand().toLowerCase() : "";
                return name.contains(query) || code.contains(query) || brand.contains(query);
            });
        });
    }

    private void loadProducts() {
        try {
            allProducts.setAll(productService.listAll());
        } catch (Exception e) {
            AlertHelper.error("Load Error", "Failed to load products");
        }
    }

    // --- Product Action Handlers ---

    @FXML
    private void handleAdd() {
        showProductDialog(null);
    }

    @FXML
    private void handleEdit() {
        Product selected = productsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertHelper.warning("No Selection", "Please select a product to edit.");
            return;
        }
        showProductDialog(selected);
    }

    @FXML
    private void handleDeactivate() {
        Product selected = productsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            AlertHelper.warning("No Selection", "Please select a product.");
            return;
        }
        if (AlertHelper.confirm("Deactivate",
                "Deactivate product '" + selected.getName() + "'?")) {
            try {
                productService.deactivate(selected.getId());
                loadProducts();
            } catch (Exception e) {
                AlertHelper.error("Error", "Failed: " + e.getMessage());
            }
        }
    }

    // --- Product Add/Edit Dialog (with Smart Search) ---

    private void showProductDialog(Product existing) {
        boolean isEdit = (existing != null);
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(isEdit ? "Edit Product" : "Add Product");
        dialog.setHeaderText(isEdit ? "Edit: " + existing.getName() : "Enter new product details");

        List<String> allTypes = productService.getDistinctValues("product_type");
        List<String> allCategories = productService.getDistinctValues("category");
        List<String> allBrands = productService.getDistinctValues("brand");
        List<String> allUnits = productService.getDistinctValues("unit");

        if (allTypes.isEmpty()) {
            allTypes = List.of("Split", "Window", "Ducted", "Portable", "Cassette", "VRF", "Ceiling");
        }
        if (allUnits.isEmpty()) {
            allUnits = List.of("Piece", "Set", "Meter", "Kg", "Liter", "Box", "Roll", "Pair");
        }

        TextField nameField = new TextField();
        nameField.setPromptText("e.g. 1.5 Ton Split AC");

        TextField modelField = new TextField();
        modelField.setPromptText("e.g. FTKF50TV16");

        // Smart search fields
        TextField typeField = new TextField();
        typeField.setPromptText("Type to search or enter new...");
        ListView<String> typeList = new ListView<>();
        typeList.setPrefHeight(60);
        SmartSearchHelper.setupLocal(typeField, typeList, allTypes);

        TextField categoryField = new TextField();
        categoryField.setPromptText("Type to search or enter new...");
        ListView<String> categoryList = new ListView<>();
        categoryList.setPrefHeight(60);
        SmartSearchHelper.setupLocal(categoryField, categoryList, allCategories);

        TextField brandField = new TextField();
        brandField.setPromptText("Type to search or enter new...");
        ListView<String> brandList = new ListView<>();
        brandList.setPrefHeight(60);
        SmartSearchHelper.setup(brandField, brandList,
            query -> productService.getDistinctValues("brand").stream()
                .filter(b -> b.toLowerCase().contains(query.toLowerCase()))
                .limit(10).toList()
        );

        TextField unitField = new TextField();
        unitField.setPromptText("Type to search or enter new...");
        ListView<String> unitList = new ListView<>();
        unitList.setPrefHeight(60);
        SmartSearchHelper.setupLocal(unitField, unitList, allUnits);

        TextField sellingPriceField = new TextField();
        sellingPriceField.setPromptText("0.00");
        TextField costPriceField = new TextField();
        costPriceField.setPromptText("0.00");
        TextField taxRateField = new TextField();
        taxRateField.setPromptText("e.g. 5 for 5%");
        TextField stockField = new TextField();
        stockField.setPromptText("0");
        TextField minStockField = new TextField();
        minStockField.setPromptText("0");

        if (isEdit) {
            nameField.setText(existing.getName());
            typeField.setText(existing.getProductType() != null ? existing.getProductType() : "");
            categoryField.setText(existing.getCategory() != null ? existing.getCategory() : "");
            brandField.setText(existing.getBrand() != null ? existing.getBrand() : "");
            modelField.setText(existing.getModel() != null ? existing.getModel() : "");
            unitField.setText(existing.getUnit() != null ? existing.getUnit() : "");
            sellingPriceField.setText(existing.getSellingPrice() != null ? existing.getSellingPrice().toPlainString() : "0");
            costPriceField.setText(existing.getCostPrice() != null ? existing.getCostPrice().toPlainString() : "0");
            taxRateField.setText(existing.getTaxRate() != null ? existing.getTaxRate().toPlainString() : "0");
            stockField.setText(String.valueOf(existing.getStockQuantity()));
            minStockField.setText(String.valueOf(existing.getMinStockLevel()));
        }

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        grid.setPadding(new Insets(10));
        int row = 0;

        grid.add(new Label("Product Name:*"), 0, row); grid.add(nameField, 1, row); row++;
        grid.add(new Label("Type:"), 0, row); grid.add(new VBox(2, typeField, typeList), 1, row); row++;
        grid.add(new Label("Category:"), 0, row); grid.add(new VBox(2, categoryField, categoryList), 1, row); row++;
        grid.add(new Label("Brand:"), 0, row); grid.add(new VBox(2, brandField, brandList), 1, row); row++;
        grid.add(new Label("Model:"), 0, row); grid.add(modelField, 1, row); row++;
        grid.add(new Label("Unit:"), 0, row); grid.add(new VBox(2, unitField, unitList), 1, row); row++;
        grid.add(new Separator(), 0, row, 2, 1); row++;
        grid.add(new Label("Selling Price:"), 0, row); grid.add(sellingPriceField, 1, row); row++;
        grid.add(new Label("Cost Price:"), 0, row); grid.add(costPriceField, 1, row); row++;
        grid.add(new Label("Tax Rate (%):"), 0, row); grid.add(taxRateField, 1, row); row++;
        grid.add(new Separator(), 0, row, 2, 1); row++;
        grid.add(new Label("Stock Quantity:"), 0, row); grid.add(stockField, 1, row); row++;
        grid.add(new Label("Min Stock Level:"), 0, row); grid.add(minStockField, 1, row);

        ScrollPane scrollPane = new ScrollPane(grid);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(500);

        dialog.getDialogPane().setContent(scrollPane);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResizable(true);

        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                String name = nameField.getText().trim();
                if (name.isEmpty()) {
                    AlertHelper.warning("Missing Name", "Product name is required.");
                    return null;
                }
                try {
                    Product p = isEdit ? existing : new Product();
                    p.setName(name);
                    p.setProductType(typeField.getText().trim());
                    p.setCategory(categoryField.getText().trim());
                    p.setBrand(brandField.getText().trim());
                    p.setModel(modelField.getText().trim());
                    p.setUnit(unitField.getText().trim());
                    p.setSellingPrice(parseDecimal(sellingPriceField.getText()));
                    p.setCostPrice(parseDecimal(costPriceField.getText()));
                    p.setTaxRate(parseDecimal(taxRateField.getText()));
                    p.setStockQuantity(parseIntSafe(stockField.getText()));
                    p.setMinStockLevel(parseIntSafe(minStockField.getText()));

                    if (isEdit) {
                        productService.updateProduct(p);
                        AlertHelper.info("Success", "Product updated.");
                    } else {
                        productService.addProduct(
                            p.getName(), p.getProductType(), p.getCategory(),
                            p.getBrand(), p.getModel(), p.getUnit(),
                            p.getSellingPrice(), p.getCostPrice(), p.getTaxRate(),
                            p.getStockQuantity(), p.getMinStockLevel()
                        );
                        AlertHelper.info("Success", "Product created.");
                    }
                    loadProducts();
                } catch (Exception e) {
                    AlertHelper.error("Error", "Failed: " + e.getMessage());
                    return null;
                }
            }
            return button;
        });
        dialog.showAndWait();
    }

    // ================================================================
    //  TAB 2: STOCK MOVEMENTS
    // ================================================================

    private void setupMovementColumns() {
        colDate.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(
                c.getValue().getCreatedAt() != null ? dateFormat.format(c.getValue().getCreatedAt()) : ""));
        colProduct.setCellValueFactory(c ->
            new javafx.beans.property.SimpleStringProperty(c.getValue().getProductDisplay()));
        colMvType.setCellValueFactory(c ->
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

    private void setupMovementSearch() {
        filteredMovements = new FilteredList<>(allMovements, m -> true);
        movementsTable.setItems(filteredMovements);

        movementSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
            applyMovementFilters();
        });
    }

    private void setupFilterButtons() {
        ToggleGroup filterGroup = new ToggleGroup();
        btnAll.setToggleGroup(filterGroup);
        btnIn.setToggleGroup(filterGroup);
        btnOut.setToggleGroup(filterGroup);
        btnAdjust.setToggleGroup(filterGroup);

        btnAll.setOnAction(e -> { currentFilter = "ALL"; applyMovementFilters(); });
        btnIn.setOnAction(e -> { currentFilter = "IN"; applyMovementFilters(); });
        btnOut.setOnAction(e -> { currentFilter = "OUT"; applyMovementFilters(); });
        btnAdjust.setOnAction(e -> { currentFilter = "ADJUSTMENT"; applyMovementFilters(); });
    }

    private void applyMovementFilters() {
        String searchText = movementSearchField.getText().toLowerCase().trim();
        filteredMovements.setPredicate(m -> {
            if (!"ALL".equals(currentFilter) && !currentFilter.equals(m.getMovementType())) {
                return false;
            }
            if (!searchText.isEmpty()) {
                String product = m.getProductDisplay().toLowerCase();
                String ref = m.getReferenceDisplay().toLowerCase();
                String notes = m.getNotes() != null ? m.getNotes().toLowerCase() : "";
                if (!product.contains(searchText) && !ref.contains(searchText) && !notes.contains(searchText)) {
                    return false;
                }
            }
            return true;
        });
    }

    private void loadMovements() {
        try {
            allMovements.setAll(inventoryService.getRecentMovements("ALL", 500));
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

    // --- Stock Action Handlers ---

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

    // --- Smart Product Picker Dialog for Stock In/Out ---

    private void showStockDialog(String title, String movementType) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText("Record " + title);

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

        TextField qtyField = new TextField();
        qtyField.setPromptText("Enter number of units");
        TextField refField = new TextField();
        refField.setPromptText("e.g. PO-001, JOB-001");
        TextField notesField = new TextField();
        notesField.setPromptText("Optional notes");

        VBox content = new VBox(8);
        content.setPadding(new Insets(10));
        content.getChildren().addAll(
            new Label("Search Product:"), productSearch, productList, selectedLabel,
            new Separator(),
            new Label("Quantity:"), qtyField,
            new Label("Reference:"), refField,
            new Label("Notes:"), notesField
        );

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                if (selected[0] == null) {
                    AlertHelper.warning("Missing Product", "Please select a product.");
                    return null;
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
                try {
                    int userId = getCurrentUserId();
                    String refType = "IN".equals(movementType) ? "PURCHASE" : "SALE";
                    String refNum = refField.getText().trim();
                    if (refNum.isEmpty()) refNum = null;
                    String notes = notesField.getText().trim();
                    if (notes.isEmpty()) notes = null;

                    if ("IN".equals(movementType)) {
                        inventoryService.stockIn(selected[0].getId(), qty, refType, refNum, notes, userId);
                    } else {
                        inventoryService.stockOut(selected[0].getId(), qty, refType, refNum, notes, userId);
                    }
                    AlertHelper.info("Success", title + " recorded: " + qty + " units of " + selected[0].getName());
                    loadMovements();
                    loadLowStockAlerts();
                    loadProducts();
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

    // --- Stock Adjustment Dialog ---

    private void showAdjustDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Adjust Stock");
        dialog.setHeaderText("Enter the actual physical count");

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
            } else {
                productList.getItems().clear();
            }
            selected[0] = null;
            selectedLabel.setText("No product selected");
            selectedLabel.setStyle("-fx-font-weight: bold;");
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

        TextField countField = new TextField();
        countField.setPromptText("Enter the real physical count");
        TextField reasonField = new TextField();
        reasonField.setPromptText("e.g. Physical count, Damaged, Lost");

        VBox content = new VBox(8);
        content.setPadding(new Insets(10));
        content.getChildren().addAll(
            new Label("Search Product:"), productSearch, productList, selectedLabel,
            new Separator(),
            new Label("Actual Count:"), countField,
            new Label("Reason:"), reasonField
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
                    int userId = getCurrentUserId();
                    inventoryService.adjustStock(selected[0].getId(), actualCount, reason, userId);
                    AlertHelper.info("Success", "Stock adjusted for " + selected[0].getName() + " to " + actualCount);
                    loadMovements();
                    loadLowStockAlerts();
                    loadProducts();
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

    // ==================== SHARED HELPERS ====================

    private int getCurrentUserId() {
        try {
            if (UserSession.getInstance().isLoggedIn()) {
                return UserSession.getInstance().getCurrentUser().getId();
            }
        } catch (Exception e) { /* ignore */ }
        return 0;
    }

    private BigDecimal parseDecimal(String text) {
        if (text == null || text.trim().isEmpty()) return BigDecimal.ZERO;
        try { return new BigDecimal(text.trim()); }
        catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private int parseIntSafe(String text) {
        if (text == null || text.trim().isEmpty()) return 0;
        try { return Integer.parseInt(text.trim()); }
        catch (NumberFormatException e) { return 0; }
    }
}