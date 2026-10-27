package com.aceerp.util;

import com.aceerp.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class CurrencyFormatter {

    private static final Logger log = LoggerFactory.getLogger(CurrencyFormatter.class);

    private CurrencyFormatter() {}

    /**
     * Reads the currency symbol directly from SettingsService.
     * Checks both 'currency_symbol' and 'currency.symbol' or derives from code.
     */
    public static String getCurrencySymbol() {
        try {
            SettingsService settings = SettingsService.getInstance();
            if (settings != null) {
                // 1. Check "currency_symbol" or "currency.symbol"
                String symbol = settings.get("currency_symbol");
                if (symbol == null || symbol.isBlank()) {
                    symbol = settings.get("currency.symbol");
                }
                if (symbol != null && !symbol.isBlank()) {
                    return symbol.trim();
                }

                // 2. Fallback to "currency_code" or "currency.code"
                String code = settings.get("currency_code");
                if (code == null || code.isBlank()) {
                    code = settings.get("currency.code");
                }
                if (code != null && !code.isBlank()) {
                    return codeToSymbol(code.trim());
                }
            }
        } catch (Exception e) {
            log.warn("Could not retrieve currency setting from SettingsService: {}", e.getMessage());
        }

        return "$";
    }

    /**
     * Converts standard 3-letter currency ISO codes to symbols.
     */
    public static String codeToSymbol(String code) {
        if (code == null || code.isBlank()) return "$";
        return switch (code.toUpperCase().trim()) {
            case "INR", "RUPEE" -> "₹";
            case "USD", "DOLLAR" -> "$";
            case "EUR" -> "€";
            case "GBP" -> "£";
            case "AED" -> "AED";
            case "SAR" -> "SAR";
            case "LKR" -> "Rs";
            case "PKR" -> "Rs";
            case "BDT" -> "৳";
            case "JPY" -> "¥";
            case "CNY" -> "¥";
            case "AUD" -> "A$";
            case "CAD" -> "C$";
            default -> code.trim();
        };
    }

    /**
     * Clears and reloads settings cache.
     */
    public static void clearCache() {
        try {
            SettingsService.getInstance().reload();
        } catch (Exception e) {
            log.warn("Failed to reload SettingsService cache: {}", e.getMessage());
        }
    }

    /**
     * Formats a BigDecimal with full currency symbol, grouping separators,
     * and symbol position (BEFORE / AFTER).
     */
    public static String format(BigDecimal amount) {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }

        SettingsService settings = SettingsService.getInstance();
        String symbol = getCurrencySymbol();
        
        String position = settings.get("currency_position");
        if (position == null || position.isBlank()) {
            position = settings.get("currency.position", "BEFORE");
        }

        int decimals = 2;
        String decimalsStr = settings.get("currency_decimals");
        if (decimalsStr == null || decimalsStr.isBlank()) {
            decimalsStr = settings.get("currency.decimals", "2");
        }
        try {
            decimals = Integer.parseInt(decimalsStr.trim());
        } catch (NumberFormatException ignored) {}

        BigDecimal scaled = amount.setScale(decimals, RoundingMode.HALF_UP);

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(',');
        symbols.setDecimalSeparator('.');

        StringBuilder pattern = new StringBuilder("#,##0");
        if (decimals > 0) {
            pattern.append(".");
            for (int i = 0; i < decimals; i++) {
                pattern.append("0");
            }
        }

        DecimalFormat df = new DecimalFormat(pattern.toString(), symbols);
        String formattedNumber = df.format(scaled);

        if ("AFTER".equalsIgnoreCase(position.trim())) {
            return formattedNumber + " " + symbol;
        } else {
            return symbol + " " + formattedNumber;
        }
    }

    /**
     * Formats a primitive double value.
     */
    public static String format(double amount) {
        return format(BigDecimal.valueOf(amount));
    }

    /**
     * Reloads cache if called statically.
     */
    public static void reload() {
        clearCache();
    }
}