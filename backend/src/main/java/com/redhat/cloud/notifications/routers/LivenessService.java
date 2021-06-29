package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.StuffHolder;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Liveness;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
@Liveness
public class LivenessService implements HealthCheck {

//    @Inject
//    HealthCenter messagingHealth;

    @Inject
    Mutiny.Session session;

//    public Uni<Boolean> getLiveness() {
//        Uni<Boolean> postgresHealth = postgresConnectionHealth();
//        Uni<Boolean> kafkaHealth = Uni.createFrom().item(messagingHealth.getReadiness())
//                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
//                .map(hr -> {
//                    for (HealthReport.ChannelInfo channel : hr.getChannels()) {
//                        if (!channel.isOk()) {
//                            return false;
//                        }
//                    }
//                    return true;
//                });
//
//        return Uni.combine().all().unis(postgresHealth, kafkaHealth).combinedWith((p, k) -> p && k);
//    }

    Uni<Boolean> postgresConnectionHealth() {
        return session.createNativeQuery("SELECT 1")
                .getSingleResult()
                .replaceWith(Boolean.TRUE)
                .onFailure().recoverWithItem(Boolean.FALSE);
    }

    @Override
    public HealthCheckResponse call() {

        boolean adminDown = StuffHolder.getInstance().isAdminDown();

        HealthCheckResponseBuilder response = HealthCheckResponse.named("Notifications readiness check");
        if (adminDown) {
            response.down().withData("status", "admin-down");
        } else {
            boolean dbState = postgresConnectionHealth().await().indefinitely();
            response.state(dbState).withData("reactive-db-check", dbState);
        }

        return response.build();
    }
}
