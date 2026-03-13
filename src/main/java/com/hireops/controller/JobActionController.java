package com.hireops.controller;

import com.hireops.model.JobPosting;
import com.hireops.model.JobStatus;
import com.hireops.repository.JobPostingRepository;
import com.hireops.service.DispatchService;
import com.hireops.service.OllamaMatchmakerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Controller
@RequestMapping("/api/jobs")
public class JobActionController {

    private static final Logger log = LoggerFactory.getLogger(JobActionController.class);

    private final JobPostingRepository jobPostingRepository;
    private final OllamaMatchmakerService ollamaMatchmakerService;
    private final DispatchService dispatchService;

    public JobActionController(JobPostingRepository jobPostingRepository,
            OllamaMatchmakerService ollamaMatchmakerService,
            DispatchService dispatchService) {
        this.jobPostingRepository = jobPostingRepository;
        this.ollamaMatchmakerService = ollamaMatchmakerService;
        this.dispatchService = dispatchService;
    }

    @PostMapping("/{id}/score")
    public RedirectView scoreJob(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            ollamaMatchmakerService.scoreJob(id);
            redirectAttributes.addFlashAttribute("success",
                    "AI Matchmaking started in the background. Job will move to 'AI Review' once completed.");
        } catch (Exception e) {
            log.error("Failed to start AI Matchmaking for job {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("error",
                    "Failed to start matchmaking: " + getRootCause(e).getMessage());
        }
        return new RedirectView("/dashboard");
    }

    @PostMapping("/{id}/approve")
    public RedirectView approveJob(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        Optional<JobPosting> jobOpt = jobPostingRepository.findById(id);
        if (jobOpt.isPresent()) {
            JobPosting job = jobOpt.get();
            job.setStatus(JobStatus.APPROVED);
            jobPostingRepository.save(job);
            redirectAttributes.addFlashAttribute("success", "Job approved and ready to dispatch.");
        } else {
            redirectAttributes.addFlashAttribute("error", "Job not found.");
        }
        return new RedirectView("/dashboard");
    }

    @PostMapping("/{id}/dispatch")
    public RedirectView dispatchJob(@PathVariable UUID id,
            @RequestParam(name = "recipientEmail", required = false) String recipientEmail,
            RedirectAttributes redirectAttributes) {
        try {
            dispatchService.dispatchApplication(id, recipientEmail);
            redirectAttributes.addFlashAttribute("success", "Application dispatched successfully.");
        } catch (Exception e) {
            log.error("Dispatch failed for job {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("error",
                    "Dispatch failed: " + getRootCause(e).getMessage() +
                            ". Please check your SMTP settings.");
        }
        return new RedirectView("/dashboard");
    }

    /**
     * PATCH /api/jobs/{id}/notes — saves a user's CRM note for a job.
     * Called via fetch() from the dashboard JS; returns 200 OK on success.
     */
    @PatchMapping("/{id}/notes")
    @ResponseBody
    public ResponseEntity<Map<String, String>> saveNotes(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        return jobPostingRepository.findById(id).map(job -> {
            job.setNotes(body.getOrDefault("notes", ""));
            jobPostingRepository.save(job);
            return ResponseEntity.ok(Map.of("status", "saved"));
        }).orElse(ResponseEntity.notFound().<Map<String, String>>build());
    }

    /**
     * POST /api/jobs/{id}/reject — removes a job from the pipeline.
     */
    @PostMapping("/{id}/reject")
    public RedirectView rejectJob(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        jobPostingRepository.findById(id).ifPresent(jobPostingRepository::delete);
        redirectAttributes.addFlashAttribute("success", "Job removed from pipeline.");
        return new RedirectView("/dashboard");
    }

    private Throwable getRootCause(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
