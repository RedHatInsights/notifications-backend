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

import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;

@ApplicationScoped
public class EngineConfig {

    /*
     * Env vars configuration
     */
    private static final String DEFAULT_TEMPLATE = "notifications.use-default-template";
    private static final String EMAILS_ONLY_MODE = "notifications.emails-only-mode.enabled";
    private static final String EVENT_CONSUMER_CORE_THREAD_POOL_SIZE = "notifications.event-consumer.core-thread-pool-size";
    private static final String EVENT_CONSUMER_MAX_THREAD_POOL_SIZE = "notifications.event-consumer.max-thread-pool-size";
    private static final String EVENT_CONSUMER_KEEP_ALIVE_TIME_SECONDS = "notifications.event-consumer.keep-alive-time-seconds";
    private static final String EVENT_CONSUMER_QUEUE_CAPACITY = "notifications.event-consumer.queue-capacity";
    private static final String SECURED_EMAIL_TEMPLATES = "notifications.use-secured-email-templates.enabled";
    private static final String NOTIFICATIONS_KAFKA_OUTGOING_HIGH_VOLUME_TOPIC_ENABLED = "notifications.kafka.outgoing.high-volume.topic.enabled";
    private static final String KAFKA_TOCAMEL_MAXIMUM_REQUEST_SIZE = "mp.messaging.outgoing.tocamel.max.request.size";
    private static final String UNLEASH = "notifications.unleash.enabled";
    private static final String PROCESSOR_CONNECTORS_MAX_SERVER_ERRORS = "processor.connectors.max-server-errors";
    private static final String PROCESSOR_CONNECTORS_MIN_DELAY_SINCE_FIRST_SERVER_ERROR = "processor.connectors.min-delay-since-first-server-error";

    /**
     * Standard "Red Hat Hybrid Cloud Console" sender that the vast majority of the
     * ConsoleDot applications will use.
     */
    private static final String RH_HCC_SENDER = "\"Red Hat Hybrid Cloud Console\" noreply@redhat.com";
    private static final String OPENSHIFT_SENDER_STAGE_NOREPLY_REDHAT = "\"Red Hat OpenShift (staging)\" noreply@redhat.com";
    private static final String OPENSHIFT_SENDER_PROD_NOREPLY_REDHAT = "\"Red Hat OpenShift\" noreply@redhat.com";
    private static final String NOTIFICATIONS_EMAIL_SENDER_HYBRID_CLOUD_CONSOLE = "notifications.email.sender.hybrid.cloud.console";
    private static final String NOTIFICATIONS_EMAIL_SENDER_OPENSHIFT_STAGE = "notifications.email.sender.openshift.stage";
    private static final String NOTIFICATIONS_EMAIL_SENDER_OPENSHIFT_PROD = "notifications.email.sender.openshift.prod";
    private static final String NOTIFICATIONS_USE_OCM_REFACTORED_TEMPLATES = "notifications.use-ocm-refactored-templates";

    /*
     * Unleash configuration
     */
    private String asyncAggregationToggle;
    private String asyncEventProcessingToggle;
    private String drawerToggle;
    private String kafkaConsumedTotalCheckerToggle;
    private String toggleKafkaOutgoingHighVolumeTopic;
    private String toggleDirectEndpointToEventTypeDryRunEnabled;
    private String toggleUseDirectEndpointToEventTypeEnabled;

    @ConfigProperty(name = UNLEASH, defaultValue = "false")
    @Deprecated(forRemoval = true, since = "To be removed when we're done migrating to Unleash in all environments")
    boolean unleashEnabled;

    @ConfigProperty(name = "notifications.drawer.enabled", defaultValue = "false")
    @Deprecated(forRemoval = true, since = "To be removed when we're done migrating to Unleash in all environments")
    boolean drawerEnabled;

    @ConfigProperty(name = "notifications.async-aggregation.enabled", defaultValue = "true")
    @Deprecated(forRemoval = true, since = "To be removed when we're done migrating to Unleash in all environments")
    boolean asyncAggregation;

    @ConfigProperty(name = "processor.email.aggregation.use-recipients-resolver-clowdapp.enabled", defaultValue = "true")
    @Deprecated(forRemoval = true, since = "To be removed when we're done migrating to Unleash in all environments")
    boolean useRecipientsResolverClowdappForDailyDigestEnabled;

    @ConfigProperty(name = "notifications.kafka-consumed-total-checker.enabled", defaultValue = "false")
    @Deprecated(forRemoval = true, since = "To be removed when we're done migrating to Unleash in all environments")
    boolean kafkaConsumedTotalCheckerEnabled;

    @ConfigProperty(name = NOTIFICATIONS_KAFKA_OUTGOING_HIGH_VOLUME_TOPIC_ENABLED, defaultValue = "false")
    @Deprecated(forRemoval = true, since = "To be removed when we're done migrating to Unleash in all environments")
    Boolean outgoingKafkaHighVolumeTopicEnabled;

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

    @ConfigProperty(name = EVENT_CONSUMER_CORE_THREAD_POOL_SIZE, defaultValue = "10")
    int eventConsumerCoreThreadPoolSize;

    @ConfigProperty(name = EVENT_CONSUMER_MAX_THREAD_POOL_SIZE, defaultValue = "10")
    int eventConsumerMaxThreadPoolSize;

    @ConfigProperty(name = EVENT_CONSUMER_KEEP_ALIVE_TIME_SECONDS, defaultValue = "60")
    long eventConsumerKeepAliveTimeSeconds;

    @ConfigProperty(name = EVENT_CONSUMER_QUEUE_CAPACITY, defaultValue = "1")
    int eventConsumerQueueCapacity;

    // Only used in special environments.
    @ConfigProperty(name = SECURED_EMAIL_TEMPLATES, defaultValue = "false")
    boolean useSecuredEmailTemplates;

    @ConfigProperty(name = PROCESSOR_CONNECTORS_MAX_SERVER_ERRORS, defaultValue = "10")
    int maxServerErrors;

    @ConfigProperty(name = PROCESSOR_CONNECTORS_MIN_DELAY_SINCE_FIRST_SERVER_ERROR, defaultValue = "2D")
    Duration minDelaySinceFirstServerErrorBeforeDisabling;

    @ConfigProperty(name = KAFKA_TOCAMEL_MAXIMUM_REQUEST_SIZE, defaultValue = "10485760")
    int kafkaToCamelMaximumRequestSize;

    @ConfigProperty(name = NOTIFICATIONS_USE_OCM_REFACTORED_TEMPLATES, defaultValue = "false")
    boolean useOCMRefactoredTemplates;

    /**
     * The email sender address for the Red Hat Hybrid Cloud Console.
     */
    @ConfigProperty(name = NOTIFICATIONS_EMAIL_SENDER_HYBRID_CLOUD_CONSOLE, defaultValue = RH_HCC_SENDER)
    String rhHccSender;

    /**
     * The email sender address for OpenShift in stage.
     */
    @ConfigProperty(name = NOTIFICATIONS_EMAIL_SENDER_OPENSHIFT_STAGE, defaultValue = OPENSHIFT_SENDER_STAGE_NOREPLY_REDHAT)
    String rhOpenshiftSenderStage;

    /**
     * The email sender address for OpenShift in production.
     */
    @ConfigProperty(name = NOTIFICATIONS_EMAIL_SENDER_OPENSHIFT_PROD, defaultValue = OPENSHIFT_SENDER_PROD_NOREPLY_REDHAT)
    String rhOpenshiftSenderProd;

    @Inject
    ToggleRegistry toggleRegistry;

    @Inject
    Unleash unleash;

    @PostConstruct
    void postConstruct() {
        asyncAggregationToggle = toggleRegistry.register("async-aggregation", true);
        asyncEventProcessingToggle = toggleRegistry.register("async-event-processing", true);
        drawerToggle = toggleRegistry.register("drawer", true);
        kafkaConsumedTotalCheckerToggle = toggleRegistry.register("kafka-consumed-total-checker", true);
        toggleKafkaOutgoingHighVolumeTopic = toggleRegistry.register("kafka-outgoing-high-volume-topic", true);
        toggleDirectEndpointToEventTypeDryRunEnabled = toggleRegistry.register("endpoint-to-event-type-dry-run", true);
        toggleUseDirectEndpointToEventTypeEnabled = toggleRegistry.register("use-endpoint-to-event-type", true);
    }

    void logConfigAtStartup(@Observes Startup event) {

        Map<String, Object> config = new TreeMap<>();
        config.put(asyncAggregationToggle, isAsyncAggregationEnabled());
        config.put(DEFAULT_TEMPLATE, isDefaultTemplateEnabled());
        config.put(drawerToggle, isDrawerEnabled());
        config.put(EMAILS_ONLY_MODE, isEmailsOnlyModeEnabled());
        config.put(EVENT_CONSUMER_CORE_THREAD_POOL_SIZE, eventConsumerCoreThreadPoolSize);
        config.put(EVENT_CONSUMER_MAX_THREAD_POOL_SIZE, eventConsumerMaxThreadPoolSize);
        config.put(EVENT_CONSUMER_KEEP_ALIVE_TIME_SECONDS, eventConsumerKeepAliveTimeSeconds);
        config.put(EVENT_CONSUMER_QUEUE_CAPACITY, eventConsumerQueueCapacity);
        config.put(kafkaConsumedTotalCheckerToggle, isKafkaConsumedTotalCheckerEnabled());
        config.put(KAFKA_TOCAMEL_MAXIMUM_REQUEST_SIZE, getKafkaToCamelMaximumRequestSize());
        config.put(SECURED_EMAIL_TEMPLATES, isSecuredEmailTemplatesEnabled());
        config.put(UNLEASH, unleashEnabled);
        config.put(PROCESSOR_CONNECTORS_MAX_SERVER_ERRORS, maxServerErrors);
        config.put(PROCESSOR_CONNECTORS_MIN_DELAY_SINCE_FIRST_SERVER_ERROR, minDelaySinceFirstServerErrorBeforeDisabling);
        config.put(NOTIFICATIONS_EMAIL_SENDER_HYBRID_CLOUD_CONSOLE, rhHccSender);
        config.put(NOTIFICATIONS_EMAIL_SENDER_OPENSHIFT_STAGE, rhOpenshiftSenderStage);
        config.put(NOTIFICATIONS_EMAIL_SENDER_OPENSHIFT_PROD, rhOpenshiftSenderProd);
        config.put(toggleKafkaOutgoingHighVolumeTopic, isOutgoingKafkaHighVolumeTopicEnabled());
        config.put(asyncEventProcessingToggle, isAsyncEventProcessing());
        config.put(NOTIFICATIONS_USE_OCM_REFACTORED_TEMPLATES, isUseOCMRefactoredTemplates());
        config.put(toggleDirectEndpointToEventTypeDryRunEnabled, isDirectEndpointToEventTypeDryRunEnabled());
        config.put(toggleUseDirectEndpointToEventTypeEnabled, isUseDirectEndpointToEventTypeEnabled());

        Log.info("=== Startup configuration ===");
        config.forEach((key, value) -> {
            Log.infof("%s=%s", key, value);
        });
    }

    public boolean isAsyncAggregationEnabled() {
        if (unleashEnabled) {
            return unleash.isEnabled(asyncAggregationToggle, false);
        } else {
            return asyncAggregation;
        }
    }

    public boolean isAsyncEventProcessing() {
        if (unleashEnabled) {
            return unleash.isEnabled(asyncEventProcessingToggle, false);
        } else {
            return false;
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

    public int getEventConsumerCoreThreadPoolSize() {
        return eventConsumerCoreThreadPoolSize;
    }

    public int getEventConsumerMaxThreadPoolSize() {
        return eventConsumerMaxThreadPoolSize;
    }

    public long getEventConsumerKeepAliveTimeSeconds() {
        return eventConsumerKeepAliveTimeSeconds;
    }

    public int getEventConsumerQueueCapacity() {
        return eventConsumerQueueCapacity;
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

    public int getMaxServerErrors() {
        return maxServerErrors;
    }

    public Duration getMinDelaySinceFirstServerErrorBeforeDisabling() {
        return minDelaySinceFirstServerErrorBeforeDisabling;
    }

    public String getRhHccSender() {
        return rhHccSender;
    }

    public String getRhOpenshiftSenderStage() {
        return rhOpenshiftSenderStage;
    }

    public String getRhOpenshiftSenderProd() {
        return rhOpenshiftSenderProd;
    }

    public int getKafkaToCamelMaximumRequestSize() {
        return kafkaToCamelMaximumRequestSize;
    }

    public boolean isDirectEndpointToEventTypeDryRunEnabled() {
        if (unleashEnabled) {
            return unleash.isEnabled(toggleDirectEndpointToEventTypeDryRunEnabled, false);
        } else {
            return false;
        }
    }

    public boolean isUseDirectEndpointToEventTypeEnabled() {
        if (unleashEnabled) {
            return unleash.isEnabled(toggleUseDirectEndpointToEventTypeEnabled, false);
        } else {
            return false;
        }
    }

    public boolean isOutgoingKafkaHighVolumeTopicEnabled() {
        if (unleashEnabled) {
            return this.unleash.isEnabled(this.toggleKafkaOutgoingHighVolumeTopic, false);
        } else {
            return this.outgoingKafkaHighVolumeTopicEnabled;
        }
    }

    public boolean isUseOCMRefactoredTemplates() {
        return useOCMRefactoredTemplates;
    }
}
