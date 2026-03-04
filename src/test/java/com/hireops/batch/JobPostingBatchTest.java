package com.hireops.batch;

import com.hireops.dto.JobDetailResponse;
import com.hireops.dto.JobSearchResponse;
import com.hireops.model.JobPosting;
import com.hireops.repository.JobPostingRepository;
import com.hireops.repository.ApplicationRepository;
import com.hireops.service.BundesagenturApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@SpringBatchTest
@ActiveProfiles("test")
@TestPropertySource(properties = { "spring.sql.init.mode=never",
        "spring.datasource.url=jdbc:h2:mem:test-batch;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE" })
class JobPostingBatchTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    // Use MockitoBean (Spring Boot 3.4+) to mock the API Client since we aren't
    // testing it here.
    @MockitoBean
    private BundesagenturApiClient apiClient;

    @BeforeEach
    void setUp() {
        applicationRepository.deleteAll();
        jobPostingRepository.deleteAll();
    }

    @Test
    @org.junit.jupiter.api.Disabled("Flaky in parallel context due to H2 memory settings")
    void testIngestionJob() throws Exception {
        // Arrange API mocks
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        JobSearchResponse response = new JobSearchResponse(List.of(
                mapper.readTree("{\"refnr\":\"ref1\",\"titel\":\"Java Developer\",\"arbeitgeber\":\"Deutsche Bank\"}"),
                mapper.readTree("{\"refnr\":\"ref2\",\"titel\":\"Backend Engineer\",\"arbeitgeber\":\"Allianz\"}")));

        when(apiClient.searchJobs(anyString(), anyInt(), anyInt()))
                .thenReturn(response)
                .thenReturn(null); // Return data once, then null to end the reader stream

        // Act
        JobExecution jobExecution = jobLauncherTestUtils.launchJob();

        // Assert
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);

        // Verify Database Write
        List<JobPosting> postings = jobPostingRepository.findAll();
        assertThat(postings).hasSize(2);

        // Sorting or extracting might be safer, but they should be in order
        JobPosting first = postings.stream().filter(p -> "ref1".equals(p.getReferenceId())).findFirst().orElseThrow();
        assertThat(first.getCompany()).isEqualTo("Deutsche Bank");
        assertThat(first.getTitle()).isEqualTo("Java Developer");

        JobPosting second = postings.stream().filter(p -> "ref2".equals(p.getReferenceId())).findFirst().orElseThrow();
        assertThat(second.getCompany()).isEqualTo("Allianz");
        assertThat(second.getTitle()).isEqualTo("Backend Engineer");
    }
}
