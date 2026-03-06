# Lena Fischer
**Senior Backend Engineer (Java / Cloud Native)**
Email: lena.fischer@dev-mail.de | Phone: +49 176 4821 0034
Location: Berlin, Germany | LinkedIn: linkedin.com/in/lena-fischer-dev
GitHub: github.com/lena-fischer-dev | Nationality: German (EU citizen)

---

## Professional Summary

Senior Backend Engineer with 7+ years of experience designing and delivering high-throughput distributed systems in fast-paced SaaS and FinTech environments. Proven track record of leading backend teams to ship event-driven microservice platforms on Kubernetes. Deep expertise in the Java ecosystem (Spring Boot, Spring Cloud, Batch), relational databases, and cloud infrastructure (AWS, GCP). Passionate about clean architecture, TDD, and building systems that scale.

---

## Work Experience

### Solaris Bank AG — Senior Backend Engineer *(Berlin, Germany | Jan 2022 – Present)*

- Led a 4-person backend squad delivering a real-time payment reconciliation service handling €2B+ in monthly transaction volume using Java 21, Spring Boot 3, and Apache Kafka.
- Designed a multi-tenant event-sourcing architecture deployed on AWS EKS, reducing per-tenant setup time from 3 days to under 2 hours.
- Introduced pessimistic locking (`FOR UPDATE SKIP LOCKED`) for idempotent job queue processing, eliminating duplicate-payment incidents in a high-concurrency ingestion pipeline.
- Improved batch job throughput by 340% by migrating sequential Spring Batch steps to parallel partitioned readers backed by SQS.
- Mentored two junior engineers, achieving their first independent feature delivery within 6 weeks.

### Zalando SE — Backend Engineer *(Berlin, Germany | Mar 2019 – Dec 2021)*

- Built and maintained catalog search indexing services using Java 11, Spring WebFlux, and Elasticsearch, reducing P99 search latency by 28%.
- Delivered Flyway-managed PostgreSQL schema migrations for a multi-region deployment with zero-downtime rollout strategies.
- Collaborated cross-functionally with Product, QA, and Infrastructure teams in a scaled Agile (SAFe) environment.
- Introduced API contract testing with Pact, catching 14 regressions that would have reached production in Q3 2021.

### Fraunhofer FOKUS — Software Engineer (Working Student) *(Berlin, Germany | Sep 2017 – Feb 2019)*

- Developed a Java-based IoT gateway integration module using REST and MQTT protocols for a smart city research project.
- Wrote integration tests using JUnit 5 and Testcontainers, achieving 87% code coverage on critical data-ingestion paths.

---

## Education

**M.Sc. Computer Science** — Technical University of Berlin *(2015 – 2018)*
Specialization: Distributed Systems and Databases. Master thesis: *"Optimistic vs. Pessimistic Concurrency Control in High-Throughput OLTP Systems"* (Grade: 1.2 / very good).

**B.Sc. Computer Science** — Humboldt University of Berlin *(2012 – 2015)*
Bachelor thesis: *"Performance Analysis of JVM Garbage Collectors Under Sustained Load"* (Grade: 1.5 / very good).

---

## Technical Skills

| Category | Technologies |
|---|---|
| Languages | Java 21/25, Kotlin, Python (scripting), SQL |
| Frameworks | Spring Boot 3, Spring Cloud, Spring Batch, Spring AI, Micronaut |
| Messaging & Streaming | Apache Kafka, RabbitMQ, Amazon SQS |
| Databases | PostgreSQL (incl. pgvector), Redis, MongoDB, H2 |
| Infrastructure | Docker, Kubernetes (EKS, k3s), Helm, Terraform, AWS (EC2, RDS, EKS, SQS), GCP |
| Observability | Prometheus, Grafana, Loki, OpenTelemetry, Micrometer |
| Testing | JUnit 5, Mockito, Testcontainers, Pact, WireMock |
| Other | Flyway, Maven, Gradle, Git, GitHub Actions, Jenkins, Jira, Confluence |

---

## Languages

- **German**: Native
- **English**: Fluent (C2)
- **French**: Conversational (B2)

---

## Certifications

- **AWS Certified Solutions Architect – Associate** (2023)
- **Certified Kubernetes Administrator (CKA)** (2022)
- **Oracle Certified Professional: Java SE 17** (2021)

---

## Projects & Open Source

- **hireops-engine** (2025): Personal project — AI-powered job application pipeline for the German job market using Spring AI, Ollama (LLaMA-3), pgvector, Spring Batch, and OpenHTMLToPDF. Full containerised stack deployed via Docker Compose.
- Contributed a connection-pool leak fix merged into the `spring-data-jdbc` OSS project (April 2023).

---

## Interests

Distributed systems research, competitive programming (Advent of Code, LeetCode), hiking in the Alps, and visiting Berlin tech meetups.
