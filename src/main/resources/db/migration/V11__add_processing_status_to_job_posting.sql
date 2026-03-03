ALTER TABLE job_posting ADD COLUMN processing_status VARCHAR(50) DEFAULT 'IDLE';
UPDATE job_posting SET processing_status = 'IDLE' WHERE processing_status IS NULL;
