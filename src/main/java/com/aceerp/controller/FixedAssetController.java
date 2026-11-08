package com.aceerp.controller;


import com.aceerp.model.FixedAsset;
import com.aceerp.service.FixedAssetService;
import com.aceerp.util.AlertHelper;
import com.aceerp.util.CurrencyFormatter;
import com.aceerp.util.InputValidation;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class FixedAssetController {

    private static final Logger logger = LoggerFactory.getLogger(FixedAssetController.class);

    @FXML private TableView<FixedAsset> tblAssets;
    @FXML private TableColumn<FixedAsset, String> colAssetCode;
    @FXML private TableColumn<FixedAsset, String> colAssetName;
    @FXML private TableColumn<FixedAsset, String> colCategory;
    @FXML private TableColumn<FixedAsset, LocalDate> colAcquisitionDate;
    @FXML private TableColumn<FixedAsset, BigDecimal> colCost;
    @FXML private TableColumn<FixedAsset, BigDecimal> colAccumDep;
    @FXML private TableColumn<FixedAsset, BigDecimal> colBookValue;
    @FXML private TableColumn<FixedAsset, String> colMonthlyDep;
    @FXML private TableColumn<FixedAsset, String> colStatus;

    @FXML private Label lblTotalCost;
    @FXML private Label lblTotalAccumDep;
    @FXML private Label lblTotalBookValue;

    private final FixedAssetService assetService = new FixedAssetService();
    private final ObservableList<FixedAsset> assetList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupTable();
        handleRefresh();
    }

    private void setupTable() {
        colAssetCode.setCellValueFactory(new PropertyValueFactory<>("assetCode"));
        colAssetName.setCellValueFactory(new PropertyValueFactory<>("assetName"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colAcquisitionDate.setCellValueFactory(new PropertyValueFactory<>("acquisitionDate"));
        
        colCost.setCellValueFactory(new PropertyValueFactory<>("acquisitionCost"));
        colCost.setCellFactory(tc -> moneyCell());
        
        colAccumDep.setCellValueFactory(new PropertyValueFactory<>("accumulatedDepreciation"));
        colAccumDep.setCellFactory(tc -> moneyCell());

        colBookValue.setCellValueFactory(new PropertyValueFactory<>("bookValue"));
        colBookValue.setCellFactory(tc -> moneyCell());

        colMonthlyDep.setCellValueFactory(cdf -> new SimpleStringProperty(CurrencyFormatter.format(cdf.getValue().calculateMonthlyDepreciation())));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        tblAssets.setItems(assetList);
        com.aceerp.util.QuickBooksContextMenus.installTableView(tblAssets, asset ->
            java.util.List.of(com.aceerp.util.QuickBooksContextMenus.action("Edit", this::handleEditAsset))
        );
    }

    @FXML
    public void handleRefresh() {
        try {
            List<FixedAsset> list = assetService.getAllAssets();
            assetList.setAll(list);

            BigDecimal totalCost = BigDecimal.ZERO;
            BigDecimal totalAccum = BigDecimal.ZERO;
            BigDecimal totalBook = BigDecimal.ZERO;

            for (FixedAsset a : list) {
                totalCost = totalCost.add(a.getAcquisitionCost());
                totalAccum = totalAccum.add(a.getAccumulatedDepreciation());
                totalBook = totalBook.add(a.getBookValue());
            }

            lblTotalCost.setText(CurrencyFormatter.format(totalCost));
            lblTotalAccumDep.setText(CurrencyFormatter.format(totalAccum));
            lblTotalBookValue.setText(CurrencyFormatter.format(totalBook));
        } catch (Exception e) {
            logger.error("Failed to refresh fixed assets: {}", e.getMessage(), e);
        }
    }

    @FXML
    public void handleAddAsset() {
        showAssetDialog(new FixedAsset());
    }

    @FXML
    public void handleEditAsset() {
        FixedAsset sel = tblAssets.getSelectionModel().getSelectedItem();
        if (sel == null) {
            AlertHelper.warning("No Selection", "Please select an asset to edit.");
            return;
        }
        showAssetDialog(sel);
    }

    @FXML
    public void handleRunDepreciation() {
        if (!AlertHelper.confirm("Run Depreciation", "Post monthly depreciation journal entries for all active assets?")) {
            return;
        }

        try {
            int count = assetService.postMonthlyDepreciationRun(LocalDate.now());
            AlertHelper.info("Depreciation Run Complete", "Posted monthly depreciation entries for " + count + " asset(s).");
            handleRefresh();
        } catch (Exception e) {
            logger.error("Depreciation run failed: {}", e.getMessage(), e);
            AlertHelper.error("Depreciation Error", e.getMessage());
        }
    }

    private void showAssetDialog(FixedAsset asset) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(asset.getId() <= 0 ? "New Fixed Asset" : "Edit Fixed Asset");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField txtName = new TextField(asset.getAssetName() != null ? asset.getAssetName() : "");
        ComboBox<String> cmbCategory = new ComboBox<>(FXCollections.observableArrayList("VEHICLE", "EQUIPMENT", "TOOLS", "OFFICE"));
        cmbCategory.setValue(asset.getCategory() != null ? asset.getCategory() : "VEHICLE");

        DatePicker dpAcq = new DatePicker(asset.getAcquisitionDate() != null ? asset.getAcquisitionDate() : LocalDate.now());
        TextField txtCost = new TextField(asset.getAcquisitionCost() != null ? asset.getAcquisitionCost().toPlainString() : "0.00");
        TextField txtSalvage = new TextField(asset.getSalvageValue() != null ? asset.getSalvageValue().toPlainString() : "0.00");
        InputValidation.decimalOnly(txtCost);
        InputValidation.decimalOnly(txtSalvage);
        Spinner<Integer> spLife = new Spinner<>(1, 30, asset.getUsefulLifeYears() > 0 ? asset.getUsefulLifeYears() : 5);

        ComboBox<String> cmbMethod = new ComboBox<>(FXCollections.observableArrayList("STRAIGHT_LINE", "DECLINING_BALANCE"));
        cmbMethod.setValue(asset.getDepreciationMethod() != null ? asset.getDepreciationMethod() : "STRAIGHT_LINE");

        TextArea txtNotes = new TextArea(asset.getNotes() != null ? asset.getNotes() : "");
        txtNotes.setPrefRowCount(3);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        grid.addRow(0, new Label("Asset Name:"), txtName);
        grid.addRow(1, new Label("Category:"), cmbCategory);
        grid.addRow(2, new Label("Acquisition Date:"), dpAcq);
        grid.addRow(3, new Label("Acquisition Cost:"), txtCost);
        grid.addRow(4, new Label("Salvage Value:"), txtSalvage);
        grid.addRow(5, new Label("Useful Life (Years):"), spLife);
        grid.addRow(6, new Label("Depreciation Method:"), cmbMethod);
        grid.addRow(7, new Label("Notes:"), txtNotes);

        dialog.getDialogPane().setContent(grid);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            if (txtName.getText().isBlank()) {
                AlertHelper.warning("Validation Error", "Asset Name is required.");
                ev.consume();
            }
        });

        dialog.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                asset.setAssetName(txtName.getText().trim());
                asset.setCategory(cmbCategory.getValue());
                asset.setAcquisitionDate(dpAcq.getValue());
                asset.setAcquisitionCost(parseDecimal(txtCost.getText()));
                asset.setSalvageValue(parseDecimal(txtSalvage.getText()));
                asset.setUsefulLifeYears(spLife.getValue());
                asset.setDepreciationMethod(cmbMethod.getValue());
                asset.setNotes(txtNotes.getText());

                assetService.saveAsset(asset);
                handleRefresh();
            }
        });
    }

    private BigDecimal parseDecimal(String s) {
        if (s == null || s.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(s.trim().replace(",", "").replace("$", ""));
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
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