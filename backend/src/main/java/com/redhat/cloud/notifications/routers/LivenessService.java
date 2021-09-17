package com.redhat.cloud.notifications.routers;

import com.redhat.cloud.notifications.StuffHolder;
import io.smallrye.health.api.AsyncHealthCheck;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Liveness;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
@Liveness
public class LivenessService implements AsyncHealthCheck {

    @Inject
    Mutiny.StatelessSession statelessSession;

    Uni<Boolean> postgresConnectionHealth() {
        return statelessSession.createNativeQuery("SELECT 1")
                .getSingleResult()
                .replaceWith(Boolean.TRUE)
                .onFailure().recoverWithItem(Boolean.FALSE);
    }

    @Override
    public Uni<HealthCheckResponse> call() {
        boolean adminDown = StuffHolder.getInstance().isAdminDown();

        HealthCheckResponseBuilder response = HealthCheckResponse.named("Notifications readiness check");
        if (adminDown) {
            return Uni.createFrom().item(() ->
                response.down().withData("status", "admin-down").build()
            );
        }
        return postgresConnectionHealth().onItem().transform(dbState ->
                response.state(dbState).withData("reactive-db-check", dbState).build()
        );
    }
}
