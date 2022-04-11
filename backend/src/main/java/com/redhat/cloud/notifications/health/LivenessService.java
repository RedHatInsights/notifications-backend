package com.redhat.cloud.notifications.health;

import com.redhat.cloud.notifications.StuffHolder;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Liveness;

import javax.enterprise.context.ApplicationScoped;

@Liveness
@ApplicationScoped
public class LivenessService implements HealthCheck {

    @Override
    public HealthCheckResponse call() {
        boolean adminDown = StuffHolder.getInstance().isAdminDown();

        HealthCheckResponseBuilder response = HealthCheckResponse.named("Notifications liveness check");
        if (adminDown) {
            return response.down().withData("status", "admin-down").build();
        } else {
            return response.up().build();
        }
    }
}
