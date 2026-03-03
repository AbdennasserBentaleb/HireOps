ALTER TABLE user_profiles ADD COLUMN min_match_score INTEGER DEFAULT 75;
ALTER TABLE user_profiles ADD COLUMN max_job_age_days INTEGER DEFAULT 30;

CREATE TABLE resume_personas (
    id UUID PRIMARY KEY,
    user_profile_id UUID NOT NULL,
    persona_name VARCHAR(255) NOT NULL,
    role VARCHAR(255),
    system_prompt_override TEXT,
    cv_pdf_path VARCHAR(255),
    is_default BOOLEAN,
    CONSTRAINT fk_user_profile FOREIGN KEY (user_profile_id) REFERENCES user_profiles (id) ON DELETE CASCADE
);
