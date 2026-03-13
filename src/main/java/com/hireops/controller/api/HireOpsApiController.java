package com.hireops.controller.api;

import com.hireops.model.JobPosting;
import com.hireops.model.JobStatus;
import com.hireops.model.UserProfile;
import com.hireops.model.ResumePersona;
import com.hireops.repository.JobPostingRepository;
import com.hireops.repository.UserProfileRepository;
import com.hireops.service.BundesagenturApiClient;
import com.hireops.dto.JobSearchResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "http://localhost:3000") // Enable CORS for Next.js frontend
public class HireOpsApiController {

    private final JobPostingRepository jobPostingRepository;
    private final UserProfileRepository userProfileRepository;
    private final BundesagenturApiClient bundesagenturApiClient;
    private final com.hireops.repository.ResumePersonaRepository resumePersonaRepository;
    private final com.hireops.service.OllamaMatchmakerService ollamaMatchmakerService;

    public HireOpsApiController(JobPostingRepository jobPostingRepository,
            UserProfileRepository userProfileRepository,
            BundesagenturApiClient bundesagenturApiClient,
            com.hireops.repository.ResumePersonaRepository resumePersonaRepository,
            com.hireops.service.OllamaMatchmakerService ollamaMatchmakerService) {
        this.jobPostingRepository = jobPostingRepository;
        this.userProfileRepository = userProfileRepository;
        this.bundesagenturApiClient = bundesagenturApiClient;
        this.resumePersonaRepository = resumePersonaRepository;
        this.ollamaMatchmakerService = ollamaMatchmakerService;
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

        return ResponseEntity.ok(userProfileRepository.save(profile));
    }

    @PostMapping("/discovery/scan")
    public ResponseEntity<Map<String, Object>> triggerScan() {
        UserProfile profile = getSettings().getBody();
        String keyword = profile != null && profile.getSearchKeyword() != null ? profile.getSearchKeyword() : "Java";
        Integer maxAge = profile != null && profile.getMaxJobAgeDays() != null ? profile.getMaxJobAgeDays() : 30;

        JobSearchResponse response = bundesagenturApiClient.searchJobs(keyword, 1, 10);
        int added = 0;

        if (response != null && response.stellenangebote() != null) {
            LocalDate cutoffDate;
            if (maxAge > 0) {
                cutoffDate = LocalDate.now().minusDays(maxAge);
            } else {
                cutoffDate = LocalDate.of(1970, 1, 1);
            }

            for (com.fasterxml.jackson.databind.JsonNode detail : response.stellenangebote()) {
                String hashId = detail.path("hashId").asText(UUID.randomUUID().toString());
                if (detail.hasNonNull("refnr")) {
                    hashId = detail.path("refnr").asText();
                }

                if (jobPostingRepository.findByReferenceId(hashId).isEmpty()) {
                    JobPosting job = new JobPosting();
                    job.setReferenceId(hashId);

                    String titel = detail.path("titel").asText("Unknown Title");
                    String arbeitgeber = detail.path("arbeitgeber").asText("Unknown Employer");
                    String ort = detail.path("arbeitsort").path("ort").asText("Location Unspecified");

                    job.setTitle(titel);
                    job.setCompany(arbeitgeber);
                    job.setDescription(titel + " in " + ort);
                    job.setStatus(JobStatus.FETCHED);
                    jobPostingRepository.save(job);
                    added++;
                }
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
            ollamaMatchmakerService.scoreJob(id);
            return ResponseEntity.ok(Map.of("message", "Job analyzed and scored successfully"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown error"));
        }
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
}
