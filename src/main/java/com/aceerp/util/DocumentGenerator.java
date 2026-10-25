package com.aceerp.util;

import com.aceerp.model.Quotation;
import com.aceerp.model.QuotationLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class DocumentGenerator {

    private static final Logger logger = LoggerFactory.getLogger(DocumentGenerator.class);

    public static String generateQuotationHTML(Quotation quote) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>Quotation ")
            .append(quote.getQuoteNumber()).append("</title>")
            .append("<style>")
            .append("body { font-family: Arial, sans-serif; margin: 30px; color: #333; }")
            .append(".header { display: flex; justify-content: space-between; border-bottom: 2px solid #2c3e50; padding-bottom: 15px; }")
            .append("table { width: 100%; border-collapse: collapse; margin-top: 20px; }")
            .append("th, td { border: 1px solid #ddd; padding: 10px; text-align: left; }")
            .append("th { background-color: #f4f6f7; }")
            .append(".total { text-align: right; font-weight: bold; font-size: 16px; margin-top: 15px; }")
            .append("</style></head><body>");

        html.append("<div class='header'>")
            .append("<div><h2>ACE AIR CONDITIONING</h2><p>Sales Quotation</p></div>")
            .append("<div><h3>Quote #: ").append(quote.getQuoteNumber()).append("</h3>")
            .append("<p>Date: ").append(quote.getQuoteDate()).append("</p></div></div>");

        html.append("<div style='margin-top: 20px;'><strong>Customer:</strong> ")
            .append(quote.getCustomerName() != null ? quote.getCustomerName() : "N/A").append("</div>");

        html.append("<table><thead><tr><th>Product / Description</th><th>Qty</th><th>Unit Price</th><th>Total</th></tr></thead><tbody>");
        for (QuotationLine line : quote.getLines()) {
            html.append("<tr>")
                .append("<td>").append(line.getDescription() != null ? line.getDescription() : "").append("</td>")
                .append("<td>").append(line.getQuantity()).append("</td>")
                .append("<td>$").append(String.format("%.2f", line.getUnitPrice())).append("</td>")
                .append("<td>$").append(String.format("%.2f", line.getTotalPrice())).append("</td>")
                .append("</tr>");
        }
        html.append("</tbody></table>");

        html.append("<div class='total'>Total Amount: $").append(String.format("%.2f", quote.getTotalAmount())).append("</div>");
        html.append("</body></html>");
        return html.toString();
    }

    public static void generateQuotationPDF(Quotation quotation) {
        try {
            File file = File.createTempFile("Quotation_" + quotation.getQuoteNumber(), ".html");
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(generateQuotationHTML(quotation));
            }
            openFileInBrowser(file);
        } catch (IOException e) {
            logger.error("Failed to generate document for quote {}", quotation.getQuoteNumber(), e);
        }
    }

    private static void openFileInBrowser(File file) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("cmd.exe", "/c", "start", "", file.getAbsolutePath()).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", file.getAbsolutePath()).start();
            } else {
                new ProcessBuilder("xdg-open", file.getAbsolutePath()).start();
            }
        } catch (Exception e) {
            logger.error("Could not open file in browser: {}", file.getAbsolutePath(), e);
        }
    }
}