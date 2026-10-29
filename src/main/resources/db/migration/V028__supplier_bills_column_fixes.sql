-- =============================================================
-- V028 : Fix supplier_bills schema columns
-- =============================================================

-- Add missing columns to supplier_bills if they don't exist
ALTER TABLE supplier_bills ADD COLUMN IF NOT EXISTS supplier_invoice_ref VARCHAR(100);
ALTER TABLE supplier_bills ADD COLUMN IF NOT EXISTS supplier_invoice_no VARCHAR(100);
ALTER TABLE supplier_bills ADD COLUMN IF NOT EXISTS bill_number VARCHAR(100);

-- Backfill values across alias columns
UPDATE supplier_bills
   SET supplier_invoice_ref = COALESCE(supplier_invoice_ref, supplier_invoice_no, bill_number, 'BILL-' || id),
       supplier_invoice_no  = COALESCE(supplier_invoice_no, supplier_invoice_ref, bill_number, 'BILL-' || id),
       bill_number          = COALESCE(bill_number, supplier_invoice_ref, supplier_invoice_no, 'BILL-' || id);

-- Keep column aliases automatically synchronized
CREATE OR REPLACE FUNCTION fn_sync_supplier_bill_cols()
RETURNS trigger AS $$
BEGIN
    IF NEW.supplier_invoice_ref IS NULL OR btrim(NEW.supplier_invoice_ref) = '' THEN
        NEW.supplier_invoice_ref := COALESCE(NEW.supplier_invoice_no, NEW.bill_number);
    END IF;
    IF NEW.supplier_invoice_no IS NULL OR btrim(NEW.supplier_invoice_no) = '' THEN
        NEW.supplier_invoice_no := COALESCE(NEW.supplier_invoice_ref, NEW.bill_number);
    END IF;
    IF NEW.bill_number IS NULL OR btrim(NEW.bill_number) = '' THEN
        NEW.bill_number := COALESCE(NEW.supplier_invoice_ref, NEW.supplier_invoice_no);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_supplier_bill_cols ON supplier_bills;
CREATE TRIGGER trg_sync_supplier_bill_cols
BEFORE INSERT OR UPDATE ON supplier_bills
FOR EACH ROW EXECUTE FUNCTION fn_sync_supplier_bill_cols();