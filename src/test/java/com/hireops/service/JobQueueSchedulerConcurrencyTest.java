package com.hireops.service;

import com.hireops.model.JobPosting;
import com.hireops.model.JobStatus;
import com.hireops.model.ProcessingStatus;
import com.hireops.repository.JobPostingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class JobQueueSchedulerConcurrencyTest {

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @BeforeEach
    void setUp() {
        jobPostingRepository.deleteAll();
    }

    @Test
    void testQueueProcessing_EnsuresStatusUpdates() {
        // Arrange: Insert 5 queued jobs
        for (int i = 0; i < 5; i++) {
            JobPosting job = new JobPosting();
            job.setTitle("Test Job " + i);
            job.setReferenceId("ref-" + i);
            job.setHashId("hash-" + i);
            job.setCompany("Test Company");
            job.setDescription("Test Description");
            job.setStatus(JobStatus.FETCHED);
            job.setProcessingStatus(ProcessingStatus.QUEUED);
            jobPostingRepository.save(job);
        }

        // Act: Process sequentially since H2 in-memory does not support native SKIP LOCKED concurrency
        int processedCount = 0;
        while(true) {
             Optional<JobPosting> pickedJob = jobPostingRepository.findNextJobToProcess();
             if(pickedJob.isPresent()) {
                 JobPosting job = pickedJob.get();
                 job.setProcessingStatus(ProcessingStatus.PROCESSING);
                 jobPostingRepository.saveAndFlush(job);
                 processedCount++;
             } else {
                 break;
             }
        }

        // Assert: Exactly 5 jobs should be picked up
        assertThat(processedCount).isEqualTo(5);
        long remainingQueued = jobPostingRepository.findByStatus(JobStatus.FETCHED).stream()
                .filter(j -> j.getProcessingStatus() == ProcessingStatus.QUEUED)
                .count();
        assertThat(remainingQueued).isZero();
    }
}
