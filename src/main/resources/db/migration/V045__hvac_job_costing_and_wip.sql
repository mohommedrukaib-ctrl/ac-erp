-- V045__hvac_job_costing_and_wip.sql

-- Ensure system account 1250 (Work in Progress Inventory) exists
INSERT INTO accounts (code, name, account_type, normal_balance, is_system, is_active, description)
VALUES ('1250', 'Work In Progress (WIP) Inventory', 'ASSET', 'DEBIT', TRUE, TRUE, 'Unbilled/uncompleted job costs')
ON CONFLICT (code) DO NOTHING;

-- Job Cost Budgets per Service Job
CREATE TABLE IF NOT EXISTS job_budgets (
    id SERIAL PRIMARY KEY,
    service_job_id INT NOT NULL REFERENCES service_jobs(id) ON DELETE CASCADE,
    budgeted_materials NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    budgeted_labor NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    budgeted_equipment NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    budgeted_subcontractor NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    budgeted_overhead NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    total_budgeted_cost NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unq_job_budget UNIQUE(service_job_id)
);

-- Actual Job Cost Tracking Ledger
CREATE TABLE IF NOT EXISTS job_cost_entries (
    id SERIAL PRIMARY KEY,
    service_job_id INT NOT NULL REFERENCES service_jobs(id) ON DELETE CASCADE,
    cost_category VARCHAR(30) NOT NULL, -- 'MATERIAL', 'LABOR', 'EQUIPMENT', 'SUBCONTRACTOR', 'OVERHEAD'
    entry_date DATE NOT NULL DEFAULT CURRENT_DATE,
    description TEXT NOT NULL,
    quantity NUMERIC(12,2) NOT NULL DEFAULT 1.00,
    unit_cost NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    total_cost NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    source_type VARCHAR(30), -- 'SUPPLIER_BILL', 'STOCK_ISSUE', 'TIMESHEET', 'MANUAL'
    source_id INT,
    journal_entry_id INT REFERENCES journal_entries(id),
    created_by INT REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_job_cost_entries_job ON job_cost_entries(service_job_id);
CREATE INDEX IF NOT EXISTS idx_job_cost_entries_cat ON job_cost_entries(cost_category);