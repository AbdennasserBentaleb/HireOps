CREATE TABLE user_profiles (
    id UUID PRIMARY KEY,
    full_name VARCHAR(100),
    search_keyword VARCHAR(100),
    system_prompt TEXT,
    email_signature TEXT,
    cv_pdf_path VARCHAR(255)
);
