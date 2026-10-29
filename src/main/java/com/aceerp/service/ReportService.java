package com.aceerp.service;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.AgingReportDTO;
import com.aceerp.model.CompanyProfile;
import com.aceerp.model.ProfitAndLossDTO;
import com.aceerp.model.StockValuationDTO;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.view.JasperViewer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportService {

    private static final Logger logger = LoggerFactory.getLogger(ReportService.class);
    private final CompanyProfileService companyProfileService = new CompanyProfileService();

    // ===================== Branding parameters for ALL prints =====================

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
                CompanyProfile c = companyProfileService.getProfile();
                String theme = "classic";
                if (c != null && c.getDefaultPrintTemplate() != null && !c.getDefaultPrintTemplate().isBlank()) {
                    theme = c.getDefaultPrintTemplate().toLowerCase();
                } else {
                    theme = SettingsService.getInstance().get("PRINT_THEME", "classic").toLowerCase();
                }

                String templatePath = "/reports/" + theme + "/" + docType + ".jrxml";
                InputStream stream = ReportService.class.getResourceAsStream(templatePath);
                
                if (stream == null) {
                    logger.warn("Theme template not found: {}. Falling back to classic theme.", templatePath);
                    templatePath = "/reports/classic/" + docType + ".jrxml";
                    stream = ReportService.class.getResourceAsStream(templatePath);
                }

                if (stream == null) {
                    logger.warn("Classic theme template not found: {}. Falling back to root reports folder.", templatePath);
                    templatePath = "/reports/" + docType + ".jrxml";
                    stream = ReportService.class.getResourceAsStream(templatePath);
                }

                if (stream == null) {
                    logger.error("Could not find template anywhere for docType: {}", docType);
                    return;
                }

                JasperReport report = JasperCompileManager.compileReport(stream);
                Map<String, Object> parameters = buildCompanyParameters();
                parameters.put(idParamName, id);

                JasperPrint print = JasperFillManager.fillReport(report, parameters, conn);
                JasperViewer.viewReport(print, false);
            } catch (Exception e) {
                logger.error("Failed to generate report {} for id {}", docType, id, e);
            }
        }).start();
    }

    public static void printInvoice(int invoiceId) {
        new ReportService().printDocument("invoice", "INVOICE_ID", invoiceId);
    }

    public static void printQuotation(int quotationId) {
        new ReportService().printDocument("quotation", "QUOTE_ID", quotationId);
    }

    public static void printSalesOrder(int orderId) {
        new ReportService().printDocument("sales_order", "ORDER_ID", orderId);
    }

        public static void printSupplierBill(int billId) {
        new ReportService().printDocument("supplier_bill", "BILL_ID", billId);
    }

    public static void printDeliveryNote(int dnId) {
        new ReportService().printDocument("delivery_note", "DN_ID", dnId);
    }

    public static void printPurchaseOrder(int poId) {
        new ReportService().printDocument("purchase_order", "PO_ID", poId);
    }

    public static void printJobNote(int jobId) {
        new ReportService().printDocument("job_note_a5", "JOB_ID", jobId);
    }

    // ===================== Analytics =====================

    public ProfitAndLossDTO getProfitAndLoss(LocalDate startDate, LocalDate endDate) {
        ProfitAndLossDTO dto = new ProfitAndLossDTO();
        String sqlSales = "SELECT COALESCE(SUM(total_amount), 0) FROM invoices WHERE COALESCE(status,'') <> 'CANCELLED' AND invoice_date BETWEEN ? AND ?";
        String sqlServices = "SELECT COALESCE(SUM(labor_cost), 0) FROM service_jobs WHERE status = 'COMPLETED' AND COALESCE(completion_date, scheduled_date) BETWEEN ? AND ?";
        String sqlPurchases = "SELECT COALESCE(SUM(total_amount), 0) FROM supplier_bills WHERE COALESCE(status,'') <> 'CANCELLED' AND bill_date BETWEEN ? AND ?";

        try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(sqlSales)) {
                stmt.setDate(1, Date.valueOf(startDate));
                stmt.setDate(2, Date.valueOf(endDate));
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) dto.setSalesRevenue(rs.getBigDecimal(1));
            } catch (SQLException e) { logger.warn("P&L sales: {}", e.getMessage()); }

            try (PreparedStatement stmt = conn.prepareStatement(sqlServices)) {
                stmt.setDate(1, Date.valueOf(startDate));
                stmt.setDate(2, Date.valueOf(endDate));
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) dto.setServiceRevenue(rs.getBigDecimal(1));
            } catch (SQLException e) { logger.warn("P&L services: {}", e.getMessage()); }

            try (PreparedStatement stmt = conn.prepareStatement(sqlPurchases)) {
                stmt.setDate(1, Date.valueOf(startDate));
                stmt.setDate(2, Date.valueOf(endDate));
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) dto.setPurchaseExpenses(rs.getBigDecimal(1));
            } catch (SQLException e) { logger.warn("P&L purchases: {}", e.getMessage()); }

            dto.setCostOfGoodsSold(dto.getSalesRevenue().multiply(BigDecimal.valueOf(0.65)));
            dto.calculateTotals();
        } catch (SQLException e) {
            logger.error("P&L error: {}", e.getMessage(), e);
        }
        return dto;
    }

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