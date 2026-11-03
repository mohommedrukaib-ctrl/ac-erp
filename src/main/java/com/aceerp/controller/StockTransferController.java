
package com.aceerp.controller;

import com.aceerp.model.*;
import com.aceerp.service.ProductService;
import com.aceerp.service.StockTransferService;
import com.aceerp.util.AlertHelper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.converter.IntegerStringConverter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class StockTransferController {

    @FXML private ComboBox<Warehouse> cmbSourceWh;
    @FXML private ComboBox<Warehouse> cmbDestWh;
    @FXML private DatePicker dpTransferDate;
    @FXML private TextField txtTransferNotes;

    @FXML private TableView<StockTransferLine> tblLines;
    @FXML private TableColumn<StockTransferLine, String> colProductCode;
    @FXML private TableColumn<StockTransferLine, String> colProductName;
    @FXML private TableColumn<StockTransferLine, Integer> colQty;
    @FXML private TableColumn<StockTransferLine, String> colNotes;

    @FXML private TableView<StockTransfer> tblHistory;
    @FXML private TableColumn<StockTransfer, String> colHistNumber;
    @FXML private TableColumn<StockTransfer, LocalDate> colHistDate;
    @FXML private TableColumn<StockTransfer, String> colHistSource;
    @FXML private TableColumn<StockTransfer, String> colHistDest;
    @FXML private TableColumn<StockTransfer, String> colHistStatus;

    private final StockTransferService transferService = new StockTransferService();
    private final ProductService productService = new ProductService();

    private final ObservableList<StockTransferLine> transferLines = FXCollections.observableArrayList();
    private final ObservableList<StockTransfer> historyList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        setupWarehouses();
        setupLinesTable();
        setupHistoryTable();
        loadHistory();
        dpTransferDate.setValue(LocalDate.now());
    }

    private void setupWarehouses() {
        List<Warehouse> whs = transferService.getAllWarehouses();
        cmbSourceWh.setItems(FXCollections.observableArrayList(whs));
        cmbDestWh.setItems(FXCollections.observableArrayList(whs));
        if (whs.size() >= 2) {
            cmbSourceWh.setValue(whs.get(0));
            cmbDestWh.setValue(whs.get(1));
        }
    }

    private void setupLinesTable() {
        tblLines.setEditable(true);

        colProductCode.setCellValueFactory(new PropertyValueFactory<>("productCode"));
        colProductName.setCellValueFactory(new PropertyValueFactory<>("productName"));

        colQty.setCellValueFactory(new PropertyValueFactory<>("quantityTransferred"));
        colQty.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
        colQty.setOnEditCommit(e -> e.getRowValue().setQuantityTransferred(e.getNewValue()));

        colNotes.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNotes()));
        colNotes.setCellFactory(TextFieldTableCell.forTableColumn());
        colNotes.setOnEditCommit(e -> e.getRowValue().setNotes(e.getNewValue()));

        tblLines.setItems(transferLines);
    }

    private void setupHistoryTable() {
        colHistNumber.setCellValueFactory(new PropertyValueFactory<>("transferNumber"));
        colHistDate.setCellValueFactory(new PropertyValueFactory<>("transferDate"));
        colHistSource.setCellValueFactory(new PropertyValueFactory<>("sourceWarehouseName"));
        colHistDest.setCellValueFactory(new PropertyValueFactory<>("destinationWarehouseName"));
        colHistStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
        tblHistory.setItems(historyList);
    }

    private void loadHistory() {
        historyList.setAll(transferService.getAllTransfers());
    }

    @FXML
    public void handleAddProduct() {
        List<Product> products = productService.listAll();
        ChoiceDialog<Product> dialog = new ChoiceDialog<>(products.isEmpty() ? null : products.get(0), products);
        dialog.setTitle("Select Product");
        dialog.setHeaderText("Select Product to Transfer between locations");
        dialog.showAndWait().ifPresent(p -> {
            StockTransferLine line = new StockTransferLine();
            line.setProductId(p.getId());
            line.setProductCode(p.getProductCode());
            line.setProductName(p.getName());
            line.setQuantityTransferred(1);
            transferLines.add(line);
        });
    }

    @FXML
    public void handleRemoveProduct() {
        StockTransferLine sel = tblLines.getSelectionModel().getSelectedItem();
        if (sel != null) transferLines.remove(sel);
    }

    @FXML
    public void handleExecuteTransfer() {
        Warehouse src = cmbSourceWh.getValue();
        Warehouse dst = cmbDestWh.getValue();
        if (src == null || dst == null) { AlertHelper.warning("Validation", "Select source and destination locations."); return; }
        if (src.getId() == dst.getId()) { AlertHelper.warning("Validation", "Source and Destination cannot be identical."); return; }
        if (transferLines.isEmpty()) { AlertHelper.warning("Validation", "Add at least one product to transfer."); return; }

        StockTransfer st = new StockTransfer();
        st.setSourceWarehouseId(src.getId());
        st.setDestinationWarehouseId(dst.getId());
        st.setTransferDate(dpTransferDate.getValue());
        st.setNotes(txtTransferNotes.getText());
        st.setLines(new ArrayList<>(transferLines));

        try {
            int userId = UserSession.getInstance().getCurrentUser() != null ? UserSession.getInstance().getCurrentUser().getId() : 0;
            transferService.executeStockTransfer(st, userId);
            AlertHelper.info("Success", "Stock Transfer " + st.getTransferNumber() + " executed successfully!");
            transferLines.clear();
            txtTransferNotes.clear();
            loadHistory();
        } catch (Exception e) {
            AlertHelper.error("Transfer Error", e.getMessage());
        }
    }
}