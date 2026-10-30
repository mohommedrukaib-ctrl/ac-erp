package com.aceerp.controller;

import com.aceerp.model.*;
import com.aceerp.service.AuditLogService;
import com.aceerp.service.CustomerService;
import com.aceerp.service.ProductService;
import com.aceerp.service.ReportService;
import com.aceerp.service.ServiceJobService;
import com.aceerp.util.CurrencyFormatter;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.StringConverter;
import javafx.util.converter.IntegerStringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;

public class ServiceJobController {
    private static final Logger logger = LoggerFactory.getLogger(ServiceJobController.class);

    @FXML private TextField searchField;
    @FXML private ListView<ServiceJob> jobListView;

    @FXML private TextField txtJobNumber;
    @FXML private ComboBox<String> cmbJobType;
    @FXML private ComboBox<String> cmbStatus;
    @FXML private DatePicker dpScheduledDate;
    @FXML private DatePicker dpCompletionDate;

    @FXML private Label lblCustomerName;
    @FXML private Button btnSelectCustomer;
    @FXML private ComboBox<User> cmbTechnician;

    @FXML private TextField txtAcModelInfo;
    @FXML private TextArea txtReportedIssue;
    @FXML private TextArea txtResolutionNotes;

    @FXML private TableView<ServiceJobLine> linesTable;
    @FXML private TableColumn<ServiceJobLine, String> colPartCode;
    @FXML private TableColumn<ServiceJobLine, String> colPartName;
    @FXML private TableColumn<ServiceJobLine, Integer> colQty;
    @FXML private TableColumn<ServiceJobLine, String> colUnitPrice;
    @FXML private TableColumn<ServiceJobLine, String> colLineTotal;
    @FXML private TableColumn<ServiceJobLine, Void> colAction;

    @FXML private TextField txtLaborCost;
    @FXML private Label lblPartsTotal;
    @FXML private Label lblGrandTotal;

    @FXML private Button btnNew;
    @FXML private Button btnEdit;
    @FXML private Button btnSave;
    @FXML private Button btnAddPart;

    private final ServiceJobService jobService = new ServiceJobService();
    private final CustomerService customerService = new CustomerService();
    private final ProductService productService = new ProductService();

    private final ObservableList<ServiceJob> jobList = FXCollections.observableArrayList();
    private final ObservableList<ServiceJobLine> lineList = FXCollections.observableArrayList();
    private final ObservableList<Customer> customerList = FXCollections.observableArrayList();
    private final ObservableList<Product> productList = FXCollections.observableArrayList();

    private ServiceJob currentJob;
    private Customer selectedCustomer;
    private boolean editMode = false;

    @FXML
    public void initialize() {
        setupListView();
        setupComboBoxes();
        setupTable();
        loadInitialData();

        searchField.textProperty().addListener((obs, oldVal, newVal) -> filterJobs(newVal));

        jobListView.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                currentJob = newSel;
                editMode = false;
                populateForm(currentJob);
            }
        });

        txtLaborCost.textProperty().addListener((obs, oldV, newV) -> recalculateTotals());
    }

    private void setupListView() {
        jobListView.setItems(jobList);
        jobListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(ServiceJob job, boolean empty) {
                super.updateItem(job, empty);
                if (empty || job == null) {
                    setText(null);
                } else {
                    setText(job.getJobNumber() + " - " + job.getCustomerName() + " (" + job.getStatus() + ")");
                }
            }
        });
    }

    private void setupComboBoxes() {
        cmbJobType.setItems(FXCollections.observableArrayList("INSTALLATION", "REPAIR", "MAINTENANCE", "GAS_REFILL"));
        cmbStatus.setItems(FXCollections.observableArrayList("PENDING", "IN_PROGRESS", "COMPLETED", "CANCELLED"));

        cmbTechnician.setConverter(new StringConverter<>() {
            @Override
            public String toString(User u) { return u == null ? "" : u.getFullName() + " (" + u.getRole() + ")"; }
            @Override
            public User fromString(String string) { return null; }
        });
    }

    private void setupTable() {
        linesTable.setEditable(true);
        colPartCode.setCellValueFactory(new PropertyValueFactory<>("productCode"));
        colPartName.setCellValueFactory(new PropertyValueFactory<>("productName"));

        colQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colQty.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
        colQty.setOnEditCommit(event -> {
            ServiceJobLine line = event.getRowValue();
            line.setQuantity(event.getNewValue() != null ? event.getNewValue() : 1);
            linesTable.refresh();
            recalculateTotals();
        });

        colUnitPrice.setCellValueFactory(cell -> new SimpleStringProperty(CurrencyFormatter.format(cell.getValue().getUnitPrice())));
        colLineTotal.setCellValueFactory(cell -> new SimpleStringProperty(CurrencyFormatter.format(cell.getValue().getLineTotal())));

        colAction.setCellFactory(param -> new TableCell<>() {
            private final Button btnDelete = new Button("X");
            {
                btnDelete.getStyleClass().add("btn-danger");
                btnDelete.setOnAction(e -> {
                    ServiceJobLine line = getTableView().getItems().get(getIndex());
                    lineList.remove(line);
                    recalculateTotals();
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btnDelete);
            }
        });

        linesTable.setItems(lineList);
    }

    private void loadInitialData() {
        try {
            jobList.setAll(jobService.getAllJobs());
        } catch (Exception e) {
            logger.error("Error loading service jobs: {}", e.getMessage(), e);
            jobList.clear();
        }

        try {
            customerList.setAll(customerService.getAllCustomers());
        } catch (Exception e) {
            try {
                customerList.setAll(customerService.listAll());
            } catch (Exception ex) {
                logger.error("Error loading customers: {}", ex.getMessage());
            }
        }

        try {
            productList.setAll(productService.listAll());
        } catch (Exception e) {
            logger.error("Error loading products: {}", e.getMessage());
        }

        try {
            cmbTechnician.setItems(FXCollections.observableArrayList(jobService.getTechnicians()));
        } catch (Exception e) {
            logger.error("Error loading technicians: {}", e.getMessage());
            cmbTechnician.setItems(FXCollections.observableArrayList());
        }

        if (!jobList.isEmpty()) {
            jobListView.getSelectionModel().selectFirst();
        } else {
            handleNew();
        }
    }

    private void filterJobs(String query) {
        if (query == null || query.isBlank()) {
            jobListView.setItems(jobList);
            return;
        }
        String q = query.toLowerCase();
        ObservableList<ServiceJob> filtered = FXCollections.observableArrayList();
        for (ServiceJob job : jobList) {
            if ((job.getJobNumber() != null && job.getJobNumber().toLowerCase().contains(q)) ||
                (job.getCustomerName() != null && job.getCustomerName().toLowerCase().contains(q))) {
                filtered.add(job);
            }
        }
        jobListView.setItems(filtered);
    }

    private void populateForm(ServiceJob job) {
        if (job == null) return;
        txtJobNumber.setText(job.getJobNumber() != null ? job.getJobNumber() : "Auto-Generated");
        cmbJobType.setValue(job.getJobType());
        cmbStatus.setValue(job.getStatus());
        dpScheduledDate.setValue(job.getScheduledDate());
        dpCompletionDate.setValue(job.getCompletionDate());

        lblCustomerName.setText(job.getCustomerName() != null ? job.getCustomerName() : "No Customer Selected");
        selectedCustomer = null;
        if (job.getCustomerId() > 0) {
            for (Customer c : customerList) {
                if (c.getId() == job.getCustomerId()) {
                    selectedCustomer = c;
                    break;
                }
            }
        }

        txtAcModelInfo.setText(job.getAcModelInfo());
        txtReportedIssue.setText(job.getReportedIssue());
        txtResolutionNotes.setText(job.getResolutionNotes());
        txtLaborCost.setText(job.getLaborCost() != null ? job.getLaborCost().toPlainString() : "0.00");

        if (job.getTechnicianId() != null) {
            for (User u : cmbTechnician.getItems()) {
                if (u.getId() == job.getTechnicianId()) {
                    cmbTechnician.setValue(u);
                    break;
                }
            }
        } else {
            cmbTechnician.setValue(null);
        }

        lineList.setAll(job.getLines() != null ? job.getLines() : FXCollections.emptyObservableList());
        recalculateTotals();
        setFormEditable(editMode);
    }

    private void setFormEditable(boolean editable) {
        cmbJobType.setDisable(!editable);
        cmbStatus.setDisable(!editable);
        dpScheduledDate.setDisable(!editable);
        dpCompletionDate.setDisable(!editable);
        cmbTechnician.setDisable(!editable);
        if (btnSelectCustomer != null) btnSelectCustomer.setDisable(!editable);
        txtAcModelInfo.setEditable(editable);
        txtReportedIssue.setEditable(editable);
        txtResolutionNotes.setEditable(editable);
        txtLaborCost.setEditable(editable);
        linesTable.setEditable(editable);
        btnAddPart.setDisable(!editable);
        btnSave.setDisable(!editable);

        boolean isCompleted = currentJob != null && ("COMPLETED".equals(currentJob.getStatus()) || "CANCELLED".equals(currentJob.getStatus()));
        btnEdit.setDisable(isCompleted);
    }

    @FXML
    private void handleNew() {
        jobListView.getSelectionModel().clearSelection();
        currentJob = new ServiceJob();
        editMode = true;
        lineList.clear();
        txtJobNumber.setText("Auto-Generated");
        cmbJobType.setValue("REPAIR");
        cmbStatus.setValue("PENDING");
        dpScheduledDate.setValue(LocalDate.now());
        dpCompletionDate.setValue(null);
        selectedCustomer = null;
        lblCustomerName.setText("No Customer Selected");
        txtAcModelInfo.clear();
        txtReportedIssue.clear();
        txtResolutionNotes.clear();
        txtLaborCost.setText("0.00");
        cmbTechnician.setValue(null);
        recalculateTotals();
        setFormEditable(true);
    }

    @FXML
    private void handleEdit() {
        if (currentJob != null && !("COMPLETED".equals(currentJob.getStatus()) || "CANCELLED".equals(currentJob.getStatus()))) {
            editMode = true;
            setFormEditable(true);
        }
    }

    @FXML
    private void handleSelectCustomer() {
        if (customerList.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Customers", "No customers available.");
            return;
        }
        ChoiceDialog<Customer> dialog = new ChoiceDialog<>(customerList.getFirst(), customerList);
        dialog.setTitle("Select Customer");
        dialog.setHeaderText("Choose Customer for Service Job");
        dialog.setContentText("Customer:");

        dialog.showAndWait().ifPresent(cust -> {
            selectedCustomer = cust;
            lblCustomerName.setText(cust.getName());
        });
    }

    @FXML
    private void handleAddPart() {
        if (productList.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "No Products", "No spare parts/products available.");
            return;
        }
        ChoiceDialog<Product> dialog = new ChoiceDialog<>(productList.getFirst(), productList);
        dialog.setTitle("Select Spare Part / Product");
        dialog.setHeaderText("Add Part to Service Job");
        dialog.setContentText("Product:");

        dialog.showAndWait().ifPresent(prod -> {
            ServiceJobLine line = new ServiceJobLine();
            line.setProductId(prod.getId());
            line.setProductCode(prod.getProductCode());
            line.setProductName(prod.getName());
            line.setQuantity(1);
            line.setUnitPrice(prod.getSellingPrice());
            lineList.add(line);
            recalculateTotals();
        });
    }

    @FXML
    private void handleSave() {
        if (currentJob == null) {
            currentJob = new ServiceJob();
            editMode = true;
        }

        if (selectedCustomer == null && currentJob.getCustomerId() <= 0) {
            showAlert(Alert.AlertType.WARNING, "Validation Error", "Please select a Customer for this Service Job.");
            return;
        }

        if (cmbJobType.getValue() == null || cmbJobType.getValue().isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Validation Error", "Please select a Job Type.");
            return;
        }

        boolean isNew = currentJob.getId() <= 0;

        currentJob.setJobType(cmbJobType.getValue());
        currentJob.setStatus(cmbStatus.getValue() != null ? cmbStatus.getValue() : "PENDING");
        currentJob.setScheduledDate(dpScheduledDate.getValue());
        currentJob.setCompletionDate(dpCompletionDate.getValue());

        if (selectedCustomer != null) {
            currentJob.setCustomerId(selectedCustomer.getId());
            currentJob.setCustomerName(selectedCustomer.getName());
        }

        if (cmbTechnician.getValue() != null) {
            currentJob.setTechnicianId(cmbTechnician.getValue().getId());
            currentJob.setTechnicianName(cmbTechnician.getValue().getFullName());
        } else {
            currentJob.setTechnicianId(null);
            currentJob.setTechnicianName(null);
        }

        currentJob.setAcModelInfo(txtAcModelInfo.getText());
        currentJob.setReportedIssue(txtReportedIssue.getText());
        currentJob.setResolutionNotes(txtResolutionNotes.getText());

        try {
            String laborText = txtLaborCost.getText();
            currentJob.setLaborCost(laborText == null || laborText.isBlank() ? BigDecimal.ZERO : new BigDecimal(laborText.trim()));
        } catch (Exception e) {
            currentJob.setLaborCost(BigDecimal.ZERO);
        }

        currentJob.setLines(new ArrayList<>(lineList));
        currentJob.recalculateTotal();

        if (jobService.saveJob(currentJob)) {
            try {
                AuditLogService.log(
                    isNew ? "CREATE" : "UPDATE",
                    "SERVICE_JOB",
                    currentJob.getId(),
                    (isNew ? "Created" : "Updated") + " service job " + currentJob.getJobNumber() + " (" + currentJob.getJobType() + ")"
                );
            } catch (Exception auditEx) {
                logger.warn("Audit log failed for service job save: {}", auditEx.getMessage());
            }

            showAlert(Alert.AlertType.INFORMATION, "Success", "Service Job saved successfully.");
            editMode = false;
            loadInitialData();
        } else {
            showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to save Service Job.");
        }
    }

    @FXML
    private void handlePrintJobNote() {
        if (currentJob == null || currentJob.getId() <= 0) {
            showAlert(Alert.AlertType.WARNING, "Print Error", "Please save the Service Job first before printing.");
            return;
        }
        try {
            ReportService.printJobNote(currentJob.getId());
            try {
                AuditLogService.log(
                    "PRINT",
                    "SERVICE_JOB",
                    currentJob.getId(),
                    "Printed A5 job note for " + currentJob.getJobNumber()
                );
            } catch (Exception auditEx) {
                logger.warn("Audit log failed for service job print: {}", auditEx.getMessage());
            }
        } catch (Exception e) {
            logger.error("Failed to print job note for ID: {}", currentJob.getId(), e);
            showAlert(Alert.AlertType.ERROR, "Print Error", "Failed to generate A5 Job Note: " + e.getMessage());
        }
    }

    @FXML
    private void handlePrint() {
        handlePrintJobNote();
    }

    private void recalculateTotals() {
        BigDecimal labor = BigDecimal.ZERO;
        try {
            String laborText = txtLaborCost.getText();
            if (laborText != null && !laborText.isBlank()) {
                labor = new BigDecimal(laborText.trim());
            }
        } catch (Exception ignored) {}

        BigDecimal partsSum = BigDecimal.ZERO;
        for (ServiceJobLine line : lineList) {
            line.recalculateTotal();
            partsSum = partsSum.add(line.getLineTotal());
        }

        lblPartsTotal.setText(CurrencyFormatter.format(partsSum));
        lblGrandTotal.setText(CurrencyFormatter.format(labor.add(partsSum)));
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}