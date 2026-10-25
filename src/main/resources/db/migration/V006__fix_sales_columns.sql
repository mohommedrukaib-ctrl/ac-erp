-- Migration V006: Ensure column aliases for quotations and invoices exist
DO $$
BEGIN
    -- Quotations table updates
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='quotations' AND column_name='issue_date') THEN
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='quotations' AND column_name='quote_date') THEN
            ALTER TABLE quotations RENAME COLUMN quote_date TO issue_date;
        ELSE
            ALTER TABLE quotations ADD COLUMN issue_date DATE NOT NULL DEFAULT CURRENT_DATE;
        END IF;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='quotations' AND column_name='expiry_date') THEN
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='quotations' AND column_name='valid_until') THEN
            ALTER TABLE quotations RENAME COLUMN valid_until TO expiry_date;
        ELSE
            ALTER TABLE quotations ADD COLUMN expiry_date DATE;
        END IF;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='quotations' AND column_name='tax_amount') THEN
        ALTER TABLE quotations ADD COLUMN tax_amount NUMERIC(12,2) DEFAULT 0.00;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='quotations' AND column_name='total_amount') THEN
        ALTER TABLE quotations ADD COLUMN total_amount NUMERIC(12,2) DEFAULT 0.00;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='quotations' AND column_name='tax_total') THEN
        ALTER TABLE quotations ADD COLUMN tax_total NUMERIC(12,2) DEFAULT 0.00;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='quotations' AND column_name='grand_total') THEN
        ALTER TABLE quotations ADD COLUMN grand_total NUMERIC(12,2) DEFAULT 0.00;
    END IF;

    -- Invoices table updates
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='invoices' AND column_name='tax_amount') THEN
        ALTER TABLE invoices ADD COLUMN tax_amount NUMERIC(12,2) DEFAULT 0.00;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='invoices' AND column_name='total_amount') THEN
        ALTER TABLE invoices ADD COLUMN total_amount NUMERIC(12,2) DEFAULT 0.00;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='invoices' AND column_name='tax_total') THEN
        ALTER TABLE invoices ADD COLUMN tax_total NUMERIC(12,2) DEFAULT 0.00;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='invoices' AND column_name='grand_total') THEN
        ALTER TABLE invoices ADD COLUMN grand_total NUMERIC(12,2) DEFAULT 0.00;
    END IF;
END $$;