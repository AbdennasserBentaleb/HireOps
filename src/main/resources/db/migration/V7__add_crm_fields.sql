-- V7: Add hashId (for Arbeitsagentur portal link), notes (CRM notes), and scored_at (for weekly stats)
ALTER TABLE job_posting ADD COLUMN IF NOT EXISTS hash_id VARCHAR(255);
ALTER TABLE job_posting ADD COLUMN IF NOT EXISTS notes TEXT;
ALTER TABLE job_posting ADD COLUMN IF NOT EXISTS scored_at TIMESTAMP;
