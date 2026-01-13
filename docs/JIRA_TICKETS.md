# Jira Tickets: HireOps Engine MVP

Project Prerequisites:
- Strict adherence to TDD (JUnit 5 + Mockito). Tests must be written before implementation.
- Constructor injection only (No `@Autowired` fields).
- Java 25 & Spring Boot 3.x.

## Ticket 1: Project Initialization & Infrastructure Configuration
- **Type**: Task
- **Description**: Setup the initial Maven project for Spring Boot 3.x with Java 25. Create the base Dockerfile using a Distroless image and write the `docker-compose.yml` file for local infrastructure (Postgres with pgvector).
- **Acceptance Criteria**: 
  - `pom.xml` configured for Java 25.
  - `Dockerfile` structured for secure unprivileged execution.
  - Basic database orchestration documented in `docker-compose.yml`.

## Ticket 2: Database Schema & Entity Layer (TDD)
- **Type**: Story
- **Description**: Implement `JobPosting` and `Application` entities. Define Spring Data JPA Repositories.
- **Acceptance Criteria**:
  - Flyway or Liquibase migrations for table creation.
  - `JobPostingRepository` and `ApplicationRepository` created.
  - **TDD Requirement**: `DataJpaTest` using Testcontainers (PostgreSQL) to verify entity mapping and basic CRUD operations.

## Ticket 3: Ingestion Engine - API Client (TDD)
- **Type**: Story
- **Description**: Create a service communicating with the Bundesagentur für Arbeit API using `RestClient` or `WebClient`.
- **Acceptance Criteria**:
  - Service fetches jobs using keywords.
  - **TDD Requirement**: Write MockRestServiceServer/WireMock tests to validate request/response parsing BEFORE writing the API client.

## Ticket 4: Ingestion Engine - Spring Batch Process (TDD)
- **Type**: Story
- **Description**: Configure Spring Batch to schedule and execute the ingestion job.
- **Acceptance Criteria**:
  - Scheduled job fetches from API, transforms data, and writes to `JobPostingRepository`.
  - Emits `JobFetchedEvent`.
  - **TDD Requirement**: Use `SpringBatchTest` to test `ItemReader`, `ItemProcessor`, and `ItemWriter` in isolation.

## Ticket 5: Local AI Matchmaker - Ollama Integration (TDD)
- **Type**: Story
- **Description**: Implement the matching and cover letter generation using Spring AI configured for local Ollama.
- **Acceptance Criteria**:
  - Service listens for `JobFetchedEvent`.
  - Prompts Ollama with Job Description + Markdown CV.
  - Parses JSON response (score + B1/B2 German cover letter).
  - **TDD Requirement**: Mock the `ChatClient` to return predefined responses and verify the service's formatting, scoring logic, and event publishing (`ApplicationScoredEvent`).

## Ticket 6: PDF Generation Engine (TDD)
- **Type**: Task
- **Description**: Implement Markdown to PDF conversion using Apache PDFBox (or Flexmark-java + OpenPDF).
- **Acceptance Criteria**:
  - Service converts Markdown strings to formatted PDFs.
  - Saves files to the mounted `/app/data/pdfs` volume and updates DB entity paths.
  - **TDD Requirement**: Write unit tests passing sample markdown to ensure the output `byte[]` or `File` is generated without exceptions and contains text metadata.

## Ticket 7: CRM Dashboard Backend & UI (TDD)
- **Type**: Story
- **Description**: Build the Thymeleaf controllers and views for the Kanban board.
- **Acceptance Criteria**:
  - Routes for `/dashboard`, displaying jobs grouped by status.
  - Thymeleaf templates utilizing simple CSS (Tailwind/Bootstrap).
  - "Approve" button triggers a POST request.
  - **TDD Requirement**: `WebMvcTest` for the dashboard controller, mocking the underlying services to assert model attributes and view names.

## Ticket 8: Approval & Dispatch Mechanism (TDD)
- **Type**: Story
- **Description**: Handle the application approval lifecycle and email dispatch using predicted or extracted employer emails.
- **Acceptance Criteria**:
  - Controller handles POST to `/jobs/{id}/approve`.
  - Service predicts or extracts the hiring manager's email heuristically.
  - Dispatch via `JavaMailSender` attaching the PDFs.
  - **TDD Requirement**: Use `GreenMail` or mock `JavaMailSender` to guarantee attachments and recipients are strictly formatted before sending and fallback heuristics work correctly.
