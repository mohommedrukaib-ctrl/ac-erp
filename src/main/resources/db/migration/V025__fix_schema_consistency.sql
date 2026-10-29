-- =============================================================================
-- V025: Fix schema consistency (runtime errors batch)
-- - Invoice status constraint accepts PARTIAL + PARTIALLY_PAID + UNPAID
-- - products.product_type default
-- - purchase_order_lines / sales_order_lines / quotation_lines / invoice_lines
--   keep line_total <-> total_price in sync
-- - sales_orders: so_number <-> order_number compatibility
-- - Safe defaults for quick-add codes where columns exist
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1) Invoice status check constraint
-- Live (V001): DRAFT, SENT, PAID, PARTIAL, OVERDUE, CANCELLED
-- App may emit: PARTIALLY_PAID, UNPAID
-- ---------------------------------------------------------------------------
ALTER TABLE invoices DROP CONSTRAINT IF EXISTS chk_invoice_status;

ALTER TABLE invoices ADD CONSTRAINT chk_invoice_status CHECK (
    status IN (
        'DRAFT', 'SENT', 'PAID', 'PARTIAL', 'PARTIALLY_PAID',
        'UNPAID', 'OVERDUE', 'CANCELLED'
    )
);

-- Normalize legacy / app-only values to canonical forms (optional data cleanup)
UPDATE invoices SET status = 'PARTIAL' WHERE status = 'PARTIALLY_PAID';
UPDATE invoices SET status = 'SENT'    WHERE status = 'UNPAID';

-- ---------------------------------------------------------------------------
-- 2) products.product_type — NOT NULL + CHECK (V001)
-- ---------------------------------------------------------------------------
ALTER TABLE products ALTER COLUMN product_type SET DEFAULT 'SPARE_PART';

UPDATE products
SET product_type = 'SPARE_PART'
WHERE product_type IS NULL
   OR TRIM(product_type) = '';

-- ---------------------------------------------------------------------------
-- 3) Line tables: ensure BOTH line_total and total_price exist and are filled
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    -- purchase_order_lines
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'purchase_order_lines' AND column_name = 'line_total') THEN
        ALTER TABLE purchase_order_lines ADD COLUMN line_total NUMERIC(15,2) DEFAULT 0;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'purchase_order_lines' AND column_name = 'total_price') THEN
        ALTER TABLE purchase_order_lines ADD COLUMN total_price NUMERIC(15,2) DEFAULT 0;
    END IF;

    -- sales_order_lines
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'sales_order_lines' AND column_name = 'line_total') THEN
        ALTER TABLE sales_order_lines ADD COLUMN line_total NUMERIC(15,2) DEFAULT 0;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'sales_order_lines' AND column_name = 'total_price') THEN
        ALTER TABLE sales_order_lines ADD COLUMN total_price NUMERIC(15,2) DEFAULT 0;
    END IF;

    -- quotation_lines
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'quotation_lines' AND column_name = 'line_total') THEN
        ALTER TABLE quotation_lines ADD COLUMN line_total NUMERIC(15,2) DEFAULT 0;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'quotation_lines' AND column_name = 'total_price') THEN
        ALTER TABLE quotation_lines ADD COLUMN total_price NUMERIC(15,2) DEFAULT 0;
    END IF;

    -- invoice_lines
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'invoice_lines' AND column_name = 'line_total') THEN
        ALTER TABLE invoice_lines ADD COLUMN line_total NUMERIC(15,2) DEFAULT 0;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'invoice_lines' AND column_name = 'total_price') THEN
        ALTER TABLE invoice_lines ADD COLUMN total_price NUMERIC(15,2) DEFAULT 0;
    END IF;
END $$;

-- Backfill PO lines (the failing table)
UPDATE purchase_order_lines
SET total_price = COALESCE(total_price, line_total, quantity * unit_price, 0)
WHERE total_price IS NULL;

UPDATE purchase_order_lines
SET line_total = COALESCE(line_total, total_price, quantity * unit_price, 0)
WHERE line_total IS NULL;

UPDATE sales_order_lines
SET total_price = COALESCE(total_price, line_total, quantity * unit_price, 0)
WHERE total_price IS NULL;

UPDATE sales_order_lines
SET line_total = COALESCE(line_total, total_price, quantity * unit_price, 0)
WHERE line_total IS NULL;

UPDATE quotation_lines
SET total_price = COALESCE(total_price, line_total, quantity * unit_price, 0)
WHERE total_price IS NULL;

UPDATE quotation_lines
SET line_total = COALESCE(line_total, total_price, quantity * unit_price, 0)
WHERE line_total IS NULL;

UPDATE invoice_lines
SET total_price = COALESCE(total_price, line_total, quantity * unit_price, 0)
WHERE total_price IS NULL;

UPDATE invoice_lines
SET line_total = COALESCE(line_total, total_price, quantity * unit_price, 0)
WHERE line_total IS NULL;

ALTER TABLE purchase_order_lines ALTER COLUMN total_price SET DEFAULT 0;
ALTER TABLE purchase_order_lines ALTER COLUMN line_total  SET DEFAULT 0;
ALTER TABLE sales_order_lines    ALTER COLUMN total_price SET DEFAULT 0;
ALTER TABLE sales_order_lines    ALTER COLUMN line_total  SET DEFAULT 0;

-- Keep line_total and total_price mirrored on write
CREATE OR REPLACE FUNCTION sync_line_totals() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.line_total IS NULL AND NEW.total_price IS NOT NULL THEN
        NEW.line_total := NEW.total_price;
    ELSIF NEW.total_price IS NULL AND NEW.line_total IS NOT NULL THEN
        NEW.total_price := NEW.line_total;
    ELSIF NEW.line_total IS NULL AND NEW.total_price IS NULL THEN
        NEW.line_total  := COALESCE(NEW.quantity, 0) * COALESCE(NEW.unit_price, 0);
        NEW.total_price := NEW.line_total;
    ELSE
        -- Prefer explicit line_total as source of truth when both provided differently
        NEW.total_price := NEW.line_total;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_po_line_totals ON purchase_order_lines;
CREATE TRIGGER trg_sync_po_line_totals
    BEFORE INSERT OR UPDATE ON purchase_order_lines
    FOR EACH ROW EXECUTE FUNCTION sync_line_totals();

DROP TRIGGER IF EXISTS trg_sync_so_line_totals ON sales_order_lines;
CREATE TRIGGER trg_sync_so_line_totals
    BEFORE INSERT OR UPDATE ON sales_order_lines
    FOR EACH ROW EXECUTE FUNCTION sync_line_totals();

DROP TRIGGER IF EXISTS trg_sync_qt_line_totals ON quotation_lines;
CREATE TRIGGER trg_sync_qt_line_totals
    BEFORE INSERT OR UPDATE ON quotation_lines
    FOR EACH ROW EXECUTE FUNCTION sync_line_totals();

DROP TRIGGER IF EXISTS trg_sync_inv_line_totals ON invoice_lines;
CREATE TRIGGER trg_sync_inv_line_totals
    BEFORE INSERT OR UPDATE ON invoice_lines
    FOR EACH ROW EXECUTE FUNCTION sync_line_totals();

-- ---------------------------------------------------------------------------
-- 4) sales_orders: so_number (V005) vs order_number (app conversion SQL)
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'sales_orders' AND column_name = 'so_number'
    ) THEN
        ALTER TABLE sales_orders ADD COLUMN so_number VARCHAR(50);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'sales_orders' AND column_name = 'order_number'
    ) THEN
        ALTER TABLE sales_orders ADD COLUMN order_number VARCHAR(50);
    END IF;
END $$;

UPDATE sales_orders SET order_number = so_number
WHERE order_number IS NULL AND so_number IS NOT NULL;

UPDATE sales_orders SET so_number = order_number
WHERE so_number IS NULL AND order_number IS NOT NULL;

-- Unique indexes (tolerant if data already unique via the other column)
CREATE UNIQUE INDEX IF NOT EXISTS uq_sales_orders_so_number
    ON sales_orders (so_number) WHERE so_number IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_sales_orders_order_number
    ON sales_orders (order_number) WHERE order_number IS NOT NULL;

CREATE OR REPLACE FUNCTION sync_so_numbers() RETURNS TRIGGER AS $$
BEGIN
    IF NEW.so_number IS NULL AND NEW.order_number IS NOT NULL THEN
        NEW.so_number := NEW.order_number;
    ELSIF NEW.order_number IS NULL AND NEW.so_number IS NOT NULL THEN
        NEW.order_number := NEW.so_number;
    ELSIF NEW.so_number IS NOT NULL AND NEW.order_number IS NULL THEN
        NEW.order_number := NEW.so_number;
    ELSIF NEW.order_number IS NOT NULL AND (NEW.so_number IS NULL OR NEW.so_number <> NEW.order_number) THEN
        -- Prefer so_number as canonical when both set inconsistently on insert of so_number path
        IF TG_OP = 'INSERT' AND NEW.so_number IS NOT NULL THEN
            NEW.order_number := NEW.so_number;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_so_numbers ON sales_orders;
CREATE TRIGGER trg_sync_so_numbers
    BEFORE INSERT OR UPDATE ON sales_orders
    FOR EACH ROW EXECUTE FUNCTION sync_so_numbers();

-- ---------------------------------------------------------------------------
-- 5) Tax columns safety on PO lines (in case older DB missed V016/V018)
-- ---------------------------------------------------------------------------
ALTER TABLE purchase_order_lines ADD COLUMN IF NOT EXISTS tax_rate   NUMERIC(5,2)  DEFAULT 0;
ALTER TABLE purchase_order_lines ADD COLUMN IF NOT EXISTS tax_amount NUMERIC(12,2) DEFAULT 0;

-- ---------------------------------------------------------------------------
-- Record migration
-- ---------------------------------------------------------------------------
INSERT INTO schema_migrations (version, description)
VALUES ('V025', 'Fix schema consistency: invoice status, product_type default, line totals sync, SO number alias')
ON CONFLICT (version) DO NOTHING;