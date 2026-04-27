package com.redhat.cloud.notifications.health;

import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.events.ValkeyService;
import io.smallrye.mutiny.TimeoutException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@Liveness
@ApplicationScoped
public class ValkeyHealthCheck implements HealthCheck {

    @Inject
    ValkeyService valkeyService;

    @Inject
    EngineConfig config;

    @Override
    public HealthCheckResponse call() {
        String hostName = "Valkey in-memory DB";
        HealthCheckResponseBuilder builder = HealthCheckResponse.named("Valkey connection health check");

        try {
            String healthCheckResp = valkeyService.runHealthCheck();
            builder.up().withData(hostName, healthCheckResp);
        } catch (TimeoutException te) {
            builder.down().withData(hostName, "Unable to execute Valkey health check due to 10 second timeout");
        } catch (Exception e) {
            builder.down().withData(hostName, String.format("Unable to execute Valkey health check: %s", e));
        }

        return builder.build();
    }


}
