-- HVAC Service Jobs table
CREATE TABLE IF NOT EXISTS service_jobs (
    id SERIAL PRIMARY KEY,
    job_number VARCHAR(50) NOT NULL UNIQUE,
    customer_id INT NOT NULL REFERENCES customers(id),
    technician_id INT REFERENCES users(id) ON DELETE SET NULL,
    job_type VARCHAR(50) NOT NULL DEFAULT 'REPAIR', -- INSTALLATION, REPAIR, MAINTENANCE, GAS_REFILL
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- PENDING, IN_PROGRESS, COMPLETED, CANCELLED
    scheduled_date DATE,
    completion_date DATE,
    ac_model_info VARCHAR(255),
    reported_issue TEXT,
    resolution_notes TEXT,
    labor_cost NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    parts_total NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    grand_total NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_by INT REFERENCES users(id) ON DELETE SET NULL
);

-- Service Job Line Items (Spare Parts / Materials Consumed)
CREATE TABLE IF NOT EXISTS service_job_lines (
    id SERIAL PRIMARY KEY,
    service_job_id INT NOT NULL REFERENCES service_jobs(id) ON DELETE CASCADE,
    product_id INT NOT NULL REFERENCES products(id),
    quantity INT NOT NULL DEFAULT 1,
    unit_price NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    line_total NUMERIC(15, 2) NOT NULL DEFAULT 0.00
);

CREATE INDEX IF NOT EXISTS idx_sj_customer ON service_jobs(customer_id);
CREATE INDEX IF NOT EXISTS idx_sj_technician ON service_jobs(technician_id);
CREATE INDEX IF NOT EXISTS idx_sjl_job ON service_job_lines(service_job_id);