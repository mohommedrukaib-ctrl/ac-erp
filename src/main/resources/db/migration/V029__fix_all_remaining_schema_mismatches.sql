-- =============================================================
-- V029 : Fix Delivery Notes, Purchase Order Lines, GRN, and Invoices Schema
-- =============================================================

-- 1. DELIVERY NOTES: Sync 'dn_date', 'delivery_date', and 'date'
ALTER TABLE delivery_notes ADD COLUMN IF NOT EXISTS dn_date DATE;
ALTER TABLE delivery_notes ADD COLUMN IF NOT EXISTS delivery_date DATE;
ALTER TABLE delivery_notes ADD COLUMN IF NOT EXISTS date DATE;

UPDATE delivery_notes 
   SET dn_date = COALESCE(dn_date, delivery_date, date, CURRENT_DATE),
       delivery_date = COALESCE(delivery_date, dn_date, date, CURRENT_DATE),
       date = COALESCE(date, dn_date, delivery_date, CURRENT_DATE);

CREATE OR REPLACE FUNCTION fn_sync_dn_dates()
RETURNS trigger AS $$
BEGIN
    IF NEW.dn_date IS NULL THEN NEW.dn_date := COALESCE(NEW.delivery_date, NEW.date); END IF;
    IF NEW.delivery_date IS NULL THEN NEW.delivery_date := COALESCE(NEW.dn_date, NEW.date); END IF;
    IF NEW.date IS NULL THEN NEW.date := COALESCE(NEW.dn_date, NEW.delivery_date); END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_dn_dates ON delivery_notes;
CREATE TRIGGER trg_sync_dn_dates
BEFORE INSERT OR UPDATE ON delivery_notes
FOR EACH ROW EXECUTE FUNCTION fn_sync_dn_dates();


-- 2. PURCHASE ORDER LINES: Sync 'unit_cost' and 'unit_price'
ALTER TABLE purchase_order_lines ADD COLUMN IF NOT EXISTS unit_cost NUMERIC(15,2);
ALTER TABLE purchase_order_lines ADD COLUMN IF NOT EXISTS unit_price NUMERIC(15,2);

UPDATE purchase_order_lines 
   SET unit_cost = COALESCE(unit_cost, unit_price, 0.00),
       unit_price = COALESCE(unit_price, unit_cost, 0.00);

CREATE OR REPLACE FUNCTION fn_sync_pol_prices()
RETURNS trigger AS $$
BEGIN
    IF NEW.unit_cost IS NULL THEN NEW.unit_cost := NEW.unit_price; END IF;
    IF NEW.unit_price IS NULL THEN NEW.unit_price := NEW.unit_cost; END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_pol_prices ON purchase_order_lines;
CREATE TRIGGER trg_sync_pol_prices
BEFORE INSERT OR UPDATE ON purchase_order_lines
FOR EACH ROW EXECUTE FUNCTION fn_sync_pol_prices();


-- 3. GOODS RECEIVED NOTES: Add missing 'delivery_note_ref', 'delivery_note_no', 'reference_no'
ALTER TABLE goods_received_notes ADD COLUMN IF NOT EXISTS delivery_note_ref VARCHAR(100);
ALTER TABLE goods_received_notes ADD COLUMN IF NOT EXISTS delivery_note_no VARCHAR(100);
ALTER TABLE goods_received_notes ADD COLUMN IF NOT EXISTS reference_no VARCHAR(100);

UPDATE goods_received_notes 
   SET delivery_note_ref = COALESCE(delivery_note_ref, delivery_note_no, reference_no, 'GRN-' || id),
       delivery_note_no  = COALESCE(delivery_note_no, delivery_note_ref, reference_no, 'GRN-' || id),
       reference_no      = COALESCE(reference_no, delivery_note_ref, delivery_note_no, 'GRN-' || id);

CREATE OR REPLACE FUNCTION fn_sync_grn_refs()
RETURNS trigger AS $$
BEGIN
    IF NEW.delivery_note_ref IS NULL OR btrim(NEW.delivery_note_ref) = '' THEN
        NEW.delivery_note_ref := COALESCE(NEW.delivery_note_no, NEW.reference_no);
    END IF;
    IF NEW.delivery_note_no IS NULL OR btrim(NEW.delivery_note_no) = '' THEN
        NEW.delivery_note_no := COALESCE(NEW.delivery_note_ref, NEW.reference_no);
    END IF;
    IF NEW.reference_no IS NULL OR btrim(NEW.reference_no) = '' THEN
        NEW.reference_no := COALESCE(NEW.delivery_note_ref, NEW.delivery_note_no);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_grn_refs ON goods_received_notes;
CREATE TRIGGER trg_sync_grn_refs
BEFORE INSERT OR UPDATE ON goods_received_notes
FOR EACH ROW EXECUTE FUNCTION fn_sync_grn_refs();


-- 4. INVOICES: Add missing 'quotation_id' and 'quote_id'
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS quotation_id INT;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS quote_id INT;

UPDATE invoices 
   SET quotation_id = COALESCE(quotation_id, quote_id),
       quote_id     = COALESCE(quote_id, quotation_id);

CREATE OR REPLACE FUNCTION fn_sync_invoice_quote_ids()
RETURNS trigger AS $$
BEGIN
    IF NEW.quotation_id IS NULL THEN NEW.quotation_id := NEW.quote_id; END IF;
    IF NEW.quote_id IS NULL THEN NEW.quote_id := NEW.quotation_id; END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_invoice_quote_ids ON invoices;
CREATE TRIGGER trg_sync_invoice_quote_ids
BEFORE INSERT OR UPDATE ON invoices
FOR EACH ROW EXECUTE FUNCTION fn_sync_invoice_quote_ids();