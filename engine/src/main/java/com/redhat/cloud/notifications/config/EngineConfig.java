package com.redhat.cloud.notifications.config;

import com.redhat.cloud.notifications.unleash.ToggleRegistry;
import io.getunleash.Unleash;
import io.getunleash.UnleashContext;
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
public class EngineConfig {

    /*
     * Env vars configuration
     */
    private static final String DEFAULT_TEMPLATE = "notifications.use-default-template";
    private static final String EMAILS_ONLY_MODE = "notifications.emails-only-mode.enabled";
    private static final String SECURED_EMAIL_TEMPLATES = "notifications.use-secured-email-templates.enabled";
    private static final String UNLEASH = "notifications.unleash.enabled";

    /*
     * Unleash configuration
     */
    private String aggregationWithRecipientsResolverToggle;
    private String asyncAggregationToggle;
    private String drawerToggle;
    private String kafkaConsumedTotalCheckerToggle;

    @ConfigProperty(name = UNLEASH, defaultValue = "false")
    @Deprecated(forRemoval = true, since = "To be removed when we're done migrating to Unleash in all environments")
    boolean unleashEnabled;

    @ConfigProperty(name = "notifications.drawer.enabled", defaultValue = "false")
    @Deprecated(forRemoval = true, since = "To be removed when we're done migrating to Unleash in all environments")
    boolean drawerEnabled;

    @ConfigProperty(name = "notifications.async-aggregation.enabled", defaultValue = "true")
    @Deprecated(forRemoval = true, since = "To be removed when we're done migrating to Unleash in all environments")
    boolean asyncAggregation;

    @ConfigProperty(name = "processor.email.aggregation.use-recipients-resolver-clowdapp.enabled", defaultValue = "false")
    @Deprecated(forRemoval = true, since = "To be removed when we're done migrating to Unleash in all environments")
    boolean useRecipientsResolverClowdappForDailyDigestEnabled;

    @ConfigProperty(name = "notifications.kafka-consumed-total-checker.enabled", defaultValue = "false")
    @Deprecated(forRemoval = true, since = "To be removed when we're done migrating to Unleash in all environments")
    boolean kafkaConsumedTotalCheckerEnabled;

    @ConfigProperty(name = "notifications.use-rbac-for-fetching-users", defaultValue = "false")
    @Deprecated(forRemoval = true, since = "To be removed when we're done migrating to Unleash in all environments")
    boolean useRbacForFetchingUsers;

    @ConfigProperty(name = "notifications.use-mbop-for-fetching-users", defaultValue = "false")
    @Deprecated(forRemoval = true, since = "To be removed when we're done migrating to Unleash in all environments")
    boolean useMBOPForFetchingUsers;

    // Only used in stage environments.
    @ConfigProperty(name = DEFAULT_TEMPLATE, defaultValue = "false")
    boolean defaultTemplateEnabled;

    // Only used in special environments.
    @ConfigProperty(name = EMAILS_ONLY_MODE, defaultValue = "false")
    boolean emailsOnlyModeEnabled;

    // Only used in special environments.
    @ConfigProperty(name = SECURED_EMAIL_TEMPLATES, defaultValue = "false")
    boolean useSecuredEmailTemplates;

    @Inject
    ToggleRegistry toggleRegistry;

    @Inject
    Unleash unleash;

    @PostConstruct
    void postConstruct() {
        aggregationWithRecipientsResolverToggle = toggleRegistry.register("aggregation-with-recipients-resolver", true);
        asyncAggregationToggle = toggleRegistry.register("async-aggregation", true);
        drawerToggle = toggleRegistry.register("drawer", true);
        kafkaConsumedTotalCheckerToggle = toggleRegistry.register("kafka-consumed-total-checker", true);
    }

    void logConfigAtStartup(@Observes Startup event) {

        Map<String, Object> config = new TreeMap<>();
        config.put(aggregationWithRecipientsResolverToggle, isAggregationWithRecipientsResolverEnabled());
        config.put(asyncAggregationToggle, isAsyncAggregationEnabled());
        config.put(DEFAULT_TEMPLATE, isDefaultTemplateEnabled());
        config.put(drawerToggle, isDrawerEnabled());
        config.put(EMAILS_ONLY_MODE, isEmailsOnlyModeEnabled());
        config.put(kafkaConsumedTotalCheckerToggle, isKafkaConsumedTotalCheckerEnabled());
        config.put(SECURED_EMAIL_TEMPLATES, isSecuredEmailTemplatesEnabled());
        config.put(UNLEASH, unleashEnabled);

        Log.info("=== Startup configuration ===");
        config.forEach((key, value) -> {
            Log.infof("%s=%s", key, value);
        });
    }

    public boolean isAggregationWithRecipientsResolverEnabled() {
        if (unleashEnabled) {
            return unleash.isEnabled(aggregationWithRecipientsResolverToggle, false);
        } else {
            return useRecipientsResolverClowdappForDailyDigestEnabled;
        }
    }

    public boolean isAsyncAggregationEnabled() {
        if (unleashEnabled) {
            return unleash.isEnabled(asyncAggregationToggle, false);
        } else {
            return asyncAggregation;
        }
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

    public boolean isKafkaConsumedTotalCheckerEnabled() {
        if (unleashEnabled) {
            return unleash.isEnabled(kafkaConsumedTotalCheckerToggle, false);
        } else {
            return kafkaConsumedTotalCheckerEnabled;
        }
    }

    public boolean isSecuredEmailTemplatesEnabled() {
        return useSecuredEmailTemplates;
    }

    @Deprecated(forRemoval = true)
    public boolean isUseMBOPForFetchingUsers() {
        return useMBOPForFetchingUsers;
    }

    @Deprecated(forRemoval = true)
    public boolean isUseRbacForFetchingUsers() {
        return useRbacForFetchingUsers;
    }
}
