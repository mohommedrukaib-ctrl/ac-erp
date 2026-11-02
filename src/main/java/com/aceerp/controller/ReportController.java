
package com.aceerp.controller;

import com.aceerp.model.*;
import com.aceerp.service.ReportService;
import com.aceerp.util.CurrencyFormatter;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class ReportController {

    private static final Logger logger = LoggerFactory.getLogger(ReportController.class);

    @FXML private DatePicker dpFrom;
    @FXML private DatePicker dpTo;
    @FXML private TabPane reportTabs;

    // P&L
    @FXML private Label lblSalesRevenue;
    @FXML private Label lblServiceRevenue;
    @FXML private Label lblTotalRevenue;
    @FXML private Label lblCogs;
    @FXML private Label lblGrossProfit;
    @FXML private Label lblPurchases;
    @FXML private Label lblNetProfit;

    // Balance Sheet Controls
    @FXML private TableView<BalanceSheetDTO.BalanceRow> bsTable;
    @FXML private TableColumn<BalanceSheetDTO.BalanceRow, String> colBsCode;
    @FXML private TableColumn<BalanceSheetDTO.BalanceRow, String> colBsName;
    @FXML private TableColumn<BalanceSheetDTO.BalanceRow, String> colBsType;
    @FXML private TableColumn<BalanceSheetDTO.BalanceRow, String> colBsAmount;
    @FXML private Label lblBsTotalAssets;
    @FXML private Label lblBsTotalLiabEquity;
    @FXML private Label lblBsStatus;

    // Cash Flow Controls
    @FXML private Label lblCfNetIncome;
    @FXML private Label lblCfArChange;
    @FXML private Label lblCfApChange;
    @FXML private Label lblCfInvChange;
    @FXML private Label lblCfNetOperating;

    // Sub-ledger Tables
    @FXML private TableView<StockValuationDTO> stockValuationTable;
    @FXML private TableColumn<StockValuationDTO, String> colStockCode;
    @FXML private TableColumn<StockValuationDTO, String> colStockName;
    @FXML private TableColumn<StockValuationDTO, String> colStockCategory;
    @FXML private TableColumn<StockValuationDTO, Integer> colStockQty;
    @FXML private TableColumn<StockValuationDTO, String> colStockCost;
    @FXML private TableColumn<StockValuationDTO, String> colStockTotalCost;
    @FXML private TableColumn<StockValuationDTO, String> colStockTotalRetail;

    @FXML private TableView<AgingReportDTO> customerAgingTable;
    @FXML private TableColumn<AgingReportDTO, String> colCustName;
    @FXML private TableColumn<AgingReportDTO, String> colCust0To30;
    @FXML private TableColumn<AgingReportDTO, String> colCust31To60;
    @FXML private TableColumn<AgingReportDTO, String> colCust61To90;
    @FXML private TableColumn<AgingReportDTO, String> colCustOver90;
    @FXML private TableColumn<AgingReportDTO, String> colCustTotal;

    @FXML private TableView<AgingReportDTO> supplierAgingTable;
    @FXML private TableColumn<AgingReportDTO, String> colSuppName;
    @FXML private TableColumn<AgingReportDTO, String> colSupp0To30;
    @FXML private TableColumn<AgingReportDTO, String> colSupp31To60;
    @FXML private TableColumn<AgingReportDTO, String> colSupp61To90;
    @FXML private TableColumn<AgingReportDTO, String> colSuppOver90;
    @FXML private TableColumn<AgingReportDTO, String> colSuppTotal;

    private final ReportService reportService = new ReportService();
    private final ObservableList<BalanceSheetDTO.BalanceRow> bsList = FXCollections.observableArrayList();
    private final ObservableList<StockValuationDTO> stockList = FXCollections.observableArrayList();
    private final ObservableList<AgingReportDTO> customerAgingList = FXCollections.observableArrayList();
    private final ObservableList<AgingReportDTO> supplierAgingList = FXCollections.observableArrayList();

    private ProfitAndLossDTO currentPnl = new ProfitAndLossDTO();
    private BalanceSheetDTO currentBs = new BalanceSheetDTO();

    @FXML
    public void initialize() {
        dpFrom.setValue(LocalDate.now().withDayOfMonth(1));
        dpTo.setValue(LocalDate.now());
        setupTables();
        loadAllReports();
    }

    private void setupTables() {
        if (bsTable != null) {
            colBsCode.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().accountCode()));
            colBsName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().accountName()));
            colBsType.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().accountType()));
            colBsAmount.setCellValueFactory(c -> new SimpleStringProperty(CurrencyFormatter.format(c.getValue().balance())));
            bsTable.setItems(bsList);
        }

        colStockCode.setCellValueFactory(new PropertyValueFactory<>("productCode"));
        colStockName.setCellValueFactory(new PropertyValueFactory<>("productName"));
        colStockCategory.setCellValueFactory(new PropertyValueFactory<>("category"));
        colStockQty.setCellValueFactory(new PropertyValueFactory<>("stockQuantity"));
        colStockCost.setCellValueFactory(c -> new SimpleStringProperty(CurrencyFormatter.format(c.getValue().getCostPrice())));
        colStockTotalCost.setCellValueFactory(c -> new SimpleStringProperty(CurrencyFormatter.format(c.getValue().getTotalCostValue())));
        colStockTotalRetail.setCellValueFactory(c -> new SimpleStringProperty(CurrencyFormatter.format(c.getValue().getTotalRetailValue())));
        stockValuationTable.setItems(stockList);

        colCustName.setCellValueFactory(new PropertyValueFactory<>("entityName"));
        colCust0To30.setCellValueFactory(c -> new SimpleStringProperty(CurrencyFormatter.format(c.getValue().getCurrent0To30())));
        colCust31To60.setCellValueFactory(c -> new SimpleStringProperty(CurrencyFormatter.format(c.getValue().getDays31To60())));
        colCust61To90.setCellValueFactory(c -> new SimpleStringProperty(CurrencyFormatter.format(c.getValue().getDays61To90())));
        colCustOver90.setCellValueFactory(c -> new SimpleStringProperty(CurrencyFormatter.format(c.getValue().getDaysOver90())));
        colCustTotal.setCellValueFactory(c -> new SimpleStringProperty(CurrencyFormatter.format(c.getValue().getTotalOutstanding())));
        customerAgingTable.setItems(customerAgingList);

        colSuppName.setCellValueFactory(new PropertyValueFactory<>("entityName"));
        colSupp0To30.setCellValueFactory(c -> new SimpleStringProperty(CurrencyFormatter.format(c.getValue().getCurrent0To30())));
        colSupp31To60.setCellValueFactory(c -> new SimpleStringProperty(CurrencyFormatter.format(c.getValue().getDays31To60())));
        colSupp61To90.setCellValueFactory(c -> new SimpleStringProperty(CurrencyFormatter.format(c.getValue().getDays61To90())));
        colSuppOver90.setCellValueFactory(c -> new SimpleStringProperty(CurrencyFormatter.format(c.getValue().getDaysOver90())));
        colSuppTotal.setCellValueFactory(c -> new SimpleStringProperty(CurrencyFormatter.format(c.getValue().getTotalOutstanding())));
        supplierAgingTable.setItems(supplierAgingList);
    }

    @FXML
    private void loadAllReports() {
        LocalDate from = dpFrom.getValue() != null ? dpFrom.getValue() : LocalDate.now().withDayOfMonth(1);
        LocalDate to = dpTo.getValue() != null ? dpTo.getValue() : LocalDate.now();

        // 1. Profit & Loss
        currentPnl = reportService.getProfitAndLoss(from, to);
        lblSalesRevenue.setText(CurrencyFormatter.format(currentPnl.getSalesRevenue()));
        lblServiceRevenue.setText(CurrencyFormatter.format(currentPnl.getServiceRevenue()));
        lblTotalRevenue.setText(CurrencyFormatter.format(currentPnl.getTotalRevenue()));
        lblCogs.setText(CurrencyFormatter.format(currentPnl.getCostOfGoodsSold()));
        lblGrossProfit.setText(CurrencyFormatter.format(currentPnl.getGrossProfit()));
        lblPurchases.setText(CurrencyFormatter.format(currentPnl.getPurchaseExpenses()));
        lblNetProfit.setText(CurrencyFormatter.format(currentPnl.getNetProfit()));

        // 2. Balance Sheet
        currentBs = reportService.getBalanceSheet(to);
        bsList.clear();
        bsList.addAll(currentBs.getAssets());
        bsList.addAll(currentBs.getLiabilities());
        bsList.addAll(currentBs.getEquity());
        if (lblBsTotalAssets != null) lblBsTotalAssets.setText(CurrencyFormatter.format(currentBs.getTotalAssets()));
        if (lblBsTotalLiabEquity != null) lblBsTotalLiabEquity.setText(CurrencyFormatter.format(currentBs.getTotalLiabilitiesAndEquity()));
        if (lblBsStatus != null) {
            if (currentBs.isBalanced()) {
                lblBsStatus.setText("● BALANCED");
                lblBsStatus.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
            } else {
                lblBsStatus.setText("● UNBALANCED");
                lblBsStatus.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
            }
        }

        // 3. Statement of Cash Flows
        CashFlowDTO cf = reportService.getCashFlowStatement(from, to);
        if (lblCfNetIncome != null) lblCfNetIncome.setText(CurrencyFormatter.format(cf.getNetIncome()));
        if (lblCfArChange != null) lblCfArChange.setText(CurrencyFormatter.format(cf.getArChange()));
        if (lblCfApChange != null) lblCfApChange.setText(CurrencyFormatter.format(cf.getApChange()));
        if (lblCfInvChange != null) lblCfInvChange.setText(CurrencyFormatter.format(cf.getInventoryChange()));
        if (lblCfNetOperating != null) lblCfNetOperating.setText(CurrencyFormatter.format(cf.getOperatingCashFlow()));

        // 4. Sub-ledgers
        stockList.setAll(reportService.getStockValuation());
        customerAgingList.setAll(reportService.getCustomerAging());
        supplierAgingList.setAll(reportService.getSupplierAging());
    }

    @FXML
    private void handleExportCsv() {
        int tabIndex = reportTabs.getSelectionModel().getSelectedIndex();
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Report to CSV");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        String stamp = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

        switch (tabIndex) {
            case 0 -> chooser.setInitialFileName("profit_and_loss_" + stamp + ".csv");
            case 1 -> chooser.setInitialFileName("balance_sheet_" + stamp + ".csv");
            case 2 -> chooser.setInitialFileName("cash_flow_" + stamp + ".csv");
            case 3 -> chooser.setInitialFileName("stock_valuation_" + stamp + ".csv");
            case 4 -> chooser.setInitialFileName("customer_aging_" + stamp + ".csv");
            case 5 -> chooser.setInitialFileName("supplier_aging_" + stamp + ".csv");
            default -> chooser.setInitialFileName("report_" + stamp + ".csv");
        }

        File file = chooser.showSaveDialog(reportTabs.getScene().getWindow());
        if (file == null) return;

        try (PrintWriter out = new PrintWriter(new FileWriter(file))) {
            switch (tabIndex) {
                case 0 -> exportPnl(out);
                case 1 -> exportBs(out);
                case 2 -> exportCf(out);
                case 3 -> exportStock(out);
                case 4 -> exportAging(out, customerAgingList, "Customer");
                case 5 -> exportAging(out, supplierAgingList, "Supplier");
                default -> out.println("No data");
            }
            showAlert(Alert.AlertType.INFORMATION, "Export Complete", "Report exported to:\n" + file.getAbsolutePath());
        } catch (Exception e) {
            logger.error("CSV export failed: {}", e.getMessage(), e);
            showAlert(Alert.AlertType.ERROR, "Export Failed", e.getMessage());
        }
    }

    private void exportPnl(PrintWriter out) {
        out.println("Metric,Amount");
        out.println("Sales Revenue," + currentPnl.getSalesRevenue());
        out.println("Service Revenue," + currentPnl.getServiceRevenue());
        out.println("Total Revenue," + currentPnl.getTotalRevenue());
        out.println("Cost of Goods Sold," + currentPnl.getCostOfGoodsSold());
        out.println("Gross Profit," + currentPnl.getGrossProfit());
        out.println("Operating Expenses," + currentPnl.getPurchaseExpenses());
        out.println("Net Profit," + currentPnl.getNetProfit());
    }

    private void exportBs(PrintWriter out) {
        out.println("Account Code,Account Name,Type,Balance");
        for (BalanceSheetDTO.BalanceRow row : bsList) {
            out.printf("%s,%s,%s,%s%n", csv(row.accountCode()), csv(row.accountName()), csv(row.accountType()), row.balance());
        }
    }

    private void exportCf(PrintWriter out) {
        CashFlowDTO cf = reportService.getCashFlowStatement(
            dpFrom.getValue() != null ? dpFrom.getValue() : LocalDate.now().withDayOfMonth(1),
            dpTo.getValue() != null ? dpTo.getValue() : LocalDate.now()
        );
        out.println("Cash Flow Activity,Amount");
        out.println("Net Income," + cf.getNetIncome());
        out.println("AR Change," + cf.getArChange());
        out.println("AP Change," + cf.getApChange());
        out.println("Inventory Change," + cf.getInventoryChange());
        out.println("Net Operating Cash Flow," + cf.getOperatingCashFlow());
    }

    private void exportStock(PrintWriter out) {
        out.println("Product Code,Product Name,Category,Qty,Cost Price,Total Cost,Selling Price,Total Retail");
        for (StockValuationDTO r : stockList) {
            out.printf("%s,%s,%s,%d,%s,%s,%s,%s%n",
                    csv(r.getProductCode()),
                    csv(r.getProductName()),
                    csv(r.getCategory()),
                    r.getStockQuantity(),
                    r.getCostPrice(),
                    r.getTotalCostValue(),
                    r.getSellingPrice(),
                    r.getTotalRetailValue());
        }
    }

    private void exportAging(PrintWriter out, ObservableList<AgingReportDTO> rows, String entityLabel) {
        out.println(entityLabel + ",0-30 Days,31-60 Days,61-90 Days,Over 90 Days,Total Outstanding");
        for (AgingReportDTO r : rows) {
            out.printf("%s,%s,%s,%s,%s,%s%n",
                    csv(r.getEntityName()),
                    r.getCurrent0To30(),
                    r.getDays31To60(),
                    r.getDays61To90(),
                    r.getDaysOver90(),
                    r.getTotalOutstanding());
        }
    }

    private String csv(String v) {
        if (v == null) return "";
        String s = v.replace("\"", "\"\"");
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s + "\"";
        }
        return s;
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
