package com.redhat.cloud.notifications.config;

import com.redhat.cloud.notifications.unleash.ToggleRegistry;
import io.getunleash.Unleash;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Startup;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;
import java.util.TreeMap;

@ApplicationScoped
public class AggregatorConfig {

    /*
     * Env vars configuration
     */
    private static final String UNLEASH = "notifications.unleash.enabled";

    /*
     * Unleash configuration
     */

    private static String toggleName(String feature) {
        return String.format("notifications-aggregator.%s.enabled", feature);
    }

    @ConfigProperty(name = UNLEASH, defaultValue = "false")
    @Deprecated(forRemoval = true, since = "To be removed when we're done migrating to Unleash in all environments")
    boolean unleashEnabled;

    @Inject
    ToggleRegistry toggleRegistry;

    @Inject
    Unleash unleash;

    private static final String FETCH_AGGREGATIONS_WITH_AT_LEAST_ONE_SUBSCRIBER = "fetch-aggregations-with-at-least-one-subscriber";

    private String fetchAggregationsWithAtLeastOneSubscriberToggle;

    @PostConstruct
    void postConstruct() {
        fetchAggregationsWithAtLeastOneSubscriberToggle = toggleRegistry.register(FETCH_AGGREGATIONS_WITH_AT_LEAST_ONE_SUBSCRIBER, true);
    }

    void logConfigAtStartup(@Observes Startup event) {
        Map<String, Object> config = new TreeMap<>();
        config.put(UNLEASH, unleashEnabled);
        config.put(fetchAggregationsWithAtLeastOneSubscriberToggle, isFetchAggregationsWithAtLeastOneSubscriber());
        Log.info("=== Startup configuration ===");
        config.forEach((key, value) -> {
            Log.infof("%s=%s", key, value);
        });
    }

    public boolean isFetchAggregationsWithAtLeastOneSubscriber() {
        if (unleashEnabled) {
            return unleash.isEnabled(fetchAggregationsWithAtLeastOneSubscriberToggle, false);
        } else {
            return false;
        }
    }
}
