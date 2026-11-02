-- Option B: line discount % and notes on document lines

-- Quotations
ALTER TABLE quotation_lines
    ADD COLUMN IF NOT EXISTS discount_percent NUMERIC(8,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS notes TEXT;

-- Sales Orders (support both historical column names if present)
ALTER TABLE sales_order_lines
    ADD COLUMN IF NOT EXISTS discount_percent NUMERIC(8,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS notes TEXT;

-- Invoices (safe if already present)
ALTER TABLE invoice_lines
    ADD COLUMN IF NOT EXISTS discount_percent NUMERIC(8,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS notes TEXT;

ALTER TABLE delivery_note_lines
    ADD COLUMN IF NOT EXISTS notes TEXT;

    