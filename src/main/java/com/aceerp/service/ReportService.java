package com.aceerp.service;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.*;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.view.JasperViewer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        new Thread(() -> {
            try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
                ReportTemplateService templateService = new ReportTemplateService();
                InputStream stream = templateService.openActiveTemplateStream(docType);

                JasperReport report = JasperCompileManager.compileReport(stream);
                stream.close();

                Map<String, Object> parameters = buildCompanyParameters();
                parameters.put(idParamName, id);

                JasperPrint print = JasperFillManager.fillReport(report, parameters, conn);
                JasperViewer.viewReport(print, false);
            } catch (Exception e) {
                logger.error("Failed to generate report {} for id {}", docType, id, e);
            }
        }).start();
    }

    // ===================== Standard Business Documents =====================
    public static void printInvoice(int invoiceId) { new ReportService().printDocument("invoice", "INVOICE_ID", invoiceId); }
    public static void printQuotation(int quotationId) { new ReportService().printDocument("quotation", "QUOTE_ID", quotationId); }
    public static void printSalesOrder(int orderId) { new ReportService().printDocument("sales_order", "ORDER_ID", orderId); }
    public static void printSupplierBill(int billId) { new ReportService().printDocument("supplier_bill", "BILL_ID", billId); }
    public static void printDeliveryNote(int dnId) { new ReportService().printDocument("delivery_note", "DN_ID", dnId); }
    public static void printPurchaseOrder(int poId) { new ReportService().printDocument("purchase_order", "PO_ID", poId); }
    public static void printJobNote(int jobId) { new ReportService().printDocument("job_note_a5", "JOB_ID", jobId); }

    // ===================== HVAC Enterprise Documents =====================
    public static void printWarrantyCertificate(int serialId) {
        new ReportService().printDocument("warranty_certificate", "SERIAL_ID", serialId);
    }

    public static void printAmcRenewalNotice(int contractId) {
        new ReportService().printDocument("amc_renewal_notice", "CONTRACT_ID", contractId);
    }

    public static void printTechnicianPayslip(int technicianId) {
        new ReportService().printDocument("technician_payslip", "TECH_ID", technicianId);
    }

    // ===================== Double-Entry General Ledger Financials =====================

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

    // ===================== Sub-Ledgers & Inventory =====================

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