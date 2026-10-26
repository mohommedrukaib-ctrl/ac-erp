-- Fix: PurchaseOrderRepository INSERT uses line_total
ALTER TABLE purchase_order_lines ADD COLUMN IF NOT EXISTS line_total NUMERIC(12,2) DEFAULT 0.00;
ALTER TABLE purchase_order_lines ADD COLUMN IF NOT EXISTS total_price NUMERIC(12,2) DEFAULT 0.00;

-- Ensure tax columns exist (safe if already added)
ALTER TABLE purchase_order_lines ADD COLUMN IF NOT EXISTS tax_rate   NUMERIC(5,2)  DEFAULT 0.00;
ALTER TABLE purchase_order_lines ADD COLUMN IF NOT EXISTS tax_amount NUMERIC(12,2) DEFAULT 0.00;

-- Same safety for other line tables
ALTER TABLE quotation_lines    ADD COLUMN IF NOT EXISTS tax_amount NUMERIC(12,2) DEFAULT 0.00;
ALTER TABLE sales_order_lines  ADD COLUMN IF NOT EXISTS tax_rate   NUMERIC(5,2)  DEFAULT 0.00;
ALTER TABLE sales_order_lines  ADD COLUMN IF NOT EXISTS tax_amount NUMERIC(12,2) DEFAULT 0.00;
ALTER TABLE sales_order_lines  ADD COLUMN IF NOT EXISTS line_total NUMERIC(12,2) DEFAULT 0.00;
ALTER TABLE invoice_lines      ADD COLUMN IF NOT EXISTS tax_rate   NUMERIC(5,2)  DEFAULT 0.00;
ALTER TABLE invoice_lines      ADD COLUMN IF NOT EXISTS tax_amount NUMERIC(12,2) DEFAULT 0.00;
ALTER TABLE invoice_lines      ADD COLUMN IF NOT EXISTS line_total NUMERIC(12,2) DEFAULT 0.00;