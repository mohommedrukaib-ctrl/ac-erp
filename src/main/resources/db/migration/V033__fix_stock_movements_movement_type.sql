-- V033: Relax movement_type check constraint on stock_movements table
-- Allows all inventory movement types across modules ('PURCHASE_RECEIPT', 'DELIVERY', 'SALE', 'RETURN', 'ADJUSTMENT', etc.)

ALTER TABLE stock_movements DROP CONSTRAINT IF EXISTS stock_movements_movement_type_check;