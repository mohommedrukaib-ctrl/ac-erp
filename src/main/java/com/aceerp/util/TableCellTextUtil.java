package com.aceerp.util;

import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.TextFieldTableCell;

public final class TableCellTextUtil {

    private TableCellTextUtil() {}

    public static <S> void showFullTextOnHover(TableColumn<S, String> column) {
        column.setCellFactory(ignored -> new TextFieldTableCell<>() {
            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setTooltip(empty || item == null || item.isBlank() ? null : new Tooltip(item));
            }
        });
    }
}
