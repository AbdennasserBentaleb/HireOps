package com.hireops.batch;

import com.hireops.event.JobFetchedEvent;
import com.hireops.model.JobPosting;
import com.hireops.repository.JobPostingRepository;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

public class JobPostingItemWriter implements ItemWriter<JobPosting> {

    private final JobPostingRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public JobPostingItemWriter(JobPostingRepository repository, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void write(Chunk<? extends JobPosting> chunk) {
        for (JobPosting posting : chunk) {
            // Check existence based on reference ID to avoid overwriting or duplicates
            if (repository.findByReferenceId(posting.getReferenceId()).isEmpty()) {
                try {
                    JobPosting saved = repository.save(posting);
                    eventPublisher.publishEvent(new JobFetchedEvent(this, saved));
                } catch (DataIntegrityViolationException e) {
                    // Ignore concurrent duplicates
                }
            }
        }
    }
}
