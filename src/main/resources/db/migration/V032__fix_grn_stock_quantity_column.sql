-- V032: Synchronize grn_lines column aliases and drop redundant process_grn_stock trigger.
-- GRN stock posting is handled in Java (GoodsReceivedNoteRepository.saveAndPostStock).

-- 1. Ensure grn_lines has alias columns so queries using any standard naming don't fail
ALTER TABLE grn_lines ADD COLUMN IF NOT EXISTS qty_received NUMERIC(12,2);
ALTER TABLE grn_lines ADD COLUMN IF NOT EXISTS quantity_received NUMERIC(12,2);
ALTER TABLE grn_lines ADD COLUMN IF NOT EXISTS quantity NUMERIC(12,2);

-- 2. Drop triggers that cause double stock posting and missing column runtime errors
DROP TRIGGER IF EXISTS trg_process_grn_stock ON grn_lines;
DROP TRIGGER IF EXISTS trg_process_grn_stock ON goods_received_notes;
DROP TRIGGER IF EXISTS process_grn_stock ON grn_lines;
DROP TRIGGER IF EXISTS process_grn_stock ON goods_received_notes;

-- 3. Dynamic clean-up of any triggers calling process_grn_stock
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT t.tgname, t.tgrelid::regclass AS tbl
        FROM pg_trigger t
        JOIN pg_proc p ON t.tgfoid = p.oid
        WHERE p.proname = 'process_grn_stock'
          AND NOT t.tgisinternal
    LOOP
        EXECUTE format('DROP TRIGGER IF EXISTS %I ON %s', r.tgname, r.tbl);
    END LOOP;
END $$;

-- 4. Drop the function
DROP FUNCTION IF EXISTS process_grn_stock() CASCADE;