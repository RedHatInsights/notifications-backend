package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import java.util.List;

@Dependent
public class AsyncAggregation implements Runnable {

    @Inject
    EmailAggregationProcessor emailAggregationProcessor;

    private Event event;

    private List<Endpoint> endpoints;

    @PostConstruct
    void postConstruct() {
        // Temporary log entry.
        Log.infof("Instance created: %d", hashCode());
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    public void setEndpoints(List<Endpoint> endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    @ActivateRequestContext
    public void run() {
        emailAggregationProcessor.processAggregationAsync(event, endpoints);
    }

    @PreDestroy
    void preDestroy() {
        // Temporary log entry.
        Log.infof("Instance destroyed: %d", hashCode());
    }
}
