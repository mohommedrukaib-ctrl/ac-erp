package com.aceerp.util;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TemplateEditorDialog {

    private static final Logger logger = LoggerFactory.getLogger(TemplateEditorDialog.class);

    // Assuming running from IDE or source, map to the resources folder. 
    // If packaged as .exe, this path needs to point to an external template folder.
    private static final String TEMPLATE_DIR = "src/main/resources/reports/";

    public static void show() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("JRXML Template Editor");
        dialog.setHeaderText("Edit Report Layouts (Raw XML)");

        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.setPrefSize(800, 600);

        ComboBox<String> cbTemplates = new ComboBox<>();
        cbTemplates.getItems().addAll(
            "warranty_certificate.jrxml",
            "amc_renewal_notice.jrxml",
            "technician_payslip.jrxml",
            "invoice.jrxml"
        );
        cbTemplates.setPromptText("Select a Template to Edit");

        TextArea txtEditor = new TextArea();
        txtEditor.setFont(Font.font("Consolas", 12));
        txtEditor.setStyle("-fx-control-inner-background: #2b2b2b; -fx-text-fill: #a9b7c6;");
        VBox.setVgrow(txtEditor, Priority.ALWAYS);

        Button btnSave = new Button("💾 Save Changes");
        btnSave.setDisable(true);
        btnSave.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;");

        // Load File Logic
        cbTemplates.setOnAction(e -> {
            String selected = cbTemplates.getValue();
            if (selected != null) {
                try {
                    Path path = Paths.get(TEMPLATE_DIR + selected);
                    if (Files.exists(path)) {
                        txtEditor.setText(Files.readString(path));
                        btnSave.setDisable(false);
                    } else {
                        txtEditor.setText("<!-- File not found: " + path.toAbsolutePath() + " -->\n<!-- Please create it first. -->");
                        btnSave.setDisable(true);
                    }
                } catch (Exception ex) {
                    logger.error("Failed to load template", ex);
                    txtEditor.setText("Error reading file.");
                }
            }
        });

        // Save File Logic
        btnSave.setOnAction(e -> {
            String selected = cbTemplates.getValue();
            if (selected != null) {
                try {
                    Path path = Paths.get(TEMPLATE_DIR + selected);
                    Files.writeString(path, txtEditor.getText());
                    AlertHelper.info("Success", "Template saved successfully!\n" + path.toAbsolutePath());
                } catch (Exception ex) {
                    logger.error("Failed to save template", ex);
                    AlertHelper.error("Error", "Could not save template: " + ex.getMessage());
                }
            }
        });

        Label lblHelp = new Label("Tip: For visual drag-and-drop editing, use Jaspersoft Studio and paste the XML here.");
        lblHelp.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 11px;");

        content.getChildren().addAll(cbTemplates, txtEditor, btnSave, lblHelp);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        
        dialog.showAndWait();
    }
}