package com.redhat.cloud.notifications.health;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Liveness;

@Liveness
@ApplicationScoped
public class LivenessService implements HealthCheck {

    @Inject
    KafkaConsumedTotalChecker kafkaConsumedTotalChecker;

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder response = HealthCheckResponse.named("Notifications liveness check");
        if (kafkaConsumedTotalChecker.isDown()) {
            return response.down().withData("kafka-consumed-total", "DOWN").build();
        } else {
            return response.up().build();
        }
    }
}
