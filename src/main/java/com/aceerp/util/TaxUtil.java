package com.aceerp.util;

import com.aceerp.service.SettingsService;
import java.math.BigDecimal;

public class TaxUtil {

    /**
     * Reads the default tax rate directly from Settings cache.
     * Checks the settings UI key first, then legacy keys.
     */
    public static BigDecimal getDefaultTaxRate() {
        if (!isTaxEnabled()) {
            return BigDecimal.ZERO;
        }

        SettingsService settings = SettingsService.getInstance();
        
        String val = settings.get("tax_default_rate");
        if (val == null || val.isBlank()) {
            val = settings.get("tax.default_rate");
        }
        if (val == null || val.isBlank()) {
            val = settings.get("tax_rate");
        }
        if (val == null || val.isBlank()) {
            val = settings.get("tax.rate");
        }
        
        if (val != null && !val.isBlank()) {
            try {
                return new BigDecimal(val.trim());
            } catch (NumberFormatException ignored) {}
        }
        
        return new BigDecimal("18.00");
    }

    /**
     * Reads the settings UI key first, then its legacy equivalent.
     */
    public static boolean isTaxEnabled() {
        SettingsService settings = SettingsService.getInstance();
        String enabled = settings.get("tax_enabled");
        if (enabled == null || enabled.isBlank()) {
            enabled = settings.get("tax.enabled");
        }
        if (enabled == null || enabled.isBlank()) {
            return true;
        }
        return "true".equalsIgnoreCase(enabled) || "1".equals(enabled) || "yes".equalsIgnoreCase(enabled);
    }

    /**
     * Reads the tax label/name from Settings ("VAT", "TAX", etc.).
     */
    public static String getTaxName() {
        SettingsService settings = SettingsService.getInstance();
        String name = settings.get("tax_name");
        if (name == null || name.isBlank()) {
            name = settings.get("tax.name");
        }
        return (name != null && !name.isBlank()) ? name : "VAT";
    }
}