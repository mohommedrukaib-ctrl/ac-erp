CREATE OR REPLACE FUNCTION sync_product_stock_cols()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF COALESCE(NEW.stock_quantity, 0) = 0 AND NEW.current_stock IS NOT NULL THEN
            NEW.stock_quantity := NEW.current_stock;
        ELSE
            NEW.current_stock := NEW.stock_quantity;
        END IF;
    ELSIF NEW.current_stock IS DISTINCT FROM OLD.current_stock THEN
        NEW.stock_quantity := NEW.current_stock;
    ELSIF NEW.stock_quantity IS DISTINCT FROM OLD.stock_quantity THEN
        NEW.current_stock := NEW.stock_quantity;
    END IF;

    IF NEW.product_type IS NULL OR NEW.product_type = '' THEN
        NEW.product_type := 'SPARE_PART';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
