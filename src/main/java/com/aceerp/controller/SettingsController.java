package com.aceerp.controller;

import com.aceerp.model.CompanyProfile;
import com.aceerp.service.CompanyProfileService;
import com.aceerp.service.SettingsService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aceerp.service.ReportTemplateService;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.Region;

import java.awt.Desktop;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;


import java.io.File;

public class SettingsController {

    private static final Logger logger = LoggerFactory.getLogger(SettingsController.class);

    // ---- Status Label ----
    @FXML private Label statusLabel;

    // ---- Company Branding & Profile ----
    @FXML private TextField txtCompanyName;
    @FXML private TextField txtTagline;
    @FXML private TextField txtAddress1;
    @FXML private TextField txtAddress2;
    @FXML private TextField txtCity;
    @FXML private TextField txtState;
    @FXML private TextField txtPostal;
    @FXML private TextField txtCountry;
    @FXML private TextField txtPhone;
    @FXML private TextField txtEmail;
    @FXML private TextField txtWebsite;
    @FXML private TextField txtTaxNumber;
    @FXML private TextArea txtInvoiceFooter;
    @FXML private ComboBox<String> cmbPrintTemplate;
    @FXML private ImageView imgLogoPreview;
    @FXML private Label lblLogoPath;

    // ---- Currency Settings ----
    @FXML private TextField currencyCodeField;
    @FXML private TextField currencySymbolField;
    @FXML private TextField currencyDecimalsField;
    @FXML private ChoiceBox<String> currencyPositionChoice;

        // ---- Report Design ----
    @FXML private ComboBox<String> cmbReportDocType;
    @FXML private ComboBox<String> cmbReportTemplate;
    @FXML private TextField txtPreviewDocId;
    @FXML private Label lblActiveReportTemplate;
    @FXML private TextArea txtJrxmlEditor;
    @FXML private ImageView imgReportPreview;


    

    // ---- Invoice & Numbering Settings ----
    @FXML private TextField invoicePrefixField;
    @FXML private TextField invoiceNextNumberField;
    @FXML private TextField invoiceDueDaysField;
    @FXML private TextArea invoiceTermsField;

    @FXML private TextField quotationPrefixField;
    @FXML private TextField quotationNextNumberField;

    @FXML private TextField purchasePrefixField;
    @FXML private TextField purchaseNextNumberField;

    // ---- Tax Settings ----
    @FXML private CheckBox taxEnabledCheck;
    @FXML private TextField taxNameField;
    @FXML private TextField taxDefaultRateField;

    // ---- System Settings ----
    @FXML private TextField dateFormatField;
    @FXML private TextField itemsPerPageField;

    private final CompanyProfileService companyService = new CompanyProfileService();
    private final SettingsService settingsService = SettingsService.getInstance();
    private final ReportTemplateService reportTemplateService = new ReportTemplateService();



    private CompanyProfile currentProfile;
    private String pendingLogoPath;

    @FXML
    public void initialize() {
        if (cmbPrintTemplate != null) {
            cmbPrintTemplate.getItems().setAll("CLASSIC", "MODERN", "PROFESSIONAL");
        }
        if (currencyPositionChoice != null) {
            currencyPositionChoice.getItems().setAll("BEFORE", "AFTER");
        }

        loadAllSettings();
        initReportDesignTab();
    }

    @FXML
    private void onReload() {
        loadAllSettings();
        if (statusLabel != null) {
            statusLabel.setText("Settings reloaded.");
            statusLabel.setStyle("-fx-text-fill: #15803d;");
        }
    }

    private void loadAllSettings() {
        loadCompanyProfile();
        loadKeyValueSettings();
    }

    private void loadCompanyProfile() {
        currentProfile = companyService.getProfile();
        pendingLogoPath = currentProfile.getLogoPath();

        if (txtCompanyName != null) txtCompanyName.setText(n(currentProfile.getCompanyName()));
        if (txtTagline != null) txtTagline.setText(n(currentProfile.getTagline()));
        if (txtAddress1 != null) txtAddress1.setText(n(currentProfile.getAddressLine1()));
        if (txtAddress2 != null) txtAddress2.setText(n(currentProfile.getAddressLine2()));
        if (txtCity != null) txtCity.setText(n(currentProfile.getCity()));
        if (txtState != null) txtState.setText(n(currentProfile.getState()));
        if (txtPostal != null) txtPostal.setText(n(currentProfile.getPostalCode()));
        if (txtCountry != null) txtCountry.setText(n(currentProfile.getCountry()));
        if (txtPhone != null) txtPhone.setText(n(currentProfile.getPhone()));
        if (txtEmail != null) txtEmail.setText(n(currentProfile.getEmail()));
        if (txtWebsite != null) txtWebsite.setText(n(currentProfile.getWebsite()));
        if (txtTaxNumber != null) txtTaxNumber.setText(n(currentProfile.getTaxNumber()));
        if (txtInvoiceFooter != null) txtInvoiceFooter.setText(n(currentProfile.getInvoiceFooter()));

        if (cmbPrintTemplate != null) {
            String activeTheme = currentProfile.getDefaultPrintTemplate();
            if (activeTheme == null || activeTheme.isBlank()) {
                activeTheme = settingsService.getSetting("PRINT_THEME", "CLASSIC");
            }
            cmbPrintTemplate.setValue(activeTheme.toUpperCase());
        }
        if (lblLogoPath != null) {
            lblLogoPath.setText(pendingLogoPath != null && !pendingLogoPath.isBlank() ? pendingLogoPath : "No logo uploaded");
        }
        refreshLogoPreview(pendingLogoPath);
    }

    private void loadKeyValueSettings() {
        if (currencyCodeField != null) currencyCodeField.setText(settingsService.getSetting("currency_code", "USD"));
        if (currencySymbolField != null) currencySymbolField.setText(settingsService.getSetting("currency_symbol", "$"));
        if (currencyDecimalsField != null) currencyDecimalsField.setText(settingsService.getSetting("currency_decimals", "2"));
        if (currencyPositionChoice != null) currencyPositionChoice.setValue(settingsService.getSetting("currency_position", "BEFORE"));

        if (invoicePrefixField != null) invoicePrefixField.setText(settingsService.getSetting("invoice_prefix", "INV-"));
        if (invoiceNextNumberField != null) invoiceNextNumberField.setText(settingsService.getSetting("invoice_next_number", "1001"));
        if (invoiceDueDaysField != null) invoiceDueDaysField.setText(settingsService.getSetting("invoice_due_days", "30"));
        if (invoiceTermsField != null) invoiceTermsField.setText(settingsService.getSetting("invoice_terms", "Payment due in 30 days."));

        if (quotationPrefixField != null) quotationPrefixField.setText(settingsService.getSetting("quotation_prefix", "QTN-"));
        if (quotationNextNumberField != null) quotationNextNumberField.setText(settingsService.getSetting("quotation_next_number", "1001"));

        if (purchasePrefixField != null) purchasePrefixField.setText(settingsService.getSetting("po_prefix", "PO-"));
        if (purchaseNextNumberField != null) purchaseNextNumberField.setText(settingsService.getSetting("po_next_number", "1001"));

        if (taxEnabledCheck != null) taxEnabledCheck.setSelected("true".equalsIgnoreCase(settingsService.getSetting("tax_enabled", "true")));
        if (taxNameField != null) taxNameField.setText(settingsService.getSetting("tax_name", "VAT"));
        if (taxDefaultRateField != null) taxDefaultRateField.setText(settingsService.getSetting("tax_default_rate", "18.00"));

        if (dateFormatField != null) dateFormatField.setText(settingsService.getSetting("date_format", "yyyy-MM-dd"));
        if (itemsPerPageField != null) itemsPerPageField.setText(settingsService.getSetting("items_per_page", "25"));
    }

    private void initReportDesignTab() {
    if (cmbReportDocType == null) return;

    cmbReportDocType.getItems().setAll(
            "invoice",
            "quotation",
            "sales_order",
            "delivery_note",
            "purchase_order",
            "job_note_a5",
            "supplier_bill"
    );
    cmbReportDocType.setValue("invoice");

    cmbReportDocType.valueProperty().addListener((o, a, b) -> refreshReportTemplateList());
    cmbReportTemplate.valueProperty().addListener((o, a, b) -> {
        // optional: auto-load on change — keep manual via button to avoid slow IO
    });

    refreshReportTemplateList();
}

private void refreshReportTemplateList() {
    if (cmbReportDocType == null || cmbReportTemplate == null) return;
    String doc = cmbReportDocType.getValue();
    if (doc == null) return;

    List<String> keys = reportTemplateService.listTemplatesFor(doc);
    cmbReportTemplate.getItems().setAll(keys);

    String active = reportTemplateService.getActiveTemplateKey(doc);
    if (lblActiveReportTemplate != null) {
        lblActiveReportTemplate.setText(active);
    }
    if (keys.contains(active)) {
        cmbReportTemplate.setValue(active);
    } else if (!keys.isEmpty()) {
        cmbReportTemplate.setValue(keys.getFirst());
    }
}

@FXML
private void handleSetActiveReportTemplate() {
    String doc = cmbReportDocType.getValue();
    String tpl = cmbReportTemplate.getValue();
    if (doc == null || tpl == null) {
        showAlert(Alert.AlertType.WARNING, "Report Design", "Select document type and template.");
        return;
    }
    reportTemplateService.setActiveTemplateKey(doc, tpl);
    if (lblActiveReportTemplate != null) lblActiveReportTemplate.setText(tpl);
    if (statusLabel != null) {
        statusLabel.setText("Active template for " + doc + " → " + tpl);
        statusLabel.setStyle("-fx-text-fill: #15803d;");
    }
    showAlert(Alert.AlertType.INFORMATION, "Active Template",
            "Prints for " + doc + " will use: " + tpl);
}

@FXML
private void handleLoadJrxmlEditor() {
    String doc = cmbReportDocType.getValue();
    String tpl = cmbReportTemplate.getValue();
    if (doc == null || tpl == null) {
        showAlert(Alert.AlertType.WARNING, "Report Design", "Select document type and template.");
        return;
    }
    try {
        String jrxml = reportTemplateService.loadJrxmlText(doc, tpl);
        txtJrxmlEditor.setText(jrxml);
        if (reportTemplateService.isBuiltin(tpl)) {
            showAlert(Alert.AlertType.INFORMATION, "Read-only source",
                    "Loaded built-in '" + tpl + "'.\n\nUse Save As New Template to create a custom copy. Built-ins are never overwritten.");
        }
    } catch (Exception e) {
        logger.error("Load JRXML failed: {}", e.getMessage(), e);
        showAlert(Alert.AlertType.ERROR, "Load Failed", e.getMessage());
    }
}

@FXML
private void handleSaveAsNewReportTemplate() {
    String doc = cmbReportDocType.getValue();
    if (doc == null) {
        showAlert(Alert.AlertType.WARNING, "Report Design", "Select a document type.");
        return;
    }
    String jrxml = txtJrxmlEditor != null ? txtJrxmlEditor.getText() : null;
    if (jrxml == null || jrxml.isBlank()) {
        showAlert(Alert.AlertType.WARNING, "Report Design", "Editor is empty. Load a template first.");
        return;
    }

    TextInputDialog dialog = new TextInputDialog("MyCustom");
    dialog.setTitle("Save As New Template");
    dialog.setHeaderText("Create a NEW custom template (built-ins stay unchanged)");
    dialog.setContentText("Template name:");
    Optional<String> nameOpt = dialog.showAndWait();
    if (nameOpt.isEmpty() || nameOpt.get().isBlank()) return;

    try {
        String key = reportTemplateService.saveAsNewCustomTemplate(doc, nameOpt.get().trim(), jrxml);
        refreshReportTemplateList();
        cmbReportTemplate.setValue(key);
        showAlert(Alert.AlertType.INFORMATION, "Saved",
                "New template created: " + key + "\n\nClick Set Active if you want prints to use it.");
    } catch (Exception e) {
        logger.error("Save custom template failed: {}", e.getMessage(), e);
        showAlert(Alert.AlertType.ERROR, "Save Failed", e.getMessage());
    }
}

@FXML
private void handlePreviewReportTemplate() {
    String doc = cmbReportDocType.getValue();
    String tpl = cmbReportTemplate.getValue();
    if (doc == null || tpl == null) {
        showAlert(Alert.AlertType.WARNING, "Preview", "Select document type and template.");
        return;
    }
    runPreview(() -> reportTemplateService.renderPreviewPng(doc, tpl, parsePreviewId(), 1.6f));
}

@FXML
private void handlePreviewEditorJrxml() {
    String doc = cmbReportDocType.getValue();
    String jrxml = txtJrxmlEditor != null ? txtJrxmlEditor.getText() : null;
    if (doc == null || jrxml == null || jrxml.isBlank()) {
        showAlert(Alert.AlertType.WARNING, "Preview", "Select document type and put JRXML in the editor.");
        return;
    }
    runPreview(() -> reportTemplateService.renderPreviewPngFromText(doc, jrxml, parsePreviewId(), 1.6f));
}

private Integer parsePreviewId() {
    if (txtPreviewDocId == null || txtPreviewDocId.getText() == null || txtPreviewDocId.getText().isBlank()) {
        return null;
    }
    try {
        return Integer.parseInt(txtPreviewDocId.getText().trim());
    } catch (NumberFormatException e) {
        return null;
    }
}

private void runPreview(PreviewSupplier supplier) {
    if (statusLabel != null) {
        statusLabel.setText("Generating preview…");
        statusLabel.setStyle("-fx-text-fill: #2563eb;");
    }
    new Thread(() -> {
        try {
            byte[] png = supplier.get();
            Platform.runLater(() -> {
                imgReportPreview.setImage(new Image(new ByteArrayInputStream(png)));
                if (statusLabel != null) {
                    statusLabel.setText("Preview ready.");
                    statusLabel.setStyle("-fx-text-fill: #15803d;");
                }
            });
        } catch (Exception e) {
            logger.error("Preview failed: {}", e.getMessage(), e);
            Platform.runLater(() -> {
                if (statusLabel != null) {
                    statusLabel.setText("Preview failed: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: #dc2626;");
                }
                showAlert(Alert.AlertType.ERROR, "Preview Failed", e.getMessage());
            });
        }
    }, "report-preview").start();
}

@FunctionalInterface
private interface PreviewSupplier {
    byte[] get() throws Exception;
}

@FXML
private void handleOpenTemplatesFolder() {
    try {
        Path root = reportTemplateService.getCustomRoot();
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(root.toFile());
        } else {
            showAlert(Alert.AlertType.INFORMATION, "Templates Folder", root.toAbsolutePath().toString());
        }
    } catch (Exception e) {
        showAlert(Alert.AlertType.ERROR, "Folder", e.getMessage());
    }
}

    private void refreshLogoPreview(String logoPath) {
        if (imgLogoPreview == null) return;
        try {
            String abs = companyService.resolveLogoAbsolutePath(logoPath);
            if (abs != null) {
                imgLogoPreview.setImage(new Image("file:///" + abs, 180, 90, true, true));
            } else {
                imgLogoPreview.setImage(null);
            }
        } catch (Exception e) {
            imgLogoPreview.setImage(null);
            logger.warn("Logo preview failed: {}", e.getMessage());
        }
    }

    @FXML
    private void handleUploadLogo() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Company Logo");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp"),
                new FileChooser.ExtensionFilter("PNG", "*.png"),
                new FileChooser.ExtensionFilter("JPEG", "*.jpg", "*.jpeg")
        );
        File file = chooser.showOpenDialog(txtCompanyName.getScene().getWindow());
        if (file == null) return;

        try {
            pendingLogoPath = companyService.uploadLogo(file.toPath());
            if (lblLogoPath != null) lblLogoPath.setText(pendingLogoPath);
            refreshLogoPreview(pendingLogoPath);
            showAlert(Alert.AlertType.INFORMATION, "Logo Ready",
                    "Logo uploaded. Click Save Changes to apply it on prints.");
        } catch (Exception e) {
            logger.error("Logo upload failed: {}", e.getMessage(), e);
            showAlert(Alert.AlertType.ERROR, "Upload Failed", e.getMessage());
        }
    }

    @FXML
    private void handleRemoveLogo() {
        pendingLogoPath = null;
        if (lblLogoPath != null) lblLogoPath.setText("No logo uploaded");
        if (imgLogoPreview != null) imgLogoPreview.setImage(null);
    }

    @FXML
    private void onSave() {
        if (txtCompanyName != null && (txtCompanyName.getText() == null || txtCompanyName.getText().isBlank())) {
            showAlert(Alert.AlertType.WARNING, "Validation", "Company name is required.");
            return;
        }

        // 1. Save Company Profile & Theme
        if (currentProfile == null) {
            currentProfile = new CompanyProfile();
        }
        if (txtCompanyName != null) currentProfile.setCompanyName(txtCompanyName.getText().trim());
        if (txtTagline != null) currentProfile.setTagline(val(txtTagline));
        if (txtAddress1 != null) currentProfile.setAddressLine1(val(txtAddress1));
        if (txtAddress2 != null) currentProfile.setAddressLine2(val(txtAddress2));
        if (txtCity != null) currentProfile.setCity(val(txtCity));
        if (txtState != null) currentProfile.setState(val(txtState));
        if (txtPostal != null) currentProfile.setPostalCode(val(txtPostal));
        if (txtCountry != null) currentProfile.setCountry(val(txtCountry));
        if (txtPhone != null) currentProfile.setPhone(val(txtPhone));
        if (txtEmail != null) currentProfile.setEmail(val(txtEmail));
        if (txtWebsite != null) currentProfile.setWebsite(val(txtWebsite));
        if (txtTaxNumber != null) currentProfile.setTaxNumber(val(txtTaxNumber));
        if (txtInvoiceFooter != null) currentProfile.setInvoiceFooter(txtInvoiceFooter.getText());
        currentProfile.setLogoPath(pendingLogoPath);
        
        if (cmbPrintTemplate != null && cmbPrintTemplate.getValue() != null) {
            String selectedTheme = cmbPrintTemplate.getValue();
            currentProfile.setDefaultPrintTemplate(selectedTheme);
            settingsService.setSetting("PRINT_THEME", selectedTheme);
        }

        boolean companySaved = companyService.saveProfile(currentProfile);

        // 2. Save Key-Value Settings
        saveKeyValueSettings();

        if (companySaved) {
            if (statusLabel != null) {
                statusLabel.setText("All settings saved successfully!");
                statusLabel.setStyle("-fx-text-fill: #15803d;");
            }
            showAlert(Alert.AlertType.INFORMATION, "Saved",
                    "Settings and Company Profile saved successfully.");
        } else {
            if (statusLabel != null) {
                statusLabel.setText("Error saving company profile.");
                statusLabel.setStyle("-fx-text-fill: #dc2626;");
            }
            showAlert(Alert.AlertType.ERROR, "Error", "Could not save company profile.");
        }
    }

    private void saveKeyValueSettings() {
        if (currencyCodeField != null) settingsService.setSetting("currency_code", currencyCodeField.getText().trim());
        if (currencySymbolField != null) settingsService.setSetting("currency_symbol", currencySymbolField.getText().trim());
        if (currencyDecimalsField != null) settingsService.setSetting("currency_decimals", currencyDecimalsField.getText().trim());
        if (currencyPositionChoice != null && currencyPositionChoice.getValue() != null) {
            settingsService.setSetting("currency_position", currencyPositionChoice.getValue());
        }

        if (invoicePrefixField != null) settingsService.setSetting("invoice_prefix", invoicePrefixField.getText().trim());
        if (invoiceNextNumberField != null) settingsService.setSetting("invoice_next_number", invoiceNextNumberField.getText().trim());
        if (invoiceDueDaysField != null) settingsService.setSetting("invoice_due_days", invoiceDueDaysField.getText().trim());
        if (invoiceTermsField != null) settingsService.setSetting("invoice_terms", invoiceTermsField.getText());

        if (quotationPrefixField != null) settingsService.setSetting("quotation_prefix", quotationPrefixField.getText().trim());
        if (quotationNextNumberField != null) settingsService.setSetting("quotation_next_number", quotationNextNumberField.getText().trim());

        if (purchasePrefixField != null) settingsService.setSetting("po_prefix", purchasePrefixField.getText().trim());
        if (purchaseNextNumberField != null) settingsService.setSetting("po_next_number", purchaseNextNumberField.getText().trim());

        if (taxEnabledCheck != null) settingsService.setSetting("tax_enabled", String.valueOf(taxEnabledCheck.isSelected()));
        if (taxNameField != null) settingsService.setSetting("tax_name", taxNameField.getText().trim());
        if (taxDefaultRateField != null) settingsService.setSetting("tax_default_rate", taxDefaultRateField.getText().trim());

        if (dateFormatField != null) settingsService.setSetting("date_format", dateFormatField.getText().trim());
        if (itemsPerPageField != null) settingsService.setSetting("items_per_page", itemsPerPageField.getText().trim());
    }

    private String n(String s) { return s == null ? "" : s; }
    private String val(TextField t) {
        return t == null || t.getText() == null ? null : t.getText().trim();
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(content);
        a.showAndWait();
    }

    
}