package com.hireops.service;

import com.hireops.event.ApplicationScoredEvent;

import com.hireops.model.AiMatchResult;
import com.hireops.model.Application;
import com.hireops.model.JobPosting;
import com.hireops.model.JobStatus;
import com.hireops.model.UserProfile;
import com.hireops.model.ResumePersona;
import com.hireops.repository.ApplicationRepository;
import com.hireops.repository.JobPostingRepository;
import com.hireops.repository.UserProfileRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OllamaMatchmakerService {

    private static final Logger log = LoggerFactory.getLogger(OllamaMatchmakerService.class);

    private final ChatClient.Builder chatClientBuilder;
    private final ApplicationRepository applicationRepository;
    private final JobPostingRepository jobPostingRepository;
    private final UserProfileRepository userProfileRepository;
    private final PdfService pdfService;
    private final ApplicationEventPublisher eventPublisher;
    private final AiProgressService aiProgressService;

    @Value("classpath:cv.md")
    private Resource fallbackCvResource;

    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${hireops.pdf.storage-path:./data/resumes/}")
    private String pdfStoragePath;

    public OllamaMatchmakerService(ChatClient.Builder chatClientBuilder,
            ApplicationRepository applicationRepository,
            JobPostingRepository jobPostingRepository,
            UserProfileRepository userProfileRepository,
            PdfService pdfService,
            ApplicationEventPublisher eventPublisher,
            AiProgressService aiProgressService) {
        this.chatClientBuilder = chatClientBuilder;
        this.applicationRepository = applicationRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.userProfileRepository = userProfileRepository;
        this.pdfService = pdfService;
        this.eventPublisher = eventPublisher;
        this.aiProgressService = aiProgressService;
    }

    private UserProfile getProfile() {
        List<UserProfile> profiles = userProfileRepository.findAll();
        return profiles.isEmpty() ? new UserProfile() : profiles.get(0);
    }

    @Transactional
    public void scoreJob(UUID jobId) {
        JobPosting job = jobPostingRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        String jobLabel = job.getTitle() + " @ " + job.getCompany();
        aiProgressService.broadcast("start", "⏳ Scoring: " + jobLabel);

        UserProfile profile = getProfile();

        try {
            // 1. Get CV Content from the User's Persona
            String cvContent = "";
            String systemPrompt;
            ResumePersona activePersona = null;

            String defaultSystemPrompt = "You are a professional IT recruiter and senior cover letter writer in Germany. "
                    + "You write in formal, convincing German (B2/C1 level). "
                    + "You MUST respond ONLY with a raw JSON object — no markdown fences, no explanation, no prose outside the JSON.";

            if (profile.getPersonas() != null && !profile.getPersonas().isEmpty()) {
                activePersona = profile.getPersonas().stream()
                        .filter(ResumePersona::isDefault)
                        .findFirst()
                        .orElse(profile.getPersonas().get(0));

                if (activePersona.getCvPdfPath() != null && !activePersona.getCvPdfPath().isBlank()) {
                    try {
                        cvContent = pdfService.extractTextFromPdf(activePersona.getCvPdfPath());
                    } catch (IOException e) {
                        log.warn("Could not read CV PDF at {}: {}. Trying fallback.", activePersona.getCvPdfPath(), e.getMessage());
                    }
                }

                if (cvContent.isBlank()) {
                    cvContent = ""; // No CV markdown fallback available, will use classpath cv.md below
                }

                systemPrompt = activePersona.getSystemPromptOverride() != null && !activePersona.getSystemPromptOverride().isBlank()
                        ? activePersona.getSystemPromptOverride()
                        : defaultSystemPrompt;
            } else {
                systemPrompt = defaultSystemPrompt;
            }

            // 2. Fallback CV: classpath cv.md
            if (cvContent.isBlank()) {
                try {
                    cvContent = fallbackCvResource.getContentAsString(StandardCharsets.UTF_8);
                    log.info("Using fallback cv.md for job {}", jobId);
                } catch (IOException e) {
                    aiProgressService.broadcast("error", "✗ " + jobLabel + " — No CV found. Upload CV in Settings.");
                    log.error("Could not load fallback CV resource: {}", e.getMessage());
                    throw new RuntimeException(
                            "No CV content found. Please upload your CV PDF in the Settings page before running AI Matching.");
                }
            }

            String aiModel = profile.getAiModel() != null && !profile.getAiModel().trim().isEmpty()
                    ? profile.getAiModel()
                    : "llama3.1";

            // ==== Auto-Pull Logic ====
            if (!"custom".equalsIgnoreCase(aiModel)) {
                ensureModelExists(aiModel);
            }

            ChatClient chatClient = chatClientBuilder
                    .defaultSystem(systemPrompt)
                    .defaultOptions(org.springframework.ai.ollama.api.OllamaOptions.builder()
                            .model(aiModel)
                            .format("json")
                            .build())
                    .build();

            // 3. Make LLM Call
            AiMatchResult result = scoreAndGenerate(chatClient, job, cvContent);

            // 4. Update Job Posting
            job.setMatchScore(result.score());
            
            String fullAnalysis = "";
            if (result.analysis() != null) {
                if (result.analysis().isArray()) {
                    StringBuilder sb = new StringBuilder();
                    for (com.fasterxml.jackson.databind.JsonNode node : result.analysis()) {
                        sb.append(node.asText()).append("\n");
                    }
                    fullAnalysis = sb.toString().trim();
                } else {
                    fullAnalysis = result.analysis().asText();
                }
            }
            job.setAiAnalysis(fullAnalysis);
            job.setStatus(JobStatus.SCORED);
            job.setScoredAt(java.time.LocalDateTime.now());

            String currentNotes = job.getNotes() != null ? job.getNotes() : "";
            job.setNotes(currentNotes);

            jobPostingRepository.save(job);

            // 5. Generate Real Cover Letter PDF
            String clFileName = "cl_" + jobId.toString() + ".pdf";
            String generatedPdfPath = pdfService.generateCoverLetterPdf(result.coverLetterMarkdown(), pdfStoragePath,
                    clFileName);

            // 6. Create Application Record
            Application application = new Application();
            application.setJobPosting(job);

            if (activePersona != null && activePersona.getCvPdfPath() != null) {
                application.setCvPdfPath(activePersona.getCvPdfPath());
            }
            application.setCoverLetterPdfPath(generatedPdfPath);

            applicationRepository.save(application);
            eventPublisher.publishEvent(new ApplicationScoredEvent(this, application));

            aiProgressService.broadcast("success", "✓ " + jobLabel + " — " + result.score() + "% match");

        } catch (Exception e) {
        try {
            job.setMatchScore(0);
            String root = getRootCause(e);
            
            String uiFriendlyMessage = root;
            if (root.contains("404") || root.toLowerCase().contains("model 'llama3' not found")) {
                uiFriendlyMessage = "The AI model ('llama3') is currently downloading in the background or unavailable. First-time setup usually takes 5-10 minutes. Please wait and try again.";
            } else if (root.contains("No valid JSON object delimiters")) {
                uiFriendlyMessage = "The AI model returned an incomplete response (likely due to hallucination or exceeding context buffers). It has been gracefully deferred.";
            } else if (root.contains("OPEN")) {
                uiFriendlyMessage = "AI Matchmaking is deferred due to ongoing connection recovery. The system is re-establishing model health (Circuit Breaker OPEN).";
            }

            String fallbackNote = "[System] AI Matchmaking Deferred — " + uiFriendlyMessage;
            job.setNotes(job.getNotes() == null || job.getNotes().isEmpty() ? fallbackNote
                    : job.getNotes() + "\n\n" + fallbackNote);
            job.setAiAnalysis("AI Matching deferred. Reason: " + uiFriendlyMessage);
            job.setStatus(JobStatus.SCORED);
            job.setScoredAt(java.time.LocalDateTime.now());
            jobPostingRepository.save(job);

            Application application = new Application();
            application.setJobPosting(job);

            String fallbackMarkdown = "# AI Matchmaking Failed\n\n**Cause:** " + root +
                    "\n\n## What to check\n1. Verify Ollama is running: `docker compose ps`\n" +
                    "2. Check the model is pulled: open Settings → AI Status panel\n" +
                    "3. Retry after confirming the AI status shows **Healthy**";
            String clFileName = "cl_fallback_" + jobId.toString() + ".pdf";
            String generatedPdfPath = pdfService.generateCoverLetterPdf(fallbackMarkdown, pdfStoragePath, clFileName);

            application.setCoverLetterPdfPath(generatedPdfPath);
            applicationRepository.save(application);
            eventPublisher.publishEvent(new ApplicationScoredEvent(this, application));

            aiProgressService.broadcast("error", "✗ " + jobLabel + " — " + uiFriendlyMessage);
        } catch (Exception ex) {
            log.error("Failed to execute fallback logic for job {}: {}", jobId, ex.getMessage());
        }
        }
    }

    private String getRootCause(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }

    private void ensureModelExists(String modelName) {
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            
            // Check if model exists
            java.net.http.HttpRequest checkReq = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(ollamaBaseUrl + "/api/tags"))
                    .GET()
                    .build();
            java.net.http.HttpResponse<String> checkRes = client.send(checkReq, java.net.http.HttpResponse.BodyHandlers.ofString());
            String tagsJson = checkRes.body();
            
            boolean modelExists = tagsJson.contains("\"name\":\"" + modelName + "\"") 
                               || tagsJson.contains("\"name\":\"" + modelName + ":latest\"");
            
            if (!modelExists) {
                log.info("Model '{}' missing locally. Auto-pulling from Ollama registry...", modelName);
                aiProgressService.broadcast("start", "⏳ Model '" + modelName + "' not found locally. Auto-pulling from Ollama registry (this may take a few minutes)...");
                
                String pullJson = "{\"name\": \"" + modelName + "\"}";
                java.net.http.HttpRequest pullReq = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(ollamaBaseUrl + "/api/pull"))
                        .header("Content-Type", "application/json")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(pullJson))
                        .build();
                // Pulling is synchronous and can take a long time to return
                java.net.http.HttpResponse<String> pullRes = client.send(pullReq, java.net.http.HttpResponse.BodyHandlers.ofString());
                
                if (pullRes.statusCode() >= 200 && pullRes.statusCode() < 300) {
                    log.info("Successfully pulled model: {}", modelName);
                    aiProgressService.broadcast("start", "✅ Model '" + modelName + "' pulled successfully.");
                } else {
                    log.error("Failed to pull model '{}'. Status: {}, Response: {}", modelName, pullRes.statusCode(), pullRes.body());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to verify/pull Ollama model '{}': {}", modelName, e.getMessage());
        }
    }

    /**
     * Calls the LLM with a DEEP PERSONALIZED prompt that generates:
     * - A score (0-100) based on CV-to-JD fit
     * - A full DIN 5008-style German cover letter (300-400 words), personalized 
     *   to the specific job title, company, and skill requirements
     * - An analysis with 3-5 bullet points mapping specific CV skills to JD requirements
     */
    public AiMatchResult scoreAndGenerate(ChatClient chatClient, JobPosting job, String cvMarkdown) {

        // Trim inputs to avoid token overflow
        String jdTrimmed = job.getDescription() != null
                ? (job.getDescription().length() > 4000 ? job.getDescription().substring(0, 4000) + "..." : job.getDescription())
                : job.getTitle();
        String cvTrimmed = cvMarkdown.length() > 4000 ? cvMarkdown.substring(0, 4000) + "..." : cvMarkdown;

        String jobTitle = job.getTitle() != null ? job.getTitle() : "die ausgeschriebene Stelle";
        String company = job.getCompany() != null ? job.getCompany() : "Ihr Unternehmen";

        String prompt = "You are a senior German cover letter writer. Analyse the Job Description vs. the CV below and return ONLY the JSON object shown. No other text.\n\n"
                + "CRITICAL DIRECTIVE: Do NOT remember or reference previous prompts. Do NOT state that you have 'already graded' this. Treat this as a completely fresh task regardless of duplication. You MUST output JSON.\n\n"
                + "REQUIRED JSON (fill all fields, copy exact structure):\n"
                + "{\"score\": 82, \"coverLetterMarkdown\": \"<full letter here>\", \"analysis\": [\"bullet 1\", \"bullet 2\"]}\n\n"
                + "=== RULES ===\n"
                + "score: integer 0-100 measuring how well the CV matches the job requirements.\n\n"
                + "coverLetterMarkdown: A professional German cover letter with ALL of these:\n"
                + "  - Betreff line naming the position '" + jobTitle + "'\n"
                + "  - Anrede: 'Sehr geehrte Damen und Herren,'\n"
                + "  - Paragraph 1 (3-4 sentences): Express interest in '" + company + "'.\n"
                + "  - Paragraph 2 (4-5 sentences): Map SPECIFIC items from CV to JD. Name exact technologies.\n"
                + "  - Grußformel: 'Mit freundlichen Grüßen,\\n[Applicant Name]'\n"
                + "  - Escape all double quotes as \\\".\n\n"
                + "analysis: A JSON Array of 3 exact strings in German explaining the score. E.g. [\"• Match in...\", \"• Lack of...\", \"• Best fit...\"]\n\n"
                + "Return ONLY the JSON object. No other text.\n\n"
                + "### Job Description\n" + jdTrimmed + "\n\n"
                + "### Candidate CV\n" + cvTrimmed;

        try {
            ChatResponse response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .chatResponse();

            String rawContent = response.getResult().getOutput().getText();
            log.info("LLM RAW RESPONSE for scoring: {}", rawContent);

            String cleaned = extractJsonFromResponse(rawContent);

            com.fasterxml.jackson.databind.ObjectMapper mapper = com.fasterxml.jackson.databind.json.JsonMapper
                    .builder()
                    .enable(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
                    .enable(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_SINGLE_QUOTES)
                    .enable(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
                    .enable(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
                    .enable(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_TRAILING_COMMA)
                    .build();
            
            return mapper.readValue(cleaned, AiMatchResult.class);

        } catch (Exception e) {
            log.error("LLM parsing failed: {}", e.getMessage());
            throw new RuntimeException(
                    "Primary LLM Service returned invalid JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts the first valid JSON object from LLM output.
     * Handles: markdown fences, leading text, trailing text.
     */
    private String extractJsonFromResponse(String raw) {
        if (raw == null || raw.isBlank()) throw new RuntimeException("LLM returned empty response");

        // Strip common markdown fences
        String cleaned = raw.trim();
        if (cleaned.contains("```json")) {
            cleaned = cleaned.substring(cleaned.indexOf("```json") + 7);
            cleaned = cleaned.contains("```") ? cleaned.substring(0, cleaned.lastIndexOf("```")) : cleaned;
        } else if (cleaned.contains("```")) {
            cleaned = cleaned.substring(cleaned.indexOf("```") + 3);
            cleaned = cleaned.contains("```") ? cleaned.substring(0, cleaned.lastIndexOf("```")) : cleaned;
        }

        // Try to find the JSON object boundary using braces
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start == -1 || end == -1 || end <= start) {
            throw new RuntimeException("No valid JSON object delimiters ({ }) found in LLM response");
        }

        cleaned = cleaned.substring(start, end + 1);
        return cleaned.trim();
    }
}
