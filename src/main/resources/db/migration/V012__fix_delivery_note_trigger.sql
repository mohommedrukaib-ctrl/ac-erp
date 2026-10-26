-- Replace the old trigger function so PostgreSQL no longer throws the current_stock error.
-- Stock deduction is handled directly by Java within a single database transaction.
CREATE OR REPLACE FUNCTION process_delivery_note_stock()
RETURNS TRIGGER AS $$
BEGIN
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;