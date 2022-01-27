package com.redhat.cloud.notifications.health;

import io.smallrye.health.api.AsyncHealthCheck;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Liveness;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@Liveness
@ApplicationScoped
public class LivenessService implements AsyncHealthCheck {

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @Inject
    KafkaConsumedTotalChecker kafkaConsumedTotalChecker;

    @Override
    public Uni<HealthCheckResponse> call() {
        HealthCheckResponseBuilder response = HealthCheckResponse.named("Notifications readiness check");
        if (kafkaConsumedTotalChecker.isDown()) {
            return Uni.createFrom().item(() ->
                    response.down().withData("kafka-consumed-total", "DOWN").build()
            );
        }
        return postgresConnectionHealth().onItem().transform(dbState ->
                    response.status(dbState).withData("reactive-db-check", dbState).build()
        );
    }

    private Uni<Boolean> postgresConnectionHealth() {
        return sessionFactory.withStatelessSession(statelessSession -> {
            return statelessSession.createNativeQuery("SELECT 1")
                    .getSingleResult()
                    .replaceWith(Boolean.TRUE)
                    .onFailure().recoverWithItem(Boolean.FALSE);
        });
    }
}
