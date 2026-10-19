package com.redhat.cloud.notifications.connector.v2.metrics;

import com.redhat.cloud.notifications.connector.v2.ConnectorConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;

/**
 * Common metrics utility for all connectors to track notifications sent and integration times.
 * The connector type is automatically determined from the ConnectorConfig.
 * Each connector records:
 * - Counter: notifications.connector.{type}.sent with tags: bundle, application, event_type
 * - Timer: notifications.connector.{type}.integration.time with tags: bundle, application, event_type
 */
@ApplicationScoped
public class ConnectorMetrics {

    public static final String TAG_BUNDLE = "bundle";
    public static final String TAG_APPLICATION = "application";
    public static final String TAG_EVENT_TYPE = "event_type";

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    ConnectorConfig connectorConfig;

    /**
     * Records that a notification was successfully sent through a connector.
     *
     * @param notificationData the event data containing bundle, application, and event_type
     */
    public void recordNotificationSent(JsonObject notificationData) {
        NotificationTags tags = extractTags(notificationData);
        String metricName = "notifications.connector." + connectorConfig.getConnectorName() + ".sent";

        meterRegistry.counter(metricName,
            TAG_BUNDLE, tags.bundle,
            TAG_APPLICATION, tags.application,
            TAG_EVENT_TYPE, tags.eventType
        ).increment();
    }

    /**
     * Records that a notification was successfully sent through a connector with additional custom tags.
     *
     * @param notificationData the event data containing bundle, application, and event_type
     * @param additionalTags additional tags to include in the metric (e.g., subscription_type)
     */
    public void recordNotificationSent(JsonObject notificationData, Map<String, String> additionalTags) {
        NotificationTags tags = extractTags(notificationData);
        String metricName = "notifications.connector." + connectorConfig.getConnectorName() + ".sent";

        String[] tagsArray = buildTagsArray(tags, additionalTags);
        meterRegistry.counter(metricName, tagsArray).increment();
    }

    /**
     * Starts a timer to measure integration time.
     *
     * @return a Timer.Sample that should be stopped when the integration completes
     */
    public Timer.Sample startIntegrationTimer() {
        return Timer.start(meterRegistry);
    }

    /**
     * Records the integration time for a connector.
     *
     * @param sample the Timer.Sample started before the integration began
     * @param notificationData the event data containing bundle, application, and event_type
     */
    public void recordIntegrationTime(Timer.Sample sample, JsonObject notificationData) {
        NotificationTags tags = extractTags(notificationData);
        String metricName = "notifications.connector." + connectorConfig.getConnectorName() + ".integration.time";

        Timer timer = Timer.builder(metricName)
            .tag(TAG_BUNDLE, tags.bundle)
            .tag(TAG_APPLICATION, tags.application)
            .tag(TAG_EVENT_TYPE, tags.eventType)
            .register(meterRegistry);

        sample.stop(timer);
    }

    /**
     * Records the integration time for a connector with additional custom tags.
     *
     * @param sample the Timer.Sample started before the integration began
     * @param notificationData the event data containing bundle, application, and event_type
     * @param additionalTags additional tags to include in the metric
     */
    public void recordIntegrationTime(Timer.Sample sample, JsonObject notificationData, Map<String, String> additionalTags) {
        NotificationTags tags = extractTags(notificationData);
        String metricName = "notifications.connector." + connectorConfig.getConnectorName() + ".integration.time";

        Timer.Builder timerBuilder = Timer.builder(metricName)
            .tag(TAG_BUNDLE, tags.bundle)
            .tag(TAG_APPLICATION, tags.application)
            .tag(TAG_EVENT_TYPE, tags.eventType);

        if (additionalTags != null) {
            additionalTags.forEach(timerBuilder::tag);
        }

        Timer timer = timerBuilder.register(meterRegistry);
        sample.stop(timer);
    }

    /**
     * Extracts bundle, application, and event_type from notification data.
     * Checks both top level and nested eventData/event_data fields.
     * For email aggregation/daily digest, detects aggregation structure and uses "aggregation" defaults.
     * Returns "unknown" for any missing field to ensure metrics are always recorded.
     */
    private NotificationTags extractTags(JsonObject notificationData) {
        String bundle = extractField(notificationData, "bundle");
        String application = extractField(notificationData, "application");
        String eventType = extractField(notificationData, "event_type");

        // TODO: This special-case aggregation detection patches a symptom instead of the root cause.
        // Aggregation emails should populate bundle/application/event_type in the standard CloudEvent
        // location like other notifications. This hardcoded pattern detection is fragile and will break
        // if the aggregation email structure changes. Consider standardizing upstream data structure.
        // Special handling for email aggregation/daily digest:
        // Check if this is an aggregation email by looking for bundle_display_name
        // and application_aggregated_data_list (unique to aggregation emails)
        JsonObject eventData = notificationData.getJsonObject("eventData");
        if (eventData != null) {
            boolean hasBundleDisplayName = eventData.getString("bundle_display_name") != null ||
                                          eventData.getString("bundleDisplayName") != null;
            boolean hasAggregationList = eventData.getJsonArray("application_aggregated_data_list") != null;

            if (hasBundleDisplayName && hasAggregationList && "unknown".equals(application) && "unknown".equals(eventType)) {
                // This is an email aggregation/daily digest
                application = "aggregation";
                eventType = "aggregation";
            }
        }

        return new NotificationTags(bundle, application, eventType);
    }

    private String extractField(JsonObject notificationData, String fieldName) {
        // Try top level first
        String value = notificationData.getString(fieldName);
        if (value != null && !value.isEmpty()) {
            return value;
        }

        // Try nested eventData field (used by email connector)
        JsonObject eventData = notificationData.getJsonObject("eventData");
        if (eventData != null) {
            value = eventData.getString(fieldName);
            if (value != null && !value.isEmpty()) {
                return value;
            }
            // For bundle field, also try bundleDisplayName (used by email aggregation)
            if ("bundle".equals(fieldName)) {
                value = eventData.getString("bundle_display_name");
                if (value != null && !value.isEmpty()) {
                    return value;
                }
                value = eventData.getString("bundleDisplayName");
                if (value != null && !value.isEmpty()) {
                    return value;
                }
            }
        }

        // Try nested event_data field (alternative naming)
        eventData = notificationData.getJsonObject("event_data");
        if (eventData != null) {
            value = eventData.getString(fieldName);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }

        return "unknown";
    }

    private String[] buildTagsArray(NotificationTags tags, Map<String, String> additionalTags) {
        int baseSize = 6; // 3 tags × 2 (key + value)
        int additionalSize = additionalTags != null ? additionalTags.size() * 2 : 0;
        String[] tagsArray = new String[baseSize + additionalSize];

        tagsArray[0] = TAG_BUNDLE;
        tagsArray[1] = tags.bundle;
        tagsArray[2] = TAG_APPLICATION;
        tagsArray[3] = tags.application;
        tagsArray[4] = TAG_EVENT_TYPE;
        tagsArray[5] = tags.eventType;

        if (additionalTags != null) {
            int index = baseSize;
            for (Map.Entry<String, String> entry : additionalTags.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key == null || value == null) {
                    throw new IllegalArgumentException("Additional tags must not contain null keys or values");
                }
                tagsArray[index++] = key;
                tagsArray[index++] = value;
            }
        }

        return tagsArray;
    }

    private record NotificationTags(String bundle, String application, String eventType) { }
}
