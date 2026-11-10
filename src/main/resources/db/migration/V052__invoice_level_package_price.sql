ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS package_price_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS package_price_amount NUMERIC(12,2) NOT NULL DEFAULT 0;

UPDATE invoice_lines
SET unit_price = CASE
        WHEN quantity > 0 THEN ROUND(unit_price / quantity, 2)
        ELSE unit_price
    END,
    package_price = FALSE
WHERE package_price = TRUE;
