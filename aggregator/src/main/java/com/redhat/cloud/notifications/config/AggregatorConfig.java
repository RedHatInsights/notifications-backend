package com.redhat.cloud.notifications.config;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Startup;

import java.util.Map;
import java.util.TreeMap;

@ApplicationScoped
public class AggregatorConfig {

    /*
     * Unleash configuration
     */

    private static String toggleName(String feature) {
        return String.format("notifications-aggregator.%s.enabled", feature);
    }

    void logConfigAtStartup(@Observes Startup event) {
        Map<String, Object> config = new TreeMap<>();
        Log.info("=== Startup configuration ===");
        config.forEach((key, value) -> {
            Log.infof("%s=%s", key, value);
        });
    }
}
