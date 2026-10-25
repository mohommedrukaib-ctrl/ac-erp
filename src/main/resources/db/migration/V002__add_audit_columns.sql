-- =============================================================================
-- V002: Add audit columns to track who created/modified records
-- =============================================================================

-- Users table: add created_by and updated_by columns
-- (NULL allowed because the first SUPER_ADMIN is created before any user exists)
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS created_by INTEGER REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS updated_by INTEGER REFERENCES users(id);

-- Customers: track who created/modified
ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS created_by INTEGER REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS updated_by INTEGER REFERENCES users(id);

-- Suppliers: track who created/modified
ALTER TABLE suppliers
    ADD COLUMN IF NOT EXISTS created_by INTEGER REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS updated_by INTEGER REFERENCES users(id);

-- Products: track who created/modified
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS created_by INTEGER REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS updated_by INTEGER REFERENCES users(id);

-- Settings: track who last updated
ALTER TABLE app_settings
    ADD COLUMN IF NOT EXISTS updated_by INTEGER REFERENCES users(id);

-- Record this migration
INSERT INTO schema_migrations (version, description)
VALUES ('V002', 'Added audit columns (created_by, updated_by)');