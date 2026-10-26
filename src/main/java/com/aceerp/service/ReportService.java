package com.aceerp.service;

import com.aceerp.config.DatabaseConfig;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.view.JasperViewer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

public class ReportService {
    
    private static final Logger logger = LoggerFactory.getLogger(ReportService.class);

    public static void printInvoice(int invoiceId) {
        // Run Jasper Viewer on a separate thread to prevent freezing the JavaFX UI
        new Thread(() -> {
            try (Connection conn = DatabaseConfig.getInstance().getConnection()) {
                
                // 1. Load the template
                InputStream stream = ReportService.class.getResourceAsStream("/reports/invoice.jrxml");
                if (stream == null) {
                    logger.error("Could not find invoice.jrxml in resources!");
                    return;
                }

                // 2. Compile the template
                JasperReport report = JasperCompileManager.compileReport(stream);

                // 3. Map parameters (matches $P{INVOICE_ID} in the SQL)
                Map<String, Object> parameters = new HashMap<>();
                parameters.put("INVOICE_ID", invoiceId);

                // 4. Fill the report with data from the database
                JasperPrint print = JasperFillManager.fillReport(report, parameters, conn);

                // 5. Show the Report Viewer window. 
                // false means "Dispose on Close" (don't exit the whole app when closing the print window)
                JasperViewer.viewReport(print, false);
                
            } catch (Exception e) {
                logger.error("Failed to generate JasperReport", e);
            }
        }).start();
    }
}