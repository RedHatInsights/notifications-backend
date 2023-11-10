package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.models.Event;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

@Dependent
public class AsyncAggregation implements Runnable {

    @Inject
    EmailSubscriptionTypeProcessor emailSubscriptionTypeProcessor;

    private Event event;

    @PostConstruct
    void postConstruct() {
        // Temporary log entry.
        Log.infof("Instance created: %d", hashCode());
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    @Override
    @ActivateRequestContext
    public void run() {
        emailSubscriptionTypeProcessor.processAggregationSync(event, true);
    }

    @PreDestroy
    void preDestroy() {
        // Temporary log entry.
        Log.infof("Instance destroyed: %d", hashCode());
    }
}
