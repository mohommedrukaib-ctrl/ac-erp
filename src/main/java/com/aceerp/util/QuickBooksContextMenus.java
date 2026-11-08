package com.aceerp.util;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Control;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ContextMenuEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.function.Supplier;

public final class QuickBooksContextMenus {

    private static final Object INSTALLED_KEY = new Object();

    private QuickBooksContextMenus() {}

    public static void installDefaultMenus(Parent root) {
        installInTree(root);
    }

    public static MenuItem action(String title, Runnable action) {
        MenuItem item = new MenuItem(title);
        item.setOnAction(event -> action.run());
        return item;
    }

    public static <T> void installListView(ListView<T> list, Function<T, List<MenuItem>> actions) {
        if (list.getProperties().putIfAbsent(INSTALLED_KEY, Boolean.TRUE) != null) return;
        list.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            ListCell<T> cell = findAncestor(event.getTarget(), ListCell.class);
            if (cell == null || cell.isEmpty()) return;
            if (cell.getContextMenu() != null) return;

            list.getSelectionModel().select(cell.getIndex());
            T item = list.getSelectionModel().getSelectedItem();
            if (item == null) return;

            List<MenuItem> items = new ArrayList<>();
            items.add(copyItem(() -> cell.getText() != null ? cell.getText() : String.valueOf(item)));
            appendActions(items, actions.apply(item));
            showMenu(list, items, event);
        });
    }

    public static <T> void installTableView(TableView<T> table, Function<T, List<MenuItem>> actions) {
        if (table.getProperties().putIfAbsent(INSTALLED_KEY, Boolean.TRUE) != null) return;
        table.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            TableRow<T> row = findAncestor(event.getTarget(), TableRow.class);
            if (row == null || row.isEmpty()) return;
            if (row.getContextMenu() != null) return;

            table.getSelectionModel().select(row.getIndex());
            T item = table.getSelectionModel().getSelectedItem();
            if (item == null) return;

            List<MenuItem> items = new ArrayList<>();
            items.add(copyItem(() -> tableRowText(table, item)));
            appendActions(items, actions.apply(item));
            showMenu(table, items, event);
        });
    }

    private static void installInTree(Node node) {
        if (node instanceof ListView<?> list) {
            installListView(list, item -> List.of());
        } else if (node instanceof TableView<?> table) {
            installTableView(table, item -> List.of());
        }

        if (node instanceof TabPane tabPane) {
            for (Tab tab : tabPane.getTabs()) {
                if (tab.getContent() != null) installInTree(tab.getContent());
            }
        }

        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                installInTree(child);
            }
        }
    }

    private static void showMenu(Control control, List<MenuItem> items, ContextMenuEvent event) {
        ContextMenu menu = new ContextMenu();
        menu.getItems().setAll(items);
        menu.show(control, event.getScreenX(), event.getScreenY());
        event.consume();
    }

    private static void appendActions(List<MenuItem> items, List<MenuItem> actions) {
        if (actions == null || actions.isEmpty()) return;
        if (!items.isEmpty()) items.add(new SeparatorMenuItem());
        items.addAll(actions);
    }

    private static MenuItem copyItem(Supplier<String> text) {
        MenuItem item = new MenuItem("Copy");
        item.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(text.get());
            Clipboard.getSystemClipboard().setContent(content);
        });
        return item;
    }

    private static <T> String tableRowText(TableView<T> table, T item) {
        StringJoiner values = new StringJoiner("\t");
        table.getVisibleLeafColumns().forEach(column -> {
            Object value = column.getCellData(item);
            values.add(value == null ? "" : value.toString());
        });
        return values.toString();
    }

    private static <T> T findAncestor(Object target, Class<T> type) {
        if (!(target instanceof Node node)) return null;
        Node current = node;
        while (current != null) {
            if (type.isInstance(current)) return type.cast(current);
            current = current.getParent();
        }
        return null;
    }
}
