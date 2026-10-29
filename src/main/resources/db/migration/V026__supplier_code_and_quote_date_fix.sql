-- =============================================================
-- V026 : Auto-generate supplier_code/customer_code and sync quote_date
-- =============================================================

-- 1. SUPPLIERS AUTO-CODE TRIGGER & SEQUENCE
CREATE SEQUENCE IF NOT EXISTS supplier_code_seq START 1;

UPDATE suppliers
   SET supplier_code = 'SUP-' || LPAD(id::text, 4, '0')
 WHERE supplier_code IS NULL OR btrim(supplier_code) = '';

CREATE OR REPLACE FUNCTION fn_gen_supplier_code()
RETURNS trigger AS $$
BEGIN
    IF NEW.supplier_code IS NULL OR btrim(NEW.supplier_code) = '' THEN
        LOOP
            NEW.supplier_code := 'SUP-' || LPAD(nextval('supplier_code_seq')::text, 4, '0');
            EXIT WHEN NOT EXISTS (
                SELECT 1 FROM suppliers s WHERE s.supplier_code = NEW.supplier_code
            );
        END LOOP;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_gen_supplier_code ON suppliers;
CREATE TRIGGER trg_gen_supplier_code
BEFORE INSERT OR UPDATE ON suppliers
FOR EACH ROW EXECUTE FUNCTION fn_gen_supplier_code();

SELECT setval('supplier_code_seq',
    GREATEST(
        COALESCE((SELECT MAX(id) FROM suppliers), 0) + 1,
        1
    )
);

-- 2. CUSTOMERS AUTO-CODE TRIGGER & SEQUENCE
CREATE SEQUENCE IF NOT EXISTS customer_code_seq START 1;

UPDATE customers
   SET customer_code = 'CUS-' || LPAD(id::text, 4, '0')
 WHERE customer_code IS NULL OR btrim(customer_code) = '';

CREATE OR REPLACE FUNCTION fn_gen_customer_code()
RETURNS trigger AS $$
BEGIN
    IF NEW.customer_code IS NULL OR btrim(NEW.customer_code) = '' THEN
        LOOP
            NEW.customer_code := 'CUS-' || LPAD(nextval('customer_code_seq')::text, 4, '0');
            EXIT WHEN NOT EXISTS (
                SELECT 1 FROM customers c WHERE c.customer_code = NEW.customer_code
            );
        END LOOP;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_gen_customer_code ON customers;
CREATE TRIGGER trg_gen_customer_code
BEFORE INSERT OR UPDATE ON customers
FOR EACH ROW EXECUTE FUNCTION fn_gen_customer_code();

SELECT setval('customer_code_seq',
    GREATEST(
        COALESCE((SELECT MAX(id) FROM customers), 0) + 1,
        1
    )
);

-- 3. QUOTATION COLUMN ALIASES FOR JASPER REPORTS (quote_date <-> quotation_date)
ALTER TABLE quotations ADD COLUMN IF NOT EXISTS quote_date DATE;
ALTER TABLE quotations ADD COLUMN IF NOT EXISTS quote_number VARCHAR(50);
ALTER TABLE quotations ADD COLUMN IF NOT EXISTS quotation_date DATE;
ALTER TABLE quotations ADD COLUMN IF NOT EXISTS quotation_number VARCHAR(50);

UPDATE quotations SET 
    quote_date = COALESCE(quote_date, quotation_date, CURRENT_DATE),
    quotation_date = COALESCE(quotation_date, quote_date, CURRENT_DATE),
    quote_number = COALESCE(quote_number, quotation_number, 'QTN-0000'),
    quotation_number = COALESCE(quotation_number, quote_number, 'QTN-0000');

CREATE OR REPLACE FUNCTION fn_sync_quotation_columns()
RETURNS trigger AS $$
BEGIN
    IF NEW.quote_date IS NULL THEN NEW.quote_date := NEW.quotation_date; END IF;
    IF NEW.quotation_date IS NULL THEN NEW.quotation_date := NEW.quote_date; END IF;
    IF NEW.quote_number IS NULL OR btrim(NEW.quote_number) = '' THEN NEW.quote_number := NEW.quotation_number; END IF;
    IF NEW.quotation_number IS NULL OR btrim(NEW.quotation_number) = '' THEN NEW.quotation_number := NEW.quote_number; END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_quotation_columns ON quotations;
CREATE TRIGGER trg_sync_quotation_columns
BEFORE INSERT OR UPDATE ON quotations
FOR EACH ROW EXECUTE FUNCTION fn_sync_quotation_columns();