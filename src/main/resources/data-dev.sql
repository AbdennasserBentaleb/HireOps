-- Insert default user profile
INSERT INTO user_profiles (id, full_name, search_keyword, system_prompt, email_signature, cv_pdf_path) 
VALUES (
    RANDOM_UUID(), 
    'Alex Developer', 
    'Java Backend Developer', 
    'You are a professional IT job matcher and cover letter writer. Respond ONLY in valid JSON matching the schema. Generate the coverLetterMarkdown in B2 level German suitable for a Software Engineer applying in Germany.', 
    'Mit freundlichen Grüßen,\nAlex Developer\nSenior Java Engineer\nhttps://github.com/alexdev', 
    NULL
);

-- Insert Dummy Jobs (FETCHED)
INSERT INTO job_posting (id, reference_id, title, company, description, status, match_score, employer_email, created_at)
VALUES (
    RANDOM_UUID(), 
    'REF-1001', 
    'Senior Java Software Engineer (m/w/d)', 
    'TechFinance GmbH', 
    'Wir suchen einen erfahrenen Java-Entwickler mit tiefgehenden Kenntnissen in Spring Boot, Microservices und PostgreSQL. Wir bieten ein agiles Arbeitsumfeld und Remote-Work Optionen. Fließendes Deutsch ist ein Plus, aber Englisch ist die Hauptsprache. Erfahrung mit Kubernetes und Docker wird erwartet.', 
    'FETCHED', 
    NULL, 
    'hr@techfinance.de', 
    CURRENT_TIMESTAMP()
);

INSERT INTO job_posting (id, reference_id, title, company, description, status, match_score, employer_email, created_at)
VALUES (
    RANDOM_UUID(), 
    'REF-1002', 
    'Backend Developer - Spring Boot (f/m/x)', 
    'HealthCore Startup', 
    'Join our fast-growing MedTech startup! We are looking for a passionate Java developer to help build scalable APIs handling sensitive medical data. You should have 3+ years of experience with Java and Spring Framework. Nice to have: AWS, Terraform, and a passion for clean code.', 
    'FETCHED', 
    NULL, 
    'jobs@healthcore.com', 
    CURRENT_TIMESTAMP()
);

-- Insert Dummy Jobs (SCORED)
INSERT INTO job_posting (id, reference_id, title, company, description, status, match_score, employer_email, created_at)
VALUES (
    RANDOM_UUID(), 
    'REF-1003', 
    'Lead Backend Software Engineer', 
    'RetailLogistics Global', 
    'Looking for a tech lead to architect our next-generation logistics platform. Requirements: 8+ years Java, expertise in distributed systems, Kafka, Spring Cloud, and team leadership. Fluent German required (C1 level). AWS certifications are highly preferred.', 
    'SCORED', 
    88, 
    'recruiting@retaillogistics.de', 
    CURRENT_TIMESTAMP()
);

INSERT INTO job_posting (id, reference_id, title, company, description, status, match_score, employer_email, created_at)
VALUES (
    RANDOM_UUID(), 
    'REF-1004', 
    'Java Developer - E-Commerce', 
    'ShopNinja GmbH', 
    'We run one of the largest e-commerce platforms in Europe. Looking for Java developers to join our checkout team. Stack: Java 21, Spring Boot 3, Redis, Postgres. We emphasize TDD and pair programming. B1 German is sufficient.', 
    'SCORED', 
    95, 
    'careers@shopninja.example', 
    CURRENT_TIMESTAMP()
);

-- Insert Dummy Jobs (APPROVED)
INSERT INTO job_posting (id, reference_id, title, company, description, status, match_score, employer_email, created_at)
VALUES (
    RANDOM_UUID(), 
    'REF-1005', 
    'Cloud Native Java Engineer', 
    'CloudScale Systems', 
    'Help us build multi-tenant SaaS platforms using Java and Kubernetes. Strong experience in optimizing JVM performance and building resilient APIs required. 100% remote within Germany. English is the company language.', 
    'APPROVED', 
    92, 
    'talent@cloudscale.io', 
    CURRENT_TIMESTAMP()
);

-- Insert Dummy Jobs (APPLIED)
INSERT INTO job_posting (id, reference_id, title, company, description, status, match_score, employer_email, created_at)
VALUES (
    RANDOM_UUID(), 
    'REF-1006', 
    'Software Engineer (Java/Kotlin)', 
    'FinTech Innovators', 
    'Build the future of payment processing. We are transitioning from Java to Kotlin. Experience in either language is great. High security standards, PCI-DSS knowledge is a bonus. Hybrid work in our Berlin office.', 
    'APPLIED', 
    85, 
    'hiring@fintech.berlin', 
    CURRENT_TIMESTAMP()
);
