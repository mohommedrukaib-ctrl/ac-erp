-- V044__multi_location_warehouses_and_stock_transfers.sql

-- Warehouses / Locations
CREATE TABLE IF NOT EXISTS warehouses (
    id SERIAL PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    location_type VARCHAR(30) NOT NULL DEFAULT 'STORE', -- 'STORE', 'VAN', 'SERVICE_BAY', 'JOB_SITE'
    address TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Seed Default Warehouse and HVAC Service Van #1
INSERT INTO warehouses (code, name, location_type, address)
VALUES 
    ('MAIN', 'Main Central Store', 'STORE', 'HQ Warehouse'),
    ('VAN-01', 'HVAC Service Van #1', 'VAN', 'Technician Unit 01')
ON CONFLICT (code) DO NOTHING;

-- Stock by Warehouse
CREATE TABLE IF NOT EXISTS warehouse_stocks (
    id SERIAL PRIMARY KEY,
    warehouse_id INT NOT NULL REFERENCES warehouses(id) ON DELETE CASCADE,
    product_id INT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    stock_quantity INT NOT NULL DEFAULT 0,
    min_stock_level INT NOT NULL DEFAULT 0,
    CONSTRAINT unq_wh_prod UNIQUE(warehouse_id, product_id)
);

-- Stock Transfer Header
CREATE TABLE IF NOT EXISTS stock_transfers (
    id SERIAL PRIMARY KEY,
    transfer_number VARCHAR(50) NOT NULL UNIQUE,
    source_warehouse_id INT NOT NULL REFERENCES warehouses(id),
    destination_warehouse_id INT NOT NULL REFERENCES warehouses(id),
    transfer_date DATE NOT NULL DEFAULT CURRENT_DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'COMPLETED', -- REQUESTED, IN_TRANSIT, COMPLETED, CANCELLED
    notes TEXT,
    journal_entry_id INT REFERENCES journal_entries(id),
    created_by INT REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Stock Transfer Lines
CREATE TABLE IF NOT EXISTS stock_transfer_lines (
    id SERIAL PRIMARY KEY,
    stock_transfer_id INT NOT NULL REFERENCES stock_transfers(id) ON DELETE CASCADE,
    product_id INT NOT NULL REFERENCES products(id),
    quantity_transferred INT NOT NULL DEFAULT 1,
    notes TEXT
);

CREATE INDEX IF NOT EXISTS idx_wh_stocks_wh ON warehouse_stocks(warehouse_id);
CREATE INDEX IF NOT EXISTS idx_stock_transfers_src ON stock_transfers(source_warehouse_id);
CREATE INDEX IF NOT EXISTS idx_stock_transfers_dst ON stock_transfers(destination_warehouse_id);