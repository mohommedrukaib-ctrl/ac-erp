package com.aceerp.controller;

import com.aceerp.model.*;
import com.aceerp.service.AssemblyService;
import com.aceerp.service.InventoryService;
import com.aceerp.util.AlertHelper;
import com.aceerp.util.CurrencyFormatter;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class InventoryController implements Initializable {

    // --- Stock Movements FXML Fields ---
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

    // --- Bill of Materials FXML Fields ---
    @FXML private TableView<BillOfMaterials> tblBoms;
    @FXML private TableColumn<BillOfMaterials, String> colBomCode;
    @FXML private TableColumn<BillOfMaterials, String> colBomAssyCode;
    @FXML private TableColumn<BillOfMaterials, String> colBomAssyName;
    @FXML private TableColumn<BillOfMaterials, String> colBomDesc;
    @FXML private TableColumn<BillOfMaterials, BigDecimal> colBomCost;

    @FXML private TableView<BomComponent> tblBomComps;
    @FXML private TableColumn<BomComponent, String> colCompCode;
    @FXML private TableColumn<BomComponent, String> colCompName;
    @FXML private TableColumn<BomComponent, BigDecimal> colCompQty;
    @FXML private TableColumn<BomComponent, BigDecimal> colCompUnitCost;
    @FXML private TableColumn<BomComponent, BigDecimal> colCompTotalCost;

    // --- Build Assemblies FXML Fields ---
    @FXML private TableView<AssemblyBuild> tblBuilds;
    @FXML private TableColumn<AssemblyBuild, String> colBuildNumber;
    @FXML private TableColumn<AssemblyBuild, String> colBuildBomCode;
    @FXML private TableColumn<AssemblyBuild, String> colBuildAssyName;
    @FXML private TableColumn<AssemblyBuild, Integer> colBuildQty;
    @FXML private TableColumn<AssemblyBuild, BigDecimal> colBuildCost;
    @FXML private TableColumn<AssemblyBuild, String> colBuildDate;
    @FXML private TableColumn<AssemblyBuild, String> colBuildNotes;

    private final InventoryService inventoryService = new InventoryService();
    private final AssemblyService assemblyService = new AssemblyService();

    private final ObservableList<StockMovement> allMovements = FXCollections.observableArrayList();
    private FilteredList<StockMovement> filteredMovements;
    private String currentFilter = "ALL";
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    private final ObservableList<BillOfMaterials> bomList = FXCollections.observableArrayList();
    private final ObservableList<BomComponent> compList = FXCollections.observableArrayList();
    private final ObservableList<AssemblyBuild> buildList = FXCollections.observableArrayList();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTableColumns();
        setupSearch();
        setupFilterButtons();
        setupBomTables();
        setupBuildTable();

        loadMovements();
        loadLowStockAlerts();
        loadBoms();
        loadBuilds();
    }

    private void setupTableColumns() {
        if (movementsTable == null) return;
        colDate.setCellValueFactory(c -> {
            if (c.getValue().getCreatedAt() != null) {
                return new javafx.beans.property.SimpleStringProperty(dateFormat.format(c.getValue().getCreatedAt()));
            }
            return new javafx.beans.property.SimpleStringProperty("");
        });
        colProduct.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getProductDisplay()));
        colType.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getTypeDisplay()));
        colQty.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getQuantityDisplay()));
        colBefore.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(String.valueOf(c.getValue().getPreviousStock())));
        colAfter.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(String.valueOf(c.getValue().getNewStock())));
        colRef.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getReferenceDisplay()));
        colUser.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getCreatedByName() != null ? c.getValue().getCreatedByName() : ""));
        colNotes.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().getNotes() != null ? c.getValue().getNotes() : ""));
    }

    private void setupBomTables() {
        if (tblBoms == null) return;
        colBomCode.setCellValueFactory(new PropertyValueFactory<>("bomCode"));
        colBomAssyCode.setCellValueFactory(new PropertyValueFactory<>("assemblyProductCode"));
        colBomAssyName.setCellValueFactory(new PropertyValueFactory<>("assemblyProductName"));
        colBomDesc.setCellValueFactory(new PropertyValueFactory<>("description"));
        colBomCost.setCellValueFactory(new PropertyValueFactory<>("totalCost"));
        colBomCost.setCellFactory(tc -> moneyCell());
        tblBoms.setItems(bomList);

        tblBoms.getSelectionModel().selectedItemProperty().addListener((o, oldV, sel) -> {
            if (sel != null && sel.getComponents() != null) {
                compList.setAll(sel.getComponents());
            } else {
                compList.clear();
            }
        });

        colCompCode.setCellValueFactory(new PropertyValueFactory<>("componentProductCode"));
        colCompName.setCellValueFactory(new PropertyValueFactory<>("componentProductName"));
        colCompQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colCompUnitCost.setCellValueFactory(new PropertyValueFactory<>("unitCost"));
        colCompUnitCost.setCellFactory(tc -> moneyCell());
        colCompTotalCost.setCellValueFactory(cdf -> new javafx.beans.property.SimpleObjectProperty<>(cdf.getValue().getTotalCost()));
        colCompTotalCost.setCellFactory(tc -> moneyCell());
        tblBomComps.setItems(compList);
    }

    private void setupBuildTable() {
        if (tblBuilds == null) return;
        colBuildNumber.setCellValueFactory(new PropertyValueFactory<>("buildNumber"));
        colBuildBomCode.setCellValueFactory(new PropertyValueFactory<>("bomCode"));
        colBuildAssyName.setCellValueFactory(new PropertyValueFactory<>("assemblyProductName"));
        colBuildQty.setCellValueFactory(new PropertyValueFactory<>("quantityToBuild"));
        colBuildCost.setCellValueFactory(new PropertyValueFactory<>("totalCost"));
        colBuildCost.setCellFactory(tc -> moneyCell());
        colBuildDate.setCellValueFactory(cdf -> new SimpleStringProperty(cdf.getValue().getBuildDate() != null ? cdf.getValue().getBuildDate().toString() : ""));
        colBuildNotes.setCellValueFactory(new PropertyValueFactory<>("notes"));
        tblBuilds.setItems(buildList);
    }

    private void setupSearch() {
        if (searchField == null) return;
        filteredMovements = new FilteredList<>(allMovements, m -> true);
        movementsTable.setItems(filteredMovements);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());
    }

    private void setupFilterButtons() {
        if (btnAll == null) return;
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

    private void applyFilters() {
        String searchText = searchField.getText().toLowerCase().trim();
        filteredMovements.setPredicate(m -> {
            if (!"ALL".equals(currentFilter) && !currentFilter.equals(m.getMovementType())) return false;
            if (!searchText.isEmpty()) {
                String productDisplay = m.getProductDisplay().toLowerCase();
                String ref = m.getReferenceDisplay().toLowerCase();
                String notes = m.getNotes() != null ? m.getNotes().toLowerCase() : "";
                return productDisplay.contains(searchText) || ref.contains(searchText) || notes.contains(searchText);
            }
            return true;
        });
    }

    private void loadMovements() {
        try {
            List<StockMovement> movements = inventoryService.getRecentMovements("ALL", 500);
            allMovements.setAll(movements);
        } catch (Exception e) {
            AlertHelper.error("Load Error", "Failed to load stock movements");
        }
    }

    private void loadLowStockAlerts() {
        if (lowStockLabel == null) return;
        try {
            List<Product> lowStock = inventoryService.getLowStockProducts();
            if (lowStock.isEmpty()) {
                lowStockLabel.setText("All products are well stocked.");
            } else {
                StringBuilder sb = new StringBuilder();
                for (Product p : lowStock) {
                    sb.append("- ").append(p.getProductCode()).append(" ").append(p.getName())
                      .append(" (").append(p.getStockQuantity()).append(" left, min: ").append(p.getMinStockLevel()).append(")\n");
                }
                lowStockLabel.setText(sb.toString().trim());
            }
        } catch (Exception e) {
            lowStockLabel.setText("Could not load alerts.");
        }
    }

    private void loadBoms() {
        try {
            bomList.setAll(assemblyService.getAllBoms());
        } catch (Exception e) {
            AlertHelper.error("Load Error", "Failed to load Bill of Materials recipes.");
        }
    }

    private void loadBuilds() {
        try {
            buildList.setAll(assemblyService.getAllBuilds());
        } catch (Exception e) {
            AlertHelper.error("Load Error", "Failed to load Assembly Builds.");
        }
    }

    @FXML private void handleStockIn() { showStockDialog("Stock In", "IN"); }
    @FXML private void handleStockOut() { showStockDialog("Stock Out", "OUT"); }
    @FXML private void handleAdjust() { showAdjustDialog(); }
    @FXML private void handleFilterAll() {}
    @FXML private void handleFilterIn() {}
    @FXML private void handleFilterOut() {}
    @FXML private void handleFilterAdjust() {}

    // --- BOM Recipe Creation Dialog ---
    @FXML
    public void handleCreateBom() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("New Bill of Materials (Assembly Recipe)");
        dialog.setHeaderText("Create multi-component assembly formula");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefSize(720, 500);

        Label lblAssy = new Label("Assembly Finished Product:");
        TextField txtSearchAssy = new TextField();
        txtSearchAssy.setPromptText("Type assembly product name or code...");
        ListView<Product> lstAssy = new ListView<>();
        lstAssy.setPrefHeight(90);
        Label lblSelectedAssy = new Label("No Assembly Product selected");
        lblSelectedAssy.setStyle("-fx-font-weight: bold;");

        final Product[] selectedAssy = {null};
        txtSearchAssy.textProperty().addListener((o, oldV, newV) -> {
            if (newV != null && !newV.isBlank()) {
                lstAssy.getItems().setAll(inventoryService.searchProducts(newV.trim()));
            } else lstAssy.getItems().clear();
        });
        lstAssy.getSelectionModel().selectedItemProperty().addListener((o, oldV, sel) -> {
            if (sel != null) {
                selectedAssy[0] = sel;
                lblSelectedAssy.setText("Assembly: " + sel.getProductCode() + " - " + sel.getName());
                lblSelectedAssy.setStyle("-fx-font-weight: bold; -fx-text-fill: green;");
            }
        });

        TextField txtDesc = new TextField();
        txtDesc.setPromptText("Recipe memo / description");

        ObservableList<BomComponent> draftComps = FXCollections.observableArrayList();
        TableView<BomComponent> tblDraft = new TableView<>(draftComps);
        tblDraft.setEditable(true);
        tblDraft.setPrefHeight(200);

        TableColumn<BomComponent, String> colCName = new TableColumn<>("Component Product");
        colCName.setPrefWidth(260);
        colCName.setCellValueFactory(cdf -> new SimpleStringProperty(cdf.getValue().getComponentProductCode() + " - " + cdf.getValue().getComponentProductName()));

        TableColumn<BomComponent, String> colCQty = new TableColumn<>("Qty Required");
        colCQty.setPrefWidth(120);
        colCQty.setCellFactory(TextFieldTableCell.forTableColumn());
        colCQty.setCellValueFactory(cdf -> new SimpleStringProperty(cdf.getValue().getQuantity().toPlainString()));
        colCQty.setOnEditCommit(e -> {
            try {
                e.getRowValue().setQuantity(new BigDecimal(e.getNewValue().trim()));
                tblDraft.refresh();
            } catch (Exception ignored) {}
        });

        TableColumn<BomComponent, String> colCCost = new TableColumn<>("Unit Cost");
        colCCost.setPrefWidth(120);
        colCCost.setCellValueFactory(cdf -> new SimpleStringProperty(CurrencyFormatter.format(cdf.getValue().getUnitCost())));

        tblDraft.getColumns().addAll(colCName, colCQty, colCCost);

        TextField txtSearchComp = new TextField();
        txtSearchComp.setPromptText("Search raw material component...");
        ListView<Product> lstComp = new ListView<>();
        lstComp.setPrefHeight(90);
        txtSearchComp.textProperty().addListener((o, oldV, newV) -> {
            if (newV != null && !newV.isBlank()) {
                lstComp.getItems().setAll(inventoryService.searchProducts(newV.trim()));
            } else lstComp.getItems().clear();
        });

        Button btnAddComp = new Button("+ Add Component");
        btnAddComp.setOnAction(e -> {
            Product p = lstComp.getSelectionModel().getSelectedItem();
            if (p != null) {
                BomComponent c = new BomComponent();
                c.setComponentProductId(p.getId());
                c.setComponentProductCode(p.getProductCode());
                c.setComponentProductName(p.getName());
                c.setQuantity(BigDecimal.ONE);
                c.setUnitCost(p.getCostPrice() != null ? p.getCostPrice() : BigDecimal.ZERO);
                draftComps.add(c);
            }
        });

        VBox box = new VBox(8,
                lblAssy, txtSearchAssy, lstAssy, lblSelectedAssy, new Separator(),
                new Label("Description:"), txtDesc,
                new Label("Add Components:"), new HBox(8, txtSearchComp, btnAddComp), lstComp,
                new Label("Recipe Components List:"), tblDraft);
        box.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(box);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            if (selectedAssy[0] == null) {
                AlertHelper.warning("Missing Assembly Product", "Please select an Assembly Finished Product.");
                ev.consume(); return;
            }
            if (draftComps.isEmpty()) {
                AlertHelper.warning("Missing Components", "Add at least one raw material component to the BOM recipe.");
                ev.consume();
            }
        });

        dialog.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                BillOfMaterials bom = new BillOfMaterials();
                bom.setAssemblyProductId(selectedAssy[0].getId());
                bom.setDescription(txtDesc.getText());
                bom.setComponents(new ArrayList<>(draftComps));
                assemblyService.saveBom(bom);
                AlertHelper.info("BOM Saved", "Assembly recipe created successfully!");
                loadBoms();
            }
        });
    }

    // --- Execute Build Assembly Work Order ---
    @FXML
    public void handleBuildAssembly() {
        BillOfMaterials selectedBom = tblBoms.getSelectionModel().getSelectedItem();
        if (selectedBom == null) {
            AlertHelper.warning("No Selection", "Please select a Bill of Materials recipe from the table first.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Build Assembly Work Order");
        dialog.setHeaderText("Execute Assembly Build: " + selectedBom.getBomCode() + " — " + selectedBom.getAssemblyProductName());
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Spinner<Integer> spQty = new Spinner<>(1, 1000, 1);
        TextField txtNotes = new TextField();
        txtNotes.setPromptText("Build batch notes / technician memo");

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(10));
        grid.addRow(0, new Label("BOM Recipe:"), new Label(selectedBom.getBomCode() + " (" + selectedBom.getAssemblyProductName() + ")"));
        grid.addRow(1, new Label("Unit Roll-up Cost:"), new Label(CurrencyFormatter.format(selectedBom.getTotalCost())));
        grid.addRow(2, new Label("Quantity to Build:"), spQty);
        grid.addRow(3, new Label("Notes:"), txtNotes);

        dialog.getDialogPane().setContent(grid);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    int userId = UserSession.getInstance().getCurrentUser() != null ? UserSession.getInstance().getCurrentUser().getId() : 1;
                    AssemblyBuild build = assemblyService.buildAssembly(selectedBom.getId(), spQty.getValue(), txtNotes.getText(), userId);
                    AlertHelper.info("Build Complete", "Executed Build Work Order " + build.getBuildNumber() + " successfully!");
                    loadBuilds();
                    loadMovements();
                    loadLowStockAlerts();
                } catch (Exception e) {
                    AlertHelper.error("Build Error", e.getMessage());
                }
            }
        });
    }

    private void showStockDialog(String title, String movementType) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText("Record " + title);

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

        productList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                selected[0] = newVal;
                selectedLabel.setText("Selected: " + newVal.getProductCode() + " - " + newVal.getName() + " (Current stock: " + newVal.getStockQuantity() + ")");
                selectedLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: green;");
            }
        });

        Label qtyLabel = new Label("Quantity:");
        TextField qtyField = new TextField();
        qtyField.setPromptText("Enter number of units");

        Label refLabel = new Label("Reference:");
        TextField refField = new TextField();
        refField.setPromptText("e.g. PO-001, JOB-001");

        Label notesLabel = new Label("Notes:");
        TextField notesField = new TextField();
        notesField.setPromptText("Optional notes");

        VBox content = new VBox(8);
        content.setPadding(new Insets(10));
        content.getChildren().addAll(productLabel, productSearch, productList, selectedLabel, new Separator(), qtyLabel, qtyField, refLabel, refField, notesLabel, notesField);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                if (selected[0] == null) {
                    AlertHelper.warning("Missing Product", "Please select a product from the search results.");
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
                    int userId = UserSession.getInstance().getCurrentUser() != null ? UserSession.getInstance().getCurrentUser().getId() : 0;
                    String refType = movementType.equals("IN") ? "PURCHASE" : "SALE";
                    String refNum = refField.getText().trim(); if (refNum.isEmpty()) refNum = null;
                    String notes = notesField.getText().trim(); if (notes.isEmpty()) notes = null;

                    if ("IN".equals(movementType)) {
                        inventoryService.stockIn(selected[0].getId(), qty, refType, refNum, notes, userId);
                    } else {
                        inventoryService.stockOut(selected[0].getId(), qty, refType, refNum, notes, userId);
                    }

                    AlertHelper.info("Success", title + " recorded: " + qty + " units of " + selected[0].getName());
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

    private void showAdjustDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Adjust Stock");
        dialog.setHeaderText("Enter the actual physical count");

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
                selectedLabel.setText("Selected: " + newVal.getProductCode() + " - " + newVal.getName() + " (Current stock: " + newVal.getStockQuantity() + ")");
                selectedLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: green;");
            }
        });

        Label countLabel = new Label("Actual Count:");
        TextField countField = new TextField();
        countField.setPromptText("Enter the real physical count");

        Label reasonLabel = new Label("Reason:");
        TextField reasonField = new TextField();
        reasonField.setPromptText("e.g. Physical count, Damaged, Lost");

        VBox content = new VBox(8);
        content.setPadding(new Insets(10));
        content.getChildren().addAll(productLabel, productSearch, productList, selectedLabel, new Separator(), countLabel, countField, reasonLabel, reasonField);

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
                    int userId = UserSession.getInstance().getCurrentUser() != null ? UserSession.getInstance().getCurrentUser().getId() : 0;
                    inventoryService.adjustStock(selected[0].getId(), actualCount, reason, userId);

                    AlertHelper.info("Success", "Stock adjusted for " + selected[0].getName() + " to " + actualCount + " units.");
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

    private <S> TableCell<S, BigDecimal> moneyCell() {
        return new TableCell<>() {
            @Override protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : CurrencyFormatter.format(item));
            }
        };
    }
}