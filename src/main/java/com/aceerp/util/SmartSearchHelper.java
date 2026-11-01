package com.aceerp.util;

import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.geometry.Bounds;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Popup;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Reusable smart search helper.
 *
 * Connects a TextField to a ListView / Popup so that:
 * 1. As you type, matching suggestions appear in the list
 * 2. Click a suggestion to fill the field
 * 3. You can also click "+ Add new" to create a new record
 */
public class SmartSearchHelper {

    /**
     * Wires a TextField and ListView together for smart searching (simple String version).
     */
    public static void setup(
            TextField textField,
            ListView<String> listView,
            Function<String, List<String>> searchFunction) {

        listView.setVisible(false);
        listView.setManaged(false);

        textField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && newVal.trim().length() >= 1) {
                List<String> results = searchFunction.apply(newVal.trim());
                listView.getItems().setAll(results);

                boolean hasResults = !results.isEmpty();
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

    /**
     * Local String search version.
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

    /**
     * Smart Entity search with dropdown popup and "+ Add new" quick-add support.
     *
     * @param textField      TextField to bind
     * @param searchFunction Function taking typed query and returning matching items
     * @param displayFunction Function converting an item to display string
     * @param onSelect       Callback when an existing item is selected
     * @param onAddNew       Callback when user selects "+ Add new"
     */
    public static <T> void setupEntitySearch(
            TextField textField,
            Function<String, List<T>> searchFunction,
            Function<T, String> displayFunction,
            Consumer<T> onSelect,
            Consumer<String> onAddNew) {

        Popup popup = new Popup();
        popup.setAutoHide(true);

        ListView<Object> listView = new ListView<>();
        listView.setPrefHeight(180);
        popup.getContent().add(listView);

        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else if (item instanceof String s && s.startsWith("➕")) {
                    setText(s);
                    setStyle("-fx-font-weight: bold; -fx-text-fill: #1976D2; -fx-cursor: hand;");
                } else {
                    @SuppressWarnings("unchecked")
                    T entity = (T) item;
                    setText(displayFunction.apply(entity));
                    setStyle("");
                }
            }
        });

        textField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().isEmpty()) {
                popup.hide();
                return;
            }

            String query = newVal.trim();
            List<T> matches = searchFunction.apply(query);
            List<Object> items = new ArrayList<>(matches);
            items.add("➕ Add new '" + query + "'...");

            listView.setItems(FXCollections.observableArrayList(items));

            if (!popup.isShowing() && textField.getScene() != null && textField.getScene().getWindow() != null) {
                Bounds bounds = textField.localToScreen(textField.getBoundsInLocal());
                if (bounds != null) {
                    listView.setPrefWidth(Math.max(bounds.getWidth(), 250));
                    popup.show(textField, bounds.getMinX(), bounds.getMaxY());
                }
            }
        });

        listView.setOnMouseClicked(event -> {
            Object selected = listView.getSelectionModel().getSelectedItem();
            if (selected == null) return;

            popup.hide();
            if (selected instanceof String s && s.startsWith("➕")) {
                String typedText = textField.getText() != null ? textField.getText().trim() : "";
                if (onAddNew != null) {
                    onAddNew.accept(typedText);
                }
            } else {
                @SuppressWarnings("unchecked")
                T entity = (T) selected;
                if (onSelect != null) {
                    onSelect.accept(entity);
                }
            }
        });

        textField.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                PauseTransition pause = new PauseTransition(Duration.millis(200));
                pause.setOnFinished(e -> popup.hide());
                pause.play();
            }
        });
    }
}