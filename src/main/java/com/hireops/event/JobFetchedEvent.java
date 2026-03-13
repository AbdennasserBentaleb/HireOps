package com.hireops.event;

import com.hireops.model.JobPosting;
import org.springframework.context.ApplicationEvent;

public class JobFetchedEvent extends ApplicationEvent {
    private final JobPosting jobPosting;

    public JobFetchedEvent(Object source, JobPosting jobPosting) {
        super(source);
        this.jobPosting = jobPosting;
    }

    public JobPosting getJobPosting() {
        return jobPosting;
    }
}
