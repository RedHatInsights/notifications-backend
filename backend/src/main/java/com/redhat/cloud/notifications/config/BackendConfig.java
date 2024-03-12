package com.redhat.cloud.notifications.config;

import io.getunleash.Unleash;
import io.quarkus.logging.Log;
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
    private static final String DRAWER = toggleName("drawer");
    private static final String FORBID_SLACK_CHANNEL_USAGE = toggleName("forbid-slack-channel-usage");
    private static final String UNIQUE_BG_NAME = toggleName("unique-bg-name");
    private static final String UNIQUE_INTEGRATION_NAME = toggleName("unique-integration-name");

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

    @ConfigProperty(name = "notifications.slack.forbid.channel.usage.enabled", defaultValue = "false")
    @Deprecated(forRemoval = true, since = "To be removed when we're done migrating to Unleash in all environments")
    boolean slackForbidChannelUsageEnabled;

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
    Unleash unleash;

    void logConfigAtStartup(@Observes Startup event) {

        Map<String, Object> config = new TreeMap<>();
        config.put(DEFAULT_TEMPLATE, isDefaultTemplateEnabled());
        config.put(DRAWER, isDrawerEnabled());
        config.put(EMAILS_ONLY_MODE, isEmailsOnlyModeEnabled());
        config.put(INSTANT_EMAILS, isInstantEmailsEnabled());
        config.put(FORBID_SLACK_CHANNEL_USAGE, isForbidSlackChannelUsage());
        config.put(UNIQUE_BG_NAME, isUniqueBgNameEnabled());
        config.put(UNIQUE_INTEGRATION_NAME, isUniqueIntegrationNameEnabled());
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
            return unleash.isEnabled(DRAWER, false);
        } else {
            return drawerEnabled;
        }
    }

    public boolean isEmailsOnlyModeEnabled() {
        return emailsOnlyModeEnabled;
    }

    public boolean isForbidSlackChannelUsage() {
        if (unleashEnabled) {
            return unleash.isEnabled(FORBID_SLACK_CHANNEL_USAGE, false);
        } else {
            return slackForbidChannelUsageEnabled;
        }
    }

    public boolean isInstantEmailsEnabled() {
        return instantEmailsEnabled;
    }

    public boolean isUniqueBgNameEnabled() {
        if (unleashEnabled) {
            return unleash.isEnabled(UNIQUE_BG_NAME, false);
        } else {
            return enforceBehaviorGroupNameUnicity;
        }
    }

    public boolean isUniqueIntegrationNameEnabled() {
        if (unleashEnabled) {
            return unleash.isEnabled(UNIQUE_INTEGRATION_NAME, false);
        } else {
            return enforceIntegrationNameUnicity;
        }
    }
}
