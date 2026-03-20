# HireOps Portfolio & SaaS Roadmap

This document outlines strategic enhancements to transition HireOps from a local automation tool into a high-impact portfolio piece and a potential professional SaaS product.

---

## 1. Technical Hardening (For Portfolio Impact)

### 1.1 Java 25 & Performance
*   **Virtual Thread Execution**: Refactor the `JobQueueScheduler` to use `Executors.newVirtualThreadPerTaskExecutor()`. Since this is an I/O and LLM-heavy application, Virtual Threads will show senior-level mastery of the current JVM.
*   **Structured Concurrency**: Use `StructuredTaskScope` to manage the lifecycle of multi-step AI matches (Extraction -> Analysis -> Generation).

### 1.2 Observability & Reliability
*   **Sleuth/Micrometer Tracing**: Assign a `traceId` to every job fetch and AI match. This allows you to track the lifecycle of an application across logs.
*   **Model Fallback Logic**: Implement a "Circuit Breaker" that switches from a larger model (e.g., Llama 3.1 8B) to a faster, smaller one (e.g., Phi-3.5 or Gemma 2 2B) if latency exceeds a threshold.

---

## 2. AI Innovation (Hardware-Aware)

### 2.1 Multi-Agent Refinement (The "Loop")
Instead of a single massive prompt, break the LLM work into small, high-success tasks:
1.  **Skill Extractor**: (Small model, high speed) Extracts raw tech keywords from JD.
2.  **Strategic Matcher**: (Small model) Compares keywords to CV and identifies the "Winning Narrative."
3.  **Ghostwriter**: (Best model available) Writes the letter based on the Narrative.

### 2.2 Semantic RAG (Retrieval-Augmented Generation)
*   **Historical Match Memory**: Use the existing `pgvector` store to index your *past applications* and *user-edited cover letters*. 
*   **Context Injection**: For any new job, pull the top 3 similar past applications to help the AI mimic your personal writing style and successful strategies.

---

## 3. Product Features & UX

### 3.1 Interview Coaching (Value Add)
*   Automatically generate 5-10 targeted interview questions and "Suggested Answers" based on the specific job match.

### 3.2 Skill Gap Visualization
*   A Dashboard component that explicitly shows: **"Company is looking for [X], you possess [Y]. Here is how to bridge the gap in the interview."**

### 3.3 Real-Time Feedback (SSE)
*   Use Server-Sent Events to show "Live Thinking" logs in the UI while the AI is processing, making the 10-30 second GPU wait time feel interactive rather than stalled.

---

## 4. SaaS Transition Strategy

To move from "Local Tool" to "SaaS," the following architectural changes are required:

1.  **Multi-Tenancy**: Update the database schema to scope all `JobPosting`, `Application`, and `UserProfile` records to a `TenantId` or `UserId`.
2.  **API Integration Layer**: Replace the local Scraping/Batch logic with official API integrations or webhooks from job boards.
3.  **Cloud AI Gateway**: Support both local Ollama (for privacy-conscious users) and a cloud gateway (OpenAI/Anthropic) for users without high-end hardware.
4.  **Auth & Security**: Integrate Spring Security with OIDC (OAuth2/Google Login).
