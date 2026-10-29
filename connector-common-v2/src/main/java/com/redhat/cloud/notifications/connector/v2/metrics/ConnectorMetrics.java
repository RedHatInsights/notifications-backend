package com.redhat.cloud.notifications.connector.v2.metrics;

import com.redhat.cloud.notifications.connector.v2.ConnectorConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;

/**
 * Common metrics utility for all connectors to track notifications sent and integration times.
 * Uses static metric names with dimensional tags (including connector type) for unified
 * cross-connector aggregation in Prometheus/Micrometer.
 * Each connector records:
 * - Counter: notifications.connector.sent with tags: connector, bundle, application, event_type
 * - Timer: notifications.connector.integration.time with tags: connector, bundle, application, event_type
 */
@ApplicationScoped
public class ConnectorMetrics {

    public static final String SENT_COUNTER_NAME = "notifications.connector.sent";
    public static final String INTEGRATION_TIME_TIMER_NAME = "notifications.connector.integration.time";

    public static final String TAG_CONNECTOR = "connector";
    public static final String TAG_BUNDLE = "bundle";
    public static final String TAG_APPLICATION = "application";
    public static final String TAG_EVENT_TYPE = "event_type";

    // Maximum length for tag values to prevent cardinality explosion
    private static final int MAX_TAG_VALUE_LENGTH = 100;

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
        recordNotificationSent(notificationData, null);
    }

    /**
     * Records that a notification was successfully sent through a connector with additional custom tags.
     *
     * @param notificationData the event data containing bundle, application, and event_type
     * @param additionalTags additional tags to include in the metric (e.g., subscription_type)
     */
    public void recordNotificationSent(JsonObject notificationData, Map<String, String> additionalTags) {
        NotificationTags tags = extractTags(notificationData);

        Counter.Builder builder = Counter.builder(SENT_COUNTER_NAME)
            .tag(TAG_CONNECTOR, connectorConfig.getConnectorName())
            .tag(TAG_BUNDLE, sanitizeTagValue(tags.bundle))
            .tag(TAG_APPLICATION, sanitizeTagValue(tags.application))
            .tag(TAG_EVENT_TYPE, sanitizeTagValue(tags.eventType));

        if (additionalTags != null) {
            additionalTags.forEach((key, value) -> builder.tag(key, sanitizeTagValue(value)));
        }

        builder.register(meterRegistry).increment();
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
        recordIntegrationTime(sample, notificationData, null);
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

        Timer.Builder timerBuilder = Timer.builder(INTEGRATION_TIME_TIMER_NAME)
            .tag(TAG_CONNECTOR, connectorConfig.getConnectorName())
            .tag(TAG_BUNDLE, sanitizeTagValue(tags.bundle))
            .tag(TAG_APPLICATION, sanitizeTagValue(tags.application))
            .tag(TAG_EVENT_TYPE, sanitizeTagValue(tags.eventType));

        if (additionalTags != null) {
            additionalTags.forEach((key, value) -> timerBuilder.tag(key, sanitizeTagValue(value)));
        }

        Timer timer = timerBuilder.register(meterRegistry);
        sample.stop(timer);
    }

    /**
     * Extracts bundle, application, and event_type from notification data.
     * Checks both top level and nested eventData/event_data fields.
     * Returns "unknown" for any missing field to ensure metrics are always recorded.
     */
    private NotificationTags extractTags(JsonObject notificationData) {
        String bundle = extractField(notificationData, "bundle");
        String application = extractField(notificationData, "application");
        String eventType = extractField(notificationData, "event_type");

        return new NotificationTags(bundle, application, eventType);
    }

    private String extractField(JsonObject notificationData, String fieldName) {
        // Try top level first
        String value = notificationData.getString(fieldName);
        if (value != null && !value.isEmpty()) {
            return value;
        }

        // Try nested eventData field
        JsonObject eventData = notificationData.getJsonObject("eventData");
        if (eventData != null) {
            value = eventData.getString(fieldName);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }

        // Try nested event_data field (alternative snake_case naming)
        eventData = notificationData.getJsonObject("event_data");
        if (eventData != null) {
            value = eventData.getString(fieldName);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }

        return "unknown";
    }

    /**
     * Sanitizes tag values to prevent cardinality explosion in metrics.
     * Truncates overly long values and ensures the value is safe for use as a metric tag.
     *
     * @param value the tag value to sanitize
     * @return sanitized tag value
     */
    private String sanitizeTagValue(String value) {
        if (value == null || value.isEmpty()) {
            return "unknown";
        }

        // Truncate to prevent extremely long tag values
        if (value.length() > MAX_TAG_VALUE_LENGTH) {
            return value.substring(0, MAX_TAG_VALUE_LENGTH);
        }

        return value;
    }

    private record NotificationTags(String bundle, String application, String eventType) { }
}
