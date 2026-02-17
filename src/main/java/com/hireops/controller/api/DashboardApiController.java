package com.hireops.controller.api;

import com.hireops.model.JobPosting;
import com.hireops.model.JobStatus;
import com.hireops.repository.ApplicationRepository;
import com.hireops.repository.JobPostingRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lightweight JSON API for live dashboard polling.
 * The frontend polls these endpoints every 5 seconds to update stats and job cards
 * without requiring a full page reload.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardApiController {

    private final JobPostingRepository jobPostingRepository;
    private final ApplicationRepository applicationRepository;

    public DashboardApiController(JobPostingRepository jobPostingRepository,
                                  ApplicationRepository applicationRepository) {
        this.jobPostingRepository = jobPostingRepository;
        this.applicationRepository = applicationRepository;
    }

    /**
     * Returns aggregate stats: job counts per column and average match score.
     */
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        List<JobPosting> all = jobPostingRepository.findAll();

        long inbox = all.stream().filter(j -> j.getStatus() == JobStatus.FETCHED || j.getStatus() == null).count();
        long aiReview = all.stream().filter(j -> j.getStatus() == JobStatus.SCORED).count();
        long readyToDispatch = all.stream().filter(j -> j.getStatus() == JobStatus.APPROVED).count();
        long dispatched = all.stream().filter(j -> j.getStatus() == JobStatus.APPLIED).count();

        double avgScore = all.stream()
                .filter(j -> j.getMatchScore() != null && j.getMatchScore() > 0)
                .mapToInt(JobPosting::getMatchScore)
                .average()
                .orElse(0.0);

        long totalFetched = all.stream()
                .filter(j -> j.getStatus() == JobStatus.FETCHED)
                .count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("inbox", inbox);
        stats.put("aiReview", aiReview);
        stats.put("readyToDispatch", readyToDispatch);
        stats.put("dispatched", dispatched);
        stats.put("avgScore", Math.round(avgScore));
        stats.put("totalInDb", all.size());
        stats.put("pendingScoring", totalFetched);
        return stats;
    }

    /**
     * Returns all scored jobs (SCORED, APPROVED, APPLIED) as lightweight JSON
     * for the pipeline columns (AI Review, Ready to Dispatch, Dispatched).
     */
    @GetMapping("/jobs/pipeline")
    public List<Map<String, Object>> getPipelineJobs() {
        return jobPostingRepository.findAll().stream()
                .filter(j -> j.getStatus() != null && j.getStatus() != JobStatus.FETCHED)
                .map(this::toCard)
                .collect(Collectors.toList());
    }

    /**
     * Returns all inbox (FETCHED) jobs.
     */
    @GetMapping("/jobs/inbox")
    public List<Map<String, Object>> getInboxJobs() {
        return jobPostingRepository.findAll().stream()
                .filter(j -> j.getStatus() == null || j.getStatus() == JobStatus.FETCHED)
                .map(this::toCard)
                .collect(Collectors.toList());
    }

    private Map<String, Object> toCard(JobPosting j) {
        Map<String, Object> card = new HashMap<>();
        card.put("id", j.getId().toString());
        card.put("title", j.getTitle());
        card.put("employer", j.getCompany());
        card.put("status", j.getStatus() != null ? j.getStatus().name() : "FETCHED");
        card.put("score", j.getMatchScore());
        card.put("analysis", j.getAiAnalysis());
        card.put("portalUrl", j.getPortalUrl());
        card.put("notes", j.getNotes());
        card.put("scoredAt", j.getScoredAt() != null ? j.getScoredAt().toString() : null);

        // Whether this job has an application/cover letter
        boolean hasApplication = applicationRepository.findByJobPostingId(j.getId()).stream().findFirst().isPresent();
        card.put("hasApplication", hasApplication);
        return card;
    }
}
