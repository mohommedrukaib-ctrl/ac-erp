ALTER TABLE quotations
    ADD COLUMN IF NOT EXISTS package_price_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS package_price_amount NUMERIC(12,2) NOT NULL DEFAULT 0;

ALTER TABLE sales_orders
    ADD COLUMN IF NOT EXISTS package_price_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS package_price_amount NUMERIC(12,2) NOT NULL DEFAULT 0;

ALTER TABLE purchase_orders
    ADD COLUMN IF NOT EXISTS package_price_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS package_price_amount NUMERIC(12,2) NOT NULL DEFAULT 0;

ALTER TABLE supplier_bills
    ADD COLUMN IF NOT EXISTS package_price_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS package_price_amount NUMERIC(12,2) NOT NULL DEFAULT 0;

ALTER TABLE customer_credit_memos
    ADD COLUMN IF NOT EXISTS package_price_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS package_price_amount NUMERIC(12,2) NOT NULL DEFAULT 0;

ALTER TABLE vendor_credits
    ADD COLUMN IF NOT EXISTS package_price_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS package_price_amount NUMERIC(12,2) NOT NULL DEFAULT 0;

ALTER TABLE purchase_returns
    ADD COLUMN IF NOT EXISTS package_price_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS package_price_amount NUMERIC(12,2) NOT NULL DEFAULT 0;

ALTER TABLE service_jobs
    ADD COLUMN IF NOT EXISTS package_price_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS package_price_amount NUMERIC(12,2) NOT NULL DEFAULT 0;

UPDATE quotation_lines SET package_price = FALSE WHERE package_price = TRUE;
UPDATE sales_order_lines SET package_price = FALSE WHERE package_price = TRUE;
UPDATE purchase_order_lines SET package_price = FALSE WHERE package_price = TRUE;
UPDATE supplier_bill_lines SET package_price = FALSE WHERE package_price = TRUE;
UPDATE credit_memo_lines SET package_price = FALSE WHERE package_price = TRUE;
UPDATE vendor_credit_lines SET package_price = FALSE WHERE package_price = TRUE;
UPDATE purchase_return_lines SET package_price = FALSE WHERE package_price = TRUE;
UPDATE service_job_lines SET package_price = FALSE WHERE package_price = TRUE;
