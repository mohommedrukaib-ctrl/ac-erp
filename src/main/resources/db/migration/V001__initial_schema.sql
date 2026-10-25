-- =============================================================================
-- V001: Initial Schema
-- Description: Creates the foundation tables for ACE ERP
-- Date: 2025-01-01
-- =============================================================================

-- ---------------------------------------------------------------------------
-- Migration tracking table
-- Records which migration scripts have been applied to this database.
-- This prevents running the same migration twice.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS schema_migrations (
    version         VARCHAR(20)  PRIMARY KEY,
    description     VARCHAR(200) NOT NULL,
    applied_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------------------------
-- Application settings table (key-value store)
-- Stores all configurable business settings.
-- 
-- WHY KEY-VALUE?
-- Instead of a table with 50 columns (one per setting), we use rows.
-- This makes it easy to add new settings without changing the table structure.
-- 
-- category groups related settings:
--   'company'    -> company.name, company.address, etc.
--   'currency'   -> currency.code, currency.symbol, etc.
--   'invoice'    -> invoice.prefix, invoice.nextNumber, etc.
--   'system'     -> system.theme, system.dateFormat, etc.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS app_settings (
    setting_key     VARCHAR(100) PRIMARY KEY,
    setting_value   TEXT,
    category        VARCHAR(50)  NOT NULL,
    description     VARCHAR(255),
    data_type       VARCHAR(20)  NOT NULL DEFAULT 'STRING',
    is_sensitive    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create index on category for fast lookups of related settings
CREATE INDEX idx_app_settings_category ON app_settings(category);

-- ---------------------------------------------------------------------------
-- Users table
-- Stores application user accounts.
-- Passwords are stored as hashed values, NEVER plain text.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id              SERIAL       PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(100) NOT NULL,
    email           VARCHAR(100),
    phone           VARCHAR(20),
    role            VARCHAR(30)  NOT NULL DEFAULT 'USER',
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    last_login      TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------------------------
-- Customers table
-- Stores customer information for the AC business.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS customers (
    id              SERIAL       PRIMARY KEY,
    customer_code   VARCHAR(20)  NOT NULL UNIQUE,
    name            VARCHAR(150) NOT NULL,
    phone           VARCHAR(20),
    email           VARCHAR(100),
    address         TEXT,
    city            VARCHAR(50),
    tax_number      VARCHAR(50),
    notes           TEXT,
    balance         NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_customers_name ON customers(name);
CREATE INDEX idx_customers_phone ON customers(phone);
CREATE INDEX idx_customers_code ON customers(customer_code);

-- ---------------------------------------------------------------------------
-- Suppliers table
-- Stores supplier / vendor information.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS suppliers (
    id              SERIAL       PRIMARY KEY,
    supplier_code   VARCHAR(20)  NOT NULL UNIQUE,
    name            VARCHAR(150) NOT NULL,
    contact_person  VARCHAR(100),
    phone           VARCHAR(20),
    email           VARCHAR(100),
    address         TEXT,
    city            VARCHAR(50),
    tax_number      VARCHAR(50),
    notes           TEXT,
    balance         NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_suppliers_name ON suppliers(name);
CREATE INDEX idx_suppliers_code ON suppliers(supplier_code);

-- ---------------------------------------------------------------------------
-- Products table
-- Stores AC units, spare parts, and services.
-- 
-- product_type:
--   'AC_UNIT'     -> Air conditioning units
--   'SPARE_PART'  -> Replacement parts, filters, etc.
--   'SERVICE'     -> Installation, repair, maintenance services
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS products (
    id              SERIAL        PRIMARY KEY,
    product_code    VARCHAR(30)   NOT NULL UNIQUE,
    name            VARCHAR(200)  NOT NULL,
    description     TEXT,
    product_type    VARCHAR(20)   NOT NULL,
    category        VARCHAR(50),
    brand           VARCHAR(50),
    model           VARCHAR(50),
    unit            VARCHAR(20)   NOT NULL DEFAULT 'PCS',
    selling_price   NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    cost_price      NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    tax_rate        NUMERIC(5,2)  NOT NULL DEFAULT 0.00,
    stock_quantity  INTEGER       NOT NULL DEFAULT 0,
    min_stock_level INTEGER       NOT NULL DEFAULT 0,
    is_active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_product_type 
        CHECK (product_type IN ('AC_UNIT', 'SPARE_PART', 'SERVICE'))
);

CREATE INDEX idx_products_name ON products(name);
CREATE INDEX idx_products_code ON products(product_code);
CREATE INDEX idx_products_type ON products(product_type);

-- ---------------------------------------------------------------------------
-- Invoices table (header)
-- Stores sales invoice headers. Line items are in invoice_items.
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS invoices (
    id              SERIAL        PRIMARY KEY,
    invoice_number  VARCHAR(30)   NOT NULL UNIQUE,
    invoice_date    DATE          NOT NULL DEFAULT CURRENT_DATE,
    due_date        DATE,
    customer_id     INTEGER       NOT NULL REFERENCES customers(id),
    subtotal        NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    tax_amount      NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    discount_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    total_amount    NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    paid_amount     NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    status          VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    notes           TEXT,
    created_by      INTEGER       REFERENCES users(id),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_invoice_status 
        CHECK (status IN ('DRAFT', 'SENT', 'PAID', 'PARTIAL', 'OVERDUE', 'CANCELLED'))
);

CREATE INDEX idx_invoices_number ON invoices(invoice_number);
CREATE INDEX idx_invoices_customer ON invoices(customer_id);
CREATE INDEX idx_invoices_date ON invoices(invoice_date);
CREATE INDEX idx_invoices_status ON invoices(status);

-- ---------------------------------------------------------------------------
-- Insert default application settings
-- These are the initial values. Users can change them from the Settings UI.
-- ---------------------------------------------------------------------------
INSERT INTO app_settings (setting_key, setting_value, category, description, data_type) VALUES
    ('company.name', 'My AC Business', 'company', 'Business display name', 'STRING'),
    ('company.legal_name', '', 'company', 'Legal/registered business name', 'STRING'),
    ('company.address', '', 'company', 'Business address', 'STRING'),
    ('company.phone', '', 'company', 'Business phone number', 'STRING'),
    ('company.email', '', 'company', 'Business email address', 'STRING'),
    ('company.website', '', 'company', 'Business website URL', 'STRING'),
    ('company.tax_number', '', 'company', 'Tax/VAT registration number', 'STRING'),
    ('company.registration', '', 'company', 'Business registration number', 'STRING'),

    ('currency.code', 'USD', 'currency', 'ISO currency code', 'STRING'),
    ('currency.symbol', '$', 'currency', 'Currency symbol', 'STRING'),
    ('currency.decimal_places', '2', 'currency', 'Number of decimal places', 'INTEGER'),
    ('currency.symbol_position', 'BEFORE', 'currency', 'Symbol position: BEFORE or AFTER amount', 'STRING'),

    ('invoice.prefix', 'INV-', 'invoice', 'Invoice number prefix', 'STRING'),
    ('invoice.next_number', '1', 'invoice', 'Next invoice sequence number', 'INTEGER'),
    ('invoice.default_due_days', '30', 'invoice', 'Default payment due days', 'INTEGER'),
    ('invoice.terms', 'Payment is due within 30 days.', 'invoice', 'Default invoice terms', 'STRING'),

    ('quotation.prefix', 'QTN-', 'quotation', 'Quotation number prefix', 'STRING'),
    ('quotation.next_number', '1', 'quotation', 'Next quotation sequence number', 'INTEGER'),

    ('purchase.prefix', 'PO-', 'purchase', 'Purchase order number prefix', 'STRING'),
    ('purchase.next_number', '1', 'purchase', 'Next purchase order sequence number', 'INTEGER'),

    ('tax.default_rate', '0.00', 'tax', 'Default tax/VAT percentage', 'DECIMAL'),
    ('tax.name', 'VAT', 'tax', 'Tax name (VAT, GST, Tax, etc.)', 'STRING'),
    ('tax.enabled', 'true', 'tax', 'Whether tax is applied', 'BOOLEAN'),

    ('system.date_format', 'yyyy-MM-dd', 'system', 'Date display format', 'STRING'),
    ('system.theme', 'default', 'system', 'Application theme name', 'STRING'),
    ('system.items_per_page', '25', 'system', 'Default pagination size', 'INTEGER'),

    ('backup.enabled', 'true', 'backup', 'Whether automatic backups are enabled', 'BOOLEAN'),
    ('backup.schedule', 'DAILY', 'backup', 'Backup schedule: DAILY, WEEKLY', 'STRING'),
    ('backup.retention_days', '30', 'backup', 'Days to keep old backups', 'INTEGER'),
    ('backup.destination', 'backups/', 'backup', 'Backup file destination path', 'STRING');

-- Record this migration
INSERT INTO schema_migrations (version, description) VALUES ('V001', 'Initial schema with settings, users, customers, suppliers, products, invoices');