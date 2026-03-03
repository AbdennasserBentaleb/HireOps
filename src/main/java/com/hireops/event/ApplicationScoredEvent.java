package com.hireops.event;

import com.hireops.model.Application;
import org.springframework.context.ApplicationEvent;

public class ApplicationScoredEvent extends ApplicationEvent {
    private final Application application;

    public ApplicationScoredEvent(Object source, Application application) {
        super(source);
        this.application = application;
    }

    public Application getApplication() {
        return application;
    }
}
