package com.hireops.event;

import org.springframework.context.ApplicationEvent;
import java.util.UUID;

public class ApplicationApprovedEvent extends ApplicationEvent {
    private final UUID jobId;

    public ApplicationApprovedEvent(Object source, UUID jobId) {
        super(source);
        this.jobId = jobId;
    }

    public UUID getJobId() {
        return jobId;
    }
}
