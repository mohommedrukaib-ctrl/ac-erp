package com.aceerp.controller;

import com.aceerp.model.AuditLog;
import com.aceerp.service.AuditLogService;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.ResourceBundle;

public class AuditLogController implements Initializable {

    @FXML private TextField txtSearch;
    @FXML private ComboBox<String> comboAction;
    @FXML private ComboBox<String> comboEntityType;
    @FXML private DatePicker dpStartDate;
    @FXML private DatePicker dpEndDate;

    @FXML private TableView<AuditLog> tblAuditLogs;
    @FXML private TableColumn<AuditLog, String> colTimestamp;
    @FXML private TableColumn<AuditLog, String> colUser;
    @FXML private TableColumn<AuditLog, String> colAction;
    @FXML private TableColumn<AuditLog, String> colEntityType;
    @FXML private TableColumn<AuditLog, Integer> colEntityId;
    @FXML private TableColumn<AuditLog, String> colDescription;

    @FXML private Label lblCount;
    @FXML private Label lblPage;
    @FXML private Button btnPrevPage;
    @FXML private Button btnNextPage;

    private final AuditLogService auditLogService = new AuditLogService();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private int currentPage = 1;
    private final int pageSize = 50;
    private int totalRecords = 0;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTableColumns();
        loadFilters();
        loadData();
    }

    private void setupTableColumns() {
        colTimestamp.setCellValueFactory(cell -> 
            new SimpleStringProperty(cell.getValue().getCreatedAt() != null ? dateFormat.format(cell.getValue().getCreatedAt()) : "")
        );

        colUser.setCellValueFactory(cell -> new SimpleStringProperty(
            cell.getValue().getUsername() != null ? cell.getValue().getUsername() : "SYSTEM"
        ));

        colAction.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getAction()));
        
        // Custom badge rendering for action column
        colAction.setCellFactory(col -> new TableCell<AuditLog, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    getStyleClass().removeAll("badge-success", "badge-info", "badge-danger", "badge-warning");
                    switch (item.toUpperCase()) {
                        case "CREATE", "INSERT" -> getStyleClass().add("badge-success");
                        case "UPDATE", "EDIT", "STATUS_CHANGE" -> getStyleClass().add("badge-info");
                        case "DELETE", "REMOVE", "VOID" -> getStyleClass().add("badge-danger");
                        default -> getStyleClass().add("badge-warning");
                    }
                }
            }
        });

        colEntityType.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getEntityType()));
        colEntityId.setCellValueFactory(cell -> new SimpleIntegerProperty(cell.getValue().getEntityId()).asObject());
        colDescription.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getDescription()));
    }

    private void loadFilters() {
        List<String> actions = auditLogService.getDistinctActions();
        actions.add(0, "ALL");
        comboAction.setItems(FXCollections.observableArrayList(actions));
        comboAction.getSelectionModel().selectFirst();

        List<String> types = auditLogService.getDistinctEntityTypes();
        types.add(0, "ALL");
        comboEntityType.setItems(FXCollections.observableArrayList(types));
        comboEntityType.getSelectionModel().selectFirst();
    }

    @FXML
    private void handleFilter() {
        currentPage = 1;
        loadData();
    }

    @FXML
    private void handleReset() {
        txtSearch.clear();
        if (!comboAction.getItems().isEmpty()) comboAction.getSelectionModel().selectFirst();
        if (!comboEntityType.getItems().isEmpty()) comboEntityType.getSelectionModel().selectFirst();
        dpStartDate.setValue(null);
        dpEndDate.setValue(null);
        currentPage = 1;
        loadData();
    }

    @FXML
    private void handlePrevPage() {
        if (currentPage > 1) {
            currentPage--;
            loadData();
        }
    }

    @FXML
    private void handleNextPage() {
        int maxPages = (int) Math.ceil((double) totalRecords / pageSize);
        if (currentPage < maxPages) {
            currentPage++;
            loadData();
        }
    }

    private void loadData() {
        String keyword = txtSearch.getText();
        String action = comboAction.getValue();
        String entityType = comboEntityType.getValue();
        LocalDate start = dpStartDate.getValue();
        LocalDate end = dpEndDate.getValue();

        int offset = (currentPage - 1) * pageSize;

        totalRecords = auditLogService.countLogs(keyword, action, entityType, start, end);
        List<AuditLog> logs = auditLogService.findLogs(keyword, action, entityType, start, end, pageSize, offset);

        tblAuditLogs.setItems(FXCollections.observableArrayList(logs));

        int maxPages = Math.max(1, (int) Math.ceil((double) totalRecords / pageSize));
        lblCount.setText(String.format("Showing %d - %d of %d audit entries", 
            totalRecords == 0 ? 0 : offset + 1, 
            Math.min(offset + pageSize, totalRecords), 
            totalRecords));
        lblPage.setText(String.format("Page %d of %d", currentPage, maxPages));

        btnPrevPage.setDisable(currentPage <= 1);
        btnNextPage.setDisable(currentPage >= maxPages);
    }
}