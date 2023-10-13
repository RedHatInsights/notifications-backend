package com.redhat.cloud.notifications.recipientresolver.config;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class RecipientResolverConfig {
    private static final String FETCH_USERS_RBAC_ENABLED = "notifications.connector.fetch.users.rbac.enabled";

    private static final String FETCH_USERS_MBOP_ENABLED = "notifications.connector.fetch.users.mbop.enabled";

    @ConfigProperty(name = FETCH_USERS_RBAC_ENABLED, defaultValue = "false")
    boolean fetchUsersWithRBAC;

    @ConfigProperty(name = FETCH_USERS_MBOP_ENABLED, defaultValue = "false")
    boolean fetchUsersWithMbop;

    void logFeaturesStatusAtStartup(@Observes StartupEvent event) {
        Log.infof("=== %s startup status ===", RecipientResolverConfig.class.getSimpleName());
        Log.infof("The fetching users with Rbac is %s", fetchUsersWithRBAC ? "enabled" : "disabled");
        Log.infof("The fetching users with Mbop is %s", fetchUsersWithMbop ? "enabled" : "disabled");
        Log.infof("The fetching users with IT Service is %s", !(fetchUsersWithRBAC || fetchUsersWithMbop) ? "enabled" : "disabled");
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
}
