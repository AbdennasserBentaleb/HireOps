package com.hireops.batch;

import com.hireops.model.JobPosting;
import com.hireops.model.UserProfile;
import com.hireops.repository.JobPostingRepository;
import com.hireops.repository.UserProfileRepository;
import com.hireops.service.BundesagenturApiClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

@Configuration
public class JobIngestionConfig {

    @Bean
    public ItemReader<JsonNode> jobPostingItemReader(BundesagenturApiClient apiClient,
            UserProfileRepository userProfileRepository) {
        // Use the user's configured search keyword; fall back to "Java Developer"
        List<UserProfile> profiles = userProfileRepository.findAll();
        String keyword = profiles.isEmpty() || profiles.get(0).getSearchKeyword() == null
                ? "Java Developer"
                : profiles.get(0).getSearchKeyword();
        return new JobPostingItemReader(apiClient, keyword);
    }

    @Bean
    public ItemProcessor<JsonNode, JobPosting> jobPostingItemProcessor(BundesagenturApiClient apiClient) {
        return new JobPostingItemProcessor(apiClient);
    }

    @Bean
    public ItemWriter<JobPosting> jobPostingItemWriter(JobPostingRepository repository,
            ApplicationEventPublisher eventPublisher) {
        return new JobPostingItemWriter(repository, eventPublisher);
    }

    @Bean
    public Step ingestionStep(JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            ItemReader<JsonNode> reader,
            ItemProcessor<JsonNode, JobPosting> processor,
            ItemWriter<JobPosting> writer) {
        return new StepBuilder("ingestionStep", jobRepository)
                .<JsonNode, JobPosting>chunk(10, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Bean
    public Job jobIngestionJob(JobRepository jobRepository, Step ingestionStep) {
        return new JobBuilder("jobIngestionJob", jobRepository)
                .start(ingestionStep)
                .build();
    }

    /**
     * Replaces the default JobLauncherApplicationRunner to always re-run ingestion
     * on startup using a unique timestamp parameter. Spring Batch tracks job
     * executions by parameters — a new timestamp means a new job instance, so
     * the "already COMPLETED" guard is bypassed without breaking auditability.
     */
    @Bean
    public ApplicationRunner jobIngestionRunner(JobLauncher jobLauncher, Job jobIngestionJob) {
        return args -> {
            JobParameters params = new JobParametersBuilder()
                    .addLong("run.id", System.currentTimeMillis())
                    .toJobParameters();
            jobLauncher.run(jobIngestionJob, params);
        };
    }
}
