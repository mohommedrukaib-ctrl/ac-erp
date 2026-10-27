-- Migration V019: Create supplier_bills and supplier_bill_lines tables
CREATE TABLE IF NOT EXISTS supplier_bills (
    id SERIAL PRIMARY KEY,
    bill_number VARCHAR(50) NOT NULL UNIQUE,
    supplier_id INT NOT NULL REFERENCES suppliers(id) ON DELETE RESTRICT,
    po_id INT REFERENCES purchase_orders(id) ON DELETE SET NULL,
    grn_id INT REFERENCES goods_received_notes(id) ON DELETE SET NULL,
    bill_date DATE NOT NULL,
    due_date DATE,
    supplier_invoice_ref VARCHAR(100),
    status VARCHAR(30) DEFAULT 'UNPAID',
    subtotal NUMERIC(15, 2) DEFAULT 0.00,
    tax_amount NUMERIC(15, 2) DEFAULT 0.00,
    total_amount NUMERIC(15, 2) DEFAULT 0.00,
    paid_amount NUMERIC(15, 2) DEFAULT 0.00,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS supplier_bill_lines (
    id SERIAL PRIMARY KEY,
    bill_id INT NOT NULL REFERENCES supplier_bills(id) ON DELETE CASCADE,
    product_id INT REFERENCES products(id) ON DELETE RESTRICT,
    description VARCHAR(255) NOT NULL,
    quantity NUMERIC(12, 2) DEFAULT 1.00,
    unit_price NUMERIC(15, 2) DEFAULT 0.00,
    tax_rate NUMERIC(5, 2) DEFAULT 0.00,
    tax_amount NUMERIC(15, 2) DEFAULT 0.00,
    total_price NUMERIC(15, 2) DEFAULT 0.00,
    line_total NUMERIC(15, 2) DEFAULT 0.00
);