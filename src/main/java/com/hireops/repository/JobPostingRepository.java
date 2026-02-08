package com.hireops.repository;

import com.hireops.model.JobPosting;
import com.hireops.model.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobPostingRepository extends JpaRepository<JobPosting, UUID> {
    Optional<JobPosting> findByReferenceId(String referenceId);
    List<JobPosting> findByStatus(JobStatus status);
}
