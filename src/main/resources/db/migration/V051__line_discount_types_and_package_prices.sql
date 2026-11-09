ALTER TABLE quotation_lines
    ADD COLUMN IF NOT EXISTS discount_type VARCHAR(10) NOT NULL DEFAULT 'PERCENT',
    ADD COLUMN IF NOT EXISTS discount_value NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS package_price BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE invoice_lines
    ADD COLUMN IF NOT EXISTS discount_type VARCHAR(10) NOT NULL DEFAULT 'PERCENT',
    ADD COLUMN IF NOT EXISTS discount_value NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS package_price BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE sales_order_lines
    ADD COLUMN IF NOT EXISTS discount_type VARCHAR(10) NOT NULL DEFAULT 'PERCENT',
    ADD COLUMN IF NOT EXISTS discount_value NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS package_price BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE purchase_order_lines
    ADD COLUMN IF NOT EXISTS discount_type VARCHAR(10) NOT NULL DEFAULT 'PERCENT',
    ADD COLUMN IF NOT EXISTS discount_value NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS package_price BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE supplier_bill_lines
    ADD COLUMN IF NOT EXISTS discount_type VARCHAR(10) NOT NULL DEFAULT 'PERCENT',
    ADD COLUMN IF NOT EXISTS discount_value NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS package_price BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE service_job_lines
    ADD COLUMN IF NOT EXISTS discount_type VARCHAR(10) NOT NULL DEFAULT 'PERCENT',
    ADD COLUMN IF NOT EXISTS discount_value NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS package_price BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE credit_memo_lines
    ADD COLUMN IF NOT EXISTS discount_type VARCHAR(10) NOT NULL DEFAULT 'PERCENT',
    ADD COLUMN IF NOT EXISTS discount_value NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS package_price BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE vendor_credit_lines
    ADD COLUMN IF NOT EXISTS discount_type VARCHAR(10) NOT NULL DEFAULT 'PERCENT',
    ADD COLUMN IF NOT EXISTS discount_value NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS package_price BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE purchase_return_lines
    ADD COLUMN IF NOT EXISTS discount_type VARCHAR(10) NOT NULL DEFAULT 'PERCENT',
    ADD COLUMN IF NOT EXISTS discount_value NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS package_price BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE quotation_lines SET discount_value = discount_percent WHERE discount_value = 0 AND discount_percent <> 0;
UPDATE invoice_lines SET discount_value = discount_percent WHERE discount_value = 0 AND discount_percent <> 0;
UPDATE sales_order_lines SET discount_value = discount_percent WHERE discount_value = 0 AND discount_percent <> 0;
UPDATE purchase_order_lines SET discount_value = discount_percent WHERE discount_value = 0 AND discount_percent <> 0;
UPDATE supplier_bill_lines SET discount_value = discount_percent WHERE discount_value = 0 AND discount_percent <> 0;
UPDATE service_job_lines SET discount_value = discount_percent WHERE discount_value = 0 AND discount_percent <> 0;
