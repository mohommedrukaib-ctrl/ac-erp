-- Fixed Asset Management & Depreciation Engine Schema

-- Ensure System Accounts for Fixed Assets and Depreciation exist in Chart of Accounts
INSERT INTO accounts (code, name, account_type, normal_balance, is_system, is_active, description)
VALUES 
    ('1500', 'Fixed Assets', 'ASSET', 'DEBIT', TRUE, TRUE, 'Property, plant, equipment, and HVAC vehicles'),
    ('1590', 'Accumulated Depreciation', 'ASSET', 'CREDIT', TRUE, TRUE, 'Contra-asset account for accumulated depreciation'),
    ('6800', 'Depreciation Expense', 'EXPENSE', 'DEBIT', TRUE, TRUE, 'Period depreciation expense on fixed assets')
ON CONFLICT (code) DO NOTHING;

-- Fixed Assets Table
CREATE TABLE IF NOT EXISTS fixed_assets (
    id SERIAL PRIMARY KEY,
    asset_code VARCHAR(30) NOT NULL UNIQUE,
    asset_name VARCHAR(150) NOT NULL,
    category VARCHAR(50) NOT NULL DEFAULT 'EQUIPMENT',
    acquisition_date DATE NOT NULL DEFAULT CURRENT_DATE,
    acquisition_cost NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    salvage_value NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    useful_life_years INTEGER NOT NULL DEFAULT 5,
    depreciation_method VARCHAR(30) NOT NULL DEFAULT 'STRAIGHT_LINE',
    accumulated_depreciation NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    book_value NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    asset_account_id INTEGER REFERENCES accounts(id),
    accumulated_dep_account_id INTEGER REFERENCES accounts(id),
    dep_expense_account_id INTEGER REFERENCES accounts(id),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_dep_method CHECK (depreciation_method IN ('STRAIGHT_LINE', 'DECLINING_BALANCE')),
    CONSTRAINT chk_asset_status CHECK (status IN ('ACTIVE', 'DISPOSED', 'FULLY_DEPRECIATED'))
);

CREATE INDEX IF NOT EXISTS idx_fixed_assets_code ON fixed_assets(asset_code);
CREATE INDEX IF NOT EXISTS idx_fixed_assets_category ON fixed_assets(category);

-- Fixed Asset Depreciation Entries History
CREATE TABLE IF NOT EXISTS fixed_asset_depreciations (
    id SERIAL PRIMARY KEY,
    fixed_asset_id INTEGER NOT NULL REFERENCES fixed_assets(id) ON DELETE CASCADE,
    depreciation_date DATE NOT NULL,
    period_year INTEGER NOT NULL,
    period_month INTEGER NOT NULL,
    depreciation_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    journal_entry_id INTEGER REFERENCES journal_entries(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_fa_dep_asset ON fixed_asset_depreciations(fixed_asset_id);