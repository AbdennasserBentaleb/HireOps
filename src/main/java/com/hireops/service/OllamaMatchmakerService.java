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
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
public class OllamaMatchmakerService {

    private final ChatClient.Builder chatClientBuilder;
    private final ApplicationRepository applicationRepository;
    private final JobPostingRepository jobPostingRepository;
    private final UserProfileRepository userProfileRepository;
    private final PdfService pdfService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("classpath:cv.md")
    private Resource fallbackCvResource;

    @Value("${app.settings.pdf-storage-path:/tmp/hireops_pdfs}")
    private String pdfStoragePath;

    public OllamaMatchmakerService(ChatClient.Builder chatClientBuilder,
            ApplicationRepository applicationRepository,
            JobPostingRepository jobPostingRepository,
            UserProfileRepository userProfileRepository,
            PdfService pdfService,
            ApplicationEventPublisher eventPublisher) {
        this.chatClientBuilder = chatClientBuilder;
        this.applicationRepository = applicationRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.userProfileRepository = userProfileRepository;
        this.pdfService = pdfService;
        this.eventPublisher = eventPublisher;
    }

    private UserProfile getProfile() {
        List<UserProfile> profiles = userProfileRepository.findAll();
        return profiles.isEmpty() ? new UserProfile() : profiles.get(0);
    }

    @Transactional
    @org.springframework.scheduling.annotation.Async
    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name = "ollamaCB", fallbackMethod = "scoreJobFallback")
    @io.github.resilience4j.retry.annotation.Retry(name = "ollamaRetry")
    public void scoreJob(UUID jobId) {
        JobPosting job = jobPostingRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        UserProfile profile = getProfile();

        try {
            // 1. Get CV Content from the User's Persona
            String cvContent = "";
            String systemPrompt;
            ResumePersona activePersona = null;

            if (profile.getPersonas() != null && !profile.getPersonas().isEmpty()) {
                // Find default persona or just use the first one
                activePersona = profile.getPersonas().stream()
                        .filter(ResumePersona::isDefault)
                        .findFirst()
                        .orElse(profile.getPersonas().get(0));

                if (activePersona.getCvPdfPath() != null) {
                    cvContent = pdfService.extractTextFromPdf(activePersona.getCvPdfPath());
                }

                String defaultPrompt = "You are a professional IT job matcher and cover letter writer. Respond ONLY in valid JSON matching the schema. Generate the coverLetterMarkdown in B1/B2 level German suitable for a Software Engineer applying in Germany.";
                systemPrompt = activePersona.getSystemPromptOverride() != null
                        ? activePersona.getSystemPromptOverride()
                        : (profile.getSystemPrompt() != null ? profile.getSystemPrompt() : defaultPrompt);
            } else {
                // Fallback if no personas configured at all
                cvContent = new String(fallbackCvResource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                String defaultPrompt = "You are a professional IT job matcher and cover letter writer. Respond ONLY in valid JSON matching the schema. Generate the coverLetterMarkdown in B1/B2 level German suitable for a Software Engineer applying in Germany.";
                systemPrompt = profile.getSystemPrompt() != null ? profile.getSystemPrompt() : defaultPrompt;
            }

            // Guard: refuse to call LLM if no CV text could be extracted
            if (cvContent == null || cvContent.isBlank()) {
                throw new RuntimeException(
                        "No CV content found. Please upload your CV PDF in the Settings page before running AI Matching.");
            }

            ChatClient chatClient = chatClientBuilder.defaultSystem(systemPrompt).build();

            // 3. Make LLM Call
            AiMatchResult result = scoreAndGenerate(chatClient, job.getDescription(), cvContent);

            // 4. Update Job Posting
            job.setMatchScore(result.score());
            job.setStatus(JobStatus.SCORED);
            job.setScoredAt(java.time.LocalDateTime.now());
            jobPostingRepository.save(job);

            // 5. Generate Real Cover Letter PDF
            String clFileName = "cl_" + jobId.toString() + ".pdf";
            String generatedPdfPath = pdfService.generateCoverLetterPdf(result.coverLetterMarkdown(), pdfStoragePath,
                    clFileName);

            // 6. Create Application Record
            Application application = new Application();
            application.setJobPosting(job);

            if (activePersona != null && activePersona.getCvPdfPath() != null) {
                application.setCvPdfPath(activePersona.getCvPdfPath()); // Attach the real computed Persona CV
            }
            application.setCoverLetterPdfPath(generatedPdfPath); // Attach the newly generated CL PDF

            applicationRepository.save(application);

            eventPublisher.publishEvent(new ApplicationScoredEvent(this, application));

        } catch (Exception e) {
            throw new RuntimeException("Error during AI Matching and PDF Generation", e);
        }
    }

    public void scoreJobFallback(UUID jobId, Throwable t) {
        System.err.println("Circuit Breaker OPEN for job " + jobId + ". Fallback active. Error: " + t.getMessage());
        try {
            JobPosting job = jobPostingRepository.findById(jobId).orElse(null);
            if (job != null) {
                job.setMatchScore(0);
                String fallbackNote = "[System] AI Matchmaking failed (Model unavailable or Circuit Breaker OPEN or Parsing Error).";
                job.setNotes(job.getNotes() == null || job.getNotes().isEmpty() ? fallbackNote
                        : job.getNotes() + "\n\n" + fallbackNote);
                job.setStatus(JobStatus.SCORED);
                job.setScoredAt(java.time.LocalDateTime.now());
                jobPostingRepository.save(job);

                Application application = new Application();
                application.setJobPosting(job);

                String fallbackMarkdown = "# AI Matchmaking Failed\n\nThe AI LLM service is currently unavailable or returned an invalid response.\n\nDue to the Circuit Breaker fallback, the match score was set to 0. Please ensure the model is responsive and retry later.";
                String clFileName = "cl_fallback_" + jobId.toString() + ".pdf";
                String generatedPdfPath = pdfService.generateCoverLetterPdf(fallbackMarkdown, pdfStoragePath,
                        clFileName);

                application.setCoverLetterPdfPath(generatedPdfPath);
                applicationRepository.save(application);
                eventPublisher.publishEvent(new ApplicationScoredEvent(this, application));
            }
        } catch (Exception ex) {
            System.err.println("Failed to execute fallback logic: " + ex.getMessage());
        }
    }

    public AiMatchResult scoreAndGenerate(ChatClient chatClient, String jobDescription, String cvMarkdown) {
        var converter = new BeanOutputConverter<>(AiMatchResult.class);

        String prompt = String.format(
                "Based on the following Job Description and CV, give a match score (0-100) " +
                        "and write a customized Cover Letter targeting the specific job.\n\n" +
                        "CRITICAL INSTRUCTIONS FOR JSON OUTPUT:\n" +
                        "1. You MUST return ONLY a valid JSON object. Do not wrap it in markdown blocks like ```json.\n"
                        +
                        "2. For the coverLetterMarkdown field, you MUST escape all newlines as \\n. DO NOT use actual raw newlines inside the JSON string!\n\n"
                        +
                        "Job Description:\n%s\n\nCV Data:\n%s\n\n%s",
                jobDescription, cvMarkdown, converter.getFormat());

        try {
            ChatResponse response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .chatResponse();

            String rawContent = response.getResult().getOutput().getText();

            // Use BeanOutputConverter for robust parsing; strip markdown fences first as a
            // safety net
            if (rawContent.contains("```json")) {
                rawContent = rawContent.substring(rawContent.indexOf("```json") + 7);
                rawContent = rawContent.contains("```") ? rawContent.substring(0, rawContent.lastIndexOf("```"))
                        : rawContent;
            } else if (rawContent.contains("```")) {
                rawContent = rawContent.substring(rawContent.indexOf("```") + 3);
                rawContent = rawContent.contains("```") ? rawContent.substring(0, rawContent.lastIndexOf("```"))
                        : rawContent;
            }

            com.fasterxml.jackson.databind.ObjectMapper mapper = com.fasterxml.jackson.databind.json.JsonMapper
                    .builder()
                    .enable(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
                    .build();

            return mapper.readValue(rawContent.trim(), AiMatchResult.class);
        } catch (Exception e) {
            System.err.println(
                    "Primary LLM Service unavailable. Reason: " + e.getMessage());
            throw new RuntimeException(
                    "Primary LLM Service unavailable. Please ensure Ollama is running and accessible.", e);
        }
    }
}
