package com.aceerp.util;

import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class DocumentDiscount {

    public static final String PERCENT = "PERCENT";
    public static final String AMOUNT = "AMOUNT";

    private DocumentDiscount() {}

    public static String normalizeType(String type) {
        return AMOUNT.equalsIgnoreCase(type) ? AMOUNT : PERCENT;
    }

    public static BigDecimal parseValue(String text) {
        if (text == null || text.isBlank()) return BigDecimal.ZERO;
        return new BigDecimal(text.trim());
    }

    public static void configureControls(ComboBox<String> typeControl, TextField valueControl, Runnable onChange) {
        typeControl.setItems(FXCollections.observableArrayList("Percent (%)", "Fixed amount"));
        typeControl.setValue("Percent (%)");
        InputValidation.decimalOnly(valueControl);
        typeControl.setOnAction(event -> onChange.run());
        valueControl.textProperty().addListener((obs, oldValue, newValue) -> onChange.run());
    }

    public static void setControls(ComboBox<String> typeControl, TextField valueControl, String type, BigDecimal value) {
        typeControl.setValue(AMOUNT.equals(normalizeType(type)) ? "Fixed amount" : "Percent (%)");
        valueControl.setText(value == null ? "0" : value.stripTrailingZeros().toPlainString());
    }

    public static String selectedType(ComboBox<String> typeControl) {
        return "Fixed amount".equals(typeControl.getValue()) ? AMOUNT : PERCENT;
    }

    public static BigDecimal valueForCalculation(TextField valueControl) {
        try {
            return parseValue(valueControl.getText());
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    public static String validate(String type, String value, BigDecimal subtotal) {
        try {
            return validate(type, parseValue(value), subtotal);
        } catch (NumberFormatException e) {
            return "Enter a valid discount value.";
        }
    }

    public static BigDecimal calculateAmount(BigDecimal subtotal, String type, BigDecimal value) {
        BigDecimal base = nonNegative(subtotal);
        BigDecimal discount = nonNegative(value);
        if (AMOUNT.equals(normalizeType(type))) {
            return discount.min(base).setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal percent = discount.min(BigDecimal.valueOf(100));
        return base.multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal calculateTaxAfterDiscount(BigDecimal subtotal, BigDecimal tax, BigDecimal discountAmount) {
        BigDecimal base = nonNegative(subtotal);
        BigDecimal taxAmount = nonNegative(tax);
        if (base.signum() == 0 || taxAmount.signum() == 0) return taxAmount.setScale(2, RoundingMode.HALF_UP);

        BigDecimal netBase = base.subtract(nonNegative(discountAmount)).max(BigDecimal.ZERO);
        return taxAmount.multiply(netBase).divide(base, 2, RoundingMode.HALF_UP);
    }

    public static String validate(String type, BigDecimal value, BigDecimal subtotal) {
        if (value == null || value.signum() < 0) return "Discount must be zero or greater.";
        if (PERCENT.equals(normalizeType(type)) && value.compareTo(BigDecimal.valueOf(100)) > 0) {
            return "Percentage discount cannot exceed 100%.";
        }
        if (AMOUNT.equals(normalizeType(type)) && value.compareTo(nonNegative(subtotal)) > 0) {
            return "Fixed discount cannot exceed the document subtotal.";
        }
        return null;
    }

    private static BigDecimal nonNegative(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.max(BigDecimal.ZERO);
    }
}
