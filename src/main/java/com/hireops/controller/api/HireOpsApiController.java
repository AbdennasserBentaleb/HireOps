package com.hireops.controller.api;

import com.hireops.model.JobPosting;
import com.hireops.model.JobStatus;
import com.hireops.model.ProcessingStatus;
import com.hireops.model.UserProfile;
import com.hireops.model.ResumePersona;
import com.hireops.repository.JobPostingRepository;
import com.hireops.repository.UserProfileRepository;
import com.hireops.service.BundesagenturApiClient;
import com.hireops.dto.JobSearchResponse;
import com.hireops.repository.ApplicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "${CORS_ALLOWED_ORIGINS:http://localhost:3000}") // Enable configurable CORS for frontend
public class HireOpsApiController {

    private static final Logger logger = LoggerFactory.getLogger(HireOpsApiController.class);

    private final JobPostingRepository jobPostingRepository;
    private final UserProfileRepository userProfileRepository;
    private final BundesagenturApiClient bundesagenturApiClient;
    private final com.hireops.repository.ResumePersonaRepository resumePersonaRepository;
    private final com.hireops.service.OllamaMatchmakerService ollamaMatchmakerService;
    private final ApplicationRepository applicationRepository;

    public HireOpsApiController(JobPostingRepository jobPostingRepository,
            UserProfileRepository userProfileRepository,
            BundesagenturApiClient bundesagenturApiClient,
            com.hireops.repository.ResumePersonaRepository resumePersonaRepository,
            com.hireops.service.OllamaMatchmakerService ollamaMatchmakerService,
            ApplicationRepository applicationRepository) {
        this.jobPostingRepository = jobPostingRepository;
        this.userProfileRepository = userProfileRepository;
        this.bundesagenturApiClient = bundesagenturApiClient;
        this.resumePersonaRepository = resumePersonaRepository;
        this.ollamaMatchmakerService = ollamaMatchmakerService;
        this.applicationRepository = applicationRepository;
    }

    @GetMapping("/jobs/kanban")
    public ResponseEntity<List<Map<String, Object>>> getKanbanJobs() {
        List<JobPosting> allJobs = jobPostingRepository.findAll();

        Map<JobStatus, List<JobPosting>> jobsByStatus = allJobs.stream()
                .collect(Collectors.groupingBy(JobPosting::getStatus));

        List<Map<String, Object>> columns = new ArrayList<>();
        columns.add(createColumn("discovery", "New Matches", jobsByStatus.getOrDefault(JobStatus.FETCHED, List.of())));
        columns.add(createColumn("scored", "Scored", jobsByStatus.getOrDefault(JobStatus.SCORED, List.of())));
        columns.add(createColumn("applied", "Applied", jobsByStatus.getOrDefault(JobStatus.APPROVED, List.of())));
        columns.add(
                createColumn("interviewing", "Interviewing", jobsByStatus.getOrDefault(JobStatus.APPLIED, List.of())));

        return ResponseEntity.ok(columns);
    }

    private Map<String, Object> createColumn(String id, String title, List<JobPosting> jobs) {
        Map<String, Object> col = new HashMap<>();
        col.put("id", id);
        col.put("title", title);

        List<Map<String, Object>> mappedJobs = jobs.stream().map(job -> {
            Map<String, Object> j = new HashMap<>();
            j.put("id", job.getId().toString());
            j.put("company", job.getCompany());
            j.put("title", job.getTitle());
            j.put("score", job.getMatchScore() != null ? job.getMatchScore() : 0);
            j.put("location", "Location Unspecified"); // We can extract this properly later if needed
            j.put("matchReason", job.getDescription().length() > 100 ? job.getDescription().substring(0, 100) + "..."
                    : job.getDescription());
            return j;
        }).collect(Collectors.toList());

        col.put("jobs", mappedJobs);
        return col;
    }

    @GetMapping("/settings")
    public ResponseEntity<UserProfile> getSettings() {
        List<UserProfile> profiles = userProfileRepository.findAll();
        if (profiles.isEmpty()) {
            UserProfile defaultProfile = new UserProfile();
            defaultProfile.setSearchKeyword("Java");
            defaultProfile.setMinMatchScore(75);
            defaultProfile.setMaxJobAgeDays(30);
            return ResponseEntity.ok(userProfileRepository.save(defaultProfile));
        }
        return ResponseEntity.ok(profiles.get(0));
    }

    @PostMapping("/settings")
    public ResponseEntity<UserProfile> updateSettings(@RequestBody Map<String, Object> payload) {
        UserProfile profile = getSettings().getBody();
        if (profile == null)
            profile = new UserProfile();

        if (payload.containsKey("searchKeyword"))
            profile.setSearchKeyword((String) payload.get("searchKeyword"));
        if (payload.containsKey("minMatchScore"))
            profile.setMinMatchScore(Integer.parseInt(payload.get("minMatchScore").toString()));
        if (payload.containsKey("maxJobAgeDays"))
            profile.setMaxJobAgeDays(Integer.parseInt(payload.get("maxJobAgeDays").toString()));
        if (payload.containsKey("systemPrompt"))
            profile.setSystemPrompt((String) payload.get("systemPrompt"));
        if (payload.containsKey("aiModel"))
            profile.setAiModel((String) payload.get("aiModel"));

        return ResponseEntity.ok(userProfileRepository.save(profile));
    }

    /**
     * POST /api/v1/jobs/cancel-queue
     * Resets all QUEUED and PROCESSING jobs back to IDLE so the scheduler
     * stops picking them up. Safe to call while scoring is in progress.
     */
    @PostMapping("/jobs/cancel-queue")
    public ResponseEntity<Map<String, Object>> cancelQueue() {
        List<JobPosting> activeJobs = jobPostingRepository.findAll().stream()
                .filter(j -> j.getProcessingStatus() == ProcessingStatus.QUEUED
                          || j.getProcessingStatus() == ProcessingStatus.PROCESSING)
                .collect(Collectors.toList());
        activeJobs.forEach(j -> {
            j.setProcessingStatus(ProcessingStatus.IDLE);
            jobPostingRepository.save(j);
        });
        logger.info("cancel-queue: reset {} jobs to IDLE", activeJobs.size());
        return ResponseEntity.ok(Map.of("cancelled", activeJobs.size(),
                "message", "Queue drained — " + activeJobs.size() + " job(s) cancelled."));
    }

    /**
     * POST /api/v1/jobs/clear
     * Drains the queue first, then wipes all jobs and applications.
     */
    @PostMapping("/jobs/clear")
    public ResponseEntity<Map<String, String>> clearJobs() {
        // 1. Stop the scheduler from picking up any more queued jobs
        jobPostingRepository.findAll().stream()
                .filter(j -> j.getProcessingStatus() == ProcessingStatus.QUEUED
                          || j.getProcessingStatus() == ProcessingStatus.PROCESSING)
                .forEach(j -> {
                    j.setProcessingStatus(ProcessingStatus.IDLE);
                    jobPostingRepository.save(j);
                });
        // 2. Remove all data
        applicationRepository.deleteAll();
        jobPostingRepository.deleteAll();
        return ResponseEntity.ok(Map.of("message", "All jobs cleared"));
    }

    @PostMapping("/discovery/scan")
    public ResponseEntity<Map<String, Object>> triggerScan(@RequestParam(defaultValue = "10") int limit) {
        UserProfile profile = getSettings().getBody();
        String keyword = profile != null && profile.getSearchKeyword() != null ? profile.getSearchKeyword() : "Java";
        Integer maxAge = profile != null && profile.getMaxJobAgeDays() != null ? profile.getMaxJobAgeDays() : 30;

        JobSearchResponse response = bundesagenturApiClient.searchJobs(keyword, 1, limit);
        int added = 0;

        if (response != null && response.stellenangebote() != null) {
            LocalDate cutoffDate = maxAge > 0 ? LocalDate.now().minusDays(maxAge) : LocalDate.of(1970, 1, 1);

            for (com.fasterxml.jackson.databind.JsonNode summary : response.stellenangebote()) {
                if (added >= limit) {
                    break;
                }
                String hashId = summary.path("hashId").asText(UUID.randomUUID().toString());
                if (summary.hasNonNull("refnr")) {
                    hashId = summary.path("refnr").asText();
                }

                // Skip duplicates
                if (jobPostingRepository.findByReferenceId(hashId).isPresent()) {
                    continue;
                }

                JobPosting job = new JobPosting();
                job.setReferenceId(hashId);

                String titel = summary.path("titel").asText("Unknown Title");
                String arbeitgeber = summary.path("arbeitgeber").asText("Unknown Employer");
                String ort = summary.path("arbeitsort").path("ort").asText("");

                job.setTitle(titel);
                job.setCompany(arbeitgeber);

                // ── Fetch full job details for real description ──────────────────────────
                String fullDescription = titel + (ort.isEmpty() ? "" : " in " + ort);
                String portalUrl = null;
                String employerEmail = null;

                try {
                    var detail = bundesagenturApiClient.getJobDetails(hashId);
                    if (detail.isPresent()) {
                        com.fasterxml.jackson.databind.JsonNode d = detail.get();

                        // Prefer structured description fields
                        String stellenbeschreibung = d.path("stellenbeschreibung").asText("");
                        String taetigkeit = d.path("taetigkeit").asText("");
                        String anforderungen = d.path("anforderungen").asText("");

                        StringBuilder desc = new StringBuilder();
                        desc.append("Position: ").append(titel).append("\n");
                        if (!ort.isEmpty()) desc.append("Location: ").append(ort).append("\n");
                        desc.append("Employer: ").append(arbeitgeber).append("\n\n");

                        if (!stellenbeschreibung.isBlank()) {
                            desc.append("Job Description:\n").append(stellenbeschreibung).append("\n\n");
                        }
                        if (!taetigkeit.isBlank()) {
                            desc.append("Responsibilities:\n").append(taetigkeit).append("\n\n");
                        }
                        if (!anforderungen.isBlank()) {
                            desc.append("Requirements:\n").append(anforderungen).append("\n");
                        }

                        fullDescription = desc.toString().trim();
                        if (fullDescription.isEmpty()) {
                            fullDescription = titel + (ort.isEmpty() ? "" : " in " + ort);
                        }

                        // Extract contact info
                        com.fasterxml.jackson.databind.JsonNode kontakt = d.path("kontakt");
                        if (!kontakt.isMissingNode()) {
                            employerEmail = kontakt.path("email").asText(null);
                            if (employerEmail != null && employerEmail.isBlank()) employerEmail = null;
                        }

                        // Portal URL from the job detail
                        String refNr = d.path("refnr").asText("");
                        if (!refNr.isBlank()) {
                            portalUrl = "https://www.arbeitsagentur.de/jobsuche/jobdetail/" + refNr;
                        }

                        // Also check externeUrl
                        String externeUrl = d.path("externeUrl").asText("");
                        if (!externeUrl.isBlank()) portalUrl = externeUrl;
                    }
                } catch (Exception detailEx) {
                    logger.warn("Could not fetch details for job {}: {}", hashId, detailEx.getMessage());
                }
                // ────────────────────────────────────────────────────────────────────────

                job.setDescription(fullDescription);
                if (employerEmail != null) job.setEmployerEmail(employerEmail);
                if (portalUrl != null) job.setPortalUrl(portalUrl);
                job.setStatus(JobStatus.FETCHED);
                jobPostingRepository.save(job);
                added++;
            }
        }

        return ResponseEntity.ok(Map.of("message", "Scraped " + added + " new jobs", "added", added));
    }

    @PostMapping("/jobs/import")
    public ResponseEntity<Map<String, Object>> importJob(@RequestBody Map<String, String> payload) {
        String title = payload.getOrDefault("title", "Unknown Title");
        String company = payload.getOrDefault("company", "Unknown Employer");
        String location = payload.getOrDefault("location", "Location Unspecified");
        String description = payload.getOrDefault("description", "");

        // Simple deduplication based on title+company logic
        String refId = "EXT-" + UUID.randomUUID().toString().substring(0, 8);
        Optional<JobPosting> existing = jobPostingRepository.findAll().stream()
                .filter(j -> j.getTitle().equals(title) && j.getCompany().equals(company))
                .findFirst();

        if (existing.isPresent()) {
            return ResponseEntity.ok(Map.of("message", "Job already exists", "jobId", existing.get().getId()));
        }

        JobPosting job = new JobPosting();
        job.setReferenceId(refId);
        job.setTitle(title);
        job.setCompany(company);
        job.setDescription(description);
        job.setStatus(JobStatus.FETCHED);

        JobPosting saved = jobPostingRepository.save(job);
        return ResponseEntity.ok(Map.of("message", "Job imported successfully", "jobId", saved.getId()));
    }

    @PostMapping("/jobs/{id}/analyze")
    public ResponseEntity<Map<String, String>> analyzeJob(@PathVariable UUID id) {
        try {
            JobPosting job = jobPostingRepository.findById(id).orElseThrow(() -> new RuntimeException("Job not found"));
            job.setProcessingStatus(ProcessingStatus.QUEUED);
            jobPostingRepository.save(job);
            return ResponseEntity.ok(Map.of("message", "Job queued for AI scoring successfully"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
    }

    @PostMapping("/jobs/{id}/ignore")
    public ResponseEntity<Map<String, String>> ignoreJob(@PathVariable UUID id) {
        return jobPostingRepository.findById(id).map(job -> {
            job.setStatus(JobStatus.REJECTED);
            jobPostingRepository.save(job);
            return ResponseEntity.ok(Map.of("message", "Job ignored"));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/personas")
    public ResponseEntity<List<ResumePersona>> getPersonas() {
        return ResponseEntity.ok(resumePersonaRepository.findAll());
    }

    @PostMapping("/personas")
    public ResponseEntity<ResumePersona> createPersona(@RequestBody Map<String, Object> payload) {
        UserProfile profile = getSettings().getBody();
        if (profile == null) {
            profile = new UserProfile();
            profile = userProfileRepository.save(profile);
        }

        ResumePersona persona = new ResumePersona();
        persona.setPersonaName((String) payload.getOrDefault("name", "New Persona"));
        persona.setRole((String) payload.getOrDefault("role", ""));
        persona.setUserProfile(profile);

        return ResponseEntity.ok(resumePersonaRepository.save(persona));
    }

    @PutMapping("/personas/{id}/prompt")
    public ResponseEntity<ResumePersona> updatePersonaPrompt(@PathVariable UUID id,
            @RequestBody Map<String, String> payload) {
        return resumePersonaRepository.findById(id).map(persona -> {
            persona.setSystemPromptOverride(payload.get("prompt"));
            return ResponseEntity.ok(resumePersonaRepository.save(persona));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Queues all FETCHED jobs for async AI scoring.
     * Returns immediately with the count of jobs queued.
     */
    @PostMapping("/score-all")
    public ResponseEntity<Map<String, Object>> scoreAllJobs() {
        List<JobPosting> fetchedJobs = jobPostingRepository.findByStatus(JobStatus.FETCHED);
        int count = 0;
        for (JobPosting job : fetchedJobs) {
            if (job.getProcessingStatus() == ProcessingStatus.IDLE) {
                job.setProcessingStatus(ProcessingStatus.QUEUED);
                jobPostingRepository.save(job);
                count++;
            }
        }
        logger.info("Score-All: queued {} jobs for AI scoring", count);
        Map<String, Object> result = new HashMap<>();
        result.put("queued", count);
        result.put("message", count > 0
                ? "Queued " + count + " jobs for AI scoring. Check the Activity Log for progress."
                : "No unscored jobs found in your inbox.");
        return ResponseEntity.ok(result);
    }
}
