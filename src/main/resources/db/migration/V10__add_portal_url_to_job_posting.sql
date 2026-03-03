-- V10: Add portal_url column to job_posting table
-- Stores the direct application link fetched from the Bundesagentur API detail endpoint
ALTER TABLE job_posting ADD COLUMN IF NOT EXISTS portal_url VARCHAR(2000);
