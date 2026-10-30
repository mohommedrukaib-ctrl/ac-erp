-- V034: Add missing description column to purchase_return_lines table
ALTER TABLE purchase_return_lines ADD COLUMN IF NOT EXISTS description TEXT;