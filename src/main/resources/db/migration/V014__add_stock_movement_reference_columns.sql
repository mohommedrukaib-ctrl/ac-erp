-- Ensure stock_movements has all reference and user audit columns
ALTER TABLE stock_movements ADD COLUMN IF NOT EXISTS reference_type VARCHAR(50);
ALTER TABLE stock_movements ADD COLUMN IF NOT EXISTS reference_id INT;
ALTER TABLE stock_movements ADD COLUMN IF NOT EXISTS created_by INT REFERENCES users(id) ON DELETE SET NULL;