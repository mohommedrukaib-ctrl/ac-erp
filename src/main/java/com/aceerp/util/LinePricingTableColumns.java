package com.aceerp.util;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.converter.BigDecimalStringConverter;

import java.math.BigDecimal;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;

public final class LinePricingTableColumns {

    private LinePricingTableColumns() {}

    public static <S> void configureUnitAndSetPrice(
            TableColumn<S, BigDecimal> unitPriceColumn,
            TableColumn<S, BigDecimal> setPriceColumn,
            Function<S, BigDecimal> priceGetter,
            BiConsumer<S, BigDecimal> priceSetter,
            Predicate<S> packagePriceGetter,
            BooleanSupplier canEdit,
            Runnable onChange) {
        configurePriceColumn(unitPriceColumn, priceGetter, priceSetter, packagePriceGetter,
                false, canEdit, onChange);
        configurePriceColumn(setPriceColumn, priceGetter, priceSetter, packagePriceGetter,
                true, canEdit, onChange);
    }

    private static <S> void configurePriceColumn(
            TableColumn<S, BigDecimal> column,
            Function<S, BigDecimal> priceGetter,
            BiConsumer<S, BigDecimal> priceSetter,
            Predicate<S> packagePriceGetter,
            boolean setPrice,
            BooleanSupplier canEdit,
            Runnable onChange) {
        Predicate<S> active = row -> packagePriceGetter.test(row) == setPrice;
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                active.test(cell.getValue()) ? priceGetter.apply(cell.getValue()) : null));
        column.setCellFactory(ignored -> new TextFieldTableCell<S, BigDecimal>(new BigDecimalStringConverter()) {
            @Override
            public void updateItem(BigDecimal item, boolean empty) {
                super.updateItem(item, empty);
                S row = getTableRow() == null ? null : getTableRow().getItem();
                setDisable(empty || row == null || !active.test(row) || !canEdit.getAsBoolean());
            }

            @Override
            public void startEdit() {
                S row = getTableRow() == null ? null : getTableRow().getItem();
                if (row == null || !active.test(row) || !canEdit.getAsBoolean()) return;
                super.startEdit();
            }
        });
        column.setOnEditCommit(event -> {
            S row = event.getRowValue();
            if (!canEdit.getAsBoolean() || !active.test(row)) return;
            priceSetter.accept(row, event.getNewValue() != null ? event.getNewValue() : BigDecimal.ZERO);
            onChange.run();
        });
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

    public static <S> void configurePackagePrice(
            TableColumn<S, Boolean> column,
            Predicate<S> getter,
            BiConsumer<S, Boolean> setter,
            Runnable onChange) {
        column.setCellValueFactory(cell -> {
            SimpleBooleanProperty value = new SimpleBooleanProperty(getter.test(cell.getValue()));
            value.addListener((observable, oldValue, newValue) -> {
                setter.accept(cell.getValue(), newValue);
                column.getTableView().refresh();
                onChange.run();
            });
            return value;
        });
        column.setCellFactory(CheckBoxTableCell.forTableColumn(column));
    }
}
