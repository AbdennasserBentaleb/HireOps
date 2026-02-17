package com.hireops.controller;

import com.hireops.model.JobPosting;
import com.hireops.model.JobStatus;
import com.hireops.repository.JobPostingRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/dashboard")
public class DashboardController {

    private final JobPostingRepository jobPostingRepository;

    public DashboardController(JobPostingRepository jobPostingRepository) {
        this.jobPostingRepository = jobPostingRepository;
    }

    @GetMapping
    public String getDashboard(Model model) {
        List<JobPosting> allJobs = jobPostingRepository.findAll();

        Map<JobStatus, List<JobPosting>> jobsByStatus = allJobs.stream()
                .collect(Collectors.groupingBy(JobPosting::getStatus));

        // Limit Pipeline Inbox to 50 to prevent page overload; show the total count too
        List<JobPosting> allFetched = jobsByStatus.getOrDefault(JobStatus.FETCHED, List.of());
        List<JobPosting> displayedFetched = allFetched.size() > 50
                ? allFetched.subList(0, 50)
                : allFetched;

        model.addAttribute("fetchedJobs", displayedFetched);
        model.addAttribute("totalFetchedCount", allFetched.size());
        model.addAttribute("scoredJobs", jobsByStatus.getOrDefault(JobStatus.SCORED, List.of()));
        model.addAttribute("approvedJobs", jobsByStatus.getOrDefault(JobStatus.APPROVED, List.of()));
        model.addAttribute("appliedJobs", jobsByStatus.getOrDefault(JobStatus.APPLIED, List.of()));

        // --- Weekly stats for the stats header bar ---
        LocalDateTime oneWeekAgo = LocalDateTime.now().minusWeeks(1);

        long scoredThisWeek = allJobs.stream()
                .filter(j -> j.getScoredAt() != null && j.getScoredAt().isAfter(oneWeekAgo))
                .count();

        long dispatchedThisWeek = jobsByStatus.getOrDefault(JobStatus.APPLIED, List.of()).stream()
                .filter(j -> j.getScoredAt() != null && j.getScoredAt().isAfter(oneWeekAgo))
                .count();

        OptionalDouble avgScore = allJobs.stream()
                .filter(j -> j.getMatchScore() != null)
                .mapToInt(JobPosting::getMatchScore)
                .average();

        model.addAttribute("statsScoredThisWeek", scoredThisWeek);
        model.addAttribute("statsDispatchedThisWeek", dispatchedThisWeek);
        model.addAttribute("statsAvgScore", avgScore.isPresent() ? (int) avgScore.getAsDouble() : 0);
        model.addAttribute("statsTotalInPipeline",
                allJobs.stream().filter(j -> j.getStatus() != JobStatus.FETCHED).count());

        return "dashboard";
    }
}
