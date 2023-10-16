package com.redhat.cloud.notifications.recipients.config;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.time.Duration;

@ApplicationScoped
public class RecipientsResolverConfig {

    @ConfigProperty(name = "notifications.connector.fetch.users.rbac.enabled", defaultValue = "false")
    boolean fetchUsersWithRBAC;

    @ConfigProperty(name = "notifications.connector.fetch.users.mbop.enabled", defaultValue = "false")
    boolean fetchUsersWithMbop;

    @ConfigProperty(name = "recipients-provider.rbac.elements-per-page", defaultValue = "1000")
    int rbacMaxResultsPerPage;

    @ConfigProperty(name = "recipients-provider.it.max-results-per-page", defaultValue = "1000")
    int itMaxResultsPerPage;

    @ConfigProperty(name = "recipients-provider.mbop.max-results-per-page", defaultValue = "1000")
    int MBOPMaxResultsPerPage;

    @ConfigProperty(name = "rbac.retry.max-attempts", defaultValue = "3")
    int rbacMaxRetryAttempts;

    @ConfigProperty(name = "it.retry.max-attempts", defaultValue = "3")
    int itMaxRetryAttempts;

    @ConfigProperty(name = "mbop.retry.max-attempts", defaultValue = "3")
    int MBOPMaxRetryAttempts;

    @ConfigProperty(name = "it.retry.back-off.initial-value", defaultValue = "0.1S")
    Duration itInitialBackOff;

    @ConfigProperty(name = "it.retry.back-off.max-value", defaultValue = "1S")
    Duration itMaxBackOff;

    @ConfigProperty(name = "rbac.retry.back-off.initial-value", defaultValue = "0.1S")
    Duration rbacInitialBackOff;

    @ConfigProperty(name = "rbac.retry.back-off.max-value", defaultValue = "1S")
    Duration rbacMaxBackOff;

    @ConfigProperty(name = "mbop.retry.back-off.initial-value", defaultValue = "0.1S")
    Duration MBOPInitialBackOff;

    @ConfigProperty(name = "mbop.retry.back-off.max-value", defaultValue = "1S")
    Duration MBOPMaxBackOff;

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
        Log.infof("The max result per page for IT Service is %s", itMaxResultsPerPage);
        Log.infof("The max result per page for Mbop is %s", MBOPMaxResultsPerPage);
        Log.infof("The max result per page for Rbac is %s", rbacMaxResultsPerPage);
        Log.infof("The max retry attempts for IT Service is %s", itMaxRetryAttempts);
        Log.infof("The max retry attempts for Mbop is %s", MBOPMaxRetryAttempts);
        Log.infof("The max retry attempts for Rbac is %s", rbacMaxRetryAttempts);
        Log.infof("The retry back-off initial value for IT Service is %s", itInitialBackOff);
        Log.infof("The retry back-off initial value for Mbop is %s", MBOPInitialBackOff);
        Log.infof("The retry back-off initial value for Rbac is %s", rbacInitialBackOff);
        Log.infof("The max retry back-off value for IT Service is %s", itMaxBackOff);
        Log.infof("The max retry back-off value for MBOP is %s", MBOPMaxBackOff);
        Log.infof("The max retry back-off value for rbac is %s", rbacMaxBackOff);
        Log.infof("The MBOP env is %s", mbopEnv);
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

    public Integer getRbacMaxResultsPerPage() {
        return rbacMaxResultsPerPage;
    }

    public int getItMaxResultsPerPage() {
        return itMaxResultsPerPage;
    }

    public int getMBOPMaxResultsPerPage() {
        return MBOPMaxResultsPerPage;
    }

    public int getRbacMaxRetryAttempts() {
        return rbacMaxRetryAttempts;
    }

    public int getItMaxRetryAttempts() {
        return itMaxRetryAttempts;
    }

    public int getMBOPMaxRetryAttempts() {
        return MBOPMaxRetryAttempts;
    }

    public Duration getItInitialBackOff() {
        return itInitialBackOff;
    }

    public Duration getItMaxBackOff() {
        return itMaxBackOff;
    }

    public Duration getRbacInitialBackOff() {
        return rbacInitialBackOff;
    }

    public Duration getRbacMaxBackOff() {
        return rbacMaxBackOff;
    }

    public Duration getMBOPInitialBackOff() {
        return MBOPInitialBackOff;
    }

    public Duration getMBOPMaxBackOff() {
        return MBOPMaxBackOff;
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
}
