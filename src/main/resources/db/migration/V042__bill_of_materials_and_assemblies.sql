-- Bill of Materials & Assembly Build Engine Schema

-- Bill of Materials Header (Assembly Recipe)
CREATE TABLE IF NOT EXISTS bill_of_materials (
    id SERIAL PRIMARY KEY,
    assembly_product_id INTEGER NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    bom_code VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    total_cost NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_bom_assembly_product ON bill_of_materials(assembly_product_id);

-- Bill of Materials Line Components
CREATE TABLE IF NOT EXISTS bom_components (
    id SERIAL PRIMARY KEY,
    bom_id INTEGER NOT NULL REFERENCES bill_of_materials(id) ON DELETE CASCADE,
    component_product_id INTEGER NOT NULL REFERENCES products(id),
    quantity NUMERIC(12,4) NOT NULL DEFAULT 1.0000,
    unit_cost NUMERIC(15,2) NOT NULL DEFAULT 0.00
);

CREATE INDEX IF NOT EXISTS idx_bom_comp_bom ON bom_components(bom_id);

-- Assembly Build Executions (Work Orders)
CREATE TABLE IF NOT EXISTS assembly_builds (
    id SERIAL PRIMARY KEY,
    build_number VARCHAR(50) NOT NULL UNIQUE,
    bom_id INTEGER NOT NULL REFERENCES bill_of_materials(id),
    assembly_product_id INTEGER NOT NULL REFERENCES products(id),
    quantity_to_build INTEGER NOT NULL DEFAULT 1,
    total_cost NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    build_date DATE NOT NULL DEFAULT CURRENT_DATE,
    journal_entry_id INTEGER REFERENCES journal_entries(id) ON DELETE SET NULL,
    notes TEXT,
    created_by INTEGER REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_assembly_builds_product ON assembly_builds(assembly_product_id);