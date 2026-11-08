package com.aceerp.controller;

import com.aceerp.model.*;
import com.aceerp.service.ReportExportService;
import com.aceerp.service.ReportService;
import com.aceerp.util.AlertHelper;
import com.aceerp.util.CurrencyFormatter;
import com.aceerp.util.ReportDateRangeUtil;
import com.aceerp.util.ReportDateRangeUtil.DatePreset;
import com.aceerp.util.QuickBooksContextMenus;
import com.aceerp.util.VisualTemplateEditor;

import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public class ReportController {

    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);
    private record StatementRow(String section, String lineItem, BigDecimal amount, boolean footerTotal) {}

    // Left Navigation Tree / ListView
    @FXML private ListView<String> reportCategoryList;

    // Top Controls
    @FXML private ComboBox<DatePreset> cmbDatePresets;
    @FXML private DatePicker dpFrom;
    @FXML private DatePicker dpTo;
    @FXML private TextField txtSearch;
    @FXML private Label lblActiveReportTitle;

    // Main Content Area
    @FXML private VBox tableContainer;
    @FXML private TableView<Object> activeTableView;

    // Footer Labels
    @FXML private Label lblFooterRowCount;
    @FXML private Label lblFooterTotal1;
    @FXML private Label lblFooterTotal2;

    private final ReportService reportService = new ReportService();
    private final ReportExportService exportService = new ReportExportService();

    private final ObservableList<Object> masterDataList = FXCollections.observableArrayList();
    private FilteredList<Object> filteredDataList;

    private String currentReportKey = "OPEN_INVOICES";

    @FXML
    public void initialize() {
        logger.info("Initializing Reports Center Hub...");

        setupCategoryList();
        setupDatePresets();
        setupSearch();

        // Default selection
        reportCategoryList.getSelectionModel().select(0);
        selectReport("OPEN_INVOICES", "Open Invoices");
    }

    private void setupCategoryList() {
        ObservableList<String> items = FXCollections.observableArrayList(
            "📄 Open Invoices",
            "📊 A/R Aging Detail",
            "👥 Customer Balance Summary",
            "💰 Sales by Customer",
            "🛒 Sales by Product/Service",
            "📑 A/P Aging Detail",
            "💳 Bill Payment List",
            "🏭 Vendor Balance Summary",
            "📦 Stock Valuation",
            "📈 Profit & Loss Statement",
            "🏛️ Balance Sheet",
            "💸 Statement of Cash Flows"
        );
        reportCategoryList.setItems(items);
        reportCategoryList.setCellFactory(list -> new ListCell<>() {
            private final MenuItem refreshItem = new MenuItem("Refresh");
            private final MenuItem exportCsvItem = new MenuItem("Export CSV");
            private final MenuItem exportExcelItem = new MenuItem("Export Excel");
            private final ContextMenu contextMenu = new ContextMenu(refreshItem, exportCsvItem, exportExcelItem);

            {
                refreshItem.setOnAction(event -> handleRefresh());
                exportCsvItem.setOnAction(event -> handleExportCsv());
                exportExcelItem.setOnAction(event -> handleExportExcel());
                setOnContextMenuRequested(event -> {
                    if (!isEmpty()) {
                        reportCategoryList.getSelectionModel().select(getIndex());
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setContextMenu(empty ? null : contextMenu);
            }
        });

        reportCategoryList.getSelectionModel().selectedIndexProperty().addListener((obs, oldV, newV) -> {
            if (newV == null) return;
            switch (newV.intValue()) {
                case 0 -> selectReport("OPEN_INVOICES", "Open Invoices");
                case 1 -> selectReport("AR_AGING_DETAIL", "A/R Aging Detail");
                case 2 -> selectReport("CUST_BAL_SUMMARY", "Customer Balance Summary");
                case 3 -> selectReport("SALES_BY_CUST", "Sales by Customer");
                case 4 -> selectReport("SALES_BY_PROD", "Sales by Product / Service");
                case 5 -> selectReport("AP_AGING_DETAIL", "A/P Aging Detail");
                case 6 -> selectReport("BILL_PAYMENTS", "Bill Payment List");
                case 7 -> selectReport("VENDOR_BAL_SUMMARY", "Vendor Balance Summary");
                case 8 -> selectReport("STOCK_VALUATION", "Stock Valuation Summary");
                case 9 -> selectReport("PNL", "Profit & Loss Statement");
                case 10 -> selectReport("BALANCE_SHEET", "Balance Sheet");
                case 11 -> selectReport("CASH_FLOW", "Statement of Cash Flows");
            }
        });
    }

    private void setupDatePresets() {
        cmbDatePresets.setItems(FXCollections.observableArrayList(DatePreset.values()));
        cmbDatePresets.setValue(DatePreset.THIS_MONTH);

        cmbDatePresets.setOnAction(e -> {
            DatePreset preset = cmbDatePresets.getValue();
            if (preset == null) return;
            if (preset == DatePreset.CUSTOM) {
                dpFrom.setDisable(false);
                dpTo.setDisable(false);
            } else {
                dpFrom.setDisable(true);
                dpTo.setDisable(true);
                ReportDateRangeUtil.DateRange range = ReportDateRangeUtil.getRangeForPreset(preset);
                dpFrom.setValue(range.startDate());
                dpTo.setValue(range.endDate());
                loadCurrentReportData();
            }
        });

        ReportDateRangeUtil.DateRange defaultRange = ReportDateRangeUtil.getRangeForPreset(DatePreset.THIS_MONTH);
        dpFrom.setValue(defaultRange.startDate());
        dpTo.setValue(defaultRange.endDate());
    }

    private void setupSearch() {
        txtSearch.textProperty().addListener((obs, oldV, newV) -> {
            if (filteredDataList == null) return;
            filteredDataList.setPredicate(item -> {
                if (newV == null || newV.isBlank()) return true;
                String query = newV.trim().toLowerCase();
                return item.toString().toLowerCase().contains(query);
            });
            updateFooterSummary();
        });
    }

    private void selectReport(String key, String title) {
        this.currentReportKey = key;
        lblActiveReportTitle.setText("📊 " + title);
        txtSearch.clear();

        tableContainer.getChildren().clear();
        activeTableView = new TableView<>();
        QuickBooksContextMenus.installTableView(activeTableView, row -> List.of());
        tableContainer.getChildren().add(activeTableView);
        VBox.setVgrow(activeTableView, javafx.scene.layout.Priority.ALWAYS);

        buildColumnsForReport(key);
        loadCurrentReportData();
    }

    @FXML
    public void handleRefresh() {
        loadCurrentReportData();
    }

    private void loadCurrentReportData() {
        LocalDate from = dpFrom.getValue() != null ? dpFrom.getValue() : LocalDate.now().withDayOfMonth(1);
        LocalDate to = dpTo.getValue() != null ? dpTo.getValue() : LocalDate.now();

        masterDataList.clear();

        switch (currentReportKey) {
            case "OPEN_INVOICES" -> masterDataList.addAll(reportService.getOpenInvoices(from, to));
            case "AR_AGING_DETAIL" -> masterDataList.addAll(reportService.getArAgingDetail(to));
            case "CUST_BAL_SUMMARY" -> masterDataList.addAll(reportService.getCustomerBalanceSummary());
            case "SALES_BY_CUST" -> masterDataList.addAll(reportService.getSalesByCustomer(from, to));
            case "SALES_BY_PROD" -> masterDataList.addAll(reportService.getSalesByProduct(from, to));
            case "AP_AGING_DETAIL" -> masterDataList.addAll(reportService.getApAgingDetail(to));
            case "BILL_PAYMENTS" -> masterDataList.addAll(reportService.getBillPayments(from, to));
            case "VENDOR_BAL_SUMMARY" -> masterDataList.addAll(reportService.getVendorBalanceSummary());
            case "STOCK_VALUATION" -> masterDataList.addAll(reportService.getStockValuation());
            case "PNL" -> addProfitAndLossRows(reportService.getProfitAndLoss(from, to));
            case "BALANCE_SHEET" -> addBalanceSheetRows(reportService.getBalanceSheet(to));
            case "CASH_FLOW" -> addCashFlowRows(reportService.getCashFlowStatement(from, to));
        }

        filteredDataList = new FilteredList<>(masterDataList, p -> true);
        SortedList<Object> sortedList = new SortedList<>(filteredDataList);
        sortedList.comparatorProperty().bind(activeTableView.comparatorProperty());

        activeTableView.setItems(sortedList);
        updateFooterSummary();
    }

    private void addProfitAndLossRows(ProfitAndLossDTO report) {
        masterDataList.addAll(
            new StatementRow("Revenue", "Product Sales", report.getSalesRevenue(), false),
            new StatementRow("Revenue", "Service Revenue", report.getServiceRevenue(), false),
            new StatementRow("Revenue", "Total Revenue", report.getTotalRevenue(), false),
            new StatementRow("Expenses", "Cost of Goods Sold", report.getCostOfGoodsSold(), false),
            new StatementRow("Expenses", "Gross Profit", report.getGrossProfit(), false),
            new StatementRow("Expenses", "Operating Expenses", report.getPurchaseExpenses(), false),
            new StatementRow("Net Income", "Net Profit", report.getNetProfit(), true)
        );
    }

    private void addBalanceSheetRows(BalanceSheetDTO report) {
        for (BalanceSheetDTO.BalanceRow row : report.getAssets()) {
            masterDataList.add(new StatementRow("Assets", row.accountCode() + " - " + row.accountName(), row.balance(), false));
        }
        masterDataList.add(new StatementRow("Assets", "Total Assets", report.getTotalAssets(), true));

        for (BalanceSheetDTO.BalanceRow row : report.getLiabilities()) {
            masterDataList.add(new StatementRow("Liabilities", row.accountCode() + " - " + row.accountName(), row.balance(), false));
        }
        masterDataList.add(new StatementRow("Liabilities", "Total Liabilities", report.getTotalLiabilities(), false));

        for (BalanceSheetDTO.BalanceRow row : report.getEquity()) {
            masterDataList.add(new StatementRow("Equity", row.accountCode() + " - " + row.accountName(), row.balance(), false));
        }
        masterDataList.add(new StatementRow("Equity", "Current Year Net Income", report.getCurrentYearNetIncome(), false));
        masterDataList.add(new StatementRow("Equity", "Total Equity", report.getTotalEquity(), false));
        masterDataList.add(new StatementRow(
            "Liabilities & Equity",
            "Total Liabilities and Equity",
            report.getTotalLiabilitiesAndEquity(),
            false
        ));
    }

    private void addCashFlowRows(CashFlowDTO report) {
        masterDataList.addAll(
            new StatementRow("Operating Activities", "Net Income", report.getNetIncome(), false),
            new StatementRow("Operating Activities", "Accounts Receivable Change", report.getArChange(), false),
            new StatementRow("Operating Activities", "Accounts Payable Change", report.getApChange(), false),
            new StatementRow("Operating Activities", "Inventory Change", report.getInventoryChange(), false),
            new StatementRow("Operating Activities", "Operating Cash Flow", report.getOperatingCashFlow(), false),
            new StatementRow("Cash Flow", "Net Cash Change", report.getNetCashChange(), true)
        );
    }

    @SuppressWarnings("unchecked")
    private void buildColumnsForReport(String key) {
        activeTableView.getColumns().clear();

        switch (key) {
            case "PNL", "BALANCE_SHEET", "CASH_FLOW" -> {
                TableColumn<Object, String> colSection = new TableColumn<>("Section");
                colSection.setCellValueFactory(cd -> new SimpleStringProperty(((StatementRow) cd.getValue()).section()));
                colSection.setPrefWidth(180);

                TableColumn<Object, String> colLineItem = new TableColumn<>("Line Item");
                colLineItem.setCellValueFactory(cd -> new SimpleStringProperty(((StatementRow) cd.getValue()).lineItem()));
                colLineItem.setPrefWidth(280);

                TableColumn<Object, BigDecimal> colAmount = createMoneyColumn("Amount", row -> ((StatementRow) row).amount());
                activeTableView.getColumns().addAll(colSection, colLineItem, colAmount);
            }
            case "OPEN_INVOICES" -> {
                TableColumn<Object, String> colInv = new TableColumn<>("Invoice #");
                colInv.setCellValueFactory(cd -> new SimpleStringProperty(((OpenInvoiceRow) cd.getValue()).invoiceNumber()));

                TableColumn<Object, String> colCust = new TableColumn<>("Customer Name");
                colCust.setCellValueFactory(cd -> new SimpleStringProperty(((OpenInvoiceRow) cd.getValue()).customerName()));
                colCust.setPrefWidth(220);

                TableColumn<Object, LocalDate> colDate = new TableColumn<>("Issue Date");
                colDate.setCellValueFactory(cd -> new SimpleObjectProperty<>(((OpenInvoiceRow) cd.getValue()).invoiceDate()));

                TableColumn<Object, LocalDate> colDue = new TableColumn<>("Due Date");
                colDue.setCellValueFactory(cd -> new SimpleObjectProperty<>(((OpenInvoiceRow) cd.getValue()).dueDate()));

                TableColumn<Object, Long> colOverdue = new TableColumn<>("Days Overdue");
                colOverdue.setCellValueFactory(cd -> new SimpleObjectProperty<>(((OpenInvoiceRow) cd.getValue()).daysOverdue()));

                TableColumn<Object, BigDecimal> colTotal = createMoneyColumn("Total Amount", row -> ((OpenInvoiceRow) row).totalAmount());
                TableColumn<Object, BigDecimal> colBal = createMoneyColumn("Balance Due", row -> ((OpenInvoiceRow) row).balanceDue());

                activeTableView.getColumns().addAll(colInv, colCust, colDate, colDue, colOverdue, colTotal, colBal);
            }

            case "AR_AGING_DETAIL" -> {
                TableColumn<Object, String> colCust = new TableColumn<>("Customer Name");
                colCust.setCellValueFactory(cd -> new SimpleStringProperty(((ArAgingDetailRow) cd.getValue()).customerName()));
                colCust.setPrefWidth(200);

                TableColumn<Object, String> colInv = new TableColumn<>("Invoice #");
                colInv.setCellValueFactory(cd -> new SimpleStringProperty(((ArAgingDetailRow) cd.getValue()).invoiceNumber()));

                TableColumn<Object, LocalDate> colDate = new TableColumn<>("Invoice Date");
                colDate.setCellValueFactory(cd -> new SimpleObjectProperty<>(((ArAgingDetailRow) cd.getValue()).invoiceDate()));

                TableColumn<Object, LocalDate> colDue = new TableColumn<>("Due Date");
                colDue.setCellValueFactory(cd -> new SimpleObjectProperty<>(((ArAgingDetailRow) cd.getValue()).dueDate()));

                TableColumn<Object, Long> colOverdue = new TableColumn<>("Days Overdue");
                colOverdue.setCellValueFactory(cd -> new SimpleObjectProperty<>(((ArAgingDetailRow) cd.getValue()).daysOverdue()));

                TableColumn<Object, String> colBucket = new TableColumn<>("Aging Bucket");
                colBucket.setCellValueFactory(cd -> new SimpleStringProperty(((ArAgingDetailRow) cd.getValue()).agingBucket()));

                TableColumn<Object, BigDecimal> colOriginal = createMoneyColumn("Original Amount", row -> ((ArAgingDetailRow) row).originalAmount());
                TableColumn<Object, BigDecimal> colPaid = createMoneyColumn("Amount Paid", row -> ((ArAgingDetailRow) row).amountPaid());
                TableColumn<Object, BigDecimal> colBal = createMoneyColumn("Balance Due", row -> ((ArAgingDetailRow) row).balanceDue());

                activeTableView.getColumns().addAll(colCust, colInv, colDate, colDue, colOverdue, colBucket, colOriginal, colPaid, colBal);
            }

            case "CUST_BAL_SUMMARY" -> {
                TableColumn<Object, String> colCustomer = new TableColumn<>("Customer");
                colCustomer.setCellValueFactory(cd -> new SimpleStringProperty(((CustomerBalanceSummaryRow) cd.getValue()).customerName()));
                colCustomer.setPrefWidth(220);

                TableColumn<Object, String> colPhone = new TableColumn<>("Phone");
                colPhone.setCellValueFactory(cd -> new SimpleStringProperty(((CustomerBalanceSummaryRow) cd.getValue()).phone()));

                TableColumn<Object, BigDecimal> colInvoiced = createMoneyColumn("Total Invoiced", row -> ((CustomerBalanceSummaryRow) row).totalInvoiced());
                TableColumn<Object, BigDecimal> colPaid = createMoneyColumn("Total Paid", row -> ((CustomerBalanceSummaryRow) row).totalPaid());
                TableColumn<Object, BigDecimal> colBalance = createMoneyColumn("Balance Due", row -> ((CustomerBalanceSummaryRow) row).balanceDue());

                activeTableView.getColumns().addAll(colCustomer, colPhone, colInvoiced, colPaid, colBalance);
            }

            case "AP_AGING_DETAIL" -> {
                TableColumn<Object, String> colVendor = new TableColumn<>("Vendor Name");
                colVendor.setCellValueFactory(cd -> new SimpleStringProperty(((ApAgingDetailRow) cd.getValue()).vendorName()));
                colVendor.setPrefWidth(200);

                TableColumn<Object, String> colBill = new TableColumn<>("Bill #");
                colBill.setCellValueFactory(cd -> new SimpleStringProperty(((ApAgingDetailRow) cd.getValue()).billNumber()));

                TableColumn<Object, LocalDate> colDate = new TableColumn<>("Bill Date");
                colDate.setCellValueFactory(cd -> new SimpleObjectProperty<>(((ApAgingDetailRow) cd.getValue()).billDate()));

                TableColumn<Object, LocalDate> colDue = new TableColumn<>("Due Date");
                colDue.setCellValueFactory(cd -> new SimpleObjectProperty<>(((ApAgingDetailRow) cd.getValue()).dueDate()));

                TableColumn<Object, Long> colOverdue = new TableColumn<>("Days Overdue");
                colOverdue.setCellValueFactory(cd -> new SimpleObjectProperty<>(((ApAgingDetailRow) cd.getValue()).daysOverdue()));

                TableColumn<Object, String> colBucket = new TableColumn<>("Aging Bucket");
                colBucket.setCellValueFactory(cd -> new SimpleStringProperty(((ApAgingDetailRow) cd.getValue()).agingBucket()));

                TableColumn<Object, BigDecimal> colOriginal = createMoneyColumn("Original Amount", row -> ((ApAgingDetailRow) row).originalAmount());
                TableColumn<Object, BigDecimal> colPaid = createMoneyColumn("Amount Paid", row -> ((ApAgingDetailRow) row).amountPaid());
                TableColumn<Object, BigDecimal> colBalance = createMoneyColumn("Balance Due", row -> ((ApAgingDetailRow) row).balanceDue());

                activeTableView.getColumns().addAll(colVendor, colBill, colDate, colDue, colOverdue, colBucket, colOriginal, colPaid, colBalance);
            }

            case "VENDOR_BAL_SUMMARY" -> {
                TableColumn<Object, String> colVendor = new TableColumn<>("Vendor");
                colVendor.setCellValueFactory(cd -> new SimpleStringProperty(((VendorBalanceSummaryRow) cd.getValue()).vendorName()));
                colVendor.setPrefWidth(220);

                TableColumn<Object, String> colPhone = new TableColumn<>("Phone");
                colPhone.setCellValueFactory(cd -> new SimpleStringProperty(((VendorBalanceSummaryRow) cd.getValue()).phone()));

                TableColumn<Object, BigDecimal> colBilled = createMoneyColumn("Total Billed", row -> ((VendorBalanceSummaryRow) row).totalBilled());
                TableColumn<Object, BigDecimal> colPaid = createMoneyColumn("Total Paid", row -> ((VendorBalanceSummaryRow) row).totalPaid());
                TableColumn<Object, BigDecimal> colOwed = createMoneyColumn("Balance Owed", row -> ((VendorBalanceSummaryRow) row).balanceOwed());

                activeTableView.getColumns().addAll(colVendor, colPhone, colBilled, colPaid, colOwed);
            }

            case "SALES_BY_CUST" -> {
                TableColumn<Object, String> colCust = new TableColumn<>("Customer Name");
                colCust.setCellValueFactory(cd -> new SimpleStringProperty(((SalesByCustomerRow) cd.getValue()).customerName()));
                colCust.setPrefWidth(240);

                TableColumn<Object, Integer> colCount = new TableColumn<>("Invoices");
                colCount.setCellValueFactory(cd -> new SimpleObjectProperty<>(((SalesByCustomerRow) cd.getValue()).invoiceCount()));

                TableColumn<Object, BigDecimal> colTotal = createMoneyColumn("Total Sales", row -> ((SalesByCustomerRow) row).totalSales());
                TableColumn<Object, Double> colPct = new TableColumn<>("% Share");
                colPct.setCellValueFactory(cd -> new SimpleObjectProperty<>(((SalesByCustomerRow) cd.getValue()).percentOfTotal()));
                colPct.setCellFactory(col -> new TableCell<>() {
                    @Override protected void updateItem(Double val, boolean empty) {
                        super.updateItem(val, empty);
                        setText(empty || val == null ? "" : String.format("%.2f %%", val));
                    }
                });

                activeTableView.getColumns().addAll(colCust, colCount, colTotal, colPct);
            }

            case "SALES_BY_PROD" -> {
                TableColumn<Object, String> colCode = new TableColumn<>("Code");
                colCode.setCellValueFactory(cd -> new SimpleStringProperty(((SalesByProductRow) cd.getValue()).productCode()));

                TableColumn<Object, String> colName = new TableColumn<>("Product / Service");
                colName.setCellValueFactory(cd -> new SimpleStringProperty(((SalesByProductRow) cd.getValue()).productName()));
                colName.setPrefWidth(220);

                TableColumn<Object, BigDecimal> colQty = new TableColumn<>("Qty Sold");
                colQty.setCellValueFactory(cd -> new SimpleObjectProperty<>(((SalesByProductRow) cd.getValue()).totalQuantitySold()));

                TableColumn<Object, BigDecimal> colTotal = createMoneyColumn("Sales Revenue", row -> ((SalesByProductRow) row).totalSalesAmount());

                activeTableView.getColumns().addAll(colCode, colName, colQty, colTotal);
            }

            case "BILL_PAYMENTS" -> {
                TableColumn<Object, String> colNum = new TableColumn<>("Payment #");
                colNum.setCellValueFactory(cd -> new SimpleStringProperty(((BillPaymentRow) cd.getValue()).paymentNumber()));

                TableColumn<Object, LocalDate> colDate = new TableColumn<>("Date");
                colDate.setCellValueFactory(cd -> new SimpleObjectProperty<>(((BillPaymentRow) cd.getValue()).paymentDate()));

                TableColumn<Object, String> colVendor = new TableColumn<>("Vendor Name");
                colVendor.setCellValueFactory(cd -> new SimpleStringProperty(((BillPaymentRow) cd.getValue()).vendorName()));
                colVendor.setPrefWidth(200);

                TableColumn<Object, String> colBill = new TableColumn<>("Bill #");
                colBill.setCellValueFactory(cd -> new SimpleStringProperty(((BillPaymentRow) cd.getValue()).billNumber()));

                TableColumn<Object, BigDecimal> colAmt = createMoneyColumn("Amount Paid", row -> ((BillPaymentRow) row).amountPaid());

                activeTableView.getColumns().addAll(colNum, colDate, colVendor, colBill, colAmt);
            }

            case "STOCK_VALUATION" -> {
                TableColumn<Object, String> colCode = new TableColumn<>("SKU");
                colCode.setCellValueFactory(cd -> new SimpleStringProperty(((StockValuationDTO) cd.getValue()).getProductCode()));

                TableColumn<Object, String> colName = new TableColumn<>("Product Name");
                colName.setCellValueFactory(cd -> new SimpleStringProperty(((StockValuationDTO) cd.getValue()).getProductName()));
                colName.setPrefWidth(220);

                TableColumn<Object, Integer> colQty = new TableColumn<>("On-Hand");
                colQty.setCellValueFactory(cd -> new SimpleObjectProperty<>(((StockValuationDTO) cd.getValue()).getStockQuantity()));

                TableColumn<Object, BigDecimal> colCost = createMoneyColumn("Avg Cost", row -> ((StockValuationDTO) row).getCostPrice());
                TableColumn<Object, BigDecimal> colVal = createMoneyColumn("Total Cost Value", row -> ((StockValuationDTO) row).getTotalCostValue());

                activeTableView.getColumns().addAll(colCode, colName, colQty, colCost, colVal);
            }
        }
    }

    private TableColumn<Object, BigDecimal> createMoneyColumn(String title, java.util.function.Function<Object, BigDecimal> extractor) {
        TableColumn<Object, BigDecimal> col = new TableColumn<>(title);
        col.setCellValueFactory(cd -> new SimpleObjectProperty<>(extractor.apply(cd.getValue())));
        col.setComparator(Comparator.nullsLast(Comparator.naturalOrder()));
        col.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : CurrencyFormatter.format(item));
            }
        });
        return col;
    }

    private void updateFooterSummary() {
        int count = activeTableView.getItems().size();
        lblFooterRowCount.setText("Total Rows: " + count);

        BigDecimal totalBal = BigDecimal.ZERO;

        for (Object row : activeTableView.getItems()) {
            if (row instanceof OpenInvoiceRow r) totalBal = totalBal.add(r.balanceDue());
            else if (row instanceof ArAgingDetailRow r) totalBal = totalBal.add(r.balanceDue());
            else if (row instanceof CustomerBalanceSummaryRow r) totalBal = totalBal.add(r.balanceDue());
            else if (row instanceof ApAgingDetailRow r) totalBal = totalBal.add(r.balanceDue());
            else if (row instanceof VendorBalanceSummaryRow r) totalBal = totalBal.add(r.balanceOwed());
            else if (row instanceof SalesByCustomerRow r) totalBal = totalBal.add(r.totalSales());
            else if (row instanceof SalesByProductRow r) totalBal = totalBal.add(r.totalSalesAmount());
            else if (row instanceof BillPaymentRow r) totalBal = totalBal.add(r.amountPaid());
            else if (row instanceof StockValuationDTO r) totalBal = totalBal.add(r.getTotalCostValue());
            else if (row instanceof StatementRow r && r.footerTotal()) totalBal = totalBal.add(r.amount());
        }

        String totalLabel = switch (currentReportKey) {
            case "PNL" -> "Net Profit";
            case "BALANCE_SHEET" -> "Total Assets";
            case "CASH_FLOW" -> "Net Cash Change";
            default -> "Grand Total";
        };
        lblFooterTotal1.setText(totalLabel + ": " + CurrencyFormatter.format(totalBal));
        lblFooterTotal2.setText("");
    }

    @FXML
    private void handleExportCsv() {
        try {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Export Report to CSV");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
            File file = chooser.showSaveDialog(activeTableView.getScene().getWindow());

            if (file != null) {
                exportService.exportTableViewToCsv(activeTableView, lblActiveReportTitle.getText(), file);
                AlertHelper.info("Export Successful", "Report exported to CSV successfully.");
            }
        } catch (Exception e) {
            logger.error("Export to CSV failed: {}", e.getMessage(), e);
            AlertHelper.error("Export Error", "Could not export report to CSV: " + e.getMessage());
        }
    }

    @FXML
    private void handleExportExcel() {
        try {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Export Report to Excel");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel File (*.xls)", "*.xls"));
            File file = chooser.showSaveDialog(activeTableView.getScene().getWindow());

            if (file != null) {
                exportService.exportTableViewToExcelXml(activeTableView, lblActiveReportTitle.getText(), file);
                AlertHelper.info("Export Successful", "Report exported to Excel successfully.");
            }
        } catch (Exception e) {
            logger.error("Export to Excel failed: {}", e.getMessage(), e);
            AlertHelper.error("Export Error", "Could not export report to Excel: " + e.getMessage());
        }
    }

    @FXML
    private void handleEditTemplates() {
        try {
            VisualTemplateEditor.show();
        } catch (Exception e) {
            logger.error("Failed to launch Visual Template Editor: {}", e.getMessage(), e);
            AlertHelper.error("Template Editor Error", e.getMessage());
        }
    }
}