-- ============================================================================
-- V031: Fix runtime errors for Purchase Return lines, GRN Stock trigger,
--       Product Type constraint, and Delivery Note line description.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. FIX PURCHASE RETURN LINES (return_id column mismatch)
-- ----------------------------------------------------------------------------
ALTER TABLE purchase_return_lines ADD COLUMN IF NOT EXISTS return_id INTEGER;
ALTER TABLE purchase_return_lines ADD COLUMN IF NOT EXISTS purchase_return_id INTEGER;

-- Trigger to keep return_id and purchase_return_id in sync
CREATE OR REPLACE FUNCTION sync_purchase_return_line_ids()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.return_id IS NULL AND NEW.purchase_return_id IS NOT NULL THEN
        NEW.return_id := NEW.purchase_return_id;
    ELSIF NEW.purchase_return_id IS NULL AND NEW.return_id IS NOT NULL THEN
        NEW.purchase_return_id := NEW.return_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_purchase_return_line_ids ON purchase_return_lines;
CREATE TRIGGER trg_sync_purchase_return_line_ids
BEFORE INSERT OR UPDATE ON purchase_return_lines
FOR EACH ROW EXECUTE FUNCTION sync_purchase_return_line_ids();


-- ----------------------------------------------------------------------------
-- 2. FIX PRODUCTS STOCK & PRODUCT TYPE CONSTRAINT
-- ----------------------------------------------------------------------------
-- Add current_stock column to products if missing and keep synced with stock_quantity
ALTER TABLE products ADD COLUMN IF NOT EXISTS current_stock NUMERIC(12,2) DEFAULT 0;
ALTER TABLE products ADD COLUMN IF NOT EXISTS stock_quantity NUMERIC(12,2) DEFAULT 0;

-- Trigger to sync stock_quantity and current_stock bi-directionally
CREATE OR REPLACE FUNCTION sync_product_stock_cols()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.current_stock IS DISTINCT FROM OLD.current_stock THEN
        NEW.stock_quantity := NEW.current_stock;
    ELSIF NEW.stock_quantity IS DISTINCT FROM OLD.stock_quantity THEN
        NEW.current_stock := NEW.stock_quantity;
    END IF;
    
    -- Relax/sanitize product_type check
    IF NEW.product_type IS NULL OR NEW.product_type = '' THEN
        NEW.product_type := 'SPARE_PART';
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_product_stock_cols ON products;
CREATE TRIGGER trg_sync_product_stock_cols
BEFORE INSERT OR UPDATE ON products
FOR EACH ROW EXECUTE FUNCTION sync_product_stock_cols();

-- Drop overly strict check constraint on product_type if present
ALTER TABLE products DROP CONSTRAINT IF EXISTS chk_product_type;
ALTER TABLE products DROP CONSTRAINT IF EXISTS products_product_type_check;


-- ----------------------------------------------------------------------------
-- 3. FIX GRN STOCK FUNCTION (process_grn_stock)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION process_grn_stock()
RETURNS TRIGGER AS $$
DECLARE
    item RECORD;
BEGIN
    FOR item IN SELECT product_id, quantity FROM grn_lines WHERE grn_id = NEW.id LOOP
        UPDATE products 
        SET stock_quantity = COALESCE(stock_quantity, 0) + item.quantity,
            current_stock  = COALESCE(current_stock, 0) + item.quantity
        WHERE id = item.product_id;
    END LOOP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- ----------------------------------------------------------------------------
-- 4. FIX DELIVERY NOTE LINES (description column mismatch)
-- ----------------------------------------------------------------------------
ALTER TABLE delivery_note_lines ADD COLUMN IF NOT EXISTS description VARCHAR(255);

CREATE OR REPLACE FUNCTION sync_delivery_note_line_desc()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.description IS NULL OR NEW.description = '' THEN
        SELECT name INTO NEW.description FROM products WHERE id = NEW.product_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_delivery_note_line_desc ON delivery_note_lines;
CREATE TRIGGER trg_sync_delivery_note_line_desc
BEFORE INSERT OR UPDATE ON delivery_note_lines
FOR EACH ROW EXECUTE FUNCTION sync_delivery_note_line_desc();