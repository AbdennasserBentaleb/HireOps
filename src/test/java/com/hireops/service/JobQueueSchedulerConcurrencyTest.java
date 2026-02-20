package com.hireops.service;

import com.hireops.model.JobPosting;
import com.hireops.model.JobStatus;
import com.hireops.model.ProcessingStatus;
import com.hireops.repository.JobPostingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doNothing;

@SpringBootTest
@Testcontainers
public class JobQueueSchedulerConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
            .withDatabaseName("test_db")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private JobPostingRepository jobPostingRepository;

    private OllamaMatchmakerService mockMatchmakerService;
    private JobQueueScheduler scheduler;

    @BeforeEach
    void setUp() {
        jobPostingRepository.deleteAll();
        mockMatchmakerService = mock(OllamaMatchmakerService.class);
        doNothing().when(mockMatchmakerService).scoreJob(any());
        scheduler = new JobQueueScheduler(jobPostingRepository, mockMatchmakerService);
    }

    @Test
    void testConcurrentQueueProcessing_EnsuresNoRaceConditions() throws InterruptedException {
        // Arrange: Insert 5 queued jobs
        for (int i = 0; i < 5; i++) {
            JobPosting job = new JobPosting();
            job.setTitle("Test Job " + i);
            job.setStatus(JobStatus.FETCHED);
            job.setProcessingStatus(ProcessingStatus.QUEUED);
            jobPostingRepository.save(job);
        }

        // Simulate 10 concurrent scheduler tick threads
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCounter = new AtomicInteger();

        // Act
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    // Each thread runs the scheduler tick
                    Optional<JobPosting> pickedJob = jobPostingRepository.findNextJobToProcess();
                    if(pickedJob.isPresent()) {
                    	JobPosting job = pickedJob.get();
                    	job.setProcessingStatus(ProcessingStatus.PROCESSING);
                    	jobPostingRepository.saveAndFlush(job);
                    	successCounter.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Ignore transient JDBC locks in high contention testing
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Unleash all threads simultaneously
        doneLatch.await(10, TimeUnit.SECONDS);

        // Assert: Exactly 5 jobs should be picked up despite 10 concurrent competing threads
        assertThat(successCounter.get()).isEqualTo(5);
        long remainingQueued = jobPostingRepository.findByStatus(JobStatus.FETCHED).stream()
                .filter(j -> j.getProcessingStatus() == ProcessingStatus.QUEUED)
                .count();
        assertThat(remainingQueued).isZero();
    }
}
