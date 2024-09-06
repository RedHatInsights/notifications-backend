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
public class BackendConfig {

    /*
     * Env vars configuration
     */
    private static final String DEFAULT_TEMPLATE = "notifications.use-default-template";
    private static final String EMAILS_ONLY_MODE = "notifications.emails-only-mode.enabled";
    private static final String ERRATA_MIGRATION_BATCH_SIZE = "notifications.errata.migration.batch.size";
    private static final String INSTANT_EMAILS = "notifications.instant-emails.enabled";
    private static final String KESSEL_BACKEND_ENABLED = "notifications.kessel.backend.enabled";
    private static final String KESSEL_USE_SECURE_CLIENT = "notifications.kessel.secure-client";
    private static final String UNLEASH = "notifications.unleash.enabled";

    /*
     * Unleash configuration
     */
    private String drawerToggle;
    private String kesselBackendToggle;
    private String uniqueBgNameToggle;
    private String uniqueIntegrationNameToggle;

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

    @ConfigProperty(name = KESSEL_BACKEND_ENABLED, defaultValue = "false")
    boolean kesselBackendEnabled;

    @ConfigProperty(name = ERRATA_MIGRATION_BATCH_SIZE, defaultValue = "1000")
    int errataMigrationBatchSize;

    /**
     * Is the gRPC client supposed to connect to a secure, HTTPS endpoint?
     */
    @ConfigProperty(name = KESSEL_USE_SECURE_CLIENT, defaultValue = "false")
    boolean kesselUseSecureClientEnabled;

    @Inject
    ToggleRegistry toggleRegistry;

    @Inject
    Unleash unleash;

    @PostConstruct
    void postConstruct() {
        drawerToggle = toggleRegistry.register("drawer", true);
        kesselBackendToggle = toggleRegistry.register("kessel-backend", true);
    }

    void logConfigAtStartup(@Observes Startup event) {

        Map<String, Object> config = new TreeMap<>();
        config.put(DEFAULT_TEMPLATE, isDefaultTemplateEnabled());
        config.put(drawerToggle, isDrawerEnabled());
        config.put(EMAILS_ONLY_MODE, isEmailsOnlyModeEnabled());
        config.put(ERRATA_MIGRATION_BATCH_SIZE, getErrataMigrationBatchSize());
        config.put(KESSEL_BACKEND_ENABLED, isKesselBackendEnabled());
        config.put(KESSEL_USE_SECURE_CLIENT, isKesselUseSecureClientEnabled());
        config.put(INSTANT_EMAILS, isInstantEmailsEnabled());
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

    public boolean isKesselBackendEnabled() {
        if (unleashEnabled) {
            return unleash.isEnabled(kesselBackendToggle, false);
        } else {
            return kesselBackendEnabled;
        }
    }

    public boolean isKesselUseSecureClientEnabled() {
        return kesselUseSecureClientEnabled;
    }
}
