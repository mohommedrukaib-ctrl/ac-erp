package com.aceerp.controller;

import com.aceerp.model.TechnicianCommission;
import com.aceerp.service.AuditLogService;
import com.aceerp.service.CommissionService;
import com.aceerp.util.AlertHelper;
import com.aceerp.util.CurrencyFormatter;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

public class CommissionController {

    @FXML private ComboBox<String> statusFilter;
    @FXML private Label summaryLabel;
    @FXML private TableView<TechnicianCommission> commissionsTable;
    @FXML private TableColumn<TechnicianCommission, String> colTech;
    @FXML private TableColumn<TechnicianCommission, String> colJob;
    @FXML private TableColumn<TechnicianCommission, String> colAmount;
    @FXML private TableColumn<TechnicianCommission, String> colStatus;
    @FXML private TableColumn<TechnicianCommission, String> colCreated;
    @FXML private TableColumn<TechnicianCommission, String> colPaid;

    private final CommissionService commissionService = new CommissionService();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML
    public void initialize() {
        statusFilter.setItems(FXCollections.observableArrayList("ALL", "PENDING", "PAID"));
        statusFilter.setValue("PENDING");
        statusFilter.setOnAction(e -> onRefresh());

        colTech.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getTechnicianName() != null ? c.getValue().getTechnicianName() : ""));
        colJob.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getJobNumber() != null ? c.getValue().getJobNumber() : ""));
        colAmount.setCellValueFactory(c -> new SimpleStringProperty(
            CurrencyFormatter.format(c.getValue().getCommissionAmount())));
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getStatus() != null ? c.getValue().getStatus() : ""));
        colCreated.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getCreatedAt() != null ? c.getValue().getCreatedAt().format(FMT) : ""));
        colPaid.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getPaidAt() != null ? c.getValue().getPaidAt().format(FMT) : ""));

        commissionsTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        onRefresh();
    }

    @FXML
    private void onRefresh() {
        String st = statusFilter.getValue();
        var list = commissionService.listByStatus("ALL".equals(st) ? null : st);
        commissionsTable.setItems(FXCollections.observableArrayList(list));
        BigDecimal pending = list.stream()
            .filter(c -> "PENDING".equals(c.getStatus()))
            .map(TechnicianCommission::getCommissionAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        summaryLabel.setText("Rows: " + list.size()
            + "  |  Pending total (visible): " + CurrencyFormatter.format(pending));
    }

    @FXML
    private void onMarkPaid() {
        var selected = commissionsTable.getSelectionModel().getSelectedItems();
        if (selected == null || selected.isEmpty()) {
            AlertHelper.warning("No Selection", "Select one or more PENDING commissions.");
            return;
        }
        var ids = selected.stream()
            .filter(c -> "PENDING".equals(c.getStatus()))
            .map(TechnicianCommission::getId)
            .toList();
        if (ids.isEmpty()) {
            AlertHelper.warning("Nothing to Pay", "Only PENDING commissions can be marked paid.");
            return;
        }
        if (!AlertHelper.confirm("Mark Paid", "Mark " + ids.size() + " commission(s) as PAID?")) return;
        try {
            commissionService.markPaidBatch(ids);
            for (Integer id : ids) {
                AuditLogService.log("UPDATE", "TECHNICIAN_COMMISSION", id, "Marked commission paid");
            }
            onRefresh();
            AlertHelper.info("Done", "Commissions marked as paid.");
        } catch (Exception e) {
            AlertHelper.error("Error", e.getMessage());
        }
    }
}