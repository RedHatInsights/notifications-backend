package com.redhat.cloud.notifications.config;

import com.redhat.cloud.notifications.unleash.ToggleRegistry;
import io.getunleash.Unleash;
import io.getunleash.variant.Payload;
import io.getunleash.variant.Variant;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Startup;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

@ApplicationScoped
public class AggregatorConfig {

    /*
     * Env vars configuration
     */
    private static final String CLUSTER_ID = "notifications.aggregator.cluster-id";

    /*
     * Unleash configuration
     */
    private String activeClusterToggle;

    @ConfigProperty(name = CLUSTER_ID)
    Optional<String> clusterId;

    @Inject
    Unleash unleash;

    @Inject
    ToggleRegistry toggleRegistry;

    @PostConstruct
    void registerToggles() {
        activeClusterToggle = toggleRegistry.register("notifications-aggregator-active-cluster", true);
        Log.infof("Registered Unleash toggle: %s", activeClusterToggle);
    }

    public Optional<String> getClusterId() {
        return clusterId.map(String::trim).filter(value -> !value.isEmpty());
    }

    /**
     * Gets the active cluster ID from Unleash variant payload.
     * Returns Optional.empty() if:
     * - Unleash is disabled (variant will be disabled)
     * - Unleash is unreachable
     * - Variant is not enabled
     * - Payload is missing or invalid
     */
    public Optional<String> getActiveCluster() {
        try {
            Variant variant = unleash.getVariant(activeClusterToggle);
            if (!variant.isEnabled()) {
                Log.info("Unleash variant for active-cluster is not enabled");
                return Optional.empty();
            }

            Optional<Payload> payload = variant.getPayload();
            if (payload.isEmpty()) {
                Log.info("Unleash variant payload is empty");
                return Optional.empty();
            }

            String value = payload.get().getValue();
            if (value == null || value.isBlank()) {
                Log.info("Unleash variant payload value is null or blank");
                return Optional.empty();
            }

            return Optional.of(value.trim());
        } catch (Exception e) {
            Log.warn("Failed to retrieve active cluster from Unleash", e);
            return Optional.empty();
        }
    }

    void logConfigAtStartup(@Observes Startup event) {
        Map<String, Object> config = new TreeMap<>();
        config.put(CLUSTER_ID, getClusterId().orElse("not-configured"));
        if (activeClusterToggle != null) {
            config.put(activeClusterToggle, getActiveCluster().orElse("unable-to-determine"));
        }
        Log.info("=== Startup configuration ===");
        config.forEach((key, value) -> Log.infof("%s=%s", key, value));
    }
}
