package com.aceerp.util;

import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

public final class InputValidation {

    private InputValidation() {}

    public static void digitsOnly(TextField field) {
        field.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().matches("\\d*") ? change : null));
    }

    public static void phoneNumber(TextField field) {
        field.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().matches("[0-9+()\\s-]*") ? change : null));
    }

    public static void decimalOnly(TextField field) {
        field.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().matches("-?(\\d*(\\.\\d*)?)") ? change : null));
    }
}
