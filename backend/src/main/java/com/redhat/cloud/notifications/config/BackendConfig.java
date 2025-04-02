package com.redhat.cloud.notifications.config;

import com.redhat.cloud.notifications.unleash.ToggleRegistry;
import io.getunleash.Unleash;
import io.getunleash.UnleashContext;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Startup;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;
import java.util.TreeMap;

@ApplicationScoped
public class BackendConfig {

    /*
     * Env vars configuration
     */
    private static final String DEFAULT_TEMPLATE = "notifications.use-default-template";
    private static final String EMAILS_ONLY_MODE = "notifications.emails-only-mode.enabled";
    private static final String ERRATA_MIGRATION_BATCH_SIZE = "notifications.errata.migration.batch.size";
    private static final String INSTANT_EMAILS = "notifications.instant-emails.enabled";
    private static final String KESSEL_INVENTORY_CLIENT_ID = "inventory-api.authn.client.id";
    private static final String KESSEL_INVENTORY_URL = "inventory-api.target-url";
    private static final String KESSEL_INVENTORY_ENABLED = "notifications.kessel-inventory.enabled";
    private static final String KESSEL_MIGRATION_BATCH_SIZE = "notifications.kessel.migration.batch.size";
    private static final String KESSEL_RELATIONS_ENABLED = "notifications.kessel-relations.enabled";
    private static final String KESSEL_INVENTORY_FOR_PERMISSIONS_CHECKS_ENABLED = "notifications.kessel-inventory.permissions-checks.enabled";
    private static final String KESSEL_RELATIONS_LOOKUP_RESOURCES_LIMIT = "notifications.kessel-relations.lookup-resources.limit";
    private static final String KESSEL_RELATIONS_URL = "relations-api.target-url";
    private static final String KESSEL_DOMAIN = "notifications.kessel.domain";
    private static final String RBAC_ENABLED = "rbac.enabled";
    private static final String RBAC_PSKS = "notifications.rbac.psks";
    private static final String UNLEASH = "notifications.unleash.enabled";
    private static final String MAINTENANCE_MODE = "notifications.maintenance.mode";

    /*
     * Unleash configuration
     */
    private String drawerToggle;
    private String kesselInventoryToggle;
    private String kesselRelationsToggle;
    private String KesselInventoryUseForPermissionsChecksToggle;
    private String kesselChecksOnEventLogToggle;
    private String maintenanceModeToggle;
    private String bypassBehaviorGroupMaxCreationLimitToggle;
    private String ignoreSourcesErrorOnEndpointDeleteToggle;

    private static String toggleName(String feature) {
        return String.format("notifications-backend.%s.enabled", feature);
    }

    @ConfigProperty(name = UNLEASH, defaultValue = "false")
    @Deprecated(forRemoval = true, since = "To be removed when we're done migrating to Unleash in all environments")
    boolean unleashEnabled;

    @ConfigProperty(name = "notifications.drawer.enabled", defaultValue = "false")
    @Deprecated(forRemoval = true, since = "To be removed when we're done migrating to Unleash in all environments")
    boolean drawerEnabled;

    // Only used in stage environments.
    @ConfigProperty(name = DEFAULT_TEMPLATE, defaultValue = "false")
    boolean defaultTemplateEnabled;

    // Only used in special environments.
    @ConfigProperty(name = EMAILS_ONLY_MODE, defaultValue = "false")
    boolean emailsOnlyModeEnabled;

    // Only used in special environments.
    @ConfigProperty(name = INSTANT_EMAILS, defaultValue = "false")
    boolean instantEmailsEnabled;

    @ConfigProperty(name = KESSEL_INVENTORY_CLIENT_ID, defaultValue = "insights-notifications")
    String kesselInventoryClientId;

    @ConfigProperty(name = KESSEL_INVENTORY_ENABLED, defaultValue = "false")
    boolean kesselInventoryEnabled;

    @ConfigProperty(name = KESSEL_MIGRATION_BATCH_SIZE, defaultValue = "1000")
    int kesselMigrationBatchSize;

    @ConfigProperty(name = KESSEL_RELATIONS_ENABLED, defaultValue = "false")
    boolean kesselRelationsEnabled;

    @ConfigProperty(name = KESSEL_INVENTORY_FOR_PERMISSIONS_CHECKS_ENABLED, defaultValue = "false")
    boolean kesselInventoryUseForPermissionsChecksEnabled;

    @ConfigProperty(name = KESSEL_RELATIONS_LOOKUP_RESOURCES_LIMIT, defaultValue = "1000")
    int kesselRelationsLookupResourceLimit;

    @ConfigProperty(name = ERRATA_MIGRATION_BATCH_SIZE, defaultValue = "1000")
    int errataMigrationBatchSize;

    @ConfigProperty(name = KESSEL_DOMAIN, defaultValue = "redhat")
    String kesselDomain;

    @ConfigProperty(name = RBAC_ENABLED, defaultValue = "true")
    protected boolean rbacEnabled;

    @ConfigProperty(name = RBAC_PSKS, defaultValue = "{\"notifications\": {\"secret\": \"development-psk-value\"}}")
    protected String rbacPskSecrets;

    @ConfigProperty(name = MAINTENANCE_MODE, defaultValue = "false")
    boolean maintenanceModeEnabled;

    @ConfigProperty(name = KESSEL_INVENTORY_URL, defaultValue = "localhost:9081")
    String kesselInventoryUrl;

    @ConfigProperty(name = KESSEL_RELATIONS_URL, defaultValue = "localhost:9000")
    String kesselRelationsUrl;

    @Inject
    ToggleRegistry toggleRegistry;

    @Inject
    Unleash unleash;

    @PostConstruct
    void postConstruct() {
        drawerToggle = toggleRegistry.register("drawer", true);
        kesselInventoryToggle = toggleRegistry.register("kessel-inventory", true);
        kesselRelationsToggle = toggleRegistry.register("kessel-relations", true);
        kesselChecksOnEventLogToggle = toggleRegistry.register("kessel-checks-on-event-log", true);
        maintenanceModeToggle = toggleRegistry.register("notifications-maintenance-mode", true);
        bypassBehaviorGroupMaxCreationLimitToggle = toggleRegistry.register("bypass-behavior-group-max-creation-limit", true);
        ignoreSourcesErrorOnEndpointDeleteToggle = toggleRegistry.register("ignore-sources-error-on-endpoint-delete", true);
        KesselInventoryUseForPermissionsChecksToggle = toggleRegistry.register("kessel-inventory-permissions-checks", true);
    }

    void logConfigAtStartup(@Observes Startup event) {

        Map<String, Object> config = new TreeMap<>();
        config.put(DEFAULT_TEMPLATE, isDefaultTemplateEnabled());
        config.put(drawerToggle, isDrawerEnabled());
        config.put(EMAILS_ONLY_MODE, isEmailsOnlyModeEnabled());
        config.put(ERRATA_MIGRATION_BATCH_SIZE, getErrataMigrationBatchSize());
        config.put(KESSEL_INVENTORY_ENABLED, isKesselInventoryEnabled(null));
        config.put(KESSEL_INVENTORY_URL, kesselInventoryUrl);
        config.put(KESSEL_RELATIONS_ENABLED, isKesselRelationsEnabled(null));
        config.put(KESSEL_RELATIONS_LOOKUP_RESOURCES_LIMIT, getKesselRelationsLookupResourceLimit());
        config.put(KESSEL_RELATIONS_URL, kesselRelationsUrl);
        config.put(INSTANT_EMAILS, isInstantEmailsEnabled());
        config.put(KESSEL_DOMAIN, getKesselDomain());
        config.put(RBAC_ENABLED, isRBACEnabled());
        config.put(UNLEASH, unleashEnabled);

        Log.info("=== Startup configuration ===");
        config.forEach((key, value) -> {
            Log.infof("%s=%s", key, value);
        });
    }

    public boolean isDefaultTemplateEnabled() {
        return defaultTemplateEnabled;
    }

    public boolean isDrawerEnabled() {
        if (unleashEnabled) {
            return unleash.isEnabled(drawerToggle, false);
        } else {
            return drawerEnabled;
        }
    }

    public boolean isEmailsOnlyModeEnabled() {
        return emailsOnlyModeEnabled;
    }

    public int getErrataMigrationBatchSize() {
        return this.errataMigrationBatchSize;
    }

    public boolean isInstantEmailsEnabled() {
        return instantEmailsEnabled;
    }

    public boolean isKesselInventoryEnabled(String orgId) {
        if (unleashEnabled) {
            UnleashContext unleashContext = buildUnleashContextWithOrgId(orgId);
            return unleash.isEnabled(kesselInventoryToggle, unleashContext, false);
        } else {
            return kesselInventoryEnabled;
        }
    }

    public boolean isIgnoreSourcesErrorOnEndpointDelete(String orgId) {
        if (unleashEnabled) {
            UnleashContext unleashContext = buildUnleashContextWithOrgId(orgId);
            return unleash.isEnabled(ignoreSourcesErrorOnEndpointDeleteToggle, unleashContext, false);
        } else {
            return false;
        }
    }

    /**
     * Specifies the maximum number of resources that we will ask Kessel to
     * stream back at us when we are looking up resources.
     * @return the maximum number of resources to query for in Kessel.
     */
    public int getKesselRelationsLookupResourceLimit() {
        return this.kesselRelationsLookupResourceLimit;
    }

    /**
     * Return the "reporter instance ID" that needs to be used when sending
     * gRPC requests to the Kessel Inventory.
     * @return the properly formatted reporter instance ID to be used on Kessel
     * Inventory gRPC requests. The returned format is
     * {@code service-account-${CLIENT_ID}}, since that is what the Inventory
     * API expects in that field. More information can be found in <a href="https://github.com/project-kessel/inventory-api/tree/586bf9b1ef689b8afe56546681e7f50f6ed443d9?tab=readme-ov-file#running-inventory-api-with-sso-keycloak-docker-compose-setup">
     * the Inventory API documentation.</a>
     */
    public String getKesselInventoryReporterInstanceId() {
        return "service-account-" + kesselInventoryClientId;
    }

    public int getKesselMigrationBatchSize() {
        return this.kesselMigrationBatchSize;
    }

    public boolean isKesselRelationsEnabled(String orgId) {
        if (unleashEnabled) {
            UnleashContext unleashContext = buildUnleashContextWithOrgId(orgId);
            return unleash.isEnabled(kesselRelationsToggle, unleashContext, false);
        } else {
            return kesselRelationsEnabled;
        }
    }

    public boolean isKesselInventoryUseForPermissionsChecksEnabled(String orgId) {
        if (unleashEnabled) {
            UnleashContext unleashContext = buildUnleashContextWithOrgId(orgId);
            return unleash.isEnabled(KesselInventoryUseForPermissionsChecksToggle, unleashContext, false);
        } else {
            return kesselInventoryUseForPermissionsChecksEnabled;
        }
    }

    public boolean isKesselChecksOnEventLogEnabled(String orgId) {
        if (unleashEnabled) {
            UnleashContext unleashContext = buildUnleashContextWithOrgId(orgId);
            return unleash.isEnabled(kesselChecksOnEventLogToggle, unleashContext, false);
        } else {
            return false;
        }
    }

    public String getKesselDomain() {
        return kesselDomain;
    }

    public boolean isRBACEnabled() {
        return this.rbacEnabled;
    }

    public JsonObject getRbacPskSecrets() {
        return new JsonObject(this.rbacPskSecrets);
    }


    private static UnleashContext buildUnleashContextWithOrgId(String orgId) {
        UnleashContext unleashContext = UnleashContext.builder()
            .addProperty("orgId", orgId)
            .build();
        return unleashContext;
    }

    public boolean isMaintenanceModeEnabled(String path) {
        if (unleashEnabled) {
            UnleashContext unleashContext = UnleashContext.builder()
                .addProperty("method_and_path", path)
                .build();
            return unleash.isEnabled(maintenanceModeToggle, unleashContext, false);
        }
        return maintenanceModeEnabled;
    }

    /**
     * Checks whether the behavior group creation limit is disabled for the
     * given organization.
     * @param orgId the organization we want to check the limit for.
     * @return {@code true} for almost all the organizations. We might disable
     * this limit on a very specific organization from time to time if we want
     * to perform some kind of tests, but it is going to be an exception.
     * @deprecated for removal because once behavior groups go away this
     * configuration check will not make any sense anymore.
     */
    @Deprecated(forRemoval = true)
    public boolean isBehaviorGroupCreationLimitDisabledForOrgId(final String orgId) {
        if (unleashEnabled) {
            final UnleashContext unleashContext = buildUnleashContextWithOrgId(orgId);

            return unleash.isEnabled(bypassBehaviorGroupMaxCreationLimitToggle, unleashContext, false);
        } else {
            return false;
        }
    }
}
