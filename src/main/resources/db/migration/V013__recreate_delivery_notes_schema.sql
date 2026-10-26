-- Drop problematic tables and triggers
DROP TRIGGER IF EXISTS trg_delivery_note_stock ON delivery_notes CASCADE;
DROP FUNCTION IF EXISTS process_delivery_note_stock() CASCADE;
DROP TABLE IF EXISTS delivery_note_lines CASCADE;
DROP TABLE IF EXISTS delivery_notes CASCADE;

-- Recreate delivery_notes table with clean schema
CREATE TABLE delivery_notes (
    id SERIAL PRIMARY KEY,
    dn_number VARCHAR(50) NOT NULL UNIQUE,
    so_id INT REFERENCES sales_orders(id) ON DELETE SET NULL,
    customer_id INT NOT NULL REFERENCES customers(id),
    delivery_date DATE NOT NULL DEFAULT CURRENT_DATE,
    driver_name VARCHAR(150),
    vehicle_no VARCHAR(100),
    delivery_address TEXT,
    status VARCHAR(50) DEFAULT 'DISPATCHED',
    notes TEXT,
    created_by INT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Recreate delivery_note_lines table with clean schema
CREATE TABLE delivery_note_lines (
    id SERIAL PRIMARY KEY,
    dn_id INT NOT NULL REFERENCES delivery_notes(id) ON DELETE CASCADE,
    product_id INT NOT NULL REFERENCES products(id),
    ordered_qty NUMERIC(12,2) DEFAULT 0,
    shipped_qty NUMERIC(12,2) NOT NULL DEFAULT 0,
    notes TEXT
);