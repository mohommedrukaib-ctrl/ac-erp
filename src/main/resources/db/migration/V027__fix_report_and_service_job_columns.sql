-- =============================================================
-- V027 : Schema Synchronization for Reports & ServiceJob Queries
-- =============================================================

-- 1. PRODUCTS: Synchronize 'code' and 'product_code'
ALTER TABLE products ADD COLUMN IF NOT EXISTS code VARCHAR(100);
ALTER TABLE products ADD COLUMN IF NOT EXISTS product_code VARCHAR(100);

UPDATE products 
   SET code = COALESCE(code, product_code, 'PRD-' || id),
       product_code = COALESCE(product_code, code, 'PRD-' || id);

CREATE OR REPLACE FUNCTION fn_sync_product_code_cols()
RETURNS trigger AS $$
BEGIN
    IF NEW.code IS NULL OR btrim(NEW.code) = '' THEN 
        NEW.code := NEW.product_code; 
    END IF;
    IF NEW.product_code IS NULL OR btrim(NEW.product_code) = '' THEN 
        NEW.product_code := NEW.code; 
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_product_code_cols ON products;
CREATE TRIGGER trg_sync_product_code_cols
BEFORE INSERT OR UPDATE ON products
FOR EACH ROW EXECUTE FUNCTION fn_sync_product_code_cols();


-- 2. PURCHASE ORDERS: Synchronize 'po_date' and 'order_date'
ALTER TABLE purchase_orders ADD COLUMN IF NOT EXISTS po_date DATE;
ALTER TABLE purchase_orders ADD COLUMN IF NOT EXISTS order_date DATE;

UPDATE purchase_orders 
   SET po_date = COALESCE(po_date, order_date, CURRENT_DATE),
       order_date = COALESCE(order_date, po_date, CURRENT_DATE);

CREATE OR REPLACE FUNCTION fn_sync_po_dates()
RETURNS trigger AS $$
BEGIN
    IF NEW.po_date IS NULL THEN NEW.po_date := NEW.order_date; END IF;
    IF NEW.order_date IS NULL THEN NEW.order_date := NEW.po_date; END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_po_dates ON purchase_orders;
CREATE TRIGGER trg_sync_po_dates
BEFORE INSERT OR UPDATE ON purchase_orders
FOR EACH ROW EXECUTE FUNCTION fn_sync_po_dates();


-- 3. SALES ORDER LINES: Synchronize 'order_id', 'sales_order_id', and 'so_id'
ALTER TABLE sales_order_lines ADD COLUMN IF NOT EXISTS order_id INT;
ALTER TABLE sales_order_lines ADD COLUMN IF NOT EXISTS sales_order_id INT;
ALTER TABLE sales_order_lines ADD COLUMN IF NOT EXISTS so_id INT;

UPDATE sales_order_lines 
   SET order_id = COALESCE(order_id, sales_order_id, so_id),
       sales_order_id = COALESCE(sales_order_id, order_id, so_id),
       so_id = COALESCE(so_id, sales_order_id, order_id);

CREATE OR REPLACE FUNCTION fn_sync_sol_ids()
RETURNS trigger AS $$
BEGIN
    IF NEW.order_id IS NULL THEN NEW.order_id := COALESCE(NEW.sales_order_id, NEW.so_id); END IF;
    IF NEW.sales_order_id IS NULL THEN NEW.sales_order_id := COALESCE(NEW.order_id, NEW.so_id); END IF;
    IF NEW.so_id IS NULL THEN NEW.so_id := COALESCE(NEW.sales_order_id, NEW.order_id); END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_sol_ids ON sales_order_lines;
CREATE TRIGGER trg_sync_sol_ids
BEFORE INSERT OR UPDATE ON sales_order_lines
FOR EACH ROW EXECUTE FUNCTION fn_sync_sol_ids();


-- 4. PURCHASE ORDER LINES: Synchronize 'po_id' and 'purchase_order_id'
ALTER TABLE purchase_order_lines ADD COLUMN IF NOT EXISTS po_id INT;
ALTER TABLE purchase_order_lines ADD COLUMN IF NOT EXISTS purchase_order_id INT;

UPDATE purchase_order_lines 
   SET po_id = COALESCE(po_id, purchase_order_id),
       purchase_order_id = COALESCE(purchase_order_id, po_id);

CREATE OR REPLACE FUNCTION fn_sync_pol_ids()
RETURNS trigger AS $$
BEGIN
    IF NEW.po_id IS NULL THEN NEW.po_id := NEW.purchase_order_id; END IF;
    IF NEW.purchase_order_id IS NULL THEN NEW.purchase_order_id := NEW.po_id; END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_pol_ids ON purchase_order_lines;
CREATE TRIGGER trg_sync_pol_ids
BEFORE INSERT OR UPDATE ON purchase_order_lines
FOR EACH ROW EXECUTE FUNCTION fn_sync_pol_ids();