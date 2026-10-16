package com.redhat.cloud.notifications.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.unleash.ToggleRegistry;
import com.redhat.cloud.notifications.unleash.UnleashContextBuilder;
import io.getunleash.Unleash;
import io.getunleash.UnleashContext;
import io.getunleash.variant.Payload;
import io.getunleash.variant.Variant;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Startup;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

@ApplicationScoped
public class AggregatorConfig {

    /*
     * Env vars configuration
     */
    private static final String CLUSTER_ID = "notifications.aggregator.cluster-id";
    private static final String METRICS_SCRAPE_MODE_ENABLED = "notifications.aggregator.metrics.scrape-mode-enabled";
    private static final String METRICS_SCRAPE_KEEP_ALIVE_SECONDS = "notifications.aggregator.metrics.scrape-keep-alive-seconds";
    private static final int DEFAULT_METRICS_SCRAPE_KEEP_ALIVE_SECONDS = 150;
    private static final String KAFKA_TOCAMEL_MAXIMUM_REQUEST_SIZE = "mp.messaging.outgoing.tocamel.max.request.size";
    private static final String NOTIFICATIONS_EMAIL_SENDER_HYBRID_CLOUD_CONSOLE = "notifications.email.sender.hybrid.cloud.console";
    private static final String NOTIFICATIONS_EMAIL_SENDER_OPENSHIFT_STAGE = "notifications.email.sender.openshift.stage";
    private static final String NOTIFICATIONS_EMAIL_SENDER_OPENSHIFT_PROD = "notifications.email.sender.openshift.prod";
    private static final String NOTIFICATIONS_EMAIL_SENDER_LIGHTWELL = "notifications.email.sender.lightwell";
    private static final String RH_HCC_SENDER = "\"Red Hat Hybrid Cloud Console\" noreply@redhat.com";
    private static final String OPENSHIFT_SENDER_STAGE_NOREPLY_REDHAT = "\"Red Hat OpenShift (staging)\" noreply@redhat.com";
    private static final String OPENSHIFT_SENDER_PROD_NOREPLY_REDHAT = "\"Red Hat OpenShift\" noreply@redhat.com";
    private static final String LIGHTWELL_SENDER_NOREPLY_REDHAT = "\"Lightwell\" noreply@redhat.com";

    /*
     * Unleash configuration
     */
    private String activeClusterToggle;
    private String toggleIncludeSeverityToFilterRecipients;
    private String toggleKafkaOutgoingHighVolumeTopic;
    private String toggleDirectDispatch;
    private String toggleBlacklistedEndpoints;

    @ConfigProperty(name = KAFKA_TOCAMEL_MAXIMUM_REQUEST_SIZE, defaultValue = "10485760")
    int kafkaToCamelMaximumRequestSize;

    @ConfigProperty(name = NOTIFICATIONS_EMAIL_SENDER_HYBRID_CLOUD_CONSOLE, defaultValue = RH_HCC_SENDER)
    String rhHccSender;

    @ConfigProperty(name = NOTIFICATIONS_EMAIL_SENDER_LIGHTWELL, defaultValue = LIGHTWELL_SENDER_NOREPLY_REDHAT)
    String rhLightwellSender;

    @ConfigProperty(name = NOTIFICATIONS_EMAIL_SENDER_OPENSHIFT_STAGE, defaultValue = OPENSHIFT_SENDER_STAGE_NOREPLY_REDHAT)
    String rhOpenshiftSenderStage;

    @ConfigProperty(name = NOTIFICATIONS_EMAIL_SENDER_OPENSHIFT_PROD, defaultValue = OPENSHIFT_SENDER_PROD_NOREPLY_REDHAT)
    String rhOpenshiftSenderProd;

    @ConfigProperty(name = CLUSTER_ID)
    Optional<String> clusterId;

    @ConfigProperty(name = METRICS_SCRAPE_MODE_ENABLED, defaultValue = "false")
    boolean metricsScrapeModeEnabled;

    @ConfigProperty(name = METRICS_SCRAPE_KEEP_ALIVE_SECONDS, defaultValue = DEFAULT_METRICS_SCRAPE_KEEP_ALIVE_SECONDS + "")
    int metricsScrapeKeepAliveSeconds;

    @Inject
    Unleash unleash;

    @Inject
    ToggleRegistry toggleRegistry;

    @PostConstruct
    void registerToggles() {
        activeClusterToggle = toggleRegistry.register("notifications-aggregator-active-cluster", true);
        Log.infof("Registered Unleash toggle: %s", activeClusterToggle);
        toggleIncludeSeverityToFilterRecipients = toggleRegistry.register("include-severity-to-filter-recipients", true);
        Log.infof("Registered Unleash toggle: %s", toggleIncludeSeverityToFilterRecipients);
        toggleKafkaOutgoingHighVolumeTopic = toggleRegistry.register("kafka-outgoing-high-volume-topic", true);
        Log.infof("Registered Unleash toggle: %s", toggleKafkaOutgoingHighVolumeTopic);
        toggleDirectDispatch = toggleRegistry.register("notifications-aggregator-direct-dispatch", true);
        Log.infof("Registered Unleash toggle: %s", toggleDirectDispatch);
        toggleBlacklistedEndpoints = toggleRegistry.register("blacklisted-endpoints", true);
        Log.infof("Registered Unleash toggle: %s", toggleBlacklistedEndpoints);
    }

    /**
     * Ops kill-switch to stop delivery to a specific misbehaving endpoint without a
     * deploy. Default (system-wide, {@code orgId == null}) endpoints can't be
     * blacklisted -- callers should skip this check for those, matching engine's
     * {@code EndpointProcessor} behavior.
     */
    public boolean isBlacklistedEndpoint(final UUID endpointId) {
        if (null != endpointId) {
            UnleashContext unleashContext = UnleashContext.builder()
                .addProperty("endpointId", endpointId.toString())
                .build();
            return unleash.isEnabled(toggleBlacklistedEndpoints, unleashContext, false);
        } else {
            return false;
        }
    }

    /**
     * Per-org kill-switch deciding whether an org's aggregation is dispatched
     * directly by this module (owns data aggregation, recipient resolution,
     * and dispatch end-to-end) or via the legacy path (publish an
     * {@code AggregationCommand} trigger to {@code ingress} for the engine to
     * process). Checked once per org at the start of that org's processing
     * within a single cron run.
     */
    public boolean isAggregatorDirectDispatchEnabled(String orgId) {
        return unleash.isEnabled(toggleDirectDispatch, UnleashContextBuilder.buildUnleashContextWithOrgId(orgId), false);
    }

    public String getRhHccSender() {
        return rhHccSender;
    }

    public String getLightwellSender() {
        return rhLightwellSender;
    }

    public String getRhOpenshiftSenderStage() {
        return rhOpenshiftSenderStage;
    }

    public String getRhOpenshiftSenderProd() {
        return rhOpenshiftSenderProd;
    }

    public boolean isIncludeSeverityToFilterRecipientsEnabled(String orgId, UUID applicationId) {
        final UnleashContext.Builder builder = UnleashContext.builder();
        if (orgId != null) {
            builder.addProperty("orgId", orgId);
        }
        if (applicationId != null) {
            builder.addProperty("applicationId", applicationId.toString());
        }

        return unleash.isEnabled(toggleIncludeSeverityToFilterRecipients, builder.build(), false);
    }

    public boolean isOutgoingKafkaHighVolumeTopicEnabled() {
        return this.unleash.isEnabled(this.toggleKafkaOutgoingHighVolumeTopic, false);
    }

    public int getKafkaToCamelMaximumRequestSize() {
        return kafkaToCamelMaximumRequestSize;
    }

    public Optional<String> getClusterId() {
        return clusterId.map(String::trim).filter(value -> !value.isEmpty());
    }

    public boolean isScrapeExportMode() {
        return metricsScrapeModeEnabled;
    }

    public int getMetricsScrapeKeepAliveSeconds() {
        if (metricsScrapeKeepAliveSeconds < 1) {
            Log.warnf("%s must be a positive number of seconds, got %d. Falling back to %d.",
                    METRICS_SCRAPE_KEEP_ALIVE_SECONDS, metricsScrapeKeepAliveSeconds, DEFAULT_METRICS_SCRAPE_KEEP_ALIVE_SECONDS);
            return DEFAULT_METRICS_SCRAPE_KEEP_ALIVE_SECONDS;
        }
        return metricsScrapeKeepAliveSeconds;
    }

    /**
     * POC for RHCLOUD-50496: clowder-quarkus-config-source has no property handler for
     * cdappconfig.json's metricsPort, so it can't be injected via {@code @ConfigProperty}.
     * Reads it directly instead. Once metricsPort is exposed as a proper config property,
     * this should be replaced by one.
     */
    public Optional<Integer> getMetricsPort() {
        String cdAppConfigPath = System.getenv("ACG_CONFIG");
        if (cdAppConfigPath == null || cdAppConfigPath.isBlank()) {
            Log.warn("ACG_CONFIG is not set, cannot determine the metrics port for scrape mode.");
            return Optional.empty();
        }

        try {
            JsonNode cdAppConfig = new ObjectMapper().readTree(new File(cdAppConfigPath));
            JsonNode metricsPortNode = cdAppConfig.get("metricsPort");
            // isIntegralNumber() alone is not enough: it's also true for integral values that don't
            // fit in an int (e.g. a corrupted LongNode), and asInt() silently narrows those instead
            // of rejecting them. canConvertToInt() is the guard that actually catches that case.
            if (metricsPortNode == null || !metricsPortNode.isIntegralNumber() || !metricsPortNode.canConvertToInt()) {
                Log.warnf("cdappconfig.json has no valid metricsPort field (value: %s), cannot expose metrics for scraping.", metricsPortNode);
                return Optional.empty();
            }

            int metricsPort = metricsPortNode.asInt();
            if (metricsPort < 1 || metricsPort > 65535) {
                Log.warnf("cdappconfig.json metricsPort %d is out of the valid port range, cannot expose metrics for scraping.", metricsPort);
                return Optional.empty();
            }

            return Optional.of(metricsPort);
        } catch (Exception e) {
            Log.warn("Could not read metricsPort from cdappconfig.json.", e);
            return Optional.empty();
        }
    }

    /**
     * Gets the active cluster ID from Unleash variant payload.
     * Returns Optional.empty() if:
     * - Unleash is disabled (variant will be disabled)
     * - Unleash is unreachable
     * - Variant is not enabled
     * - Payload is missing or invalid
     */
    public Optional<String> getActiveCluster() {
        try {
            Variant variant = unleash.getVariant(activeClusterToggle);
            if (!variant.isEnabled()) {
                Log.info("Unleash variant for active-cluster is not enabled");
                return Optional.empty();
            }

            Optional<Payload> payload = variant.getPayload();
            if (payload.isEmpty()) {
                Log.info("Unleash variant payload is empty");
                return Optional.empty();
            }

            String value = payload.get().getValue();
            if (value == null || value.isBlank()) {
                Log.info("Unleash variant payload value is null or blank");
                return Optional.empty();
            }

            return Optional.of(value.trim());
        } catch (Exception e) {
            Log.warn("Failed to retrieve active cluster from Unleash", e);
            return Optional.empty();
        }
    }

    void logConfigAtStartup(@Observes Startup event) {
        Map<String, Object> config = new TreeMap<>();
        config.put(CLUSTER_ID, getClusterId().orElse("not-configured"));
        config.put(METRICS_SCRAPE_MODE_ENABLED, metricsScrapeModeEnabled);
        config.put(METRICS_SCRAPE_KEEP_ALIVE_SECONDS, metricsScrapeKeepAliveSeconds);
        if (activeClusterToggle != null) {
            config.put(activeClusterToggle, getActiveCluster().orElse("unable-to-determine"));
        }
        if (toggleIncludeSeverityToFilterRecipients != null) {
            config.put(toggleIncludeSeverityToFilterRecipients, isIncludeSeverityToFilterRecipientsEnabled("", null));
        }
        if (toggleKafkaOutgoingHighVolumeTopic != null) {
            config.put(toggleKafkaOutgoingHighVolumeTopic, isOutgoingKafkaHighVolumeTopicEnabled());
        }
        if (toggleDirectDispatch != null) {
            config.put(toggleDirectDispatch, isAggregatorDirectDispatchEnabled(""));
        }
        config.put(KAFKA_TOCAMEL_MAXIMUM_REQUEST_SIZE, getKafkaToCamelMaximumRequestSize());
        config.put(NOTIFICATIONS_EMAIL_SENDER_HYBRID_CLOUD_CONSOLE, rhHccSender);
        config.put(NOTIFICATIONS_EMAIL_SENDER_OPENSHIFT_STAGE, rhOpenshiftSenderStage);
        config.put(NOTIFICATIONS_EMAIL_SENDER_OPENSHIFT_PROD, rhOpenshiftSenderProd);
        config.put(NOTIFICATIONS_EMAIL_SENDER_LIGHTWELL, rhLightwellSender);
        Log.info("=== Startup configuration ===");
        config.forEach((key, value) -> Log.infof("%s=%s", key, value));
    }
}
