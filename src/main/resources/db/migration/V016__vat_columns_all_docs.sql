-- Migration V016: Add tax_rate and tax_amount columns to all line tables

ALTER TABLE purchase_order_lines ADD COLUMN IF NOT EXISTS tax_rate NUMERIC(5,2) DEFAULT 0.00;
ALTER TABLE purchase_order_lines ADD COLUMN IF NOT EXISTS tax_amount NUMERIC(12,2) DEFAULT 0.00;

ALTER TABLE sales_order_lines ADD COLUMN IF NOT EXISTS tax_rate NUMERIC(5,2) DEFAULT 0.00;
ALTER TABLE sales_order_lines ADD COLUMN IF NOT EXISTS tax_amount NUMERIC(12,2) DEFAULT 0.00;

ALTER TABLE invoice_lines ADD COLUMN IF NOT EXISTS tax_rate NUMERIC(5,2) DEFAULT 0.00;
ALTER TABLE invoice_lines ADD COLUMN IF NOT EXISTS tax_amount NUMERIC(12,2) DEFAULT 0.00;

ALTER TABLE quotation_lines ADD COLUMN IF NOT EXISTS tax_rate NUMERIC(5,2) DEFAULT 0.00;
ALTER TABLE quotation_lines ADD COLUMN IF NOT EXISTS tax_amount NUMERIC(12,2) DEFAULT 0.00;