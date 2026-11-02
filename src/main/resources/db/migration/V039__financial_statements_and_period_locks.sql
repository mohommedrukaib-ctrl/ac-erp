-- Closing Date Lock Configuration
CREATE TABLE IF NOT EXISTS closing_date_locks (
    id SERIAL PRIMARY KEY,
    closing_date DATE NOT NULL,
    lock_password_hash VARCHAR(255),
    notes TEXT,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_by INT REFERENCES users(id) ON DELETE SET NULL
);

-- Seed default closing date (Locked prior to fiscal year start)
INSERT INTO closing_date_locks (closing_date, notes)
SELECT '2020-01-01'::DATE, 'Initial period lock baseline'
WHERE NOT EXISTS (SELECT 1 FROM closing_date_locks);

-- Ensure settings key for closing date enforcement exists in app_settings
INSERT INTO app_settings (setting_key, setting_value, category, description)
VALUES ('closing_date', '2020-01-01', 'accounting', 'Transactions prior to or on this date cannot be modified')
ON CONFLICT (setting_key) DO NOTHING;

-- Ensure system_settings table and key exist
CREATE TABLE IF NOT EXISTS system_settings (
    setting_key VARCHAR(100) PRIMARY KEY,
    setting_value TEXT NOT NULL
);

INSERT INTO system_settings (setting_key, setting_value)
VALUES ('closing_date', '2020-01-01')
ON CONFLICT (setting_key) DO NOTHING;