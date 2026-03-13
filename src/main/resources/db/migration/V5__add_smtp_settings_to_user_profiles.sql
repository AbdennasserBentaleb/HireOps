ALTER TABLE user_profiles ADD COLUMN smtp_host VARCHAR(255);
ALTER TABLE user_profiles ADD COLUMN smtp_port INTEGER;
ALTER TABLE user_profiles ADD COLUMN smtp_username VARCHAR(255);
ALTER TABLE user_profiles ADD COLUMN smtp_password VARCHAR(255);
ALTER TABLE user_profiles ADD COLUMN smtp_auth BOOLEAN DEFAULT TRUE;
ALTER TABLE user_profiles ADD COLUMN smtp_start_tls BOOLEAN DEFAULT TRUE;
