CREATE TABLE IF NOT EXISTS stock_movements (
    id               SERIAL PRIMARY KEY,
    product_id       INT NOT NULL REFERENCES products(id),
    movement_type    VARCHAR(20) NOT NULL CHECK (movement_type IN ('IN', 'OUT', 'ADJUSTMENT')),
    quantity         INT NOT NULL,
    previous_stock   INT NOT NULL DEFAULT 0,
    new_stock        INT NOT NULL DEFAULT 0,
    reference_type   VARCHAR(30),
    reference_number VARCHAR(50),
    notes            TEXT,
    created_by       INT REFERENCES users(id),
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_stock_product ON stock_movements(product_id);
CREATE INDEX IF NOT EXISTS idx_stock_date ON stock_movements(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_stock_type ON stock_movements(movement_type);