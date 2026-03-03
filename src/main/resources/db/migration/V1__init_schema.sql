CREATE TABLE job_posting (
    id UUID PRIMARY KEY,
    reference_id VARCHAR(255) UNIQUE NOT NULL,
    title VARCHAR(255) NOT NULL,
    company VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(50) NOT NULL,
    match_score INTEGER,
    employer_email VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE application (
    id UUID PRIMARY KEY,
    job_id UUID REFERENCES job_posting(id),
    cv_pdf_path VARCHAR(512),
    cover_letter_pdf_path VARCHAR(512),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
