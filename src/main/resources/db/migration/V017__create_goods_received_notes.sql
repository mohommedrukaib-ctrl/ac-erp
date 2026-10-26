-- Migration V017: Create Goods Received Notes (GRN) tables for receiving purchase stock

CREATE TABLE IF NOT EXISTS goods_received_notes (
    id SERIAL PRIMARY KEY,
    grn_number VARCHAR(50) NOT NULL UNIQUE,
    po_id INT REFERENCES purchase_orders(id) ON DELETE SET NULL,
    supplier_id INT NOT NULL REFERENCES suppliers(id) ON DELETE RESTRICT,
    received_date DATE NOT NULL DEFAULT CURRENT_DATE,
    delivery_note_ref VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED', -- COMPLETED, CANCELLED
    notes TEXT,
    created_by INT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS grn_lines (
    id SERIAL PRIMARY KEY,
    grn_id INT NOT NULL REFERENCES goods_received_notes(id) ON DELETE CASCADE,
    po_line_id INT,
    product_id INT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    description TEXT,
    quantity_ordered NUMERIC(12,2) DEFAULT 0.00,
    quantity_received NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    unit_cost NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    line_total NUMERIC(12,2) NOT NULL DEFAULT 0.00
);