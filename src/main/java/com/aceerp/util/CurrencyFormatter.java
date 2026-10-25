package com.aceerp.util;

import com.aceerp.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

public class CurrencyFormatter {

    private static final Logger log = LoggerFactory.getLogger(CurrencyFormatter.class);

    private CurrencyFormatter() {}

    /**
     * Reads the currency setting directly from SettingsService memory cache.
     */
    public static String getCurrencySymbol() {
        try {
            SettingsService settings = SettingsService.getInstance();
            if (settings != null) {
                // 1. Check "currency.symbol" (saved by Settings tab)
                String symbol = settings.get("currency.symbol");
                if (symbol != null && !symbol.isBlank()) {
                    return symbol.trim();
                }

                // 2. Fallback key "currency_symbol"
                String altSymbol = settings.get("currency_symbol");
                if (altSymbol != null && !altSymbol.isBlank()) {
                    return altSymbol.trim();
                }

                // 3. Convert 3-letter currency code (e.g., "INR" -> "₹", "USD" -> "$")
                String code = settings.get("currency.code");
                if (code != null && !code.isBlank()) {
                    return codeToSymbol(code.trim());
                }
            }
        } catch (Exception e) {
            log.warn("Could not retrieve currency setting from SettingsService", e);
        }

        return "$";
    }

    private static String codeToSymbol(String code) {
        return switch (code.toUpperCase()) {
            case "INR", "RUPEE" -> "₹";
            case "USD", "DOLLAR" -> "$";
            case "EUR" -> "€";
            case "GBP" -> "£";
            case "AED" -> "AED";
            case "SAR" -> "SAR";
            default -> code;
        };
    }

    public static void clearCache() {
        try {
            SettingsService.getInstance().reload();
        } catch (Exception e) {
            log.warn("Failed to reload SettingsService cache", e);
        }
    }

    public static String format(BigDecimal amount) {
        if (amount == null) amount = BigDecimal.ZERO;
        return getCurrencySymbol() + " " + String.format("%,.2f", amount);
    }

    public static String format(double amount) {
        return getCurrencySymbol() + " " + String.format("%,.2f", amount);
    }
}