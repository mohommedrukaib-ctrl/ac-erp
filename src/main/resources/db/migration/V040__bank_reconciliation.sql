ALTER TABLE journal_entry_lines
    ADD COLUMN IF NOT EXISTS cleared_status BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS cleared_date DATE,
    ADD COLUMN IF NOT EXISTS bank_reconciliation_id INTEGER;

-- Bank Reconciliations table
CREATE TABLE IF NOT EXISTS bank_reconciliations (
    id SERIAL PRIMARY KEY,
    account_id INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    statement_date DATE NOT NULL,
    beginning_balance NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    statement_ending_balance NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    cleared_deposits_count INTEGER NOT NULL DEFAULT 0,
    cleared_deposits_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    cleared_checks_count INTEGER NOT NULL DEFAULT 0,
    cleared_checks_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    cleared_balance NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    difference NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    notes TEXT,
    reconciled_at TIMESTAMP,
    reconciled_by INTEGER REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_recon_status CHECK (status IN ('IN_PROGRESS', 'RECONCILED'))
);

CREATE INDEX IF NOT EXISTS idx_bank_recon_account ON bank_reconciliations(account_id);
CREATE INDEX IF NOT EXISTS idx_bank_recon_date ON bank_reconciliations(statement_date);

-- Foreign key back to bank_reconciliations on journal_entry_lines
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'fk_jel_bank_recon'
    ) THEN
        ALTER TABLE journal_entry_lines
            ADD CONSTRAINT fk_jel_bank_recon
            FOREIGN KEY (bank_reconciliation_id) REFERENCES bank_reconciliations(id) ON DELETE SET NULL;
    END IF;
END $$;

-- Bank Statement Imports table
CREATE TABLE IF NOT EXISTS bank_statement_imports (
    id SERIAL PRIMARY KEY,
    account_id INTEGER NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    filename VARCHAR(255) NOT NULL,
    imported_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    imported_by INTEGER REFERENCES users(id) ON DELETE SET NULL
);

-- Bank Statement Lines table
CREATE TABLE IF NOT EXISTS bank_statement_lines (
    id SERIAL PRIMARY KEY,
    import_id INTEGER NOT NULL REFERENCES bank_statement_imports(id) ON DELETE CASCADE,
    trans_date DATE NOT NULL,
    description VARCHAR(255),
    amount NUMERIC(15,2) NOT NULL,
    reference_no VARCHAR(50),
    is_matched BOOLEAN NOT NULL DEFAULT FALSE,
    matched_journal_entry_line_id INTEGER REFERENCES journal_entry_lines(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_bs_lines_import ON bank_statement_lines(import_id);