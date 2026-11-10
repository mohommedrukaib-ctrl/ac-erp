package com.aceerp.util;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.converter.BigDecimalStringConverter;

import java.math.BigDecimal;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

public final class LinePricingTableColumns {

    private LinePricingTableColumns() {}

    public static <S> void configurePrice(
            TableColumn<S, BigDecimal> priceColumn,
            Function<S, BigDecimal> priceGetter,
            BiConsumer<S, BigDecimal> priceSetter,
            BooleanSupplier canEdit,
            BooleanSupplier packagePriceEnabled,
            Runnable onChange) {
        priceColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                packagePriceEnabled.getAsBoolean() ? BigDecimal.ZERO : priceGetter.apply(cell.getValue())));
        priceColumn.setCellFactory(ignored -> new TextFieldTableCell<S, BigDecimal>(new BigDecimalStringConverter()) {
            @Override
            public void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                setDisable(empty || !canEdit.getAsBoolean() || packagePriceEnabled.getAsBoolean());
            }

            @Override
            public void startEdit() {
                if (!canEdit.getAsBoolean() || packagePriceEnabled.getAsBoolean()) return;
                super.startEdit();
            }
        });
        priceColumn.setOnEditCommit(event -> {
            if (!canEdit.getAsBoolean() || packagePriceEnabled.getAsBoolean()) return;
            priceSetter.accept(event.getRowValue(),
                    event.getNewValue() != null ? event.getNewValue() : BigDecimal.ZERO);
            onChange.run();
        });
    }

    public static <S> void configureLineTotal(
            TableColumn<S, BigDecimal> totalColumn,
            Function<S, BigDecimal> totalGetter,
            BooleanSupplier packagePriceEnabled) {
        totalColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                packagePriceEnabled.getAsBoolean() ? BigDecimal.ZERO : totalGetter.apply(cell.getValue())));
    }

    public static <S> void configureDiscountType(
            TableColumn<S, String> column,
            Function<S, String> getter,
            BiConsumer<S, String> setter,
            Runnable onChange) {
        column.setCellValueFactory(cell ->
                new SimpleStringProperty(DocumentDiscount.AMOUNT.equals(DocumentDiscount.normalizeType(
                        getter.apply(cell.getValue()))) ? "Fixed" : "%"));
        column.setCellFactory(ignored -> new TableCell<>() {
            private final ToggleButton toggle = new ToggleButton();

            {
                toggle.setMaxWidth(Double.MAX_VALUE);
                toggle.setTooltip(new Tooltip("Toggle this line between percentage and fixed-amount discount."));
                toggle.setOnAction(event -> {
                    S row = getTableRow() == null ? null : getTableRow().getItem();
                    if (row == null) return;
                    String nextType = DocumentDiscount.AMOUNT.equals(
                            DocumentDiscount.normalizeType(getter.apply(row)))
                            ? DocumentDiscount.PERCENT : DocumentDiscount.AMOUNT;
                    setter.accept(row, nextType);
                    getTableView().refresh();
                    onChange.run();
                });
            }

            @Override
            protected void updateItem(String type, boolean empty) {
                super.updateItem(type, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                boolean fixedAmount = DocumentDiscount.AMOUNT.equals(
                        DocumentDiscount.normalizeType(getter.apply(getTableRow().getItem())));
                toggle.setSelected(fixedAmount);
                toggle.setText(fixedAmount ? "Fixed" : "%");
                setGraphic(toggle);
            }
        });
    }

}
