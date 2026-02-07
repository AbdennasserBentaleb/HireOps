package com.hireops.service;

import com.hireops.model.JobPosting;
import com.hireops.model.JobStatus;
import com.hireops.model.ProcessingStatus;
import com.hireops.repository.JobPostingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * DB-Backed Queue Scheduler.
 * Guarantees zero-loss processing across container restarts by using PostgreSQL as the state truth.
 * Polling for jobs where processingStatus = QUEUED.
 */
@Service
public class JobQueueScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobQueueScheduler.class);

    private final JobPostingRepository jobPostingRepository;
    private final OllamaMatchmakerService matchmakerService;

    public JobQueueScheduler(JobPostingRepository jobPostingRepository, OllamaMatchmakerService matchmakerService) {
        this.jobPostingRepository = jobPostingRepository;
        this.matchmakerService = matchmakerService;
    }

    // Runs dynamically every 3 seconds. Pulls precisely 1 job at a time off the queue.
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 3000)
    @org.springframework.transaction.annotation.Transactional
    public void processNextJobInQueue() {
        Optional<JobPosting> nextJob = jobPostingRepository.findNextJobToProcess();

        if (nextJob.isPresent()) {
            JobPosting job = nextJob.get();
            log.info("Queue Worker: Picked up Job ID: {} titled: {}", job.getId(), job.getTitle());
            
            // Lock the job to prevent rapid concurrent pickup
            job.setProcessingStatus(ProcessingStatus.PROCESSING);
            jobPostingRepository.saveAndFlush(job);

            try {
                // Execute blocking synchronous LLM generation on worker thread
                matchmakerService.scoreJob(job.getId());
                log.info("Queue Worker: Successfully evaluated Job ID: {}", job.getId());
            } catch (Exception e) {
                log.error("Queue Worker: Failed evaluating Job ID: {}. Reverting to IDLE.", job.getId(), e);
            } finally {
                // If it failed spectacularly outside matchmaker's transaction, gracefully unlock.
                // In normal flow, scoreJob sets status to SCORED, rendering it invisible to this poller.
                JobPosting refreshed = jobPostingRepository.findById(job.getId()).orElse(null);
                if (refreshed != null && refreshed.getProcessingStatus() == ProcessingStatus.PROCESSING) {
                    refreshed.setProcessingStatus(ProcessingStatus.IDLE);
                    jobPostingRepository.save(refreshed);
                }
            }
        }
    }
}
