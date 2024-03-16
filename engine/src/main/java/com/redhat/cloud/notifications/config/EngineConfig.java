package com.redhat.cloud.notifications.config;

import io.getunleash.Unleash;
import io.getunleash.UnleashContext;
import io.quarkus.logging.Log;
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
    public static final String AGGREGATION_WITH_RECIPIENTS_RESOLVER = toggleName("aggregation-with-recipients-resolver");
    public static final String ASYNC_AGGREGATION = toggleName("async-aggregation");
    public static final String DRAWER = toggleName("drawer");
    public static final String HCC_EMAIL_SENDER_NAME = toggleName("hcc-email-sender-name");
    public static final String KAFKA_CONSUMED_TOTAL_CHECKER = toggleName("kafka-consumed-total-checker");

    private static String toggleName(String feature) {
        return String.format("notifications-engine.%s.enabled", feature);
    }

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

    @ConfigProperty(name = "notifications.email.hcc-sender-name.enabled", defaultValue = "false")
    @Deprecated(forRemoval = true, since = "To be removed when we're done migrating to Unleash in all environments")
    boolean hccEmailSenderNameEnabled;

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
    Unleash unleash;

    void logConfigAtStartup(@Observes Startup event) {

        Map<String, Object> config = new TreeMap<>();
        config.put(AGGREGATION_WITH_RECIPIENTS_RESOLVER, isAggregationWithRecipientsResolverEnabled());
        config.put(ASYNC_AGGREGATION, isAsyncAggregationEnabled());
        config.put(DEFAULT_TEMPLATE, isDefaultTemplateEnabled());
        config.put(DRAWER, isDrawerEnabled());
        config.put(EMAILS_ONLY_MODE, isEmailsOnlyModeEnabled());
        config.put(KAFKA_CONSUMED_TOTAL_CHECKER, isKafkaConsumedTotalCheckerEnabled());
        config.put(SECURED_EMAIL_TEMPLATES, isSecuredEmailTemplatesEnabled());
        config.put(UNLEASH, unleashEnabled);

        Log.info("=== Startup configuration ===");
        config.forEach((key, value) -> {
            Log.infof("%s=%s", key, value);
        });
    }

    public boolean isAggregationWithRecipientsResolverEnabled() {
        if (unleashEnabled) {
            return unleash.isEnabled(AGGREGATION_WITH_RECIPIENTS_RESOLVER, false);
        } else {
            return useRecipientsResolverClowdappForDailyDigestEnabled;
        }
    }

    public boolean isAsyncAggregationEnabled() {
        if (unleashEnabled) {
            return unleash.isEnabled(ASYNC_AGGREGATION, false);
        } else {
            return asyncAggregation;
        }
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

    public boolean isHccEmailSenderNameEnabled(String orgId) {
        if (unleashEnabled) {
            UnleashContext unleashContext = UnleashContext.builder().addProperty("orgId", orgId).build();
            return unleash.isEnabled(HCC_EMAIL_SENDER_NAME, unleashContext, false);
        } else {
            return hccEmailSenderNameEnabled;
        }
    }

    public boolean isKafkaConsumedTotalCheckerEnabled() {
        if (unleashEnabled) {
            return unleash.isEnabled(KAFKA_CONSUMED_TOTAL_CHECKER, false);
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
