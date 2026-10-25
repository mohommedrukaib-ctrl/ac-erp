-- Migration V007: Ensure both total_price and line_total exist in line items tables
DO $$
BEGIN
    -- quotation_lines table updates
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='quotation_lines' AND column_name='total_price') THEN
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='quotation_lines' AND column_name='line_total') THEN
            ALTER TABLE quotation_lines ADD COLUMN total_price NUMERIC(12,2);
            UPDATE quotation_lines SET total_price = line_total WHERE total_price IS NULL;
        ELSE
            ALTER TABLE quotation_lines ADD COLUMN total_price NUMERIC(12,2) DEFAULT 0.00;
        END IF;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='quotation_lines' AND column_name='line_total') THEN
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='quotation_lines' AND column_name='total_price') THEN
            ALTER TABLE quotation_lines ADD COLUMN line_total NUMERIC(12,2);
            UPDATE quotation_lines SET line_total = total_price WHERE line_total IS NULL;
        ELSE
            ALTER TABLE quotation_lines ADD COLUMN line_total NUMERIC(12,2) DEFAULT 0.00;
        END IF;
    END IF;

    -- invoice_lines table updates
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='invoice_lines' AND column_name='total_price') THEN
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='invoice_lines' AND column_name='line_total') THEN
            ALTER TABLE invoice_lines ADD COLUMN total_price NUMERIC(12,2);
            UPDATE invoice_lines SET total_price = line_total WHERE total_price IS NULL;
        ELSE
            ALTER TABLE invoice_lines ADD COLUMN total_price NUMERIC(12,2) DEFAULT 0.00;
        END IF;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='invoice_lines' AND column_name='line_total') THEN
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='invoice_lines' AND column_name='total_price') THEN
            ALTER TABLE invoice_lines ADD COLUMN line_total NUMERIC(12,2);
            UPDATE invoice_lines SET line_total = total_price WHERE line_total IS NULL;
        ELSE
            ALTER TABLE invoice_lines ADD COLUMN line_total NUMERIC(12,2) DEFAULT 0.00;
        END IF;
    END IF;

    -- sales_order_lines table updates
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='sales_order_lines' AND column_name='total_price') THEN
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='sales_order_lines' AND column_name='line_total') THEN
            ALTER TABLE sales_order_lines ADD COLUMN total_price NUMERIC(12,2);
            UPDATE sales_order_lines SET total_price = line_total WHERE total_price IS NULL;
        ELSE
            ALTER TABLE sales_order_lines ADD COLUMN total_price NUMERIC(12,2) DEFAULT 0.00;
        END IF;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='sales_order_lines' AND column_name='line_total') THEN
        IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='sales_order_lines' AND column_name='total_price') THEN
            ALTER TABLE sales_order_lines ADD COLUMN line_total NUMERIC(12,2);
            UPDATE sales_order_lines SET line_total = total_price WHERE line_total IS NULL;
        ELSE
            ALTER TABLE sales_order_lines ADD COLUMN line_total NUMERIC(12,2) DEFAULT 0.00;
        END IF;
    END IF;
END $$;