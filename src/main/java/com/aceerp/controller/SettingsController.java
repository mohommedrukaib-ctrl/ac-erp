package com.aceerp.controller;

import com.aceerp.service.SettingsService;
import com.aceerp.util.AlertHelper;
import com.aceerp.util.CurrencyFormatter;

import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class SettingsController {

    private static final Logger logger = LoggerFactory.getLogger(SettingsController.class);

    private final SettingsService settingsService = SettingsService.getInstance();

    // === Company tab ===
    @FXML private TextField companyNameField;
    @FXML private TextField companyLegalNameField;
    @FXML private TextArea  companyAddressField;
    @FXML private TextField companyPhoneField;
    @FXML private TextField companyEmailField;
    @FXML private TextField companyWebsiteField;
    @FXML private TextField companyTaxNumberField;
    @FXML private TextField companyRegistrationField;

    // === Currency tab ===
    @FXML private TextField currencyCodeField;
    @FXML private TextField currencySymbolField;
    @FXML private TextField currencyDecimalsField;
    @FXML private ChoiceBox<String> currencyPositionChoice;

    // === Invoice tab ===
    @FXML private TextField invoicePrefixField;
    @FXML private TextField invoiceNextNumberField;
    @FXML private TextField invoiceDueDaysField;
    @FXML private TextArea  invoiceTermsField;
    @FXML private TextField quotationPrefixField;
    @FXML private TextField quotationNextNumberField;
    @FXML private TextField purchasePrefixField;
    @FXML private TextField purchaseNextNumberField;

    // === Tax tab ===
    @FXML private CheckBox taxEnabledCheck;
    @FXML private TextField taxNameField;
    @FXML private TextField taxDefaultRateField;

    // === System tab ===
    @FXML private TextField dateFormatField;
    @FXML private TextField itemsPerPageField;

    // === Status ===
    @FXML private Label statusLabel;

    @FXML
    public void initialize() {
        currencyPositionChoice.getItems().addAll("BEFORE", "AFTER");
        loadSettings();
    }

    private void loadSettings() {
        companyNameField.setText(getOrEmpty("company.name"));
        companyLegalNameField.setText(getOrEmpty("company.legal_name"));
        companyAddressField.setText(getOrEmpty("company.address"));
        companyPhoneField.setText(getOrEmpty("company.phone"));
        companyEmailField.setText(getOrEmpty("company.email"));
        companyWebsiteField.setText(getOrEmpty("company.website"));
        companyTaxNumberField.setText(getOrEmpty("company.tax_number"));
        companyRegistrationField.setText(getOrEmpty("company.registration"));

        currencyCodeField.setText(getOrEmpty("currency.code"));
        currencySymbolField.setText(getOrEmpty("currency.symbol"));
        currencyDecimalsField.setText(getOrEmpty("currency.decimal_places"));
        currencyPositionChoice.setValue(settingsService.get("currency.symbol_position", "BEFORE"));

        invoicePrefixField.setText(getOrEmpty("invoice.prefix"));
        invoiceNextNumberField.setText(getOrEmpty("invoice.next_number"));
        invoiceDueDaysField.setText(getOrEmpty("invoice.default_due_days"));
        invoiceTermsField.setText(getOrEmpty("invoice.terms"));
        quotationPrefixField.setText(getOrEmpty("quotation.prefix"));
        quotationNextNumberField.setText(getOrEmpty("quotation.next_number"));
        purchasePrefixField.setText(getOrEmpty("purchase.prefix"));
        purchaseNextNumberField.setText(getOrEmpty("purchase.next_number"));

        taxEnabledCheck.setSelected(settingsService.getBool("tax.enabled", true));
        taxNameField.setText(getOrEmpty("tax.name"));
        taxDefaultRateField.setText(getOrEmpty("tax.default_rate"));

        dateFormatField.setText(getOrEmpty("system.date_format"));
        itemsPerPageField.setText(getOrEmpty("system.items_per_page"));

        statusLabel.setText("");
    }

    private String getOrEmpty(String key) {
        String v = settingsService.get(key);
        return v == null ? "" : v;
    }

    @FXML
    private void onSave() {
        try {
            Map<String, String> values = collectAndValidate();
            settingsService.saveAll(values);

            // Invalidate currency cache so new symbol displays everywhere immediately
            CurrencyFormatter.clearCache();

            statusLabel.setText("Settings saved successfully.");
            statusLabel.setStyle("-fx-text-fill: #27ae60;");
            logger.info("Settings saved by user.");

        } catch (IllegalArgumentException e) {
            statusLabel.setText("Validation error: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: #e74c3c;");
        } catch (SecurityException e) {
            AlertHelper.error("Access Denied", e.getMessage());
        } catch (Exception e) {
            logger.error("Settings save failed: {}", e.getMessage(), e);
            AlertHelper.error("Save Failed", "Could not save settings: " + e.getMessage());
        }
    }

    @FXML
    private void onReload() {
        settingsService.reload();
        CurrencyFormatter.clearCache();
        loadSettings();
        statusLabel.setText("Settings reloaded from database.");
        statusLabel.setStyle("-fx-text-fill: #2980b9;");
    }

    private Map<String, String> collectAndValidate() {
        Map<String, String> v = new HashMap<>();

        String companyName = companyNameField.getText();
        if (companyName == null || companyName.trim().isEmpty()) {
            throw new IllegalArgumentException("Business name is required.");
        }
        v.put("company.name",         companyName.trim());
        v.put("company.legal_name",   trimOrEmpty(companyLegalNameField.getText()));
        v.put("company.address",      trimOrEmpty(companyAddressField.getText()));
        v.put("company.phone",        trimOrEmpty(companyPhoneField.getText()));
        v.put("company.email",        trimOrEmpty(companyEmailField.getText()));
        v.put("company.website",      trimOrEmpty(companyWebsiteField.getText()));
        v.put("company.tax_number",   trimOrEmpty(companyTaxNumberField.getText()));
        v.put("company.registration", trimOrEmpty(companyRegistrationField.getText()));

        String code = currencyCodeField.getText();
        if (code == null || code.trim().length() != 3) {
            throw new IllegalArgumentException("Currency code must be exactly 3 letters.");
        }
        v.put("currency.code",   code.trim().toUpperCase());
        v.put("currency.symbol", requireNonEmpty(currencySymbolField.getText(), "Currency symbol"));

        int decimals = parseIntInRange(currencyDecimalsField.getText(), 0, 4, "Decimal places");
        v.put("currency.decimal_places", String.valueOf(decimals));
        v.put("currency.symbol_position", currencyPositionChoice.getValue());

        v.put("invoice.prefix",   trimOrEmpty(invoicePrefixField.getText()));
        v.put("invoice.next_number", String.valueOf(
            parseIntInRange(invoiceNextNumberField.getText(), 1, 999_999_999, "Invoice next number")));
        v.put("invoice.default_due_days", String.valueOf(
            parseIntInRange(invoiceDueDaysField.getText(), 0, 365, "Due days")));
        v.put("invoice.terms", trimOrEmpty(invoiceTermsField.getText()));

        v.put("quotation.prefix", trimOrEmpty(quotationPrefixField.getText()));
        v.put("quotation.next_number", String.valueOf(
            parseIntInRange(quotationNextNumberField.getText(), 1, 999_999_999, "Quotation next number")));

        v.put("purchase.prefix", trimOrEmpty(purchasePrefixField.getText()));
        v.put("purchase.next_number", String.valueOf(
            parseIntInRange(purchaseNextNumberField.getText(), 1, 999_999_999, "Purchase next number")));

        v.put("tax.enabled", String.valueOf(taxEnabledCheck.isSelected()));
        v.put("tax.name", trimOrEmpty(taxNameField.getText()));
        try {
            double rate = Double.parseDouble(taxDefaultRateField.getText().trim());
            if (rate < 0 || rate > 100) {
                throw new IllegalArgumentException("Tax rate must be between 0 and 100.");
            }
            v.put("tax.default_rate", String.format("%.2f", rate));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Tax rate must be a valid number.");
        }

        v.put("system.date_format", requireNonEmpty(dateFormatField.getText(), "Date format"));
        v.put("system.items_per_page", String.valueOf(
            parseIntInRange(itemsPerPageField.getText(), 10, 500, "Items per page")));

        return v;
    }

    private String trimOrEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private String requireNonEmpty(String s, String fieldName) {
        if (s == null || s.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return s.trim();
    }

    private int parseIntInRange(String s, int min, int max, String fieldName) {
        try {
            int val = Integer.parseInt(s.trim());
            if (val < min || val > max) {
                throw new IllegalArgumentException(fieldName + " must be between " + min + " and " + max + ".");
            }
            return val;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must be a valid number.");
        }
    }
}