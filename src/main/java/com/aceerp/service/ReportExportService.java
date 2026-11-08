package com.aceerp.service;

import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

public class ReportExportService {

    private static final Logger logger = LoggerFactory.getLogger(ReportExportService.class);

    /**
     * Exports a TableView's current visible columns and items to CSV.
     */
    public <T> void exportTableViewToCsv(TableView<T> table, String reportTitle, File file) throws Exception {
        try (PrintWriter out = new PrintWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
            ObservableList<TableColumn<T, ?>> columns = table.getColumns();

            // Header
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < columns.size(); i++) {
                sb.append(escapeCsv(columns.get(i).getText()));
                if (i < columns.size() - 1) sb.append(",");
            }
            out.println(sb);

            // Rows
            for (T row : table.getItems()) {
                sb = new StringBuilder();
                for (int i = 0; i < columns.size(); i++) {
                    TableColumn<T, ?> col = columns.get(i);
                    Object cellValue = col.getCellData(row);
                    sb.append(escapeCsv(cellValue != null ? cellValue.toString() : ""));
                    if (i < columns.size() - 1) sb.append(",");
                }
                out.println(sb);
            }
            logger.info("Exported {} rows to CSV: {}", table.getItems().size(), file.getAbsolutePath());
        }
    }

    /**
     * Exports a TableView to Excel-compatible HTML Spreadsheet format.
     */
    public <T> void exportTableViewToExcelXml(TableView<T> table, String reportTitle, File file) throws Exception {
        try (PrintWriter out = new PrintWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
            out.println("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
            out.println("<style>");
            out.println("body { font-family: Arial, sans-serif; }");
            out.println("table { border-collapse: collapse; width: 100%; }");
            out.println("th { background-color: #2c3e50; color: white; padding: 8px; border: 1px solid #ddd; }");
            out.println("td { padding: 6px; border: 1px solid #ddd; text-align: left; }");
            out.println("tr:nth-child(even) { background-color: #f2f2f2; }");
            out.println(".title { font-size: 18px; font-weight: bold; margin-bottom: 12px; }");
            out.println("</style></head><body>");
            out.println("<div class='title'>" + escapeHtml(reportTitle) + "</div>");
            out.println("<table><thead><tr>");

            ObservableList<TableColumn<T, ?>> columns = table.getColumns();
            for (TableColumn<T, ?> col : columns) {
                out.println("<th>" + escapeHtml(col.getText()) + "</th>");
            }
            out.println("</tr></thead><tbody>");

            for (T row : table.getItems()) {
                out.println("<tr>");
                for (TableColumn<T, ?> col : columns) {
                    Object val = col.getCellData(row);
                    out.println("<td>" + escapeHtml(val != null ? val.toString() : "") + "</td>");
                }
                out.println("</tr>");
            }

            out.println("</tbody></table></body></html>");
            logger.info("Exported Excel XML report: {}", file.getAbsolutePath());
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        String clean = value.replace("\"", "\"\"");
        if (clean.contains(",") || clean.contains("\"") || clean.contains("\n")) {
            return "\"" + clean + "\"";
        }
        return clean;
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}