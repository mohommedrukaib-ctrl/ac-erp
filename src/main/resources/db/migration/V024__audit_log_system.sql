CREATE TABLE IF NOT EXISTS audit_logs (
    id SERIAL PRIMARY KEY,
    user_id INT REFERENCES users(id) ON DELETE SET NULL,
    username VARCHAR(100),
    action VARCHAR(50) NOT NULL,          -- e.g., 'CREATE', 'UPDATE', 'DELETE'
    entity_type VARCHAR(100) NOT NULL,    -- e.g., 'INVOICE', 'SERVICE_JOB'
    entity_id INT NOT NULL,               -- The ID of the record
    description TEXT,                     -- Details of what changed
    ip_address VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_user ON audit_logs(user_id);