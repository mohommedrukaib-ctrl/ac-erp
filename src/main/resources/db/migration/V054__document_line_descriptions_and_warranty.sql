ALTER TABLE invoice_lines
    ADD COLUMN IF NOT EXISTS line_description TEXT,
    ADD COLUMN IF NOT EXISTS warranty TEXT;

ALTER TABLE quotation_lines
    ADD COLUMN IF NOT EXISTS line_description TEXT,
    ADD COLUMN IF NOT EXISTS warranty TEXT;

ALTER TABLE grn_lines
    ADD COLUMN IF NOT EXISTS line_description TEXT,
    ADD COLUMN IF NOT EXISTS warranty TEXT;
