package com.redhat.cloud.notifications.health;

import com.redhat.cloud.notifications.StuffHolder;
import io.smallrye.health.api.AsyncHealthCheck;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Liveness;

import javax.enterprise.context.ApplicationScoped;

@Liveness
@ApplicationScoped
public class LivenessService implements AsyncHealthCheck {

    @Override
    public Uni<HealthCheckResponse> call() {
        boolean adminDown = StuffHolder.getInstance().isAdminDown();

        HealthCheckResponseBuilder response = HealthCheckResponse.named("Notifications readiness check");
        if (adminDown) {
            return Uni.createFrom().item(() ->
                    response.down().withData("status", "admin-down").build()
            );
        } else {
            return Uni.createFrom().item(() -> response.up().build());
        }
    }
}
