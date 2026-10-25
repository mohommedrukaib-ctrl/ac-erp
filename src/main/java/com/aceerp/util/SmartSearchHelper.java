package com.aceerp.util;

import javafx.animation.PauseTransition;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.util.Duration;

import java.util.List;
import java.util.function.Function;

/**
 * Reusable smart search helper.
 * 
 * Connects a TextField to a ListView so that:
 * 1. As you type, matching suggestions appear in the list
 * 2. Click a suggestion to fill the field
 * 3. You can also type a completely new value (free text)
 * 
 * HOW TO USE (simple example):
 * 
 *   SmartSearchHelper.setup(
 *       myTextField,
 *       myListView,
 *       query -> myService.searchBrands(query)
 *   );
 * 
 * That's it! The helper handles everything else.
 */
public class SmartSearchHelper {

    /**
     * Wires a TextField and ListView together for smart searching.
     *
     * @param textField      the input field where the user types
     * @param listView       the list that shows matching suggestions
     * @param searchFunction a function that takes the typed text and returns
     *                       a list of matching suggestions from the database
     */
    public static void setup(
            TextField textField,
            ListView<String> listView,
            Function<String, List<String>> searchFunction) {

        // Start with the list hidden
        listView.setVisible(false);
        listView.setManaged(false); // hidden = takes no space

        // --- 1. Show suggestions as user types ---
        textField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.trim().length() >= 1) {
                // Call the search function to get matching values
                List<String> results = searchFunction.apply(newVal.trim());
                listView.getItems().setAll(results);

                // Only show the list if there are matches
                boolean hasResults = !results.isEmpty();
                listView.setVisible(hasResults);
                listView.setManaged(hasResults);
            } else {
                // Hide list when field is empty
                listView.getItems().clear();
                listView.setVisible(false);
                listView.setManaged(false);
            }
        });

        // --- 2. Fill the field when user clicks a suggestion ---
        listView.setOnMouseClicked(event -> {
            String selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                textField.setText(selected);
                listView.setVisible(false);
                listView.setManaged(false);
                // Move cursor to end of text
                textField.positionCaret(selected.length());
            }
        });

        // --- 3. Hide list when user clicks away ---
        // Small delay so the click on the list registers first
        textField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                PauseTransition pause = new PauseTransition(Duration.millis(200));
                pause.setOnFinished(e -> {
                    listView.setVisible(false);
                    listView.setManaged(false);
                });
                pause.play();
            }
        });
    }

    /**
     * Simpler version: loads ALL suggestions upfront and filters locally.
     * Good for small lists (under 100 items) like units or product types.
     *
     * @param textField  the input field
     * @param listView   the suggestion list
     * @param allValues  the complete list of possible values
     */
    public static void setupLocal(
            TextField textField,
            ListView<String> listView,
            List<String> allValues) {

        listView.setVisible(false);
        listView.setManaged(false);

        textField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.trim().length() >= 1) {
                String query = newVal.trim().toLowerCase();
                // Filter the full list locally (no database call)
                List<String> matches = allValues.stream()
                    .filter(v -> v.toLowerCase().contains(query))
                    .limit(10)
                    .toList();

                listView.getItems().setAll(matches);
                boolean hasResults = !matches.isEmpty();
                listView.setVisible(hasResults);
                listView.setManaged(hasResults);
            } else {
                listView.getItems().clear();
                listView.setVisible(false);
                listView.setManaged(false);
            }
        });

        listView.setOnMouseClicked(event -> {
            String selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null) {
                textField.setText(selected);
                listView.setVisible(false);
                listView.setManaged(false);
                textField.positionCaret(selected.length());
            }
        });

        textField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                PauseTransition pause = new PauseTransition(Duration.millis(200));
                pause.setOnFinished(e -> {
                    listView.setVisible(false);
                    listView.setManaged(false);
                });
                pause.play();
            }
        });
    }
}               