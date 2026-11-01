package com.aceerp.controller;

import com.aceerp.model.CompanyProfile;
import com.aceerp.service.CompanyProfileService;
import com.aceerp.service.ReportTemplateService;
import com.aceerp.service.SettingsService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

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

    // ---- Report Visual Style ----
    @FXML private TextField txtStyleHeaderColor;
    @FXML private TextField txtStyleAccentColor;
    @FXML private TextField txtStyleTextColor;
    @FXML private ColorPicker cpHeaderColor;
    @FXML private ColorPicker cpAccentColor;
    @FXML private ColorPicker cpTextColor;
    @FXML private TextField txtMarginTop;
    @FXML private TextField txtMarginBottom;
    @FXML private TextField txtMarginLeft;
    @FXML private TextField txtMarginRight;
    @FXML private TextField txtTitleFontSize;
    @FXML private TextField txtBodyFontSize;
    @FXML private CheckBox chkShowLogo;
    @FXML private CheckBox chkShowTagline;
    @FXML private CheckBox chkShowTaxNo;
    @FXML private CheckBox chkShowWebsite;
    @FXML private CheckBox chkShowFooter;

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

    // ---- Interactive Drag & Layout Designer Controls ----
    @FXML private StackPane previewStackPane;
    @FXML private Pane overlayCanvas;
    @FXML private ComboBox<ReportTemplateService.ReportElementItem> cmbSelectedElement;
    @FXML private TextField txtElemX;
    @FXML private TextField txtElemY;
    @FXML private TextField txtElemW;
    @FXML private TextField txtElemH;

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
    private double dragStartX, dragStartY;
    private int elementStartX, elementStartY, elementStartW, elementStartH;

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
        initVisualStyleControls();
        initElementInspector();
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
        refreshReportTemplateList();
    }

    private void initElementInspector() {
        if (cmbSelectedElement == null) return;
        cmbSelectedElement.valueProperty().addListener((o, oldItem, newItem) -> {
            if (newItem != null) {
                if (txtElemX != null) txtElemX.setText(String.valueOf(newItem.x));
                if (txtElemY != null) txtElemY.setText(String.valueOf(newItem.y));
                if (txtElemW != null) txtElemW.setText(String.valueOf(newItem.width));
                if (txtElemH != null) txtElemH.setText(String.valueOf(newItem.height));
                highlightElementOnCanvas(newItem);
            }
        });
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
            handlePreviewEditorJrxml();
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
                    refreshOverlayCanvas();
                    if (statusLabel != null) {
                        statusLabel.setText("Preview & Layout ready.");
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

    // ---- Interactive Drag-and-Move Canvas Logic ----

    private void refreshOverlayCanvas() {
        if (overlayCanvas == null || txtJrxmlEditor == null || imgReportPreview == null || imgReportPreview.getImage() == null) {
            return;
        }
        overlayCanvas.getChildren().clear();

        String jrxml = txtJrxmlEditor.getText();
        if (jrxml == null || jrxml.isBlank()) return;

        List<ReportTemplateService.ReportElementItem> elements = reportTemplateService.parseReportElements(jrxml);
        if (cmbSelectedElement != null) {
            cmbSelectedElement.getItems().setAll(elements);
        }

        double imgWidth = imgReportPreview.getBoundsInParent().getWidth();
        if (imgWidth <= 0) imgWidth = 520;
        double scale = imgWidth / 595.0; // Standard JRXML A4 page width

        overlayCanvas.setPrefSize(imgReportPreview.getBoundsInParent().getWidth(), imgReportPreview.getBoundsInParent().getHeight());

        for (ReportTemplateService.ReportElementItem item : elements) {
            Pane box = createDraggableBox(item, scale);
            overlayCanvas.getChildren().add(box);
        }
    }

    private Pane createDraggableBox(ReportTemplateService.ReportElementItem item, double scale) {
        Pane box = new Pane();
        double sx = item.x * scale;
        double sy = (item.bandOffsetY + item.y) * scale;
        double sw = item.width * scale;
        double sh = item.height * scale;

        box.setLayoutX(sx);
        box.setLayoutY(sy);
        box.setPrefSize(sw, sh);
        box.setStyle("-fx-border-color: #2563eb; -fx-border-style: dashed; -fx-border-width: 1.5; -fx-background-color: rgba(59, 130, 246, 0.12);");

        Label label = new Label(item.label);
        label.setStyle("-fx-font-size: 10px; -fx-text-fill: #1e3a8a; -fx-font-weight: bold; -fx-padding: 2;");
        box.getChildren().add(label);

        // Resize handle at bottom-right
        Rectangle handle = new Rectangle(8, 8, Color.web("#2563eb"));
        handle.setX(sw - 8);
        handle.setY(sh - 8);
        box.getChildren().add(handle);

        // Drag Handler
        box.setOnMousePressed(e -> {
            dragStartX = e.getSceneX();
            dragStartY = e.getSceneY();
            elementStartX = item.x;
            elementStartY = item.y;
            elementStartW = item.width;
            elementStartH = item.height;
            if (cmbSelectedElement != null) {
                cmbSelectedElement.setValue(item);
            }
            highlightElementOnCanvas(item);
            e.consume();
        });

        box.setOnMouseDragged(e -> {
            double dx = (e.getSceneX() - dragStartX) / scale;
            double dy = (e.getSceneY() - dragStartY) / scale;

            int newX = Math.max(0, elementStartX + (int) Math.round(dx));
            int newY = Math.max(0, elementStartY + (int) Math.round(dy));

            item.x = newX;
            item.y = newY;

            box.setLayoutX(newX * scale);
            box.setLayoutY((item.bandOffsetY + newY) * scale);

            if (txtElemX != null) txtElemX.setText(String.valueOf(newX));
            if (txtElemY != null) txtElemY.setText(String.valueOf(newY));
            e.consume();
        });

        box.setOnMouseReleased(e -> {
            applyUpdatedBoundsToEditor(item);
            e.consume();
        });

        // Resize Handler
        handle.setOnMouseDragged(e -> {
            double dw = (e.getSceneX() - dragStartX) / scale;
            double dh = (e.getSceneY() - dragStartY) / scale;

            int newW = Math.max(10, elementStartW + (int) Math.round(dw));
            int newH = Math.max(10, elementStartH + (int) Math.round(dh));

            item.width = newW;
            item.height = newH;

            box.setPrefSize(newW * scale, newH * scale);
            handle.setX((newW * scale) - 8);
            handle.setY((newH * scale) - 8);

            if (txtElemW != null) txtElemW.setText(String.valueOf(newW));
            if (txtElemH != null) txtElemH.setText(String.valueOf(newH));
            e.consume();
        });

        handle.setOnMouseReleased(e -> {
            applyUpdatedBoundsToEditor(item);
            e.consume();
        });

        return box;
    }

    private void highlightElementOnCanvas(ReportTemplateService.ReportElementItem item) {
        if (overlayCanvas == null) return;
        for (javafx.scene.Node node : overlayCanvas.getChildren()) {
            if (node instanceof Pane p) {
                p.setStyle("-fx-border-color: #2563eb; -fx-border-style: dashed; -fx-border-width: 1.5; -fx-background-color: rgba(59, 130, 246, 0.12);");
            }
        }
    }

    private void applyUpdatedBoundsToEditor(ReportTemplateService.ReportElementItem item) {
        try {
            String jrxml = txtJrxmlEditor.getText();
            String updated = reportTemplateService.updateElementBounds(jrxml, item.key, item.x, item.y, item.width, item.height);
            txtJrxmlEditor.setText(updated);
            handlePreviewEditorJrxml();
        } catch (Exception e) {
            logger.error("Update element bounds failed: {}", e.getMessage(), e);
        }
    }

    @FXML
    private void handleNudgeUp() { nudgeElement(0, -1); }

    @FXML
    private void handleNudgeDown() { nudgeElement(0, 1); }

    @FXML
    private void handleNudgeLeft() { nudgeElement(-1, 0); }

    @FXML
    private void handleNudgeRight() { nudgeElement(1, 0); }

    private void nudgeElement(int dx, int dy) {
        ReportTemplateService.ReportElementItem sel = cmbSelectedElement != null ? cmbSelectedElement.getValue() : null;
        if (sel == null) return;
        sel.x = Math.max(0, sel.x + dx);
        sel.y = Math.max(0, sel.y + dy);
        if (txtElemX != null) txtElemX.setText(String.valueOf(sel.x));
        if (txtElemY != null) txtElemY.setText(String.valueOf(sel.y));
        applyUpdatedBoundsToEditor(sel);
    }

    @FXML
    private void handleApplyElementBounds() {
        ReportTemplateService.ReportElementItem sel = cmbSelectedElement != null ? cmbSelectedElement.getValue() : null;
        if (sel == null) return;
        sel.x = parseIntOr(txtElemX, sel.x);
        sel.y = parseIntOr(txtElemY, sel.y);
        sel.width = parseIntOr(txtElemW, sel.width);
        sel.height = parseIntOr(txtElemH, sel.height);
        applyUpdatedBoundsToEditor(sel);
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

    private void initVisualStyleControls() {
        setColorPair(cpHeaderColor, txtStyleHeaderColor, Color.web("#1e40af"));
        setColorPair(cpAccentColor, txtStyleAccentColor, Color.web("#3b82f6"));
        setColorPair(cpTextColor, txtStyleTextColor, Color.web("#111827"));

        if (txtMarginTop != null) txtMarginTop.setText("40");
        if (txtMarginBottom != null) txtMarginBottom.setText("40");
        if (txtMarginLeft != null) txtMarginLeft.setText("40");
        if (txtMarginRight != null) txtMarginRight.setText("40");
        if (txtTitleFontSize != null) txtTitleFontSize.setText("16");
        if (txtBodyFontSize != null) txtBodyFontSize.setText("10");
    }

    private void setColorPair(ColorPicker picker, TextField field, Color color) {
        if (picker == null || field == null) return;
        picker.setValue(color);
        field.setText(toHex(color));
        picker.valueProperty().addListener((o, a, b) -> {
            if (b != null) field.setText(toHex(b));
        });
        field.focusedProperty().addListener((o, was, isNow) -> {
            if (!isNow) {
                try {
                    picker.setValue(Color.web(field.getText().trim()));
                } catch (Exception ignored) {
                    field.setText(toHex(picker.getValue()));
                }
            }
        });
    }

    private static String toHex(Color c) {
        if (c == null) return "#000000";
        return String.format("#%02x%02x%02x",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }

    @FXML
    private void handleResetVisualStyle() {
        initVisualStyleControls();
        if (chkShowLogo != null) chkShowLogo.setSelected(true);
        if (chkShowTagline != null) chkShowTagline.setSelected(true);
        if (chkShowTaxNo != null) chkShowTaxNo.setSelected(true);
        if (chkShowWebsite != null) chkShowWebsite.setSelected(true);
        if (chkShowFooter != null) chkShowFooter.setSelected(true);
        if (statusLabel != null) {
            statusLabel.setText("Visual style defaults restored.");
            statusLabel.setStyle("-fx-text-fill: #15803d;");
        }
    }

    @FXML
    private void handleApplyVisualStyle() {
        String jrxml = txtJrxmlEditor != null ? txtJrxmlEditor.getText() : null;
        if (jrxml == null || jrxml.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Visual Style",
                    "Load a template into the editor first, then apply styles.");
            return;
        }
        try {
            ReportTemplateService.VisualStyle style = buildVisualStyleFromUi();
            String patched = reportTemplateService.applyVisualStyle(jrxml, style);
            txtJrxmlEditor.setText(patched);
            handlePreviewEditorJrxml();
            if (statusLabel != null) {
                statusLabel.setText("Style applied to editor. Preview or Save As New Template.");
                statusLabel.setStyle("-fx-text-fill: #15803d;");
            }
        } catch (Exception e) {
            logger.error("Apply visual style failed: {}", e.getMessage(), e);
            showAlert(Alert.AlertType.ERROR, "Style Failed", e.getMessage());
        }
    }

    private ReportTemplateService.VisualStyle buildVisualStyleFromUi() {
        ReportTemplateService.VisualStyle s = new ReportTemplateService.VisualStyle();
        s.headerColor = safeColorText(txtStyleHeaderColor, "#1e40af");
        s.accentColor = safeColorText(txtStyleAccentColor, "#3b82f6");
        s.textColor = safeColorText(txtStyleTextColor, "#111827");
        s.marginTop = parseIntOr(txtMarginTop, 40);
        s.marginBottom = parseIntOr(txtMarginBottom, 40);
        s.marginLeft = parseIntOr(txtMarginLeft, 40);
        s.marginRight = parseIntOr(txtMarginRight, 40);
        s.titleFontSize = parseIntOr(txtTitleFontSize, 16);
        s.bodyFontSize = parseIntOr(txtBodyFontSize, 10);
        s.showLogo = chkShowLogo == null || chkShowLogo.isSelected();
        s.showTagline = chkShowTagline == null || chkShowTagline.isSelected();
        s.showTaxNo = chkShowTaxNo == null || chkShowTaxNo.isSelected();
        s.showWebsite = chkShowWebsite == null || chkShowWebsite.isSelected();
        s.showFooter = chkShowFooter == null || chkShowFooter.isSelected();
        return s;
    }

    private static String safeColorText(TextField f, String fallback) {
        if (f == null || f.getText() == null || f.getText().isBlank()) return fallback;
        String t = f.getText().trim();
        return t.startsWith("#") ? t : "#" + t;
    }

    private static int parseIntOr(TextField f, int fallback) {
        if (f == null || f.getText() == null || f.getText().isBlank()) return fallback;
        try {
            return Integer.parseInt(f.getText().trim());
        } catch (NumberFormatException e) {
            return fallback;
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