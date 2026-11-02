-- Option B: Discount % and Line Notes for Purchase Orders, Supplier Bills, and Service Jobs
ALTER TABLE purchase_order_lines
    ADD COLUMN IF NOT EXISTS discount_percent NUMERIC(8,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS notes TEXT;

ALTER TABLE supplier_bill_lines
    ADD COLUMN IF NOT EXISTS discount_percent NUMERIC(8,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS notes TEXT;

ALTER TABLE service_job_lines
    ADD COLUMN IF NOT EXISTS discount_percent NUMERIC(8,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS notes TEXT;