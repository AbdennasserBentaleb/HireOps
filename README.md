# HireOps Engine

HireOps is a job application pipeline tool I built to automate my job search in the German tech market. It fetches job listings, scores them against my CV using a local LLM, generates cover letters, and sends out the applications. Everything runs on a self-hosted dashboard.

![Tech Stack](https://img.shields.io/badge/Spring%20Boot-3.4-brightgreen) ![AI](https://img.shields.io/badge/Ollama-llama3-blue) ![DB](https://img.shields.io/badge/PostgreSQL-pgvector-blue) ![License](https://img.shields.io/badge/license-MIT-lightgrey)

## Features

| Feature | Description |
| *|* |
| Job Ingestion | Fetches live listings from the [Bundesagentur für Arbeit API](https://rest.arbeitsagentur.de) using your configured keyword |
| AI Matchmaking | Scores each job 0 to 100 percent against your uploaded CV using a local Ollama LLM so no data leaves your machine |
| Cover Letter Gen | Generates a cover letter in professional German per job |
| Email Dispatch | Sends your CV and cover letter PDF to the hiring manager with one click |
| Kanban Pipeline | Visual board with four stages, Pipeline Inbox, AI Review, Ready to Dispatch, and Dispatched |
| IMAP Sync | Automatically detects replies and updates job status |
| Fully Self Hosted | Runs on your own machine without SaaS accounts required |

## Quick Start

### Prerequisites

* Docker and Docker Compose
* Java 21+ and Maven 3.9+
* [Ollama](https://ollama.ai) with llama3 pulled, run `ollama pull llama3`

### 1. Start PostgreSQL

```bash
docker compose up -d
```

### 2. Run the application

```bash
mvn spring-boot:run
```

The app starts on <http://localhost:8081>

### 3. Configure Settings

Navigate to Settings and fill in:

* Your name and job search keyword like `Java Entwickler`
* Upload your CV as a PDF
* SMTP credentials for email dispatch

### 4. Use the Pipeline

1. On the Kanban board, click Run AI Matchmaker on any job card
2. Wait a bit for the LLM to score and generate a cover letter
3. Review the score and preview the documents
4. Click Verify and Approve Draft
5. Confirm the recipient email and click Execute Dispatch

## Architecture

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
    |  (llama3)     |         |  Jobs API       |
    +---------------+         +-----------------+
```

## Configuration (src/main/resources/application.yml)

| Key | Default | Description |
| *|* | * |
| ba.api.url | Bundesagentur API | Base URL for job search |
| spring.ai.ollama.base-url | <http://localhost:11434> | Ollama server URL |
| spring.ai.ollama.chat.options.model | llama3 | LLM model name |
| spring.datasource.url | jdbc:postgresql://localhost:5432/hireops | PostgreSQL DB |

Mail credentials are configured per user via the Settings UI.

## Tech Stack

* Backend: Java 21, Spring Boot 3.4, Spring Batch, Spring AI
* Database: PostgreSQL with pgvector
* AI: Ollama, llama3
* Frontend: Thymeleaf, Tailwind CSS
* Email: Jakarta Mail
* External API: Bundesagentur für Arbeit REST API

## Project Structure

```text
src/main/java/com/hireops/
+-- batch/          # Spring Batch job ingestion pipeline
+-- controller/     # Web + API controllers
+-- dto/            # API response DTOs
+-- model/          # JPA entities
+-- repository/     # Spring Data repositories
+-- service/        # Business logic

src/main/resources/
+-- db/migration/   # Flyway SQL migrations
+-- templates/      # Thymeleaf HTML templates
```

## License

MIT
