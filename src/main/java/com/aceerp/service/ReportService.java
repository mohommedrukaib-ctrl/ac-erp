package com.aceerp.service;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.*;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.view.JasperViewer;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ReportService {

    private static final Logger logger = LoggerFactory.getLogger(ReportService.class);
    private final CompanyProfileService companyProfileService = new CompanyProfileService();

    private Map<String, Object> buildCompanyParameters() {
        CompanyProfile c = companyProfileService.getProfile();
        Map<String, Object> p = new HashMap<>();
        p.put("COMPANY_NAME", c.getCompanyName() != null ? c.getCompanyName() : "");
        p.put("COMPANY_TAGLINE", c.getTagline() != null ? c.getTagline() : "");
        p.put("COMPANY_ADDRESS", c.getFullAddress());
        p.put("COMPANY_PHONE", c.getPhone() != null ? c.getPhone() : "");
        p.put("COMPANY_EMAIL", c.getEmail() != null ? c.getEmail() : "");
        p.put("COMPANY_WEBSITE", c.getWebsite() != null ? c.getWebsite() : "");
        p.put("COMPANY_TAX_NO", c.getTaxNumber() != null ? c.getTaxNumber() : "");
        p.put("COMPANY_FOOTER", c.getInvoiceFooter() != null ? c.getInvoiceFooter() : "");
        p.put("COMPANY_LOGO", companyProfileService.resolveLogoAbsolutePath(c.getLogoPath()));
        return p;
    }

    private void printDocument(String docType, String idParamName, int id) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> printDocument(docType, idParamName, id));
            return;
        }

        ReportTemplateService templateService = new ReportTemplateService();
        List<String> templates = templateService.listTemplatesFor(docType);
        if (templates.isEmpty()) {
            showPrintError("No print templates are available for " + docType.replace('_', ' ') + ".");
            return;
        }

        String activeTemplate = templateService.getActiveTemplateKey(docType);
        String initialTemplate = templates.contains(activeTemplate) ? activeTemplate : templates.getFirst();
        ChoiceDialog<String> dialog = new ChoiceDialog<>(initialTemplate, templates);
        dialog.setTitle("Choose Print Template");
        dialog.setHeaderText("Select a template for this " + docType.replace('_', ' ') + ".");
        dialog.setContentText("Template:");
        Optional<String> selectedTemplate = dialog.showAndWait();
        if (selectedTemplate.isEmpty()) {
            return;
        }

        printDocument(docType, idParamName, id, selectedTemplate.get());
    }

    private void printDocument(String docType, String idParamName, int id, String templateKey) {
        new Thread(() -> {
            try (Connection conn = DatabaseConfig.getInstance().getConnection();
                 InputStream stream = new ReportTemplateService().openTemplateStream(docType, templateKey)) {
                JasperReport report = JasperCompileManager.compileReport(stream);

                Map<String, Object> parameters = buildCompanyParameters();
                parameters.put(idParamName, id);

                JasperPrint print = JasperFillManager.fillReport(report, parameters, conn);
                JasperViewer.viewReport(print, false);
            } catch (Exception e) {
                logger.error("Failed to generate report {} for id {}", docType, id, e);
                Platform.runLater(() -> showPrintError(
                        "Could not generate " + docType.replace('_', ' ') + ": " + e.getMessage()));
            }
        }).start();
    }

    private void showPrintError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Print Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ===================== Standard Business Documents =====================
    public static void printInvoice(int invoiceId) { new ReportService().printDocument("invoice", "INVOICE_ID", invoiceId); }
    public static void printQuotation(int quotationId) { new ReportService().printDocument("quotation", "QUOTE_ID", quotationId); }
    public static void printSalesOrder(int orderId) { new ReportService().printDocument("sales_order", "ORDER_ID", orderId); }
    public static void printSupplierBill(int billId) { new ReportService().printDocument("supplier_bill", "BILL_ID", billId); }
    public static void printDeliveryNote(int dnId) { new ReportService().printDocument("delivery_note", "DN_ID", dnId); }
    public static void printPurchaseOrder(int poId) { new ReportService().printDocument("purchase_order", "PO_ID", poId); }
    public static void printJobNote(int jobId) { new ReportService().printDocument("job_note_a5", "JOB_ID", jobId); }
    public static void printWarrantyCertificate(int serialId) { new ReportService().printDocument("warranty_certificate", "SERIAL_ID", serialId); }
    public static void printAmcRenewalNotice(int contractId) { new ReportService().printDocument("amc_renewal_notice", "CONTRACT_ID", contractId); }
    public static void printTechnicianPayslip(int technicianId) { new ReportService().printDocument("technician_payslip", "TECH_ID", technicianId); }

    // ===================== Double-Entry Financial Statements =====================

    public ProfitAndLossDTO getProfitAndLoss(LocalDate startDate, LocalDate endDate) {
        ProfitAndLossDTO dto = new ProfitAndLossDTO();

        String glSql = """
            SELECT a.code, a.account_type,
                   SUM(jel.debit) AS total_debit, SUM(jel.credit) AS total_credit
            FROM journal_entry_lines jel
            JOIN journal_entries je ON jel.journal_entry_id = je.id
            JOIN accounts a ON jel.account_id = a.id
            WHERE je.status = 'POSTED' AND je.entry_date BETWEEN ? AND ?
            GROUP BY a.code, a.account_type
        """;

        BigDecimal salesRevenue = BigDecimal.ZERO;
        BigDecimal serviceRevenue = BigDecimal.ZERO;
        BigDecimal cogs = BigDecimal.ZERO;
        BigDecimal operatingExpenses = BigDecimal.ZERO;

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(glSql)) {

            stmt.setDate(1, Date.valueOf(startDate));
            stmt.setDate(2, Date.valueOf(endDate));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String code = rs.getString("code");
                    String type = rs.getString("account_type");
                    BigDecimal dr = rs.getBigDecimal("total_debit");
                    BigDecimal cr = rs.getBigDecimal("total_credit");

                    if ("REVENUE".equalsIgnoreCase(type)) {
                        BigDecimal netRev = cr.subtract(dr);
                        if ("4100".equals(code)) {
                            serviceRevenue = serviceRevenue.add(netRev);
                        } else {
                            salesRevenue = salesRevenue.add(netRev);
                        }
                    } else if ("EXPENSE".equalsIgnoreCase(type)) {
                        BigDecimal netExp = dr.subtract(cr);
                        if (code.startsWith("5")) {
                            cogs = cogs.add(netExp);
                        } else {
                            operatingExpenses = operatingExpenses.add(netExp);
                        }
                    }
                }
            }

            dto.setSalesRevenue(salesRevenue.setScale(2, RoundingMode.HALF_UP));
            dto.setServiceRevenue(serviceRevenue.setScale(2, RoundingMode.HALF_UP));
            dto.setCostOfGoodsSold(cogs.setScale(2, RoundingMode.HALF_UP));
            dto.setPurchaseExpenses(operatingExpenses.setScale(2, RoundingMode.HALF_UP));
            dto.calculateTotals();

        } catch (SQLException e) {
            logger.error("Error generating GL Profit & Loss: {}", e.getMessage(), e);
        }

        return dto;
    }

    public BalanceSheetDTO getBalanceSheet(LocalDate asOfDate) {
        BalanceSheetDTO dto = new BalanceSheetDTO();

        String sql = """
            SELECT a.code, a.name, a.account_type, a.normal_balance,
                   COALESCE(SUM(jel.debit), 0) AS total_debit,
                   COALESCE(SUM(jel.credit), 0) AS total_credit
            FROM accounts a
            LEFT JOIN journal_entry_lines jel ON jel.account_id = a.id
            LEFT JOIN journal_entries je ON jel.journal_entry_id = je.id AND je.status = 'POSTED' AND je.entry_date <= ?
            WHERE a.is_active = TRUE
            GROUP BY a.id, a.code, a.name, a.account_type, a.normal_balance
            ORDER BY a.code ASC
        """;

        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal totalLiabilities = BigDecimal.ZERO;
        BigDecimal totalEquity = BigDecimal.ZERO;

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDate(1, Date.valueOf(asOfDate));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String code = rs.getString("code");
                    String name = rs.getString("name");
                    String type = rs.getString("account_type");
                    BigDecimal dr = rs.getBigDecimal("total_debit");
                    BigDecimal cr = rs.getBigDecimal("total_credit");

                    BigDecimal balance;
                    if ("ASSET".equalsIgnoreCase(type) || "EXPENSE".equalsIgnoreCase(type)) {
                        balance = dr.subtract(cr);
                    } else {
                        balance = cr.subtract(dr);
                    }

                    if (balance.compareTo(BigDecimal.ZERO) == 0) continue;

                    BalanceSheetDTO.BalanceRow row = new BalanceSheetDTO.BalanceRow(code, name, type, balance.setScale(2, RoundingMode.HALF_UP));

                    if ("ASSET".equalsIgnoreCase(type)) {
                        dto.getAssets().add(row);
                        totalAssets = totalAssets.add(balance);
                    } else if ("LIABILITY".equalsIgnoreCase(type)) {
                        dto.getLiabilities().add(row);
                        totalLiabilities = totalLiabilities.add(balance);
                    } else if ("EQUITY".equalsIgnoreCase(type)) {
                        dto.getEquity().add(row);
                        totalEquity = totalEquity.add(balance);
                    }
                }
            }

            LocalDate startOfYear = asOfDate.withDayOfYear(1);
            ProfitAndLossDTO pnl = getProfitAndLoss(startOfYear, asOfDate);
            BigDecimal netIncome = pnl.getNetProfit();
            dto.setCurrentYearNetIncome(netIncome);

            dto.setTotalAssets(totalAssets.setScale(2, RoundingMode.HALF_UP));
            dto.setTotalLiabilities(totalLiabilities.setScale(2, RoundingMode.HALF_UP));
            dto.setTotalEquity(totalEquity.setScale(2, RoundingMode.HALF_UP));
            dto.setTotalLiabilitiesAndEquity(totalLiabilities.add(totalEquity).add(netIncome).setScale(2, RoundingMode.HALF_UP));

        } catch (SQLException e) {
            logger.error("Error generating Balance Sheet: {}", e.getMessage(), e);
        }

        return dto;
    }

    public CashFlowDTO getCashFlowStatement(LocalDate startDate, LocalDate endDate) {
        CashFlowDTO dto = new CashFlowDTO();
        ProfitAndLossDTO pnl = getProfitAndLoss(startDate, endDate);
        dto.setNetIncome(pnl.getNetProfit());

        String sql = """
            SELECT a.code,
                   COALESCE(SUM(jel.debit - jel.credit), 0) AS net_dr_change
            FROM journal_entry_lines jel
            JOIN journal_entries je ON jel.journal_entry_id = je.id
            JOIN accounts a ON jel.account_id = a.id
            WHERE je.status = 'POSTED' AND je.entry_date BETWEEN ? AND ?
            GROUP BY a.code
        """;

        BigDecimal arChange = BigDecimal.ZERO;
        BigDecimal apChange = BigDecimal.ZERO;
        BigDecimal invChange = BigDecimal.ZERO;

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDate(1, Date.valueOf(startDate));
            stmt.setDate(2, Date.valueOf(endDate));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String code = rs.getString("code");
                    BigDecimal change = rs.getBigDecimal("net_dr_change");

                    if ("1200".equals(code)) arChange = change.negate();
                    if ("1300".equals(code)) invChange = change.negate();
                    if ("2100".equals(code)) apChange = change;
                }
            }

            dto.setArChange(arChange.setScale(2, RoundingMode.HALF_UP));
            dto.setApChange(apChange.setScale(2, RoundingMode.HALF_UP));
            dto.setInventoryChange(invChange.setScale(2, RoundingMode.HALF_UP));

            BigDecimal operating = pnl.getNetProfit().add(arChange).add(apChange).add(invChange);
            dto.setOperatingCashFlow(operating.setScale(2, RoundingMode.HALF_UP));
            dto.setNetCashChange(operating);

        } catch (SQLException e) {
            logger.error("Error generating Cash Flow Statement: {}", e.getMessage(), e);
        }

        return dto;
    }

    // ===================== PHASE 1 REPORTS DATA SERVICES =====================

    /**
     * 1. Open Invoices Report
     */
    public List<OpenInvoiceRow> getOpenInvoices(LocalDate startDate, LocalDate endDate) {
        List<OpenInvoiceRow> list = new ArrayList<>();
        String sql = """
            SELECT i.id, i.invoice_number, c.name AS customer_name, i.invoice_date, i.due_date,
                   i.total_amount, COALESCE(i.paid_amount, 0) AS paid_amount, i.status
            FROM invoices i
            JOIN customers c ON i.customer_id = c.id
            WHERE COALESCE(i.status, '') NOT IN ('PAID', 'CANCELLED')
              AND i.invoice_date BETWEEN ? AND ?
            ORDER BY i.due_date ASC
        """;

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDate(1, Date.valueOf(startDate));
            stmt.setDate(2, Date.valueOf(endDate));

            try (ResultSet rs = stmt.executeQuery()) {
                LocalDate today = LocalDate.now();
                while (rs.next()) {
                    Date due = rs.getDate("due_date");
                    LocalDate dueDate = due != null ? due.toLocalDate() : today;
                    long overdueDays = ChronoUnit.DAYS.between(dueDate, today);
                    if (overdueDays < 0) overdueDays = 0;

                    BigDecimal total = rs.getBigDecimal("total_amount");
                    BigDecimal paid = rs.getBigDecimal("paid_amount");
                    BigDecimal balance = total.subtract(paid);

                    list.add(new OpenInvoiceRow(
                        rs.getInt("id"),
                        rs.getString("invoice_number"),
                        rs.getString("customer_name"),
                        rs.getDate("invoice_date").toLocalDate(),
                        dueDate,
                        overdueDays,
                        total,
                        paid,
                        balance,
                        rs.getString("status")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Error fetching open invoices: {}", e.getMessage(), e);
        }
        return list;
    }

    /**
     * 2. A/R Aging Detail Report
     */
    public List<ArAgingDetailRow> getArAgingDetail(LocalDate asOfDate) {
        List<ArAgingDetailRow> list = new ArrayList<>();
        String sql = """
            SELECT c.name AS customer_name, i.invoice_number, i.invoice_date, i.due_date,
                   i.total_amount, COALESCE(i.paid_amount, 0) AS paid_amount
            FROM invoices i
            JOIN customers c ON i.customer_id = c.id
            WHERE COALESCE(i.status, '') NOT IN ('PAID', 'CANCELLED')
              AND i.invoice_date <= ?
            ORDER BY c.name ASC, i.due_date ASC
        """;

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDate(1, Date.valueOf(asOfDate));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Date due = rs.getDate("due_date");
                    LocalDate dueDate = due != null ? due.toLocalDate() : asOfDate;
                    long overdue = ChronoUnit.DAYS.between(dueDate, asOfDate);

                    String bucket;
                    if (overdue <= 30) bucket = "0-30 Days";
                    else if (overdue <= 60) bucket = "31-60 Days";
                    else if (overdue <= 90) bucket = "61-90 Days";
                    else bucket = "Over 90 Days";

                    BigDecimal orig = rs.getBigDecimal("total_amount");
                    BigDecimal paid = rs.getBigDecimal("paid_amount");
                    BigDecimal bal = orig.subtract(paid);

                    list.add(new ArAgingDetailRow(
                        rs.getString("customer_name"),
                        rs.getString("invoice_number"),
                        rs.getDate("invoice_date").toLocalDate(),
                        dueDate,
                        Math.max(0, overdue),
                        bucket,
                        orig,
                        paid,
                        bal
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Error generating AR aging detail: {}", e.getMessage(), e);
        }
        return list;
    }

    /**
     * 3. A/P Aging Detail Report
     */
    public List<ApAgingDetailRow> getApAgingDetail(LocalDate asOfDate) {
        List<ApAgingDetailRow> list = new ArrayList<>();
        String sql = """
            SELECT s.name AS vendor_name, sb.bill_number, sb.bill_date, sb.due_date,
                   sb.total_amount, COALESCE(sb.paid_amount, 0) AS paid_amount
            FROM supplier_bills sb
            JOIN suppliers s ON sb.supplier_id = s.id
            WHERE COALESCE(sb.status, '') NOT IN ('PAID', 'CANCELLED')
              AND sb.bill_date <= ?
            ORDER BY s.name ASC, sb.due_date ASC
        """;

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDate(1, Date.valueOf(asOfDate));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Date due = rs.getDate("due_date");
                    LocalDate dueDate = due != null ? due.toLocalDate() : asOfDate;
                    long overdue = ChronoUnit.DAYS.between(dueDate, asOfDate);

                    String bucket;
                    if (overdue <= 30) bucket = "0-30 Days";
                    else if (overdue <= 60) bucket = "31-60 Days";
                    else if (overdue <= 90) bucket = "61-90 Days";
                    else bucket = "Over 90 Days";

                    BigDecimal orig = rs.getBigDecimal("total_amount");
                    BigDecimal paid = rs.getBigDecimal("paid_amount");
                    BigDecimal bal = orig.subtract(paid);

                    list.add(new ApAgingDetailRow(
                        rs.getString("vendor_name"),
                        rs.getString("bill_number"),
                        rs.getDate("bill_date").toLocalDate(),
                        dueDate,
                        Math.max(0, overdue),
                        bucket,
                        orig,
                        paid,
                        bal
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Error generating AP aging detail: {}", e.getMessage(), e);
        }
        return list;
    }

    /**
     * 4. Sales By Customer Report
     */
    public List<SalesByCustomerRow> getSalesByCustomer(LocalDate startDate, LocalDate endDate) {
        List<SalesByCustomerRow> list = new ArrayList<>();
        String sql = """
            SELECT c.id, c.name, COUNT(i.id) AS inv_count,
                   COALESCE(SUM(i.subtotal), 0) AS subtotal,
                   COALESCE(SUM(i.tax_amount), 0) AS tax_amount,
                   COALESCE(SUM(i.total_amount), 0) AS total_sales
            FROM customers c
            JOIN invoices i ON i.customer_id = c.id
            WHERE i.status != 'CANCELLED' AND i.invoice_date BETWEEN ? AND ?
            GROUP BY c.id, c.name
            ORDER BY total_sales DESC
        """;

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDate(1, Date.valueOf(startDate));
            stmt.setDate(2, Date.valueOf(endDate));

            try (ResultSet rs = stmt.executeQuery()) {
                BigDecimal grandTotal = BigDecimal.ZERO;
                List<SalesByCustomerRow> temp = new ArrayList<>();

                while (rs.next()) {
                    BigDecimal total = rs.getBigDecimal("total_sales");
                    grandTotal = grandTotal.add(total);
                    temp.add(new SalesByCustomerRow(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getInt("inv_count"),
                        rs.getBigDecimal("subtotal"),
                        rs.getBigDecimal("tax_amount"),
                        total,
                        0.0
                    ));
                }

                for (SalesByCustomerRow r : temp) {
                    double pct = grandTotal.compareTo(BigDecimal.ZERO) > 0
                        ? r.totalSales().divide(grandTotal, 4, RoundingMode.HALF_UP).doubleValue() * 100
                        : 0.0;
                    list.add(new SalesByCustomerRow(
                        r.customerId(), r.customerName(), r.invoiceCount(),
                        r.totalSubtotal(), r.totalTax(), r.totalSales(), pct
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Error generating sales by customer: {}", e.getMessage(), e);
        }
        return list;
    }

    /**
     * 5. Sales By Product/Service Report
     */
    public List<SalesByProductRow> getSalesByProduct(LocalDate startDate, LocalDate endDate) {
        List<SalesByProductRow> list = new ArrayList<>();
        String sql = """
            SELECT p.product_code, p.name AS product_name, COALESCE(p.category, 'General') AS category,
                   COALESCE(SUM(il.quantity), 0) AS qty_sold,
                   COALESCE(SUM(il.line_total), 0) AS sales_amount
            FROM invoice_lines il
            JOIN invoices i ON il.invoice_id = i.id
            JOIN products p ON il.product_id = p.id
            WHERE i.status != 'CANCELLED' AND i.invoice_date BETWEEN ? AND ?
            GROUP BY p.product_code, p.name, p.category
            ORDER BY sales_amount DESC
        """;

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDate(1, Date.valueOf(startDate));
            stmt.setDate(2, Date.valueOf(endDate));

            try (ResultSet rs = stmt.executeQuery()) {
                BigDecimal grandTotal = BigDecimal.ZERO;
                List<SalesByProductRow> temp = new ArrayList<>();

                while (rs.next()) {
                    BigDecimal qty = rs.getBigDecimal("qty_sold");
                    BigDecimal sales = rs.getBigDecimal("sales_amount");
                    grandTotal = grandTotal.add(sales);

                    BigDecimal avgPrice = qty.compareTo(BigDecimal.ZERO) > 0
                        ? sales.divide(qty, 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                    temp.add(new SalesByProductRow(
                        rs.getString("product_code"),
                        rs.getString("product_name"),
                        rs.getString("category"),
                        qty, sales, avgPrice, 0.0
                    ));
                }

                for (SalesByProductRow r : temp) {
                    double pct = grandTotal.compareTo(BigDecimal.ZERO) > 0
                        ? r.totalSalesAmount().divide(grandTotal, 4, RoundingMode.HALF_UP).doubleValue() * 100
                        : 0.0;
                    list.add(new SalesByProductRow(
                        r.productCode(), r.productName(), r.category(),
                        r.totalQuantitySold(), r.totalSalesAmount(), r.averagePrice(), pct
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Error generating sales by product: {}", e.getMessage(), e);
        }
        return list;
    }

    /**
     * 6. Bill Payment List
     */
    public List<BillPaymentRow> getBillPayments(LocalDate startDate, LocalDate endDate) {
        List<BillPaymentRow> list = new ArrayList<>();
        String sql = """
            SELECT sp.id, sp.payment_number, sp.payment_date, s.name AS vendor_name,
                   sb.bill_number, sp.payment_method, sp.reference_number, sp.amount, sp.notes
            FROM supplier_payments sp
            JOIN suppliers s ON sp.supplier_id = s.id
            LEFT JOIN supplier_bills sb ON sp.bill_id = sb.id
            WHERE sp.payment_date BETWEEN ? AND ?
            ORDER BY sp.payment_date DESC
        """;

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setDate(1, Date.valueOf(startDate));
            stmt.setDate(2, Date.valueOf(endDate));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new BillPaymentRow(
                        rs.getInt("id"),
                        rs.getString("payment_number"),
                        rs.getDate("payment_date").toLocalDate(),
                        rs.getString("vendor_name"),
                        rs.getString("bill_number") != null ? rs.getString("bill_number") : "N/A",
                        rs.getString("payment_method"),
                        rs.getString("reference_number"),
                        rs.getBigDecimal("amount"),
                        rs.getString("notes")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Error generating bill payment list: {}", e.getMessage(), e);
        }
        return list;
    }

    /**
     * 7. Customer Balance Summary
     */
    public List<CustomerBalanceSummaryRow> getCustomerBalanceSummary() {
        List<CustomerBalanceSummaryRow> list = new ArrayList<>();
        String sql = """
            SELECT c.id, c.name, c.phone,
                   COALESCE(SUM(i.total_amount), 0) AS total_invoiced,
                   COALESCE(SUM(i.paid_amount), 0) AS total_paid
            FROM customers c
            LEFT JOIN invoices i ON i.customer_id = c.id AND i.status != 'CANCELLED'
            GROUP BY c.id, c.name, c.phone
            HAVING COALESCE(SUM(i.total_amount - i.paid_amount), 0) != 0
            ORDER BY c.name ASC
        """;

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                BigDecimal inv = rs.getBigDecimal("total_invoiced");
                BigDecimal paid = rs.getBigDecimal("total_paid");
                BigDecimal bal = inv.subtract(paid);

                list.add(new CustomerBalanceSummaryRow(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getString("phone") != null ? rs.getString("phone") : "N/A",
                    inv, paid, bal
                ));
            }
        } catch (SQLException e) {
            logger.error("Error generating customer balance summary: {}", e.getMessage(), e);
        }
        return list;
    }

    /**
     * 8. Vendor Balance Summary
     */
    public List<VendorBalanceSummaryRow> getVendorBalanceSummary() {
        List<VendorBalanceSummaryRow> list = new ArrayList<>();
        String sql = """
            SELECT s.id, s.name, s.phone,
                   COALESCE(SUM(sb.total_amount), 0) AS total_billed,
                   COALESCE(SUM(sb.paid_amount), 0) AS total_paid
            FROM suppliers s
            LEFT JOIN supplier_bills sb ON sb.supplier_id = s.id AND sb.status != 'CANCELLED'
            GROUP BY s.id, s.name, s.phone
            HAVING COALESCE(SUM(sb.total_amount - sb.paid_amount), 0) != 0
            ORDER BY s.name ASC
        """;

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                BigDecimal billed = rs.getBigDecimal("total_billed");
                BigDecimal paid = rs.getBigDecimal("total_paid");
                BigDecimal owed = billed.subtract(paid);

                list.add(new VendorBalanceSummaryRow(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getString("phone") != null ? rs.getString("phone") : "N/A",
                    billed, paid, owed
                ));
            }
        } catch (SQLException e) {
            logger.error("Error generating vendor balance summary: {}", e.getMessage(), e);
        }
        return list;
    }

    /**
     * Stock Valuation & Existing Agings
     */
    public List<StockValuationDTO> getStockValuation() {
        List<StockValuationDTO> list = new ArrayList<>();
        String sql = """
            SELECT COALESCE(product_code, '') AS code_val, name, COALESCE(category,'') AS category,
                   COALESCE(stock_quantity,0) AS stock_quantity,
                   COALESCE(cost_price,0) AS cost_price, COALESCE(selling_price,0) AS selling_price
            FROM products ORDER BY name ASC
            """;
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                StockValuationDTO dto = new StockValuationDTO();
                dto.setProductCode(rs.getString("code_val"));
                dto.setProductName(rs.getString("name"));
                dto.setCategory(rs.getString("category"));
                dto.setCostPrice(rs.getBigDecimal("cost_price"));
                dto.setSellingPrice(rs.getBigDecimal("selling_price"));
                dto.setStockQuantity(rs.getInt("stock_quantity"));
                list.add(dto);
            }
        } catch (SQLException e) {
            logger.error("Stock valuation error: {}", e.getMessage(), e);
        }
        return list;
    }

    public List<AgingReportDTO> getCustomerAging() {
        List<AgingReportDTO> list = new ArrayList<>();
        String sql = """
            SELECT c.name AS entity_name,
                COALESCE(SUM(CASE WHEN CURRENT_DATE - i.invoice_date <= 30 THEN (i.total_amount - COALESCE(i.paid_amount,0)) ELSE 0 END),0) AS current_30,
                COALESCE(SUM(CASE WHEN CURRENT_DATE - i.invoice_date BETWEEN 31 AND 60 THEN (i.total_amount - COALESCE(i.paid_amount,0)) ELSE 0 END),0) AS days_60,
                COALESCE(SUM(CASE WHEN CURRENT_DATE - i.invoice_date BETWEEN 61 AND 90 THEN (i.total_amount - COALESCE(i.paid_amount,0)) ELSE 0 END),0) AS days_90,
                COALESCE(SUM(CASE WHEN CURRENT_DATE - i.invoice_date > 90 THEN (i.total_amount - COALESCE(i.paid_amount,0)) ELSE 0 END),0) AS over_90
            FROM customers c
            JOIN invoices i ON i.customer_id = c.id
            WHERE COALESCE(i.status,'') NOT IN ('PAID','CANCELLED')
            GROUP BY c.id, c.name
            HAVING SUM(i.total_amount - COALESCE(i.paid_amount,0)) > 0
            ORDER BY c.name
            """;
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                AgingReportDTO dto = new AgingReportDTO();
                dto.setEntityName(rs.getString("entity_name"));
                dto.setCurrent0To30(rs.getBigDecimal("current_30"));
                dto.setDays31To60(rs.getBigDecimal("days_60"));
                dto.setDays61To90(rs.getBigDecimal("days_90"));
                dto.setDaysOver90(rs.getBigDecimal("over_90"));
                list.add(dto);
            }
        } catch (SQLException e) {
            logger.error("Customer aging error: {}", e.getMessage(), e);
        }
        return list;
    }

    public List<AgingReportDTO> getSupplierAging() {
        List<AgingReportDTO> list = new ArrayList<>();
        String sql = """
            SELECT s.name AS entity_name,
                COALESCE(SUM(CASE WHEN CURRENT_DATE - sb.bill_date <= 30 THEN (sb.total_amount - COALESCE(sb.paid_amount,0)) ELSE 0 END),0) AS current_30,
                COALESCE(SUM(CASE WHEN CURRENT_DATE - sb.bill_date BETWEEN 31 AND 60 THEN (sb.total_amount - COALESCE(sb.paid_amount,0)) ELSE 0 END),0) AS days_60,
                COALESCE(SUM(CASE WHEN CURRENT_DATE - sb.bill_date BETWEEN 61 AND 90 THEN (sb.total_amount - COALESCE(sb.paid_amount,0)) ELSE 0 END),0) AS days_90,
                COALESCE(SUM(CASE WHEN CURRENT_DATE - sb.bill_date > 90 THEN (sb.total_amount - COALESCE(sb.paid_amount,0)) ELSE 0 END),0) AS over_90
            FROM suppliers s
            JOIN supplier_bills sb ON sb.supplier_id = s.id
            WHERE COALESCE(sb.status,'') NOT IN ('PAID','CANCELLED')
            GROUP BY s.id, s.name
            HAVING SUM(sb.total_amount - COALESCE(sb.paid_amount,0)) > 0
            ORDER BY s.name
            """;
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                AgingReportDTO dto = new AgingReportDTO();
                dto.setEntityName(rs.getString("entity_name"));
                dto.setCurrent0To30(rs.getBigDecimal("current_30"));
                dto.setDays31To60(rs.getBigDecimal("days_60"));
                dto.setDays61To90(rs.getBigDecimal("days_90"));
                dto.setDaysOver90(rs.getBigDecimal("over_90"));
                list.add(dto);
            }
        } catch (SQLException e) {
            logger.error("Supplier aging error: {}", e.getMessage(), e);
        }
        return list;
    }
}