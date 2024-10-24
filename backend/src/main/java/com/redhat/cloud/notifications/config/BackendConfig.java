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
    private static final String KESSEL_INVENTORY_ENABLED = "notifications.kessel-inventory.enabled";
    private static final String KESSEL_INVENTORY_INTEGRATIONS_REMOVAL_ENABLED = "notifications.kessel-inventory.integrations-removal.enabled";
    private static final String KESSEL_RELATIONS_ENABLED = "notifications.kessel-relations.enabled";
    private static final String KESSEL_DOMAIN = "notifications.kessel.domain";
    private static final String RBAC_PSKS = "notifications.rbac.psks";
    private static final String UNLEASH = "notifications.unleash.enabled";

    /*
     * Unleash configuration
     */
    private String drawerToggle;
    private String kesselInventoryToggle;
    private String kesselInventoryIntegrationsRemovalToggle;
    private String kesselRelationsToggle;

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

    @ConfigProperty(name = KESSEL_INVENTORY_INTEGRATIONS_REMOVAL_ENABLED, defaultValue = "false")
    boolean kesselInventoryIntegrationRemovalsEnabled;

    @ConfigProperty(name = KESSEL_RELATIONS_ENABLED, defaultValue = "false")
    boolean kesselRelationsEnabled;

    @ConfigProperty(name = ERRATA_MIGRATION_BATCH_SIZE, defaultValue = "1000")
    int errataMigrationBatchSize;

    @ConfigProperty(name = KESSEL_DOMAIN, defaultValue = "redhat")
    String kesselDomain;

    @ConfigProperty(name = RBAC_PSKS, defaultValue = "{\"notifications\": {\"secret\": \"development-psk-value\"}}")
    protected String rbacPskSecrets;

    @Inject
    ToggleRegistry toggleRegistry;

    @Inject
    Unleash unleash;

    @PostConstruct
    void postConstruct() {
        drawerToggle = toggleRegistry.register("drawer", true);
        kesselInventoryToggle = toggleRegistry.register("kessel-inventory", true);
        kesselInventoryIntegrationsRemovalToggle = toggleRegistry.register("kessel-inventory-integrations-removal", true);
        kesselRelationsToggle = toggleRegistry.register("kessel-relations", true);
    }

    void logConfigAtStartup(@Observes Startup event) {

        Map<String, Object> config = new TreeMap<>();
        config.put(DEFAULT_TEMPLATE, isDefaultTemplateEnabled());
        config.put(drawerToggle, isDrawerEnabled());
        config.put(EMAILS_ONLY_MODE, isEmailsOnlyModeEnabled());
        config.put(ERRATA_MIGRATION_BATCH_SIZE, getErrataMigrationBatchSize());
        config.put(KESSEL_INVENTORY_ENABLED, isKesselInventoryEnabled(null));
        config.put(KESSEL_INVENTORY_INTEGRATIONS_REMOVAL_ENABLED, areKesselInventoryIntegrationRemovalsEnabled(null));
        config.put(KESSEL_RELATIONS_ENABLED, isKesselRelationsEnabled(null));
        config.put(INSTANT_EMAILS, isInstantEmailsEnabled());
        config.put(KESSEL_DOMAIN, getKesselDomain());
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

    /**
     * In the beginning, only the creation of the integrations was supported in
     * the Kessel inventory. The removal of them was not implemented nor
     * supported. There is a <a href="https://issues.redhat.com/browse/RHCLOUD-35755">
     * bug filed</a> for an issue related to integration removals, so once we
     * confirm everything works we can simply remove the feature flag.
     * @return {@code true} if we are allowed to delete integrations from the
     * Kessel inventory.
     */
    public boolean areKesselInventoryIntegrationRemovalsEnabled(String orgId) {
        if (unleashEnabled) {
            UnleashContext unleashContext = buildUnleashContextWithOrgId(orgId);
            return unleash.isEnabled(kesselInventoryIntegrationsRemovalToggle, unleashContext, false);
        } else {
            return kesselInventoryIntegrationRemovalsEnabled;
        }
    }

    public boolean isKesselInventoryEnabled(String orgId) {
        if (unleashEnabled) {
            UnleashContext unleashContext = buildUnleashContextWithOrgId(orgId);
            return unleash.isEnabled(kesselInventoryToggle, unleashContext, false);
        } else {
            return kesselInventoryEnabled;
        }
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

    public boolean isKesselRelationsEnabled(String orgId) {
        if (unleashEnabled) {
            UnleashContext unleashContext = buildUnleashContextWithOrgId(orgId);
            return unleash.isEnabled(kesselRelationsToggle, unleashContext, false);
        } else {
            return kesselRelationsEnabled;
        }
    }

    public String getKesselDomain() {
        return kesselDomain;
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
}
