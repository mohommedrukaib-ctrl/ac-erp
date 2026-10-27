-- Chart of Accounts table
CREATE TABLE IF NOT EXISTS accounts (
    id SERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    account_type VARCHAR(30) NOT NULL CHECK (account_type IN ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE')),
    balance NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Seed Default Chart of Accounts
INSERT INTO accounts (code, name, account_type, is_system) VALUES
('1010', 'Cash on Hand', 'ASSET', TRUE),
('1020', 'Bank Account', 'ASSET', TRUE),
('1200', 'Accounts Receivable', 'ASSET', TRUE),
('1300', 'Inventory Asset', 'ASSET', TRUE),
('2100', 'Accounts Payable', 'LIABILITY', TRUE),
('2200', 'VAT / Tax Payable', 'LIABILITY', TRUE),
('4000', 'Sales Revenue', 'REVENUE', TRUE),
('5000', 'Cost of Goods Sold', 'EXPENSE', TRUE),
('5100', 'Purchase Expense', 'EXPENSE', TRUE)
ON CONFLICT (code) DO NOTHING;

-- Journal Entries header
CREATE TABLE IF NOT EXISTS journal_entries (
    id SERIAL PRIMARY KEY,
    entry_number VARCHAR(50) NOT NULL UNIQUE,
    entry_date DATE NOT NULL DEFAULT CURRENT_DATE,
    reference_type VARCHAR(50),
    reference_id INT,
    memo TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'POSTED',
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by INT REFERENCES users(id) ON DELETE SET NULL
);

-- Journal Entry line items (Debit & Credit)
CREATE TABLE IF NOT EXISTS journal_entry_lines (
    id SERIAL PRIMARY KEY,
    journal_entry_id INT NOT NULL REFERENCES journal_entries(id) ON DELETE CASCADE,
    account_id INT NOT NULL REFERENCES accounts(id),
    debit NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    credit NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    description VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_jel_entry_id ON journal_entry_lines(journal_entry_id);
CREATE INDEX IF NOT EXISTS idx_jel_account_id ON journal_entry_lines(account_id);