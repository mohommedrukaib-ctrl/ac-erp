-- V035: Ensure all line item columns and aliases exist on purchase_return_lines
ALTER TABLE purchase_return_lines ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE purchase_return_lines ADD COLUMN IF NOT EXISTS unit_cost NUMERIC(12,2) DEFAULT 0.00;
ALTER TABLE purchase_return_lines ADD COLUMN IF NOT EXISTS unit_price NUMERIC(12,2) DEFAULT 0.00;
ALTER TABLE purchase_return_lines ADD COLUMN IF NOT EXISTS quantity NUMERIC(12,2) DEFAULT 0.00;
ALTER TABLE purchase_return_lines ADD COLUMN IF NOT EXISTS qty_returned NUMERIC(12,2) DEFAULT 0.00;
ALTER TABLE purchase_return_lines ADD COLUMN IF NOT EXISTS line_total NUMERIC(12,2) DEFAULT 0.00;
ALTER TABLE purchase_return_lines ADD COLUMN IF NOT EXISTS total_price NUMERIC(12,2) DEFAULT 0.00;