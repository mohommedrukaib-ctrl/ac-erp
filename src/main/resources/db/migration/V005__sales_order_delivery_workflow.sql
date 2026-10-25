-- =========================================================
-- SUPPLIERS / VENDORS
-- =========================================================
CREATE TABLE IF NOT EXISTS suppliers (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    company_name VARCHAR(255),
    email VARCHAR(255),
    phone VARCHAR(50),
    address TEXT,
    tax_number VARCHAR(50),
    payment_terms VARCHAR(50) DEFAULT 'NET30',
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_suppliers_name ON suppliers(name);

-- =========================================================
-- SALES ORDERS
-- =========================================================
CREATE TABLE IF NOT EXISTS sales_orders (
    id SERIAL PRIMARY KEY,
    so_number VARCHAR(50) UNIQUE NOT NULL,
    quotation_id INTEGER REFERENCES quotations(id) ON DELETE SET NULL,
    customer_id INTEGER REFERENCES customers(id) NOT NULL,
    order_date DATE DEFAULT CURRENT_DATE,
    expected_delivery_date DATE,
    status VARCHAR(20) DEFAULT 'DRAFT', -- DRAFT, APPROVED, SHIPPED, INVOICED, CANCELLED
    subtotal DECIMAL(15,2) DEFAULT 0.00,
    tax_amount DECIMAL(15,2) DEFAULT 0.00,
    total_amount DECIMAL(15,2) DEFAULT 0.00,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sales_order_lines (
    id SERIAL PRIMARY KEY,
    so_id INTEGER REFERENCES sales_orders(id) ON DELETE CASCADE,
    product_id INTEGER REFERENCES products(id),
    description TEXT,
    quantity DECIMAL(12,2) NOT NULL,
    unit_price DECIMAL(15,2) NOT NULL,
    total_price DECIMAL(15,2) NOT NULL
);

-- =========================================================
-- DELIVERY NOTES (Deducts stock on issuance)
-- =========================================================
CREATE TABLE IF NOT EXISTS delivery_notes (
    id SERIAL PRIMARY KEY,
    dn_number VARCHAR(50) UNIQUE NOT NULL,
    so_id INTEGER REFERENCES sales_orders(id) ON DELETE SET NULL,
    customer_id INTEGER REFERENCES customers(id) NOT NULL,
    delivery_date DATE DEFAULT CURRENT_DATE,
    status VARCHAR(20) DEFAULT 'DELIVERED', -- DRAFT, DELIVERED, CANCELLED
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS delivery_note_lines (
    id SERIAL PRIMARY KEY,
    dn_id INTEGER REFERENCES delivery_notes(id) ON DELETE CASCADE,
    product_id INTEGER REFERENCES products(id),
    quantity_shipped DECIMAL(12,2) NOT NULL,
    unit_price DECIMAL(15,2) DEFAULT 0.00
);

-- Link Invoices to SO or DN
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS so_id INTEGER REFERENCES sales_orders(id) ON DELETE SET NULL;
ALTER TABLE invoices ADD COLUMN IF NOT EXISTS dn_id INTEGER REFERENCES delivery_notes(id) ON DELETE SET NULL;

-- =========================================================
-- PAYMENTS / RECEIPTS (For Sales Invoices & Supplier Bills)
-- =========================================================
CREATE TABLE IF NOT EXISTS payments (
    id SERIAL PRIMARY KEY,
    payment_number VARCHAR(50) UNIQUE NOT NULL,
    payment_type VARCHAR(20) NOT NULL, -- 'RECEIPT' (from Customer), 'DISBURSEMENT' (to Supplier)
    invoice_id INTEGER REFERENCES invoices(id) ON DELETE SET NULL,
    supplier_bill_id INTEGER, -- Soft link added below after bill table creation
    customer_id INTEGER REFERENCES customers(id) ON DELETE SET NULL,
    supplier_id INTEGER REFERENCES suppliers(id) ON DELETE SET NULL,
    payment_date DATE DEFAULT CURRENT_DATE,
    payment_method VARCHAR(50) NOT NULL, -- CASH, BANK_TRANSFER, CREDIT_CARD, CHECK
    amount DECIMAL(15,2) NOT NULL,
    reference_number VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- =========================================================
-- PURCHASE REQUISITIONS & ORDERS (PO does NOT alter stock)
-- =========================================================
CREATE TABLE IF NOT EXISTS purchase_orders (
    id SERIAL PRIMARY KEY,
    po_number VARCHAR(50) UNIQUE NOT NULL,
    supplier_id INTEGER REFERENCES suppliers(id) NOT NULL,
    order_date DATE DEFAULT CURRENT_DATE,
    expected_date DATE,
    status VARCHAR(20) DEFAULT 'DRAFT', -- DRAFT, APPROVED, RECEIVED, BILLED, CANCELLED
    subtotal DECIMAL(15,2) DEFAULT 0.00,
    tax_amount DECIMAL(15,2) DEFAULT 0.00,
    total_amount DECIMAL(15,2) DEFAULT 0.00,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS purchase_order_lines (
    id SERIAL PRIMARY KEY,
    po_id INTEGER REFERENCES purchase_orders(id) ON DELETE CASCADE,
    product_id INTEGER REFERENCES products(id),
    description TEXT,
    quantity DECIMAL(12,2) NOT NULL,
    unit_price DECIMAL(15,2) NOT NULL,
    total_price DECIMAL(15,2) NOT NULL
);

-- =========================================================
-- GOODS RECEIVED NOTES (GRN - Adds stock on issuance)
-- =========================================================
CREATE TABLE IF NOT EXISTS goods_received_notes (
    id SERIAL PRIMARY KEY,
    grn_number VARCHAR(50) UNIQUE NOT NULL,
    po_id INTEGER REFERENCES purchase_orders(id) ON DELETE SET NULL,
    supplier_id INTEGER REFERENCES suppliers(id) NOT NULL,
    received_date DATE DEFAULT CURRENT_DATE,
    status VARCHAR(20) DEFAULT 'RECEIVED',
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS goods_received_note_lines (
    id SERIAL PRIMARY KEY,
    grn_id INTEGER REFERENCES goods_received_notes(id) ON DELETE CASCADE,
    product_id INTEGER REFERENCES products(id),
    quantity_received DECIMAL(12,2) NOT NULL,
    unit_price DECIMAL(15,2) DEFAULT 0.00
);

-- =========================================================
-- SUPPLIER BILLS
-- =========================================================
CREATE TABLE IF NOT EXISTS supplier_bills (
    id SERIAL PRIMARY KEY,
    bill_number VARCHAR(50) UNIQUE NOT NULL,
    po_id INTEGER REFERENCES purchase_orders(id) ON DELETE SET NULL,
    grn_id INTEGER REFERENCES goods_received_notes(id) ON DELETE SET NULL,
    supplier_id INTEGER REFERENCES suppliers(id) NOT NULL,
    bill_date DATE DEFAULT CURRENT_DATE,
    due_date DATE,
    status VARCHAR(20) DEFAULT 'UNPAID', -- UNPAID, PARTIAL, PAID, CANCELLED
    subtotal DECIMAL(15,2) DEFAULT 0.00,
    tax_amount DECIMAL(15,2) DEFAULT 0.00,
    total_amount DECIMAL(15,2) DEFAULT 0.00,
    paid_amount DECIMAL(15,2) DEFAULT 0.00,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Foreign Key link for supplier_bill_id in payments
ALTER TABLE payments ADD CONSTRAINT fk_payments_supplier_bill FOREIGN KEY (supplier_bill_id) REFERENCES supplier_bills(id) ON DELETE SET NULL;

-- =========================================================
-- STOCK MOVEMENT AUTOMATION TRIGGERS
-- =========================================================

-- Deduct physical stock on Delivery Note creation
CREATE OR REPLACE FUNCTION process_delivery_note_stock() RETURNS TRIGGER AS $$
BEGIN
    UPDATE products 
    SET current_stock = current_stock - dnl.quantity_shipped
    FROM delivery_note_lines dnl
    WHERE dnl.dn_id = NEW.id AND products.id = dnl.product_id;
    
    INSERT INTO stock_movements (product_id, type, quantity, reference, user_id)
    SELECT product_id, 'OUT', quantity_shipped, 'DN-' || NEW.dn_number, 1
    FROM delivery_note_lines WHERE dn_id = NEW.id;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_process_dn_stock ON delivery_notes;
CREATE TRIGGER trg_process_dn_stock
AFTER INSERT ON delivery_notes
FOR EACH ROW EXECUTE FUNCTION process_delivery_note_stock();

-- Add physical stock on Goods Received Note (GRN) creation
CREATE OR REPLACE FUNCTION process_grn_stock() RETURNS TRIGGER AS $$
BEGIN
    UPDATE products 
    SET current_stock = current_stock + grnl.quantity_received
    FROM goods_received_note_lines grnl
    WHERE grnl.grn_id = NEW.id AND products.id = grnl.product_id;
    
    INSERT INTO stock_movements (product_id, type, quantity, reference, user_id)
    SELECT product_id, 'IN', quantity_received, 'GRN-' || NEW.grn_number, 1
    FROM goods_received_note_lines WHERE grn_id = NEW.id;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_process_grn_stock ON goods_received_notes;
CREATE TRIGGER trg_process_grn_stock
AFTER INSERT ON goods_received_notes
FOR EACH ROW EXECUTE FUNCTION process_grn_stock();