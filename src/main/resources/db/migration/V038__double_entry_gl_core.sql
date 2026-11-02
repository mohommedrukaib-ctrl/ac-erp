-- 1. Enhance Chart of Accounts
ALTER TABLE accounts
    ADD COLUMN IF NOT EXISTS parent_id INT REFERENCES accounts(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS normal_balance VARCHAR(6) DEFAULT 'DEBIT'
        CHECK (normal_balance IN ('DEBIT', 'CREDIT')),
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP;

-- Backfill normal_balance by account type
UPDATE accounts SET normal_balance = 'DEBIT'
WHERE account_type IN ('ASSET', 'EXPENSE') AND (normal_balance IS NULL OR normal_balance = 'DEBIT');

UPDATE accounts SET normal_balance = 'CREDIT'
WHERE account_type IN ('LIABILITY', 'EQUITY', 'REVENUE');

-- 2. Expand system COA (safe upsert)
INSERT INTO accounts (code, name, account_type, normal_balance, is_system) VALUES
('1000', 'Current Assets',              'ASSET',     'DEBIT',  TRUE),
('1010', 'Cash on Hand',                'ASSET',     'DEBIT',  TRUE),
('1020', 'Bank Account',                'ASSET',     'DEBIT',  TRUE),
('1200', 'Accounts Receivable',         'ASSET',     'DEBIT',  TRUE),
('1300', 'Inventory Asset',             'ASSET',     'DEBIT',  TRUE),
('1400', 'Other Current Assets',        'ASSET',     'DEBIT',  TRUE),
('1500', 'Fixed Assets',                'ASSET',     'DEBIT',  TRUE),
('1510', 'Equipment',                   'ASSET',     'DEBIT',  TRUE),
('1520', 'Accumulated Depreciation',    'ASSET',     'CREDIT', TRUE),
('2000', 'Current Liabilities',         'LIABILITY', 'CREDIT', TRUE),
('2100', 'Accounts Payable',            'LIABILITY', 'CREDIT', TRUE),
('2200', 'VAT / Tax Payable',           'LIABILITY', 'CREDIT', TRUE),
('2300', 'Accrued Expenses',            'LIABILITY', 'CREDIT', TRUE),
('3000', 'Equity',                      'EQUITY',    'CREDIT', TRUE),
('3100', 'Owner Equity / Capital',      'EQUITY',    'CREDIT', TRUE),
('3200', 'Retained Earnings',           'EQUITY',    'CREDIT', TRUE),
('4000', 'Sales Revenue',               'REVENUE',   'CREDIT', TRUE),
('4100', 'Service Revenue',             'REVENUE',   'CREDIT', TRUE),
('4200', 'Other Income',                'REVENUE',   'CREDIT', TRUE),
('5000', 'Cost of Goods Sold',          'EXPENSE',   'DEBIT',  TRUE),
('5100', 'Purchase Expense',            'EXPENSE',   'DEBIT',  TRUE),
('5200', 'Inventory Adjustment',        'EXPENSE',   'DEBIT',  TRUE),
('6000', 'Operating Expenses',          'EXPENSE',   'DEBIT',  TRUE),
('6100', 'Salaries & Wages',            'EXPENSE',   'DEBIT',  TRUE),
('6200', 'Rent Expense',                'EXPENSE',   'DEBIT',  TRUE),
('6300', 'Utilities Expense',           'EXPENSE',   'DEBIT',  TRUE),
('6400', 'Vehicle & Fuel',              'EXPENSE',   'DEBIT',  TRUE),
('6500', 'Depreciation Expense',        'EXPENSE',   'DEBIT',  TRUE),
('6600', 'Bank Charges',                'EXPENSE',   'DEBIT',  TRUE),
('6700', 'Miscellaneous Expense',       'EXPENSE',   'DEBIT',  TRUE)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    account_type = EXCLUDED.account_type,
    normal_balance = EXCLUDED.normal_balance,
    is_system = TRUE;

-- Wire parent hierarchy (best-effort)
UPDATE accounts SET parent_id = (SELECT id FROM accounts WHERE code = '1000')
WHERE code IN ('1010','1020','1200','1300','1400') AND parent_id IS NULL;
UPDATE accounts SET parent_id = (SELECT id FROM accounts WHERE code = '1500')
WHERE code IN ('1510','1520') AND parent_id IS NULL;
UPDATE accounts SET parent_id = (SELECT id FROM accounts WHERE code = '2000')
WHERE code IN ('2100','2200','2300') AND parent_id IS NULL;
UPDATE accounts SET parent_id = (SELECT id FROM accounts WHERE code = '3000')
WHERE code IN ('3100','3200') AND parent_id IS NULL;
UPDATE accounts SET parent_id = (SELECT id FROM accounts WHERE code = '6000')
WHERE code IN ('6100','6200','6300','6400','6500','6600','6700') AND parent_id IS NULL;

-- 3. Journal entry enhancements
ALTER TABLE journal_entries
    ADD COLUMN IF NOT EXISTS total_debit  NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS total_credit NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS posted_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN IF NOT EXISTS is_auto BOOLEAN NOT NULL DEFAULT FALSE;

-- Ensure status values are controlled
ALTER TABLE journal_entries DROP CONSTRAINT IF EXISTS chk_je_status;
ALTER TABLE journal_entries
    ADD CONSTRAINT chk_je_status CHECK (status IN ('DRAFT', 'POSTED', 'VOID'));

-- 4. Line-level source tracking (optional, for drill-down)
ALTER TABLE journal_entry_lines
    ADD COLUMN IF NOT EXISTS line_no INT DEFAULT 0;

-- 5. Fiscal periods (period-end lock foundation)
CREATE TABLE IF NOT EXISTS fiscal_periods (
    id SERIAL PRIMARY KEY,
    period_name VARCHAR(50) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    is_closed BOOLEAN NOT NULL DEFAULT FALSE,
    closed_at TIMESTAMP WITHOUT TIME ZONE,
    closed_by INT REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT uq_fiscal_period_range UNIQUE (start_date, end_date)
);

INSERT INTO fiscal_periods (period_name, start_date, end_date)
SELECT
    TO_CHAR(DATE_TRUNC('month', CURRENT_DATE), 'Mon YYYY'),
    DATE_TRUNC('month', CURRENT_DATE)::DATE,
    (DATE_TRUNC('month', CURRENT_DATE) + INTERVAL '1 month - 1 day')::DATE
WHERE NOT EXISTS (
    SELECT 1 FROM fiscal_periods
    WHERE start_date = DATE_TRUNC('month', CURRENT_DATE)::DATE
);

-- 6. Indexes for performance
CREATE INDEX IF NOT EXISTS idx_accounts_type ON accounts(account_type);
CREATE INDEX IF NOT EXISTS idx_accounts_parent ON accounts(parent_id);
CREATE INDEX IF NOT EXISTS idx_je_date ON journal_entries(entry_date);
CREATE INDEX IF NOT EXISTS idx_je_ref ON journal_entries(reference_type, reference_id);
CREATE INDEX IF NOT EXISTS idx_je_status ON journal_entries(status);

-- 7. Recalculate account balances from posted journal lines (safe rebuild)
UPDATE accounts a SET balance = COALESCE((
    SELECT
        CASE
            WHEN a.account_type IN ('ASSET', 'EXPENSE') THEN
                COALESCE(SUM(jel.debit),0) - COALESCE(SUM(jel.credit),0)
            ELSE
                COALESCE(SUM(jel.credit),0) - COALESCE(SUM(jel.debit),0)
        END
    FROM journal_entry_lines jel
    JOIN journal_entries je ON je.id = jel.journal_entry_id
    WHERE jel.account_id = a.id AND je.status = 'POSTED'
), 0.00);