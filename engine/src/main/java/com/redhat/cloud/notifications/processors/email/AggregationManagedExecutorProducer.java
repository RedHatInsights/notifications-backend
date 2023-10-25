package com.redhat.cloud.notifications.processors.email;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.context.ManagedExecutor;

@ApplicationScoped
public class AggregationManagedExecutorProducer {

    @ConfigProperty(name = "notifications.aggregation.managed-executor.max-queued", defaultValue = "100")
    int maxQueued;

    @ConfigProperty(name = "notifications.aggregation.managed-executor.max-async", defaultValue = "10")
    int maxAsync;

    @Produces
    @ApplicationScoped
    @AggregationManagedExecutor
    ManagedExecutor produce() {
        return ManagedExecutor.builder()
                .maxQueued(maxQueued)
                .maxAsync(maxAsync)
                .build();
    }
}
