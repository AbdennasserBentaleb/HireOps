-- Alter title and company columns to accommodate longer strings from the API
ALTER TABLE job_posting ALTER COLUMN title TYPE VARCHAR(2000);
ALTER TABLE job_posting ALTER COLUMN company TYPE VARCHAR(2000);
