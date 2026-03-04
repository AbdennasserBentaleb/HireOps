package com.hireops.repository;

import com.hireops.model.Application;
import com.hireops.model.JobPosting;
import com.hireops.model.JobStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = { "spring.sql.init.mode=never" })
class JobPostingRepositoryTest {

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Test
    void testSaveAndFindJobPosting() {
        JobPosting job = new JobPosting();
        job.setReferenceId("REF-12345");
        job.setTitle("Senior Java Developer");
        job.setCompany("Tech Corp");
        job.setDescription("Looking for a Java 25 architect");
        job.setStatus(JobStatus.FETCHED);

        JobPosting savedJob = jobPostingRepository.save(job);
        assertThat(savedJob.getId()).isNotNull();
        assertThat(savedJob.getCreatedAt()).isNotNull();

        Optional<JobPosting> foundJob = jobPostingRepository.findByReferenceId("REF-12345");
        assertThat(foundJob).isPresent();
        assertThat(foundJob.get().getTitle()).isEqualTo("Senior Java Developer");
    }

    @Test
    void testSaveAndFindApplication() {
        JobPosting job = new JobPosting();
        job.setReferenceId("REF-67890");
        job.setTitle("Cloud Architect");
        job.setCompany("Cloud Inc");
        job.setDescription("k3s expert");
        job.setStatus(JobStatus.SCORED);
        jobPostingRepository.save(job);

        Application app = new Application();
        app.setJobPosting(job);
        app.setCvPdfPath("/app/pdfs/cv.pdf");
        app.setCoverLetterPdfPath("/app/pdfs/letter.pdf");

        Application savedApp = applicationRepository.save(app);
        assertThat(savedApp.getId()).isNotNull();
        assertThat(savedApp.getCreatedAt()).isNotNull();
        assertThat(savedApp.getJobPosting().getId()).isEqualTo(job.getId());
    }
}
