CREATE TABLE IF NOT EXISTS company_profile (
    id INTEGER PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    company_name       VARCHAR(200) NOT NULL DEFAULT 'My Company',
    tagline            VARCHAR(255),
    address_line1      VARCHAR(255),
    address_line2      VARCHAR(255),
    city               VARCHAR(100),
    state              VARCHAR(100),
    postal_code        VARCHAR(30),
    country            VARCHAR(100),
    phone              VARCHAR(50),
    email              VARCHAR(100),
    website            VARCHAR(150),
    tax_number         VARCHAR(80),
    logo_path          VARCHAR(500),
    invoice_footer     TEXT,
    default_print_template VARCHAR(50) DEFAULT 'CLASSIC',
    updated_at         TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- First-run placeholder only — you overwrite this in Settings
INSERT INTO company_profile (id, company_name, invoice_footer)
VALUES (
    1,
    'My Company',
    'Thank you for your business.'
)
ON CONFLICT (id) DO NOTHING;