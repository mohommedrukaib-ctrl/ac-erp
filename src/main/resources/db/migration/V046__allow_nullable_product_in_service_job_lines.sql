-- =============================================================================
-- V046: Allow Nullable Product ID in Service Job Lines
-- Description: Enables custom / empty lines (MISC items) without requiring a catalog product_id
-- =============================================================================

ALTER TABLE service_job_lines ALTER COLUMN product_id DROP NOT NULL;