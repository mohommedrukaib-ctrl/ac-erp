package com.aceerp.util;

import com.aceerp.model.PackagePriceDocument;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;

import java.math.BigDecimal;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class DocumentPackagePrice {
    private final CheckBox enabledControl;
    private final TextField priceControl;
    private final Supplier<? extends PackagePriceDocument> documentSupplier;
    private final BooleanSupplier canEdit;
    private final Runnable onChange;
    private boolean syncing;
    private boolean controlsDisabled;

    private DocumentPackagePrice(CheckBox enabledControl, TextField priceControl,
                                 Supplier<? extends PackagePriceDocument> documentSupplier,
                                 BooleanSupplier canEdit, Runnable onChange) {
        this.enabledControl = enabledControl;
        this.priceControl = priceControl;
        this.documentSupplier = documentSupplier;
        this.canEdit = canEdit;
        this.onChange = onChange;

        enabledControl.selectedProperty().addListener((observable, oldValue, newValue) -> updateDocument());
        priceControl.setTextFormatter(new javafx.scene.control.TextFormatter<String>(change ->
                change.getControlNewText().matches("\\d*(\\.\\d{0,2})?") ? change : null));
        priceControl.textProperty().addListener((observable, oldValue, newValue) -> {
            if (enabledControl.isSelected()) updateDocument();
        });
    }

    public static DocumentPackagePrice configure(
            CheckBox enabledControl,
            TextField priceControl,
            Supplier<? extends PackagePriceDocument> documentSupplier,
            BooleanSupplier canEdit,
            Runnable onChange) {
        return new DocumentPackagePrice(enabledControl, priceControl, documentSupplier, canEdit, onChange);
    }

    public void setControls(PackagePriceDocument document) {
        syncing = true;
        try {
            enabledControl.setSelected(document != null && document.isPackagePriceEnabled());
            enabledControl.setIndeterminate(false);
            BigDecimal amount = document != null ? document.getPackagePriceAmount() : BigDecimal.ZERO;
            priceControl.setText(amount != null ? amount.stripTrailingZeros().toPlainString() : "0");
        } finally {
            syncing = false;
        }
        priceControl.setDisable(controlsDisabled || !enabledControl.isSelected());
    }

    public void reset() {
        PackagePriceDocument document = documentSupplier.get();
        if (document != null) {
            document.setPackagePriceEnabled(false);
            document.setPackagePriceAmount(BigDecimal.ZERO);
        }
        setControls(document);
        onChange.run();
    }

    public void setControlsDisabled(boolean disabled) {
        controlsDisabled = disabled;
        enabledControl.setDisable(disabled);
        priceControl.setDisable(disabled || !enabledControl.isSelected());
    }

    private void updateDocument() {
        if (syncing || !canEdit.getAsBoolean()) return;
        PackagePriceDocument document = documentSupplier.get();
        if (document == null) return;

        boolean enabled = enabledControl.isSelected();
        document.setPackagePriceEnabled(enabled);
        if (enabled) {
            String text = priceControl.getText();
            document.setPackagePriceAmount(text == null || text.isBlank() || ".".equals(text)
                    ? BigDecimal.ZERO : new BigDecimal(text));
        }
        priceControl.setDisable(controlsDisabled || !enabled);
        onChange.run();
    }
}
