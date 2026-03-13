# Product Requirements Document for HireOps Engine

## 1. Product Overview

HireOps Engine is a job search platform tailored for the German tech market. I built it to automate the job search and application process by continuously finding relevant roles and generating applications in German.

## 2. Target Audience

Software Engineers and IT Professionals seeking opportunities in the German market, especially those optimizing for the EU Blue Card requirements.

## 3. Core Features

* Automated Job Ingestion: Periodically fetches job postings from the official Bundesagentur für Arbeit API.
* AI Matchmaker: Compares fetched job descriptions against a Markdown CV using a local LLM like Ollama.
* Document Generation: Generates Cover Letters in German and converts Markdown CVs and Cover Letters to PDF format.
* Kanban CRM Dashboard: Tracks applications across states like FETCHED, SCORED, APPROVED, and APPLIED.
* Dispatch: Requires manual approval to dispatch the application. Upon approval, it can use OSINT APIs to find the employer email and dispatch the PDFs via email.

## 4. User Flow

1. Ingestion: The system queries the Bundesagentur für Arbeit API according to a schedule and saves matches to the database.
2. Matching and Scoring: For each new job, the local LLM evaluates the fit against the user CV and assigns a match score.
3. Generation: If the score meets a threshold, the system triggers the generation of a Cover Letter in German. The CV and Cover Letter are rendered into PDFs.
4. Review: The user logs into the Thymeleaf web dashboard to view the Kanban board. The new job appears in the SCORED column.
5. Approval: The user clicks Approve on a job.
6. Dispatch: The system shifts the job to APPROVED, finds the hiring manager email, sends the application via JavaMailSender, and updates the status to APPLIED.

## 5. Non Functional Requirements

* Local AI: Must not rely on paid external APIs for LLM capabilities.
* Privacy: Keep personal data on premises. No external storage of CVs.
* Language: Generated cover letters must reflect professional German proficiency.
