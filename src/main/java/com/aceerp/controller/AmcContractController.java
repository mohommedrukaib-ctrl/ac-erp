package com.aceerp.controller;

import com.aceerp.model.AmcContract;
import com.aceerp.model.AmcSchedule;
import com.aceerp.model.Customer;
import com.aceerp.service.AmcContractService;
import com.aceerp.service.AuditLogService;
import com.aceerp.service.CustomerService;
import com.aceerp.util.AlertHelper;
import com.aceerp.util.InputValidation;
import com.aceerp.util.CurrencyFormatter;
import com.aceerp.util.GlobalQuickAdd;
import com.aceerp.util.SmartSearchHelper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class AmcContractController {

    @FXML private TextField searchField;
    @FXML private TableView<AmcContract> contractsTable;
    @FXML private TableColumn<AmcContract, String> colNumber;
    @FXML private TableColumn<AmcContract, String> colCustomer;
    @FXML private TableColumn<AmcContract, String> colStart;
    @FXML private TableColumn<AmcContract, String> colEnd;
    @FXML private TableColumn<AmcContract, String> colStatus;
    @FXML private TableColumn<AmcContract, String> colValue;
    @FXML private TableView<AmcSchedule> schedulesTable;
    @FXML private TableColumn<AmcSchedule, String> colSchedDate;
    @FXML private TableColumn<AmcSchedule, String> colSchedStatus;
    @FXML private TableColumn<AmcSchedule, String> colSchedJob;
    @FXML private TableColumn<AmcSchedule, String> colSchedNotes;

    private final AmcContractService amcService = new AmcContractService();
    private final CustomerService customerService = new CustomerService();
    private final ObservableList<AmcContract> master = FXCollections.observableArrayList();
    private FilteredList<AmcContract> filtered;

    @FXML
    public void initialize() {
        colNumber.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getContractNumber()));
        colCustomer.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getCustomerName() != null ? c.getValue().getCustomerName() : ""));
        colStart.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getStartDate() != null ? c.getValue().getStartDate().toString() : ""));
        colEnd.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getEndDate() != null ? c.getValue().getEndDate().toString() : ""));
        colStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatusDisplay()));
        colValue.setCellValueFactory(c -> new SimpleStringProperty(
            CurrencyFormatter.format(c.getValue().getTotalValue())));

        colSchedDate.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getScheduledDate() != null ? c.getValue().getScheduledDate().toString() : ""));
        colSchedStatus.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getStatus() != null ? c.getValue().getStatus() : ""));
        colSchedJob.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getServiceJobId() != null ? String.valueOf(c.getValue().getServiceJobId()) : ""));
        colSchedNotes.setCellValueFactory(c -> new SimpleStringProperty(
            c.getValue().getNotes() != null ? c.getValue().getNotes() : ""));

        filtered = new FilteredList<>(master, p -> true);
        contractsTable.setItems(filtered);
        com.aceerp.util.QuickBooksContextMenus.installTableView(contractsTable, contract ->
            java.util.List.of(
                com.aceerp.util.QuickBooksContextMenus.action("Edit", () -> showDialog(contract)),
                com.aceerp.util.QuickBooksContextMenus.action("Add Visit", this::onAddSchedule)
            )
        );
        contractsTable.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> loadSchedules(b));

        searchField.textProperty().addListener((o, a, b) -> {
            String q = b == null ? "" : b.toLowerCase().trim();
            filtered.setPredicate(c -> {
                if (q.isEmpty()) return true;
                return (c.getContractNumber() != null && c.getContractNumber().toLowerCase().contains(q))
                    || (c.getCustomerName() != null && c.getCustomerName().toLowerCase().contains(q));
            });
        });

        onRefresh();
    }

    private void loadSchedules(AmcContract c) {
        if (c == null || c.getId() <= 0) {
            schedulesTable.getItems().clear();
            return;
        }
        schedulesTable.setItems(FXCollections.observableArrayList(amcService.getSchedules(c.getId())));
    }

    @FXML
    private void onRefresh() {
        master.setAll(amcService.listAll());
    }

    @FXML
    private void onAdd() {
        showDialog(null);
    }

    @FXML
    private void onEdit() {
        AmcContract sel = contractsTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            AlertHelper.warning("No Selection", "Select a contract to edit.");
            return;
        }
        showDialog(sel);
    }

    @FXML
    private void onAddSchedule() {
        AmcContract sel = contractsTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            AlertHelper.warning("No Selection", "Select a contract first.");
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add Visit");
        dialog.setHeaderText("Schedule visit for " + sel.getContractNumber());
        DatePicker dp = new DatePicker(LocalDate.now());
        TextField notes = new TextField();
        notes.setPromptText("Notes");
        GridPane g = new GridPane();
        g.setHgap(10); g.setVgap(8); g.setPadding(new Insets(12));
        g.add(new Label("Date:"), 0, 0); g.add(dp, 1, 0);
        g.add(new Label("Notes:"), 0, 1); g.add(notes, 1, 1);
        dialog.getDialogPane().setContent(g);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK && dp.getValue() != null) {
                try {
                    AmcSchedule s = new AmcSchedule();
                    s.setContractId(sel.getId());
                    s.setScheduledDate(dp.getValue());
                    s.setStatus("PENDING");
                    s.setNotes(notes.getText());
                    amcService.addSchedule(s);
                    loadSchedules(sel);
                } catch (Exception e) {
                    AlertHelper.error("Error", e.getMessage());
                }
            }
        });
    }

    @FXML
    private void onGenerateQuarterly() {
        AmcContract sel = contractsTable.getSelectionModel().getSelectedItem();
        if (sel == null) {
            AlertHelper.warning("No Selection", "Select a contract first.");
            return;
        }
        if (!AlertHelper.confirm("Generate Schedule",
                "Create quarterly visits from " + sel.getStartDate() + " to " + sel.getEndDate() + "?")) {
            return;
        }
        try {
            amcService.generateQuarterlySchedule(sel.getId(), sel.getStartDate(), sel.getEndDate());
            loadSchedules(sel);
            AlertHelper.info("Done", "Quarterly visits generated.");
        } catch (Exception e) {
            AlertHelper.error("Error", e.getMessage());
        }
    }

    private void showDialog(AmcContract existing) {
        boolean isEdit = existing != null;
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(isEdit ? "Edit AMC" : "New AMC Contract");

        TextField numField = new TextField();
        numField.setPromptText("AMC-2026-001");
        TextField customerSearch = new TextField();
        customerSearch.setPromptText("Search or type customer name...");
        Label selectedCust = new Label("No customer selected");
        final Customer[] picked = {null};

        List<Customer> allCust;
        try {
            allCust = customerService.listAll();
        } catch (Exception e) {
            allCust = List.of();
        }
        List<Customer> masterCust = allCust;
        SmartSearchHelper.setupEntitySearch(
                customerSearch,
                query -> masterCust.stream().filter(c ->
                        (c.getName() != null && c.getName().toLowerCase().contains(query.toLowerCase().trim()))
                        || (c.getPhone() != null && c.getPhone().contains(query.trim())))
                        .limit(30).toList(),
                Customer::getName,
                customer -> {
                    customerSearch.setText(customer.getName());
                    picked[0] = customer;
                    selectedCust.setText("Selected: " + customer.getName());
                },
                typed -> GlobalQuickAdd.showCustomerDialog(typed, customer -> {
                    customerSearch.setText(customer.getName());
                    picked[0] = customer;
                    selectedCust.setText("Selected: " + customer.getName());
                }),
                typed -> {
                    if (picked[0] != null && (picked[0].getName() == null
                            || !picked[0].getName().equalsIgnoreCase(typed.trim()))) {
                        picked[0] = null;
                        selectedCust.setText("No customer selected");
                    }
                });

        DatePicker dpStart = new DatePicker(LocalDate.now());
        DatePicker dpEnd = new DatePicker(LocalDate.now().plusYears(1));
        ComboBox<String> status = new ComboBox<>(FXCollections.observableArrayList("ACTIVE", "EXPIRED", "CANCELLED"));
        status.setValue("ACTIVE");
        TextField valueField = new TextField("0.00");
        InputValidation.decimalOnly(valueField);
        TextArea notes = new TextArea();
        notes.setPrefRowCount(3);

        if (isEdit) {
            numField.setText(existing.getContractNumber());
            dpStart.setValue(existing.getStartDate());
            dpEnd.setValue(existing.getEndDate());
            status.setValue(existing.getStatus());
            valueField.setText(existing.getTotalValue() != null ? existing.getTotalValue().toPlainString() : "0");
            notes.setText(existing.getNotes());
            customerSearch.setText(existing.getCustomerName() != null ? existing.getCustomerName() : "");
            selectedCust.setText("Customer ID: " + existing.getCustomerId()
                + (existing.getCustomerName() != null ? " — " + existing.getCustomerName() : ""));
            Customer stub = new Customer();
            stub.setId(existing.getCustomerId());
            stub.setName(existing.getCustomerName());
            picked[0] = stub;
        }

        GridPane g = new GridPane();
        g.setHgap(10); g.setVgap(8); g.setPadding(new Insets(12));
        int r = 0;
        g.add(new Label("Contract #:"), 0, r); g.add(numField, 1, r); r++;
        g.add(new Label("Customer:"), 0, r); g.add(new VBox(4, customerSearch, selectedCust), 1, r); r++;
        g.add(new Label("Start:"), 0, r); g.add(dpStart, 1, r); r++;
        g.add(new Label("End:"), 0, r); g.add(dpEnd, 1, r); r++;
        g.add(new Label("Status:"), 0, r); g.add(status, 1, r); r++;
        g.add(new Label("Value:"), 0, r); g.add(valueField, 1, r); r++;
        g.add(new Label("Notes:"), 0, r); g.add(notes, 1, r);

        dialog.getDialogPane().setContent(g);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResizable(true);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        if (numField.getText() == null || numField.getText().isBlank()) {
            AlertHelper.warning("Validation", "Contract number is required.");
            return;
        }
        if (picked[0] == null || picked[0].getId() <= 0) {
            AlertHelper.warning("Validation", "Please select a customer.");
            return;
        }
        try {
            AmcContract c = isEdit ? existing : new AmcContract();
            c.setContractNumber(numField.getText().trim());
            c.setCustomerId(picked[0].getId());
            c.setStartDate(dpStart.getValue());
            c.setEndDate(dpEnd.getValue());
            c.setStatus(status.getValue());
            try {
                c.setTotalValue(new BigDecimal(valueField.getText().trim()));
            } catch (Exception ex) {
                c.setTotalValue(BigDecimal.ZERO);
            }
            c.setNotes(notes.getText());
            amcService.save(c);
            AuditLogService.log(isEdit ? "UPDATE" : "CREATE", "AMC_CONTRACT", c.getId(),
                (isEdit ? "Updated" : "Created") + " AMC " + c.getContractNumber());
            onRefresh();
        } catch (Exception e) {
            AlertHelper.error("Error", e.getMessage());
        }
    }
}