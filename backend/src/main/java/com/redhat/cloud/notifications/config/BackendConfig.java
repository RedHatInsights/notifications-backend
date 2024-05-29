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
    private static final String INSTANT_EMAILS = "notifications.instant-emails.enabled";
    private static final String UNLEASH = "notifications.unleash.enabled";

    /*
     * Unleash configuration
     */
    private String drawerToggle;
    private String uniqueBgNameToggle;
    private String uniqueIntegrationNameToggle;

    private static String toggleName(String feature) {
        return String.format("notifications-backend.%s.enabled", feature);
    }

    @ConfigProperty(name = UNLEASH, defaultValue = "false")
    @Deprecated(forRemoval = true, since = "To be removed when we're done migrating to Unleash in all environments")
    boolean unleashEnabled;

    @ConfigProperty(name = "notifications.enforce-bg-name-unicity", defaultValue = "false")
    @Deprecated(forRemoval = true, since = "To be removed when we're done migrating to Unleash in all environments")
    boolean enforceBehaviorGroupNameUnicity;

    @ConfigProperty(name = "notifications.enforce-integration-name-unicity", defaultValue = "false")
    @Deprecated(forRemoval = true, since = "To be removed when we're done migrating to Unleash in all environments")
    boolean enforceIntegrationNameUnicity;

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

    @Inject
    ToggleRegistry toggleRegistry;

    @Inject
    Unleash unleash;

    @PostConstruct
    void postConstruct() {
        drawerToggle = toggleRegistry.register("drawer", true);
        uniqueBgNameToggle = toggleRegistry.register("unique-bg-name", true);
        uniqueIntegrationNameToggle = toggleRegistry.register("unique-integration-name", true);
    }

    void logConfigAtStartup(@Observes Startup event) {

        Map<String, Object> config = new TreeMap<>();
        config.put(DEFAULT_TEMPLATE, isDefaultTemplateEnabled());
        config.put(drawerToggle, isDrawerEnabled());
        config.put(EMAILS_ONLY_MODE, isEmailsOnlyModeEnabled());
        config.put(INSTANT_EMAILS, isInstantEmailsEnabled());
        config.put(uniqueBgNameToggle, isUniqueBgNameEnabled());
        config.put(uniqueIntegrationNameToggle, isUniqueIntegrationNameEnabled());
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

    public boolean isInstantEmailsEnabled() {
        return instantEmailsEnabled;
    }

    public boolean isUniqueBgNameEnabled() {
        if (unleashEnabled) {
            return unleash.isEnabled(uniqueBgNameToggle, false);
        } else {
            return enforceBehaviorGroupNameUnicity;
        }
    }

    public boolean isUniqueIntegrationNameEnabled() {
        if (unleashEnabled) {
            return unleash.isEnabled(uniqueIntegrationNameToggle, false);
        } else {
            return enforceIntegrationNameUnicity;
        }
    }
}
