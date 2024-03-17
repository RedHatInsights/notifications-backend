package com.redhat.cloud.notifications.recipients.config;

import com.redhat.cloud.notifications.unleash.ToggleRegistry;
import io.getunleash.Unleash;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Startup;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;

@ApplicationScoped
public class RecipientsResolverConfig {

    /*
     * Env vars configuration
     */
    private static final String MAX_RESULTS_PER_PAGE = "notifications.recipients-resolver.max-results-per-page";
    private static final String MBOP_ENV = "mbop.env";
    private static final String RETRY_INITIAL_BACKOFF = "notifications.recipients-resolver.retry.initial-backoff";
    private static final String RETRY_MAX_ATTEMPTS = "notifications.recipients-resolver.retry.max-attempts";
    private static final String RETRY_MAX_BACKOFF = "notifications.recipients-resolver.retry.max-backoff";
    private static final String WARN_IF_DURATION_EXCEEDS = "notifications.recipients-resolver.warn-if-request-duration-exceeds";
    private static final String UNLEASH = "notifications.unleash.enabled";

    /*
     * Unleash configuration
     */
    private String fetchUsersWithMbopToggle;
    private String fetchUsersWithRbacToggle;

    private static String toggleName(String feature) {
        return String.format("notifications-recipients-resolver.%s.enabled", feature);
    }

    @ConfigProperty(name = UNLEASH, defaultValue = "false")
    @Deprecated(forRemoval = true, since = "To be removed when we're done migrating to Unleash in all environments")
    boolean unleashEnabled;

    @ConfigProperty(name = "notifications.recipients-resolver.fetch.users.rbac.enabled", defaultValue = "false")
    @Deprecated(forRemoval = true, since = "To be removed when we're done migrating to Unleash in all environments")
    boolean fetchUsersWithRbacEnabled;

    @ConfigProperty(name = "notifications.recipients-resolver.fetch.users.mbop.enabled", defaultValue = "false")
    @Deprecated(forRemoval = true, since = "To be removed when we're done migrating to Unleash in all environments")
    boolean fetchUsersWithMbopEnabled;

    @ConfigProperty(name = MAX_RESULTS_PER_PAGE, defaultValue = "1000")
    int maxResultsPerPage;

    @ConfigProperty(name = RETRY_INITIAL_BACKOFF, defaultValue = "0.1S")
    Duration initialRetryBackoff;

    @ConfigProperty(name = RETRY_MAX_ATTEMPTS, defaultValue = "3")
    int maxRetryAttempts;

    @ConfigProperty(name = RETRY_MAX_BACKOFF, defaultValue = "1S")
    Duration maxRetryBackoff;

    @ConfigProperty(name = WARN_IF_DURATION_EXCEEDS, defaultValue = "30S")
    Duration logTooLongRequestLimit;

    @ConfigProperty(name = "mbop.apitoken", defaultValue = "na")
    String mbopApiToken;

    @ConfigProperty(name = "mbop.client_id", defaultValue = "na")
    String mbopClientId;

    @ConfigProperty(name = MBOP_ENV, defaultValue = "na")
    String mbopEnv;

    @Inject
    ToggleRegistry toggleRegistry;

    @Inject
    Unleash unleash;

    @PostConstruct
    void postConstruct() {
        fetchUsersWithMbopToggle = toggleRegistry.register("fetch-users-with-mbop", true);
        fetchUsersWithRbacToggle = toggleRegistry.register("fetch-users-with-rbac", true);
    }

    void logConfigAtStartup(@Observes Startup event) {

        Map<String, Object> config = new TreeMap<>();
        config.put(fetchUsersWithMbopToggle, isFetchUsersWithMbopEnabled());
        config.put(fetchUsersWithRbacToggle, isFetchUsersWithRbacEnabled());
        config.put(MAX_RESULTS_PER_PAGE, getMaxResultsPerPage());
        config.put(MBOP_ENV, getMbopEnv());
        config.put(RETRY_INITIAL_BACKOFF, getInitialRetryBackoff());
        config.put(RETRY_MAX_ATTEMPTS, getMaxRetryAttempts());
        config.put(RETRY_MAX_BACKOFF, getMaxRetryBackoff());
        config.put(WARN_IF_DURATION_EXCEEDS, getLogTooLongRequestLimit());
        config.put(UNLEASH, unleashEnabled);

        Log.info("=== Startup configuration ===");
        config.forEach((key, value) -> {
            Log.infof("%s=%s", key, value);
        });
    }

    public boolean isFetchUsersWithMbopEnabled() {
        if (unleashEnabled) {
            return unleash.isEnabled(fetchUsersWithMbopToggle, false);
        } else {
            return fetchUsersWithMbopEnabled;
        }
    }

    public boolean isFetchUsersWithRbacEnabled() {
        if (unleashEnabled) {
            return unleash.isEnabled(fetchUsersWithRbacToggle, false);
        } else {
            return fetchUsersWithRbacEnabled;
        }
    }

    public int getMaxResultsPerPage() {
        return maxResultsPerPage;
    }

    public int getMaxRetryAttempts() {
        return maxRetryAttempts;
    }

    public Duration getInitialRetryBackoff() {
        return initialRetryBackoff;
    }

    public Duration getMaxRetryBackoff() {
        return maxRetryBackoff;
    }

    public String getMbopApiToken() {
        return mbopApiToken;
    }

    public String getMbopClientId() {
        return mbopClientId;
    }

    public String getMbopEnv() {
        return mbopEnv;
    }

    public Duration getLogTooLongRequestLimit() {
        return logTooLongRequestLimit;
    }
}
