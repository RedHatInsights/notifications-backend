package com.redhat.cloud.notifications.health;

import io.smallrye.health.api.AsyncHealthCheck;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Liveness;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@Liveness
@ApplicationScoped
public class LivenessService implements AsyncHealthCheck {

    @Inject
    KafkaConsumedTotalChecker kafkaConsumedTotalChecker;

    @Override
    public Uni<HealthCheckResponse> call() {
        HealthCheckResponseBuilder response = HealthCheckResponse.named("Notifications readiness check");
        if (kafkaConsumedTotalChecker.isDown()) {
            return Uni.createFrom().item(() ->
                    response.down().withData("kafka-consumed-total", "DOWN").build()
            );
        } else {
            return Uni.createFrom().item(() -> response.up().build());
        }
    }
}
