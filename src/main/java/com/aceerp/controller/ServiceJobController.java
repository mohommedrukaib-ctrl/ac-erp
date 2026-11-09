package com.aceerp.controller;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.*;
import com.aceerp.service.AuditLogService;
import com.aceerp.service.CustomerService;
import com.aceerp.service.JobCostingService;
import com.aceerp.service.ProductService;
import com.aceerp.service.ReportService;
import com.aceerp.service.ServiceJobService;
import com.aceerp.util.CurrencyFormatter;
import com.aceerp.util.GlobalQuickAdd;
import com.aceerp.util.InputValidation;
import com.aceerp.util.SmartSearchHelper;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import javafx.util.converter.BigDecimalStringConverter;
import javafx.util.converter.IntegerStringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;

public class ServiceJobController {
    private static final Logger logger = LoggerFactory.getLogger(ServiceJobController.class);

    @FXML private TextField searchField;
    @FXML private ListView<ServiceJob> jobListView;

    @FXML private TextField txtJobNumber;
    @FXML private ComboBox<String> cmbJobType;
    @FXML private ComboBox<String> cmbStatus;
    @FXML private DatePicker dpScheduledDate;
    @FXML private DatePicker dpCompletionDate;

    @FXML private TextField txtCustomerName;
    @FXML private TextField txtCustomerPhone;
    @FXML private Button btnSelectCustomer;
    @FXML private ComboBox<User> cmbTechnician;

    @FXML private TextField txtAcModelInfo;
    @FXML private TextArea txtReportedIssue;
    @FXML private TextArea txtResolutionNotes;

    @FXML private TableView<ServiceJobLine> linesTable;
    @FXML private TableColumn<ServiceJobLine, String> colPartCode;
    @FXML private TableColumn<ServiceJobLine, String> colPartName;
    @FXML private TableColumn<ServiceJobLine, Integer> colQty;
    @FXML private TableColumn<ServiceJobLine, BigDecimal> colUnitPrice;
    @FXML private TableColumn<ServiceJobLine, BigDecimal> colSetPrice;
    @FXML private TableColumn<ServiceJobLine, BigDecimal> colDisc;
    @FXML private TableColumn<ServiceJobLine, String> colDiscountType;
    @FXML private TableColumn<ServiceJobLine, Boolean> colPackagePrice;
    @FXML private TableColumn<ServiceJobLine, String> colNotes;
    @FXML private TableColumn<ServiceJobLine, String> colLineTotal;
    @FXML private TableColumn<ServiceJobLine, Void> colAction;

    @FXML private TextField txtLaborCost;
    @FXML private Label lblPartsTotal;
    @FXML private Label lblGrandTotal;

    @FXML private Button btnNew;
    @FXML private Button btnEdit;
    @FXML private Button btnSave;
    @FXML private Button btnAddPart;
    @FXML private Button btnAddEmptyLine;
    @FXML private Button btnMoveUp;
    @FXML private Button btnMoveDown;

    // ---- Costing & Profitability tab ----
    @FXML private TabPane detailTabs;
    @FXML private TextField txtBudgetMaterials;
    @FXML private TextField txtBudgetLabor;
    @FXML private TextField txtBudgetEquipment;
    @FXML private TextField txtBudgetSubcontractor;
    @FXML private TextField txtBudgetOverhead;
    @FXML private Label lblTotalBudget;
    @FXML private Button btnSaveBudget;
    @FXML private Button btnAddCostEntry;
    @FXML private Button btnRefreshCosting;

    @FXML private TableView<JobCostEntry> costEntriesTable;
    @FXML private TableColumn<JobCostEntry, String> colCostDate;
    @FXML private TableColumn<JobCostEntry, String> colCostCategory;
    @FXML private TableColumn<JobCostEntry, String> colCostDesc;
    @FXML private TableColumn<JobCostEntry, String> colCostQty;
    @FXML private TableColumn<JobCostEntry, String> colCostUnit;
    @FXML private TableColumn<JobCostEntry, String> colCostTotal;
    @FXML private TableColumn<JobCostEntry, String> colCostSource;

    @FXML private Label lblContractPrice;
    @FXML private Label lblBudgetedCost;
    @FXML private Label lblActualCost;
    @FXML private Label lblCostVariance;
    @FXML private Label lblNetProfit;
    @FXML private Label lblGrossMargin;
    @FXML private Label lblCostingHint;

    private final ServiceJobService jobService = new ServiceJobService();
    private final CustomerService customerService = new CustomerService();
    private final ProductService productService = new ProductService();
    private final JobCostingService jobCostingService = new JobCostingService();

    private final ObservableList<ServiceJob> jobList = FXCollections.observableArrayList();
    private final ObservableList<ServiceJobLine> lineList = FXCollections.observableArrayList();
    private final ObservableList<Customer> customerList = FXCollections.observableArrayList();
    private final ObservableList<Product> productList = FXCollections.observableArrayList();
    private final ObservableList<JobCostEntry> costEntryList = FXCollections.observableArrayList();

    private ServiceJob currentJob;
    private Customer selectedCustomer;
    private JobBudget currentBudget;
    private boolean editMode = false;

    @FXML
    public void initialize() {
        if (txtCustomerPhone != null) {
            InputValidation.phoneNumber(txtCustomerPhone);
        }
        if (txtLaborCost != null) {
            InputValidation.decimalOnly(txtLaborCost);
        }
        if (txtBudgetMaterials != null) InputValidation.decimalOnly(txtBudgetMaterials);
        if (txtBudgetLabor != null) InputValidation.decimalOnly(txtBudgetLabor);
        if (txtBudgetEquipment != null) InputValidation.decimalOnly(txtBudgetEquipment);
        if (txtBudgetSubcontractor != null) InputValidation.decimalOnly(txtBudgetSubcontractor);
        if (txtBudgetOverhead != null) InputValidation.decimalOnly(txtBudgetOverhead);

        setupListView();
        com.aceerp.util.QuickBooksContextMenus.installListView(jobListView, job -> java.util.List.of(
            com.aceerp.util.QuickBooksContextMenus.action("Edit", this::handleEdit),
            com.aceerp.util.QuickBooksContextMenus.action("Print", this::handlePrint)
        ));
        setupComboBoxes();
        setupTable();
        setupCostingTable();
        setupBudgetListeners();
        loadInitialData();
        setupSmartCustomerSearch();

        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> filterJobs(newVal));
        }

        if (jobListView != null) {
            jobListView.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
                if (newSel != null) {
                    currentJob = newSel;
                    editMode = false;
                    populateForm(currentJob);
                }
            });
        }

        if (txtLaborCost != null) {
            txtLaborCost.textProperty().addListener((obs, oldV, newV) -> recalculateTotals());
        }
    }

    private void setupListView() {
        if (jobListView == null) return;
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
        if (cmbJobType != null) cmbJobType.setItems(FXCollections.observableArrayList("INSTALLATION", "REPAIR", "MAINTENANCE", "GAS_REFILL"));
        if (cmbStatus != null) cmbStatus.setItems(FXCollections.observableArrayList("PENDING", "IN_PROGRESS", "COMPLETED", "CANCELLED"));

        if (cmbTechnician != null) {
            cmbTechnician.setConverter(new StringConverter<>() {
                @Override
                public String toString(User u) { return u == null ? "" : u.getFullName() + " (" + u.getRole() + ")"; }
                @Override
                public User fromString(String string) { return null; }
            });
        }
    }

    private void setupSmartCustomerSearch() {
        SmartSearchHelper.setupEntitySearch(
                txtCustomerName,
                query -> customerList.stream()
                        .filter(c -> c.getName() != null
                                && c.getName().toLowerCase().contains(query.toLowerCase().trim()))
                        .toList(),
                Customer::getName,
                this::applyCustomer,
                typed -> GlobalQuickAdd.showCustomerDialog(typed, this::applyCustomer),
                typed -> {
                    if (selectedCustomer != null && (selectedCustomer.getName() == null
                            || !selectedCustomer.getName().equalsIgnoreCase(typed.trim()))) {
                        selectedCustomer = null;
                    }
                });
    }

    private void applyCustomer(Customer customer) {
        if (customer == null) return;
        if (txtCustomerName != null) txtCustomerName.setText(customer.getName() != null ? customer.getName() : "");
        if (txtCustomerPhone != null) txtCustomerPhone.setText(customer.getPhone() != null ? customer.getPhone() : "");
        selectedCustomer = customer;
    }

    private void setupTable() {
        if (linesTable == null) return;
        linesTable.setEditable(true);
        if (colPartCode != null) colPartCode.setCellValueFactory(new PropertyValueFactory<>("productCode"));
        if (colPartName != null) colPartName.setCellValueFactory(new PropertyValueFactory<>("productName"));

        if (colQty != null) {
            colQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));
            colQty.setCellFactory(TextFieldTableCell.forTableColumn(new IntegerStringConverter()));
            colQty.setOnEditCommit(event -> {
                if (!editMode) { linesTable.refresh(); return; }
                ServiceJobLine line = event.getRowValue();
                line.setQuantity(event.getNewValue() != null ? event.getNewValue() : 1);
                linesTable.refresh();
                recalculateTotals();
            });
        }

        if (colUnitPrice != null && colSetPrice != null) {
            com.aceerp.util.LinePricingTableColumns.configureUnitAndSetPrice(
                    colUnitPrice, colSetPrice, ServiceJobLine::getUnitPrice, ServiceJobLine::setUnitPrice,
                    ServiceJobLine::isPackagePrice, () -> editMode, this::recalculateTotals);
        }

        if (colDisc != null) {
            com.aceerp.util.LinePricingTableColumns.configureDiscountType(
                    colDiscountType, ServiceJobLine::getDiscountType, ServiceJobLine::setDiscountType, this::recalculateTotals);
            com.aceerp.util.LinePricingTableColumns.configurePackagePrice(
                    colPackagePrice, ServiceJobLine::isPackagePrice, ServiceJobLine::setPackagePrice, this::recalculateTotals);
            colDisc.setCellValueFactory(new PropertyValueFactory<>("discountValue"));
            colDisc.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
            colDisc.setOnEditCommit(event -> {
                if (!editMode) { linesTable.refresh(); return; }
                ServiceJobLine line = event.getRowValue();
                line.setDiscountValue(event.getNewValue() != null ? event.getNewValue() : BigDecimal.ZERO);
                linesTable.refresh();
                recalculateTotals();
            });
        }

        if (colNotes != null) {
            colNotes.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNotes() != null ? d.getValue().getNotes() : ""));
            colNotes.setCellFactory(TextFieldTableCell.forTableColumn());
            colNotes.setOnEditCommit(event -> {
                if (!editMode) { linesTable.refresh(); return; }
                ServiceJobLine line = event.getRowValue();
                line.setNotes(event.getNewValue());
                linesTable.refresh();
            });
        }

        if (colLineTotal != null) {
            colLineTotal.setCellValueFactory(cell -> new SimpleStringProperty(CurrencyFormatter.format(cell.getValue().getLineTotal())));
        }

        if (colAction != null) {
            colAction.setCellFactory(param -> new TableCell<>() {
                private final Button btnDelete = new Button("X");
                {
                    btnDelete.getStyleClass().add("btn-danger");
                    btnDelete.setOnAction(e -> {
                        if (!editMode) return;
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
        }

        linesTable.setItems(lineList);
    }

    private void setupCostingTable() {
        if (costEntriesTable == null) return;
        if (colCostDate != null) colCostDate.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getEntryDate() != null ? c.getValue().getEntryDate().toString() : ""));
        if (colCostCategory != null) colCostCategory.setCellValueFactory(c -> new SimpleStringProperty(
                nullToEmpty(c.getValue().getCostCategory())));
        if (colCostDesc != null) colCostDesc.setCellValueFactory(c -> new SimpleStringProperty(
                nullToEmpty(c.getValue().getDescription())));
        if (colCostQty != null) colCostQty.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getQuantity() != null ? c.getValue().getQuantity().toPlainString() : "0"));
        if (colCostUnit != null) colCostUnit.setCellValueFactory(c -> new SimpleStringProperty(
                CurrencyFormatter.format(c.getValue().getUnitCost())));
        if (colCostTotal != null) colCostTotal.setCellValueFactory(c -> new SimpleStringProperty(
                CurrencyFormatter.format(c.getValue().getTotalCost())));
        if (colCostSource != null) colCostSource.setCellValueFactory(c -> new SimpleStringProperty(
                nullToEmpty(c.getValue().getSourceType())));
        costEntriesTable.setItems(costEntryList);
    }

    private void setupBudgetListeners() {
        Runnable recalc = this::recalculateBudgetTotalLocal;
        if (txtBudgetMaterials != null) txtBudgetMaterials.textProperty().addListener((o, a, b) -> recalc.run());
        if (txtBudgetLabor != null) txtBudgetLabor.textProperty().addListener((o, a, b) -> recalc.run());
        if (txtBudgetEquipment != null) txtBudgetEquipment.textProperty().addListener((o, a, b) -> recalc.run());
        if (txtBudgetSubcontractor != null) txtBudgetSubcontractor.textProperty().addListener((o, a, b) -> recalc.run());
        if (txtBudgetOverhead != null) txtBudgetOverhead.textProperty().addListener((o, a, b) -> recalc.run());
    }

    @FXML
    private void handleMoveUp() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click Edit first to modify the service job.");
            return;
        }
        if (linesTable == null) return;
        int idx = linesTable.getSelectionModel().getSelectedIndex();
        if (idx > 0) {
            ServiceJobLine line = lineList.remove(idx);
            lineList.add(idx - 1, line);
            linesTable.getSelectionModel().select(idx - 1);
        }
    }

    @FXML
    private void handleMoveDown() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click Edit first to modify the service job.");
            return;
        }
        if (linesTable == null) return;
        int idx = linesTable.getSelectionModel().getSelectedIndex();
        if (idx >= 0 && idx < lineList.size() - 1) {
            ServiceJobLine line = lineList.remove(idx);
            lineList.add(idx + 1, line);
            linesTable.getSelectionModel().select(idx + 1);
        }
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
            if (cmbTechnician != null) {
                cmbTechnician.setItems(FXCollections.observableArrayList(jobService.getTechnicians()));
            }
        } catch (Exception e) {
            logger.error("Error loading technicians: {}", e.getMessage());
            if (cmbTechnician != null) cmbTechnician.setItems(FXCollections.observableArrayList());
        }

        if (jobListView != null) {
            if (!jobList.isEmpty()) {
                jobListView.getSelectionModel().selectFirst();
            } else {
                handleNew();
            }
        }
    }

    private void filterJobs(String query) {
        if (jobListView == null) return;
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
        if (txtJobNumber != null) txtJobNumber.setText(job.getJobNumber() != null ? job.getJobNumber() : "Auto-Generated");
        if (cmbJobType != null) cmbJobType.setValue(job.getJobType());
        if (cmbStatus != null) cmbStatus.setValue(job.getStatus());
        if (dpScheduledDate != null) dpScheduledDate.setValue(job.getScheduledDate());
        if (dpCompletionDate != null) dpCompletionDate.setValue(job.getCompletionDate());

        selectedCustomer = null;
        if (job.getCustomerId() > 0) {
            for (Customer c : customerList) {
                if (c.getId() == job.getCustomerId()) {
                    applyCustomer(c);
                    break;
                }
            }
        } else {
            if (txtCustomerName != null) txtCustomerName.setText(job.getCustomerName() != null ? job.getCustomerName() : "");
            if (txtCustomerPhone != null) txtCustomerPhone.clear();
        }

        if (txtAcModelInfo != null) txtAcModelInfo.setText(job.getAcModelInfo());
        if (txtReportedIssue != null) txtReportedIssue.setText(job.getReportedIssue());
        if (txtResolutionNotes != null) txtResolutionNotes.setText(job.getResolutionNotes());
        if (txtLaborCost != null) txtLaborCost.setText(job.getLaborCost() != null ? job.getLaborCost().toPlainString() : "0.00");

        if (cmbTechnician != null) {
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
        }

        lineList.setAll(job.getLines() != null ? job.getLines() : FXCollections.emptyObservableList());
        recalculateTotals();
        setFormEditable(editMode);
        loadCostingData();
    }

    private void setFormEditable(boolean editable) {
        if (cmbJobType != null) cmbJobType.setDisable(!editable);
        if (cmbStatus != null) cmbStatus.setDisable(!editable);
        if (dpScheduledDate != null) dpScheduledDate.setDisable(!editable);
        if (dpCompletionDate != null) dpCompletionDate.setDisable(!editable);
        if (cmbTechnician != null) cmbTechnician.setDisable(!editable);
        if (txtCustomerName != null) txtCustomerName.setEditable(editable);
        if (txtCustomerPhone != null) txtCustomerPhone.setEditable(editable);
        if (btnSelectCustomer != null) btnSelectCustomer.setDisable(!editable);
        if (txtAcModelInfo != null) txtAcModelInfo.setEditable(editable);
        if (txtReportedIssue != null) txtReportedIssue.setEditable(editable);
        if (txtResolutionNotes != null) txtResolutionNotes.setEditable(editable);
        if (txtLaborCost != null) txtLaborCost.setEditable(editable);
        if (linesTable != null) linesTable.setEditable(editable);
        if (btnAddPart != null) btnAddPart.setDisable(!editable);
        if (btnAddEmptyLine != null) btnAddEmptyLine.setDisable(!editable);
        if (btnSave != null) btnSave.setDisable(!editable);

        boolean isCompleted = currentJob != null && ("COMPLETED".equals(currentJob.getStatus()) || "CANCELLED".equals(currentJob.getStatus()));
        if (btnEdit != null) btnEdit.setDisable(isCompleted);

        setCostingEnabled(currentJob != null && currentJob.getId() > 0 && !"CANCELLED".equals(currentJob.getStatus()));
    }

    private void setCostingEnabled(boolean enabled) {
        if (txtBudgetMaterials != null) txtBudgetMaterials.setDisable(!enabled);
        if (txtBudgetLabor != null) txtBudgetLabor.setDisable(!enabled);
        if (txtBudgetEquipment != null) txtBudgetEquipment.setDisable(!enabled);
        if (txtBudgetSubcontractor != null) txtBudgetSubcontractor.setDisable(!enabled);
        if (txtBudgetOverhead != null) txtBudgetOverhead.setDisable(!enabled);
        if (btnSaveBudget != null) btnSaveBudget.setDisable(!enabled);
        if (btnAddCostEntry != null) btnAddCostEntry.setDisable(!enabled);
        if (btnRefreshCosting != null) btnRefreshCosting.setDisable(currentJob == null || currentJob.getId() <= 0);
        if (lblCostingHint != null) {
            lblCostingHint.setText(enabled
                    ? "Cost entries auto-post DR 1250 WIP / CR Cash. Budget is planning-only."
                    : "Save the service job first to enable budgeting and WIP cost posting.");
        }
    }

    private void loadCostingData() {
        costEntryList.clear();
        currentBudget = null;
        clearProfitabilityLabels();

        if (currentJob == null || currentJob.getId() <= 0) {
            clearBudgetFields();
            setCostingEnabled(false);
            return;
        }

        try {
            currentBudget = jobCostingService.getBudgetForJob(currentJob.getId());
            populateBudgetFields(currentBudget);
        } catch (Exception e) {
            logger.error("Failed to load job budget: {}", e.getMessage(), e);
            clearBudgetFields();
        }

        try {
            costEntryList.setAll(jobCostingService.getCostEntriesForJob(currentJob.getId()));
        } catch (Exception e) {
            logger.error("Failed to load job cost entries: {}", e.getMessage(), e);
        }

        try {
            JobProfitabilityDTO dto = jobCostingService.getJobProfitability(currentJob.getId());
            if (currentJob.getGrandTotal() != null && currentJob.getGrandTotal().compareTo(BigDecimal.ZERO) > 0) {
                dto.setContractPrice(currentJob.getGrandTotal());
            }
            populateProfitability(dto);
        } catch (Exception e) {
            logger.error("Failed to load job profitability: {}", e.getMessage(), e);
        }

        setCostingEnabled(!"CANCELLED".equals(currentJob.getStatus()));
    }

    private void populateBudgetFields(JobBudget budget) {
        if (budget == null) {
            clearBudgetFields();
            return;
        }
        if (txtBudgetMaterials != null) txtBudgetMaterials.setText(plain(budget.getBudgetedMaterials()));
        if (txtBudgetLabor != null) txtBudgetLabor.setText(plain(budget.getBudgetedLabor()));
        if (txtBudgetEquipment != null) txtBudgetEquipment.setText(plain(budget.getBudgetedEquipment()));
        if (txtBudgetSubcontractor != null) txtBudgetSubcontractor.setText(plain(budget.getBudgetedSubcontractor()));
        if (txtBudgetOverhead != null) txtBudgetOverhead.setText(plain(budget.getBudgetedOverhead()));
        budget.recalculateTotal();
        if (lblTotalBudget != null) lblTotalBudget.setText(CurrencyFormatter.format(budget.getTotalBudgetedCost()));
    }

    private void clearBudgetFields() {
        if (txtBudgetMaterials == null) return;
        txtBudgetMaterials.setText("0.00");
        if (txtBudgetLabor != null) txtBudgetLabor.setText("0.00");
        if (txtBudgetEquipment != null) txtBudgetEquipment.setText("0.00");
        if (txtBudgetSubcontractor != null) txtBudgetSubcontractor.setText("0.00");
        if (txtBudgetOverhead != null) txtBudgetOverhead.setText("0.00");
        if (lblTotalBudget != null) lblTotalBudget.setText(CurrencyFormatter.format(BigDecimal.ZERO));
    }

    private void recalculateBudgetTotalLocal() {
        BigDecimal total = parseDecimal(txtBudgetMaterials != null ? txtBudgetMaterials.getText() : null)
                .add(parseDecimal(txtBudgetLabor != null ? txtBudgetLabor.getText() : null))
                .add(parseDecimal(txtBudgetEquipment != null ? txtBudgetEquipment.getText() : null))
                .add(parseDecimal(txtBudgetSubcontractor != null ? txtBudgetSubcontractor.getText() : null))
                .add(parseDecimal(txtBudgetOverhead != null ? txtBudgetOverhead.getText() : null));
        if (lblTotalBudget != null) lblTotalBudget.setText(CurrencyFormatter.format(total));
    }

    private void populateProfitability(JobProfitabilityDTO dto) {
        if (dto == null) {
            clearProfitabilityLabels();
            return;
        }
        dto.calculate();
        if (lblContractPrice != null) lblContractPrice.setText(CurrencyFormatter.format(dto.getContractPrice()));
        if (lblBudgetedCost != null) lblBudgetedCost.setText(CurrencyFormatter.format(dto.getBudgetedCost()));
        if (lblActualCost != null) lblActualCost.setText(CurrencyFormatter.format(dto.getActualCost()));
        if (lblCostVariance != null) lblCostVariance.setText(CurrencyFormatter.format(dto.getCostVariance()));
        if (lblNetProfit != null) lblNetProfit.setText(CurrencyFormatter.format(dto.getNetProfit()));
        if (lblGrossMargin != null) lblGrossMargin.setText(dto.getGrossMarginPercent().toPlainString() + "%");

        styleAmount(lblCostVariance, dto.getCostVariance());
        styleAmount(lblNetProfit, dto.getNetProfit());
        styleAmount(lblGrossMargin, dto.getGrossMarginPercent());
    }

    private void clearProfitabilityLabels() {
        if (lblContractPrice == null) return;
        String zero = CurrencyFormatter.format(BigDecimal.ZERO);
        lblContractPrice.setText(zero);
        if (lblBudgetedCost != null) lblBudgetedCost.setText(zero);
        if (lblActualCost != null) lblActualCost.setText(zero);
        if (lblCostVariance != null) lblCostVariance.setText(zero);
        if (lblNetProfit != null) lblNetProfit.setText(zero);
        if (lblGrossMargin != null) lblGrossMargin.setText("0.00%");
        if (lblCostVariance != null) lblCostVariance.setStyle("-fx-font-weight: bold;");
        if (lblNetProfit != null) lblNetProfit.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        if (lblGrossMargin != null) lblGrossMargin.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
    }

    private void styleAmount(Label label, BigDecimal value) {
        if (label == null) return;
        String base = label == lblNetProfit || label == lblGrossMargin
                ? "-fx-font-size: 16px; -fx-font-weight: bold; "
                : "-fx-font-weight: bold; ";
        if (value == null || value.compareTo(BigDecimal.ZERO) == 0) {
            label.setStyle(base);
        } else if (value.compareTo(BigDecimal.ZERO) > 0) {
            label.setStyle(base + "-fx-text-fill: #27ae60;");
        } else {
            label.setStyle(base + "-fx-text-fill: #c0392b;");
        }
    }

    @FXML
    private void handleSelectCustomer() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click Edit first to select a customer.");
            return;
        }

        Dialog<Customer> dialog = new Dialog<>();
        dialog.setTitle("Select Customer");
        dialog.setHeaderText("Search or select customer");

        ButtonType selectBtnType = new ButtonType("Select Customer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(selectBtnType, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        TextField searchBox = new TextField();
        searchBox.setPromptText("Search customer by name or phone...");

        ListView<Customer> listView = new ListView<>();
        listView.setPrefHeight(220);
        listView.setPrefWidth(350);

        ObservableList<Customer> masterList = FXCollections.observableArrayList(customerList);
        ObservableList<Customer> filteredList = FXCollections.observableArrayList(masterList);
        listView.setItems(filteredList);

        listView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Customer c, boolean empty) {
                super.updateItem(c, empty);
                if (empty || c == null) {
                    setText(null);
                } else {
                    String phone = (c.getPhone() != null && !c.getPhone().isBlank()) ? " | Phone: " + c.getPhone() : "";
                    setText(c.getName() + phone);
                }
            }
        });

        searchBox.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isBlank()) {
                filteredList.setAll(masterList);
            } else {
                String q = newVal.toLowerCase();
                filteredList.setAll(masterList.stream()
                    .filter(c -> (c.getName() != null && c.getName().toLowerCase().contains(q)) ||
                                 (c.getPhone() != null && c.getPhone().contains(q)))
                    .toList());
            }
        });

        listView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && listView.getSelectionModel().getSelectedItem() != null) {
                dialog.setResult(listView.getSelectionModel().getSelectedItem());
                dialog.close();
            }
        });

        content.getChildren().addAll(searchBox, listView);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(button -> {
            if (button == selectBtnType) {
                return listView.getSelectionModel().getSelectedItem();
            }
            return null;
        });

        Optional<Customer> result = dialog.showAndWait();
        result.ifPresent(this::applyCustomer);
    }

    @FXML
    private void handleAddPart() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click Edit first to modify the service job.");
            return;
        }

        Dialog<Product> dialog = new Dialog<>();
        dialog.setTitle("Select Item");
        dialog.setHeaderText("Search products");

        ButtonType addButtonType = new ButtonType("Add to Document", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPadding(new Insets(10));

        TextField searchBox = new TextField();
        searchBox.setPromptText("Search product...");

        ListView<Product> listView = new ListView<>();
        listView.setPrefHeight(250);
        listView.setPrefWidth(350);

        ObservableList<Product> masterList = FXCollections.observableArrayList(productList);
        ObservableList<Product> filteredList = FXCollections.observableArrayList(masterList);
        listView.setItems(filteredList);

        listView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Product p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) {
                    setText(null);
                } else {
                    int stock = p.getStockQuantity();
                    String stockStr = stock > 0 ? "Stock: " + stock : "OUT OF STOCK";
                    setText(p.getProductCode() + " | " + p.getName() + " [" + stockStr + "]");
                }
            }
        });

        searchBox.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isBlank()) {
                filteredList.setAll(masterList);
            } else {
                String q = newVal.toLowerCase();
                filteredList.setAll(masterList.stream()
                    .filter(p -> (p.getProductCode() != null && p.getProductCode().toLowerCase().contains(q)) ||
                                 (p.getName() != null && p.getName().toLowerCase().contains(q)))
                    .toList());
            }
        });

        listView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && listView.getSelectionModel().getSelectedItem() != null) {
                dialog.setResult(listView.getSelectionModel().getSelectedItem());
                dialog.close();
            }
        });

        content.getChildren().addAll(searchBox, listView);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(button -> {
            if (button == addButtonType) {
                return listView.getSelectionModel().getSelectedItem();
            }
            return null;
        });

        Optional<Product> result = dialog.showAndWait();
        result.ifPresent(prod -> {
            ServiceJobLine line = new ServiceJobLine();
            line.setProductId(prod.getId());
            line.setProductCode(prod.getProductCode());
            line.setProductName(prod.getName());
            line.setQuantity(1);
            line.setUnitPrice(prod.getSellingPrice());
            line.setDiscountPercent(BigDecimal.ZERO);
            line.setNotes("");
            lineList.add(line);
            recalculateTotals();
        });
    }

    @FXML
    private void handleAddEmptyLine() {
        if (!editMode) {
            showAlert(Alert.AlertType.WARNING, "View Mode", "Click Edit first to modify the service job.");
            return;
        }
        ServiceJobLine line = new ServiceJobLine();
        line.setProductId(0);
        line.setProductCode("MISC");
        line.setProductName("Custom Service / Part");
        line.setQuantity(1);
        line.setUnitPrice(BigDecimal.ZERO);
        line.setDiscountPercent(BigDecimal.ZERO);
        line.setNotes("");
        lineList.add(line);
        recalculateTotals();
    }

    @FXML
    private void handleSaveBudget() {
        if (currentJob == null || currentJob.getId() <= 0) {
            showAlert(Alert.AlertType.WARNING, "Save Job First", "Please save the service job before setting a budget.");
            return;
        }
        try {
            JobBudget budget = currentBudget != null ? currentBudget : new JobBudget();
            budget.setServiceJobId(currentJob.getId());
            budget.setBudgetedMaterials(parseDecimal(txtBudgetMaterials != null ? txtBudgetMaterials.getText() : null));
            budget.setBudgetedLabor(parseDecimal(txtBudgetLabor != null ? txtBudgetLabor.getText() : null));
            budget.setBudgetedEquipment(parseDecimal(txtBudgetEquipment != null ? txtBudgetEquipment.getText() : null));
            budget.setBudgetedSubcontractor(parseDecimal(txtBudgetSubcontractor != null ? txtBudgetSubcontractor.getText() : null));
            budget.setBudgetedOverhead(parseDecimal(txtBudgetOverhead != null ? txtBudgetOverhead.getText() : null));
            budget.recalculateTotal();

            currentBudget = jobCostingService.saveBudget(budget);
            if (lblTotalBudget != null) lblTotalBudget.setText(CurrencyFormatter.format(currentBudget.getTotalBudgetedCost()));
            loadCostingData();
            showAlert(Alert.AlertType.INFORMATION, "Budget Saved", "Job cost budget saved successfully.");
        } catch (Exception e) {
            logger.error("Save budget failed: {}", e.getMessage(), e);
            showAlert(Alert.AlertType.ERROR, "Budget Error", "Failed to save budget: " + e.getMessage());
        }
    }

    @FXML
    private void handleAddCostEntry() {
        if (currentJob == null || currentJob.getId() <= 0) {
            showAlert(Alert.AlertType.WARNING, "Save Job First", "Please save the service job before posting costs.");
            return;
        }
        if ("CANCELLED".equals(currentJob.getStatus())) {
            showAlert(Alert.AlertType.WARNING, "Job Cancelled", "Cannot post costs to a cancelled job.");
            return;
        }

        Dialog<JobCostEntry> dialog = new Dialog<>();
        dialog.setTitle("Add Job Cost Entry");
        dialog.setHeaderText("Post actual cost to Job " + currentJob.getJobNumber() + " (DR 1250 WIP)");

        ButtonType addBtnType = new ButtonType("Post Cost", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addBtnType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(15));

        ComboBox<String> cmbCategory = new ComboBox<>(FXCollections.observableArrayList(
                "MATERIAL", "LABOR", "EQUIPMENT", "SUBCONTRACTOR", "OVERHEAD"));
        cmbCategory.setValue("MATERIAL");
        DatePicker dpDate = new DatePicker(LocalDate.now());
        TextField txtDesc = new TextField();
        txtDesc.setPromptText("Description");
        TextField txtQty = new TextField("1");
        TextField txtUnit = new TextField("0.00");
        InputValidation.decimalOnly(txtQty);
        InputValidation.decimalOnly(txtUnit);

        grid.add(new Label("Category:"), 0, 0);
        grid.add(cmbCategory, 1, 0);
        grid.add(new Label("Date:"), 0, 1);
        grid.add(dpDate, 1, 1);
        grid.add(new Label("Description:"), 0, 2);
        grid.add(txtDesc, 1, 2);
        grid.add(new Label("Quantity:"), 0, 3);
        grid.add(txtQty, 1, 3);
        grid.add(new Label("Unit Cost:"), 0, 4);
        grid.add(txtUnit, 1, 4);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn != addBtnType) return null;
            JobCostEntry entry = new JobCostEntry();
            entry.setServiceJobId(currentJob.getId());
            entry.setCostCategory(cmbCategory.getValue());
            entry.setEntryDate(dpDate.getValue() != null ? dpDate.getValue() : LocalDate.now());
            entry.setDescription(txtDesc.getText() != null ? txtDesc.getText().trim() : "");
            entry.setQuantity(parseDecimal(txtQty.getText()));
            entry.setUnitCost(parseDecimal(txtUnit.getText()));
            entry.setSourceType("MANUAL");
            entry.recalculateTotal();
            return entry;
        });

        Optional<JobCostEntry> result = dialog.showAndWait();
        result.ifPresent(entry -> {
            if (entry.getDescription() == null || entry.getDescription().isBlank()) {
                showAlert(Alert.AlertType.WARNING, "Validation", "Description is required.");
                return;
            }
            if (entry.getTotalCost() == null || entry.getTotalCost().compareTo(BigDecimal.ZERO) <= 0) {
                showAlert(Alert.AlertType.WARNING, "Validation", "Total cost must be greater than zero.");
                return;
            }
            try {
                int userId = 0;
                jobCostingService.addCostEntry(entry, userId);
                showAlert(Alert.AlertType.INFORMATION, "Cost Posted",
                        "Cost entry posted and WIP journal created for " + CurrencyFormatter.format(entry.getTotalCost()) + ".");
                loadCostingData();
            } catch (Exception e) {
                logger.error("Add cost entry failed: {}", e.getMessage(), e);
                showAlert(Alert.AlertType.ERROR, "Cost Error", "Failed to post cost: " + e.getMessage());
            }
        });
    }

    @FXML
    private void handleRefreshCosting() {
        loadCostingData();
    }

    @FXML
    private void handleNew() {
        if (jobListView != null) jobListView.getSelectionModel().clearSelection();
        currentJob = new ServiceJob();
        editMode = true;
        lineList.clear();
        if (txtJobNumber != null) txtJobNumber.setText("Auto-Generated");
        if (cmbJobType != null) cmbJobType.setValue("REPAIR");
        if (cmbStatus != null) cmbStatus.setValue("PENDING");
        if (dpScheduledDate != null) dpScheduledDate.setValue(LocalDate.now());
        if (dpCompletionDate != null) dpCompletionDate.setValue(null);

        selectedCustomer = null;
        if (txtCustomerName != null) txtCustomerName.clear();
        if (txtCustomerPhone != null) txtCustomerPhone.clear();

        if (txtAcModelInfo != null) txtAcModelInfo.clear();
        if (txtReportedIssue != null) txtReportedIssue.clear();
        if (txtResolutionNotes != null) txtResolutionNotes.clear();
        if (txtLaborCost != null) txtLaborCost.setText("0.00");
        if (cmbTechnician != null) cmbTechnician.setValue(null);
        recalculateTotals();
        setFormEditable(true);
        loadCostingData();
        if (detailTabs != null) detailTabs.getSelectionModel().selectFirst();
    }

    @FXML
    private void handleEdit() {
        if (currentJob != null && !("COMPLETED".equals(currentJob.getStatus()) || "CANCELLED".equals(currentJob.getStatus()))) {
            editMode = true;
            setFormEditable(true);
        }
    }

    @FXML
    private void handleSave() {
        if (currentJob == null) {
            currentJob = new ServiceJob();
            editMode = true;
        }

        String inputCustName = txtCustomerName != null ? txtCustomerName.getText() : null;
        String inputCustPhone = txtCustomerPhone != null ? txtCustomerPhone.getText() : null;

        if (inputCustName == null || inputCustName.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Validation Error", "Please enter a Customer Name.");
            return;
        }

        // Customer Lookup or Auto Creation
        if (selectedCustomer == null || !selectedCustomer.getName().equalsIgnoreCase(inputCustName.trim())) {
            Optional<Customer> match = customerList.stream()
                .filter(c -> c.getName() != null && c.getName().equalsIgnoreCase(inputCustName.trim()))
                .findFirst();

            if (match.isPresent()) {
                selectedCustomer = match.get();
            } else {
                selectedCustomer = createNewCustomer(inputCustName.trim(), inputCustPhone);
                if (selectedCustomer != null) {
                    customerList.add(selectedCustomer);
                    logger.info("Auto-created customer: {} with ID {}", selectedCustomer.getName(), selectedCustomer.getId());
                } else {
                    showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to auto-create the Customer.");
                    return;
                }
            }
        }

        if (cmbJobType != null && (cmbJobType.getValue() == null || cmbJobType.getValue().isBlank())) {
            showAlert(Alert.AlertType.WARNING, "Validation Error", "Please select a Job Type.");
            return;
        }

        boolean isNew = currentJob.getId() <= 0;

        if (cmbJobType != null) currentJob.setJobType(cmbJobType.getValue());
        if (cmbStatus != null) currentJob.setStatus(cmbStatus.getValue() != null ? cmbStatus.getValue() : "PENDING");
        if (dpScheduledDate != null) currentJob.setScheduledDate(dpScheduledDate.getValue());
        if (dpCompletionDate != null) currentJob.setCompletionDate(dpCompletionDate.getValue());

        if (selectedCustomer != null) {
            currentJob.setCustomerId(selectedCustomer.getId());
            currentJob.setCustomerName(selectedCustomer.getName());
        }

        if (cmbTechnician != null && cmbTechnician.getValue() != null) {
            currentJob.setTechnicianId(cmbTechnician.getValue().getId());
            currentJob.setTechnicianName(cmbTechnician.getValue().getFullName());
        } else {
            currentJob.setTechnicianId(null);
            currentJob.setTechnicianName(null);
        }

        if (txtAcModelInfo != null) currentJob.setAcModelInfo(txtAcModelInfo.getText());
        if (txtReportedIssue != null) currentJob.setReportedIssue(txtReportedIssue.getText());
        if (txtResolutionNotes != null) currentJob.setResolutionNotes(txtResolutionNotes.getText());

        try {
            String laborText = txtLaborCost != null ? txtLaborCost.getText() : null;
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

            if (currentJob.getId() > 0 && jobListView != null) {
                for (ServiceJob j : jobList) {
                    if (j.getId() == currentJob.getId()) {
                        jobListView.getSelectionModel().select(j);
                        break;
                    }
                }
            }
        } else {
            showAlert(Alert.AlertType.ERROR, "Database Error", "Failed to save Service Job.");
        }
    }

    private Customer createNewCustomer(String name, String phone) {
        String sql = "INSERT INTO customers (name, phone, created_at) VALUES (?, ?, CURRENT_TIMESTAMP) RETURNING id";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, phone != null ? phone.trim() : "");

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Customer c = new Customer();
                    c.setId(rs.getInt(1));
                    c.setName(name);
                    c.setPhone(phone != null ? phone.trim() : "");
                    return c;
                }
            }
        } catch (Exception e) {
            logger.error("Failed to auto-create customer: {}", e.getMessage(), e);
        }
        return null;
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
            String laborText = txtLaborCost != null ? txtLaborCost.getText() : null;
            if (laborText != null && !laborText.isBlank()) {
                labor = new BigDecimal(laborText.trim());
            }
        } catch (Exception ignored) {}

        BigDecimal partsSum = BigDecimal.ZERO;
        for (ServiceJobLine line : lineList) {
            line.recalculateTotal();
            partsSum = partsSum.add(line.getLineTotal());
        }

        if (lblPartsTotal != null) lblPartsTotal.setText(CurrencyFormatter.format(partsSum));
        if (lblGrandTotal != null) lblGrandTotal.setText(CurrencyFormatter.format(labor.add(partsSum)));
    }

    private static BigDecimal parseDecimal(String text) {
        try {
            if (text == null || text.isBlank()) return BigDecimal.ZERO;
            return new BigDecimal(text.trim().replace(",", ""));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private static String plain(BigDecimal v) {
        return v != null ? v.toPlainString() : "0.00";
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}