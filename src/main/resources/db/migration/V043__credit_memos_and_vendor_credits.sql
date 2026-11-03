-- V043__credit_memos_and_vendor_credits.sql

-- Ensure system accounts for Returns & Allowances exist
INSERT INTO accounts (code, name, account_type, normal_balance, is_system, is_active, description)
VALUES 
    ('4200', 'Sales Returns & Allowances', 'REVENUE', 'DEBIT', TRUE, TRUE, 'Customer credit memos and sales return allowances'),
    ('5200', 'Purchase Returns & Allowances', 'EXPENSE', 'CREDIT', TRUE, TRUE, 'Vendor credit memos and purchase return allowances')
ON CONFLICT (code) DO NOTHING;

-- Customer Credit Memos Header
CREATE TABLE IF NOT EXISTS customer_credit_memos (
    id SERIAL PRIMARY KEY,
    credit_memo_number VARCHAR(50) NOT NULL UNIQUE,
    customer_id INT NOT NULL REFERENCES customers(id),
    issue_date DATE NOT NULL DEFAULT CURRENT_DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN', -- DRAFT, OPEN, PARTIALLY_APPLIED, FULLY_APPLIED, REFUNDED, VOID
    subtotal NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    total_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    remaining_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    memo TEXT,
    journal_entry_id INT REFERENCES journal_entries(id),
    created_by INT REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Customer Credit Memo Lines
CREATE TABLE IF NOT EXISTS credit_memo_lines (
    id SERIAL PRIMARY KEY,
    credit_memo_id INT NOT NULL REFERENCES customer_credit_memos(id) ON DELETE CASCADE,
    product_id INT REFERENCES products(id),
    description TEXT NOT NULL,
    quantity NUMERIC(12,2) NOT NULL DEFAULT 1.00,
    unit_price NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    tax_rate NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    total_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    line_no INT NOT NULL DEFAULT 1
);

-- Vendor Credits Header
CREATE TABLE IF NOT EXISTS vendor_credits (
    id SERIAL PRIMARY KEY,
    vendor_credit_number VARCHAR(50) NOT NULL UNIQUE,
    supplier_id INT NOT NULL REFERENCES suppliers(id),
    issue_date DATE NOT NULL DEFAULT CURRENT_DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN', -- DRAFT, OPEN, PARTIALLY_APPLIED, FULLY_APPLIED, REFUNDED, VOID
    subtotal NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    total_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    remaining_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    memo TEXT,
    journal_entry_id INT REFERENCES journal_entries(id),
    created_by INT REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Vendor Credit Lines
CREATE TABLE IF NOT EXISTS vendor_credit_lines (
    id SERIAL PRIMARY KEY,
    vendor_credit_id INT NOT NULL REFERENCES vendor_credits(id) ON DELETE CASCADE,
    product_id INT REFERENCES products(id),
    account_id INT REFERENCES accounts(id),
    description TEXT NOT NULL,
    quantity NUMERIC(12,2) NOT NULL DEFAULT 1.00,
    unit_cost NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    tax_rate NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    tax_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    total_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    line_no INT NOT NULL DEFAULT 1
);

-- Credit Applications (Linking Credit Memos/Vendor Credits to Invoices/Bills)
CREATE TABLE IF NOT EXISTS credit_applications (
    id SERIAL PRIMARY KEY,
    credit_type VARCHAR(20) NOT NULL, -- 'CUSTOMER' or 'VENDOR'
    customer_credit_memo_id INT REFERENCES customer_credit_memos(id),
    vendor_credit_id INT REFERENCES vendor_credits(id),
    invoice_id INT REFERENCES invoices(id),
    supplier_bill_id INT REFERENCES supplier_bills(id),
    applied_amount NUMERIC(15,2) NOT NULL,
    applied_date DATE NOT NULL DEFAULT CURRENT_DATE,
    notes TEXT,
    created_by INT REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cust_credit_memos_cust ON customer_credit_memos(customer_id);
CREATE INDEX IF NOT EXISTS idx_vendor_credits_supp ON vendor_credits(supplier_id);
CREATE INDEX IF NOT EXISTS idx_credit_apps_inv ON credit_applications(invoice_id);
CREATE INDEX IF NOT EXISTS idx_credit_apps_bill ON credit_applications(supplier_bill_id);