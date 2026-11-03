-- =============================================================================
-- V047: HVAC Enterprise Features (Warranty, AMC, Commissions)
-- =============================================================================

ALTER TABLE products ADD COLUMN IF NOT EXISTS warranty_months INT NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS product_serials (
    id SERIAL PRIMARY KEY,
    product_id INT NOT NULL REFERENCES products(id),
    serial_number VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(30) NOT NULL DEFAULT 'IN_STOCK',
    customer_id INT REFERENCES customers(id),
    service_job_id INT,
    installation_date DATE,
    warranty_expiry DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE service_job_lines ADD COLUMN IF NOT EXISTS serial_number VARCHAR(100);

CREATE TABLE IF NOT EXISTS amc_contracts (
    id SERIAL PRIMARY KEY,
    contract_number VARCHAR(50) NOT NULL UNIQUE,
    customer_id INT NOT NULL REFERENCES customers(id),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    total_value NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by INT REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS amc_schedules (
    id SERIAL PRIMARY KEY,
    contract_id INT NOT NULL REFERENCES amc_contracts(id) ON DELETE CASCADE,
    scheduled_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    service_job_id INT,
    notes TEXT
);

ALTER TABLE service_jobs ADD COLUMN IF NOT EXISTS amc_contract_id INT REFERENCES amc_contracts(id);

ALTER TABLE users ADD COLUMN IF NOT EXISTS commission_type VARCHAR(30) NOT NULL DEFAULT 'NONE';
ALTER TABLE users ADD COLUMN IF NOT EXISTS commission_rate NUMERIC(10,2) NOT NULL DEFAULT 0.00;

CREATE TABLE IF NOT EXISTS technician_commissions (
    id SERIAL PRIMARY KEY,
    technician_id INT NOT NULL REFERENCES users(id),
    service_job_id INT NOT NULL REFERENCES service_jobs(id),
    commission_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    paid_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);