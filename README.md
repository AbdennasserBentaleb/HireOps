# HireOps Engine

HireOps is an automated job application pipeline targeting the German tech market. It orchestrates job ingestion, LLM-based profile matching, document generation, and direct IMAP email dispatch into a unified Kanban tracking dashboard.

![Tech Stack](https://img.shields.io/badge/Spring%20Boot-3.4.3-brightgreen) ![AI](https://img.shields.io/badge/Ollama-llama3-blue) ![DB](https://img.shields.io/badge/PostgreSQL-pgvector-blue) ![License](https://img.shields.io/badge/license-MIT-lightgrey)

**Tech Stack**: Java 25, Spring Boot 3.4, Spring AI, PostgreSQL, pgvector, Ollama, Maven, Docker/Kubernetes.

## Features

| Feature | Description |
| --- | --- |
| Job Ingestion | Fetches live listings from the [Bundesagentur für Arbeit API](https://rest.arbeitsagentur.de) based on configured user profiles |
| AI Matchmaking | Local RAG pipeline scoring jobs against PDF resumes using Ollama (LLaMA-3), ensuring data privacy |
| Document Gen | Auto-generates personalized PDF cover letters context-aware to the specific job description |
| Mailing Engine | Automated dispatch tracking and IMAP synchronization for reply monitoring |
| Kanban Pipeline | Status tracking (Fetched, Scored, Applied, Interviewing, Rejected) |

## Quick Start

### Prerequisites
* Docker and Docker Compose
* Java 25+ and Maven 3.9+

### 1. Start with Hardware Auto-Detection (Recommended)
* **Windows**: Double-click `start.bat`
* **Mac/Linux**: Run `chmod +x start.sh && ./start.sh`

*These scripts auto-query if Docker supports `--gpus all`. If true, launching incorporates `docker-compose.gpu.yml` automatically; otherwise, it defaults cleanly to standard stable CPU layers to guarantee zero startup crashes.*

### 1-Alt. Manual Command
```bash
docker compose build && docker compose up -d
```

### 2. Run the Application Natively
```bash
# Linux / Mac
./mvnw spring-boot:run
# Windows PowerShell
.\mvnw.cmd spring-boot:run
```
The dashboard is accessible at <http://localhost:8081>

## API Documentation

Interactive Swagger/OpenAPI documentation is auto-generated and immediately available without configuration. Once the application is running, you can access the interactive Swagger UI and API specs at:
* **[http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)**

### Graceful Degradation (Reviewer Mode)
If you do not wish to install Ollama or pull LLaMA-3 locally, you can still test the pipeline. The system utilizes Resilience4J Circuit Breakers. If the LLM is unreachable, the system gracefully degrades—skipping AI processing but allowing manual status transitions and fallback PDF generation so the core flow remains testable in under 5 minutes without downloading heavy ML models.

## Architecture Decisions & Trade-offs

* **Tooling Choice: Spring AI & Java 25:** Selected over Python/FastAPI because the JVM provides superior static typing, maintainable enterprise integration patterns (like Spring Batch and JavaMailSender), and mature PDF tooling.
* **Limitation: Inference Hardware Constraints:** Using Ollama locally guarantees that private resume data and match metrics do not leave the host machine. *Trade-off:* This requires significant local RAM/GPU resources, limiting concurrency throughput for score generation. We mitigated this friction by implementing Resilience4J circuit breakers to gracefully fallback if the model is locally unavailable.
* **Challenge: Avoiding Queue Race Conditions:** AI matchmaking is routed via a dedicated DB-backed queue scheduler (`JobQueueScheduler`). Scaling this component originally introduced race conditions where multiple polling worker threads could pick up the same job. *Solution:* We implemented pessimistic native `FOR UPDATE SKIP LOCKED` logic within PostgreSQL to guarantee zero-loss, thread-safe message consumption without introducing heavy external brokers like Kafka.
* **Spring Batch for API Polling:** Spring Batch enables reliable API ingestion with chunk-oriented processing. *Challenge:* Handling pagination rate-limits effectively from external APIs required careful tuning of reader/writer intervals, which is managed via retry and rate-limiting configurations.

## High-Level Architecture

```text
+---------------------------------------------------------+
|                     Browser (Thymeleaf)                 |
|              Kanban Dashboard and Settings              |
+------------------------+--------------------------------+
                         | HTTP
+------------------------v--------------------------------+
|              Spring Boot Application (Port 8081)        |
|                                                         |
|  +-----------------+   +------------------------------+ |
|  |  Spring Batch   |   |      REST Controllers        | |
|  |  Job Ingestion  |   |  (Dashboard, Actions, Docs)  | |
|  +--------+--------+   +--------------+---------------+ |
|           |                           |                 |
|  +--------v---------------------------v--------------+  |
|  |              Service Layer                        |  |
|  |  OllamaMatchmakerService  |  DispatchService      |  |
|  |  ImapEmailSyncService     |  BundesagenturClient  |  |
|  +----------------+----------------------------------+  |
|                   |                                     |
|  +----------------v--------------------------------+    |
|  |  PostgreSQL + pgvector  |  Flyway Migrations    |    |
|  +-------------------------------------------------+    |
+---------------------------------------+-----------------+
             |                          |
    +--------v------+         +---------v-------+
    |  Ollama LLM   |         |  Bundesagentur  |
    |  (Graceful FB)|         |  Jobs API       |
    +---------------+         +-----------------+
```

## Configuration (12-Factor Compliant)

Credentials and configuration points are fully externalized via environment properties with sane, secure defaults.

| Variable | Default (Local) | Purpose |
| --- | --- | --- |
| `POSTGRES_USER` | `hireops_user` | PostgreSQL user |
| `POSTGRES_PASSWORD`| `hireops_secure_pass` | PostgreSQL password |
| `POSTGRES_DB` | `hireops_db` | Main database name |
| `CORS_ALLOWED_ORIGINS`| `http://localhost:3000` | Configurable UI CORS origin |

*Further settings, such as SMTP credentials and custom search keywords, can be dynamically configured within the Settings UI.*

## Project Structure

```text
src/main/java/com/hireops/
+-- batch/          # Spring Batch job ingestion pipeline
+-- controller/     # Web + API controllers
+-- dto/            # API response DTOs
+-- model/          # JPA entities
+-- repository/     # Spring Data repositories
+-- service/        # Core domains (Matchmaker, Mail, API etc.)
```

## Cloud-Native Hardening
- Containers run explicitly as an unprivileged non-root user (UID 65532).
- Container capabilities are explicitly dropped (`cap_drop: ALL`).
- Target file systems are mounted read-only where possible for security isolation.

## License
MIT


### Forward-Compatibility Notice
> **Note:** This project intentionally uses Java 25 (LTS Preview) and Spring Boot 3.4/3.5 to demonstrate forward-compatibility and readiness for the next LTS wave in enterprise environments.