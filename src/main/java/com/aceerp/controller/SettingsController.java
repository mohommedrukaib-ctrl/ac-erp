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
            cmbPrintTemplate.setValue(
                    currentProfile.getDefaultPrintTemplate() != null
                            ? currentProfile.getDefaultPrintTemplate() : "CLASSIC");
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

        // 1. Save Company Profile
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
            currentProfile.setDefaultPrintTemplate(cmbPrintTemplate.getValue());
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