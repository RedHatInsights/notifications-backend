package com.redhat.cloud.notifications.recipients.resolver.valkey;

import com.redhat.cloud.notifications.recipients.config.RecipientsResolverConfig;
import io.smallrye.mutiny.TimeoutException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class ValkeyHealthCheck implements HealthCheck {

    @Inject
    ValkeyUserCacheService valkeyUserCacheService;

    @Inject
    RecipientsResolverConfig config;

    @Override
    public HealthCheckResponse call() {
        String hostName = "Valkey user cache";
        HealthCheckResponseBuilder builder = HealthCheckResponse.named("Valkey connection health check");

        if (!config.isInMemoryDbEnabled()) {
            builder.up().withData(hostName, "In-memory DB is disabled");
            return builder.build();
        }

        try {
            String healthCheckResp = valkeyUserCacheService.runHealthCheck();
            builder.up().withData(hostName, healthCheckResp);
        } catch (TimeoutException te) {
            builder.down().withData(hostName, "Unable to execute Valkey health check due to 10 second timeout");
        } catch (Exception e) {
            builder.down().withData(hostName, String.format("Unable to execute Valkey health check: %s", e.getMessage()));
        }

        return builder.build();
    }
}
