package com.aceerp.util;

import com.aceerp.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CurrencyFormatter {

    private static final Logger log = LoggerFactory.getLogger(CurrencyFormatter.class);
    private static final String[] SMALL_NUMBER_WORDS = {
            "Zero", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
            "Seventeen", "Eighteen", "Nineteen"
    };
    private static final String[] TENS_NUMBER_WORDS = {
            "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };
    private static final String[] NUMBER_GROUPS = {
            "", "Thousand", "Million", "Billion", "Trillion", "Quadrillion"
    };

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

    public static String toWords(BigDecimal amount) {
        if (amount == null) return "";
        BigDecimal rounded = amount.setScale(2, RoundingMode.HALF_UP);
        BigInteger minorUnits = rounded.abs().movePointRight(2).toBigIntegerExact();
        BigInteger[] parts = minorUnits.divideAndRemainder(BigInteger.valueOf(100));
        String[] currencyWords = currencyWords();

        StringBuilder words = new StringBuilder();
        if (rounded.signum() < 0) words.append("Minus ");
        words.append(numberToWords(parts[0])).append(' ').append(currencyWords[0]);
        if (parts[1].signum() != 0) {
            words.append(" and ").append(numberToWords(parts[1])).append(' ').append(currencyWords[1]);
        }
        return words.append(" Only").toString();
    }

    private static String[] currencyWords() {
        SettingsService settings = SettingsService.getInstance();
        String code = settings.get("currency_code");
        if (code == null || code.isBlank()) code = settings.get("currency.code", "LKR");
        String normalizedCode = code == null || code.isBlank() ? "LKR" : code.trim().toUpperCase(Locale.ROOT);
        return switch (normalizedCode) {
            case "LKR" -> new String[]{"Rupees", "Cents"};
            case "INR" -> new String[]{"Rupees", "Paise"};
            case "PKR" -> new String[]{"Rupees", "Paisa"};
            case "USD" -> new String[]{"Dollars", "Cents"};
            case "EUR" -> new String[]{"Euros", "Cents"};
            case "GBP" -> new String[]{"Pounds", "Pence"};
            case "AED" -> new String[]{"Dirhams", "Fils"};
            case "SAR" -> new String[]{"Riyals", "Halalas"};
            case "BDT" -> new String[]{"Taka", "Paisa"};
            default -> new String[]{normalizedCode + " Units", "Minor Units"};
        };
    }

    private static String numberToWords(BigInteger number) {
        if (number.signum() == 0) return SMALL_NUMBER_WORDS[0];
        List<String> groups = new ArrayList<>();
        BigInteger thousand = BigInteger.valueOf(1000);
        int groupIndex = 0;
        while (number.signum() > 0) {
            BigInteger[] part = number.divideAndRemainder(thousand);
            int groupValue = part[1].intValueExact();
            if (groupValue > 0) {
                if (groupIndex >= NUMBER_GROUPS.length) {
                    throw new IllegalArgumentException("Amount is too large to spell out.");
                }
                String groupWords = underOneThousand(groupValue);
                if (!NUMBER_GROUPS[groupIndex].isEmpty()) groupWords += " " + NUMBER_GROUPS[groupIndex];
                groups.add(0, groupWords);
            }
            number = part[0];
            groupIndex++;
        }
        return String.join(" ", groups);
    }

    private static String underOneThousand(int number) {
        List<String> words = new ArrayList<>();
        if (number >= 100) {
            words.add(SMALL_NUMBER_WORDS[number / 100]);
            words.add("Hundred");
            number %= 100;
        }
        if (number >= 20) {
            words.add(TENS_NUMBER_WORDS[number / 10]);
            number %= 10;
        }
        if (number > 0) words.add(SMALL_NUMBER_WORDS[number]);
        return String.join(" ", words);
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