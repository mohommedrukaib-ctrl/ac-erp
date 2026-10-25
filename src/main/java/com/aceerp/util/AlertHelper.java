package com.aceerp.util;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;

import java.util.Optional;

/**
 * Helper for showing standard dialog boxes.
 * 
 * Centralizes dialog creation so our dialogs look consistent and we don't
 * repeat boilerplate code in every controller.
 */
public final class AlertHelper {

    private AlertHelper() {} // Prevent instantiation (static-only class)

    /**
     * Show an informational alert.
     */
    public static void info(String title, String message) {
        showAlert(AlertType.INFORMATION, title, null, message);
    }

    /**
     * Show a warning alert.
     */
    public static void warning(String title, String message) {
        showAlert(AlertType.WARNING, title, null, message);
    }

    /**
     * Show an error alert.
     */
    public static void error(String title, String message) {
        showAlert(AlertType.ERROR, title, null, message);
    }

    /**
     * Show an error alert with a longer explanation.
     */
    public static void error(String title, String header, String message) {
        showAlert(AlertType.ERROR, title, header, message);
    }

    /**
     * Show a confirmation dialog with Yes/No buttons.
     * Returns true if user clicked Yes.
     */
    public static boolean confirm(String title, String message) {
        Alert alert = new Alert(AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setTitle(title);
        alert.setHeaderText(null);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.YES;
    }

    private static void showAlert(AlertType type, String title, String header, String message) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(message);
        alert.setResizable(true);
        alert.getDialogPane().setMinWidth(400);
        alert.showAndWait();
    }
}