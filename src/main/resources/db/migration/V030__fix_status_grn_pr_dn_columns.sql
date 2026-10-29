-- =============================================================
-- V030 : Fix quotation status, GRN, Purchase Returns, Delivery Notes
-- =============================================================

-- 1. QUOTATIONS STATUS CHECK — allow INVOICED / CONVERTED / ORDERED
ALTER TABLE quotations DROP CONSTRAINT IF EXISTS quotations_status_check;
ALTER TABLE quotations DROP CONSTRAINT IF EXISTS chk_quotation_status;
ALTER TABLE quotations DROP CONSTRAINT IF EXISTS quotations_status_chk;

ALTER TABLE quotations ADD CONSTRAINT quotations_status_check
    CHECK (status IS NULL OR status IN (
        'DRAFT', 'SENT', 'ACCEPTED', 'REJECTED', 'EXPIRED',
        'CONVERTED', 'ORDERED', 'INVOICED', 'CANCELLED', 'CLOSED'
    ));

-- Normalize any odd statuses
UPDATE quotations SET status = 'DRAFT' WHERE status IS NULL OR btrim(status) = '';


-- 2. GOODS RECEIVED NOTES — add audit + common columns
ALTER TABLE goods_received_notes ADD COLUMN IF NOT EXISTS created_by INT;
ALTER TABLE goods_received_notes ADD COLUMN IF NOT EXISTS updated_by INT;
ALTER TABLE goods_received_notes ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE goods_received_notes ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE goods_received_notes ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE goods_received_notes ADD COLUMN IF NOT EXISTS status VARCHAR(30) DEFAULT 'POSTED';


-- 3. PURCHASE RETURNS — add bill_id and aliases
ALTER TABLE purchase_returns ADD COLUMN IF NOT EXISTS bill_id INT;
ALTER TABLE purchase_returns ADD COLUMN IF NOT EXISTS supplier_bill_id INT;
ALTER TABLE purchase_returns ADD COLUMN IF NOT EXISTS po_id INT;
ALTER TABLE purchase_returns ADD COLUMN IF NOT EXISTS purchase_order_id INT;
ALTER TABLE purchase_returns ADD COLUMN IF NOT EXISTS supplier_id INT;
ALTER TABLE purchase_returns ADD COLUMN IF NOT EXISTS return_number VARCHAR(50);
ALTER TABLE purchase_returns ADD COLUMN IF NOT EXISTS return_date DATE DEFAULT CURRENT_DATE;
ALTER TABLE purchase_returns ADD COLUMN IF NOT EXISTS reason TEXT;
ALTER TABLE purchase_returns ADD COLUMN IF NOT EXISTS status VARCHAR(30) DEFAULT 'DRAFT';
ALTER TABLE purchase_returns ADD COLUMN IF NOT EXISTS total_amount NUMERIC(15,2) DEFAULT 0;
ALTER TABLE purchase_returns ADD COLUMN IF NOT EXISTS created_by INT;
ALTER TABLE purchase_returns ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

UPDATE purchase_returns
   SET bill_id = COALESCE(bill_id, supplier_bill_id),
       supplier_bill_id = COALESCE(supplier_bill_id, bill_id),
       po_id = COALESCE(po_id, purchase_order_id),
       purchase_order_id = COALESCE(purchase_order_id, po_id);

CREATE OR REPLACE FUNCTION fn_sync_pr_ids()
RETURNS trigger AS $$
BEGIN
    IF NEW.bill_id IS NULL THEN NEW.bill_id := NEW.supplier_bill_id; END IF;
    IF NEW.supplier_bill_id IS NULL THEN NEW.supplier_bill_id := NEW.bill_id; END IF;
    IF NEW.po_id IS NULL THEN NEW.po_id := NEW.purchase_order_id; END IF;
    IF NEW.purchase_order_id IS NULL THEN NEW.purchase_order_id := NEW.po_id; END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_pr_ids ON purchase_returns;
CREATE TRIGGER trg_sync_pr_ids
BEFORE INSERT OR UPDATE ON purchase_returns
FOR EACH ROW EXECUTE FUNCTION fn_sync_pr_ids();


-- 4. DELIVERY NOTES — vehicle / driver columns for Jasper
ALTER TABLE delivery_notes ADD COLUMN IF NOT EXISTS vehicle_plate VARCHAR(50);
ALTER TABLE delivery_notes ADD COLUMN IF NOT EXISTS vehicle_no VARCHAR(50);
ALTER TABLE delivery_notes ADD COLUMN IF NOT EXISTS vehicle_number VARCHAR(50);
ALTER TABLE delivery_notes ADD COLUMN IF NOT EXISTS driver_name VARCHAR(100);
ALTER TABLE delivery_notes ADD COLUMN IF NOT EXISTS driver VARCHAR(100);
ALTER TABLE delivery_notes ADD COLUMN IF NOT EXISTS dn_number VARCHAR(50);
ALTER TABLE delivery_notes ADD COLUMN IF NOT EXISTS delivery_note_number VARCHAR(50);

UPDATE delivery_notes SET
    vehicle_plate   = COALESCE(vehicle_plate, vehicle_no, vehicle_number, ''),
    vehicle_no      = COALESCE(vehicle_no, vehicle_plate, vehicle_number, ''),
    vehicle_number  = COALESCE(vehicle_number, vehicle_plate, vehicle_no, ''),
    driver_name     = COALESCE(driver_name, driver, ''),
    driver          = COALESCE(driver, driver_name, ''),
    dn_number       = COALESCE(dn_number, delivery_note_number, 'DN-' || id),
    delivery_note_number = COALESCE(delivery_note_number, dn_number, 'DN-' || id);

CREATE OR REPLACE FUNCTION fn_sync_dn_vehicle()
RETURNS trigger AS $$
BEGIN
    IF NEW.vehicle_plate IS NULL OR btrim(NEW.vehicle_plate) = '' THEN
        NEW.vehicle_plate := COALESCE(NEW.vehicle_no, NEW.vehicle_number, '');
    END IF;
    IF NEW.vehicle_no IS NULL OR btrim(NEW.vehicle_no) = '' THEN
        NEW.vehicle_no := COALESCE(NEW.vehicle_plate, NEW.vehicle_number, '');
    END IF;
    IF NEW.vehicle_number IS NULL OR btrim(NEW.vehicle_number) = '' THEN
        NEW.vehicle_number := COALESCE(NEW.vehicle_plate, NEW.vehicle_no, '');
    END IF;
    IF NEW.driver_name IS NULL OR btrim(NEW.driver_name) = '' THEN
        NEW.driver_name := COALESCE(NEW.driver, '');
    END IF;
    IF NEW.driver IS NULL OR btrim(NEW.driver) = '' THEN
        NEW.driver := COALESCE(NEW.driver_name, '');
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_dn_vehicle ON delivery_notes;
CREATE TRIGGER trg_sync_dn_vehicle
BEFORE INSERT OR UPDATE ON delivery_notes
FOR EACH ROW EXECUTE FUNCTION fn_sync_dn_vehicle();