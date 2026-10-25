-- Migration V008: Delivery Notes, Payments, and Credit Notes

-- 1. Delivery Notes Table
CREATE TABLE IF NOT EXISTS delivery_notes (
    id SERIAL PRIMARY KEY,
    dn_number VARCHAR(50) UNIQUE NOT NULL,
    so_id INT REFERENCES sales_orders(id) ON DELETE SET NULL,
    customer_id INT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    dispatch_date DATE NOT NULL DEFAULT CURRENT_DATE,
    driver_name VARCHAR(100),
    vehicle_no VARCHAR(50),
    delivery_address TEXT,
    status VARCHAR(30) DEFAULT 'DISPATCHED',
    notes TEXT,
    created_by INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Delivery Note Lines Table
CREATE TABLE IF NOT EXISTS delivery_note_lines (
    id SERIAL PRIMARY KEY,
    dn_id INT NOT NULL REFERENCES delivery_notes(id) ON DELETE CASCADE,
    product_id INT NOT NULL REFERENCES products(id),
    ordered_qty NUMERIC(12,2) DEFAULT 0,
    shipped_qty NUMERIC(12,2) DEFAULT 0,
    notes VARCHAR(255)
);

-- 3. Payments Table (Customer Receipts)
CREATE TABLE IF NOT EXISTS payments (
    id SERIAL PRIMARY KEY,
    payment_number VARCHAR(50) UNIQUE NOT NULL,
    invoice_id INT REFERENCES invoices(id) ON DELETE RESTRICT,
    customer_id INT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    payment_date DATE NOT NULL DEFAULT CURRENT_DATE,
    payment_method VARCHAR(50) DEFAULT 'CASH',
    reference_no VARCHAR(100),
    amount NUMERIC(12,2) NOT NULL DEFAULT 0.00,
    notes TEXT,
    created_by INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. Credit Notes Table (Sales Returns)
CREATE TABLE IF NOT EXISTS credit_notes (
    id SERIAL PRIMARY KEY,
    credit_note_number VARCHAR(50) UNIQUE NOT NULL,
    invoice_id INT REFERENCES invoices(id) ON DELETE SET NULL,
    customer_id INT NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
    issue_date DATE NOT NULL DEFAULT CURRENT_DATE,
    status VARCHAR(30) DEFAULT 'ISSUED',
    subtotal NUMERIC(12,2) DEFAULT 0.00,
    tax_amount NUMERIC(12,2) DEFAULT 0.00,
    total_amount NUMERIC(12,2) DEFAULT 0.00,
    reason TEXT,
    created_by INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 5. Credit Note Lines Table
CREATE TABLE IF NOT EXISTS credit_note_lines (
    id SERIAL PRIMARY KEY,
    credit_note_id INT NOT NULL REFERENCES credit_notes(id) ON DELETE CASCADE,
    product_id INT REFERENCES products(id),
    description VARCHAR(255),
    quantity NUMERIC(12,2) DEFAULT 0,
    unit_price NUMERIC(12,2) DEFAULT 0.00,
    line_total NUMERIC(12,2) DEFAULT 0.00,
    restock_qty NUMERIC(12,2) DEFAULT 0
);

-- 6. Track shipped quantities on sales order lines
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'sales_order_lines' AND column_name = 'shipped_qty'
    ) THEN
        ALTER TABLE sales_order_lines ADD COLUMN shipped_qty NUMERIC(12,2) DEFAULT 0;
    END IF;
END $$;     