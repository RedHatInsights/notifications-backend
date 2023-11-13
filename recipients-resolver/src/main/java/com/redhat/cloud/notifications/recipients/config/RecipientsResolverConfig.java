package com.redhat.cloud.notifications.recipients.config;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.time.Duration;

@ApplicationScoped
public class RecipientsResolverConfig {

    @ConfigProperty(name = "notifications.recipients-resolver.fetch.users.rbac.enabled", defaultValue = "false")
    boolean fetchUsersWithRBAC;

    @ConfigProperty(name = "notifications.recipients-resolver.fetch.users.mbop.enabled", defaultValue = "false")
    boolean fetchUsersWithMbop;

    @ConfigProperty(name = "notifications.recipients-resolver.max-results-per-page", defaultValue = "1000")
    int maxResultsPerPage;

    @ConfigProperty(name = "notifications.recipients-resolver.retry.max-attempts", defaultValue = "3")
    int maxRetryAttempts;

    @ConfigProperty(name = "notifications.recipients-resolver.retry.initial-backoff", defaultValue = "0.1S")
    Duration initialRetryBackoff;

    @ConfigProperty(name = "notifications.recipients-resolver.retry.max-backoff", defaultValue = "1S")
    Duration maxRetryBackoff;

    @ConfigProperty(name = "notifications.log.too.long.request.limit", defaultValue = "30S")
    Duration logTooLongRequestLimit;

    @ConfigProperty(name = "mbop.apitoken", defaultValue = "na")
    String mbopApiToken;

    @ConfigProperty(name = "mbop.client_id", defaultValue = "na")
    String mbopClientId;

    @ConfigProperty(name = "mbop.env", defaultValue = "na")
    String mbopEnv;

    void logFeaturesStatusAtStartup(@Observes StartupEvent event) {
        Log.infof("=== %s startup status ===", RecipientsResolverConfig.class.getSimpleName());
        Log.infof("The fetching users with Rbac is %s", fetchUsersWithRBAC ? "enabled" : "disabled");
        Log.infof("The fetching users with Mbop is %s", fetchUsersWithMbop ? "enabled" : "disabled");
        Log.infof("The fetching users with IT Service is %s", !(fetchUsersWithRBAC || fetchUsersWithMbop) ? "enabled" : "disabled");
        Log.infof("The max result per page is %s", maxResultsPerPage);
        Log.infof("The max retry attempts is %s", maxRetryAttempts);
        Log.infof("The retry back-off initial value is %s", initialRetryBackoff);
        Log.infof("The max retry back-off value is %s", maxRetryBackoff);
        Log.infof("The MBOP env is %s", mbopEnv);
        Log.infof("The requests to external services will be logged it they exceed %s", logTooLongRequestLimit);
    }

    public boolean isFetchUsersWithMbop() {
        return fetchUsersWithMbop;
    }

    public boolean isFetchUsersWithRBAC() {
        return this.fetchUsersWithRBAC;
    }

    public void setFetchUsersWithRBAC(boolean fetchUsersWithRBAC) {
        this.fetchUsersWithRBAC = fetchUsersWithRBAC;
    }

    public void setFetchUsersWithMbop(boolean fetchUsersWithMbop) {
        this.fetchUsersWithMbop = fetchUsersWithMbop;
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
