package com.hireops.repository;

import com.hireops.model.JobPosting;
import com.hireops.model.JobStatus;
import com.hireops.model.ProcessingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobPostingRepository extends JpaRepository<JobPosting, UUID> {
    Optional<JobPosting> findByReferenceId(String referenceId);
    List<JobPosting> findByStatus(JobStatus status);
    
    @org.springframework.data.jpa.repository.Query(value = "SELECT * FROM job_posting j WHERE j.status = 'FETCHED' AND j.processing_status = 'QUEUED' ORDER BY j.created_at ASC LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<JobPosting> findNextJobToProcess();

    Optional<JobPosting> findFirstByStatusAndProcessingStatusOrderByCreatedAtAsc(JobStatus status, ProcessingStatus processingStatus);
}
