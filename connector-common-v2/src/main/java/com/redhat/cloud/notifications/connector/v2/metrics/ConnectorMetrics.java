package com.redhat.cloud.notifications.connector.v2.metrics;

import com.redhat.cloud.notifications.connector.v2.ConnectorConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
     * @param tags the notification tags containing bundle, application, and event_type
     */
    public void recordNotificationSent(NotificationTags tags) {
        recordNotificationSent(tags, null);
    }

    /**
     * Records that a notification was successfully sent through a connector with additional custom tags.
     *
     * @param tags the notification tags containing bundle, application, and event_type
     * @param additionalTags additional tags to include in the metric (e.g., subscription_type)
     */
    public void recordNotificationSent(NotificationTags tags, Map<String, String> additionalTags) {
        Counter.Builder builder = Counter.builder(SENT_COUNTER_NAME)
            .tag(TAG_CONNECTOR, connectorConfig.getConnectorName())
            .tag(TAG_BUNDLE, sanitizeTagValue(tags.bundle()))
            .tag(TAG_APPLICATION, sanitizeTagValue(tags.application()))
            .tag(TAG_EVENT_TYPE, sanitizeTagValue(tags.eventType()));

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
     * @param integrationTimer the Timer.Sample started before the integration began
     * @param tags the notification tags containing bundle, application, and event_type
     */
    public void recordIntegrationTime(Timer.Sample integrationTimer, NotificationTags tags) {
        recordIntegrationTime(integrationTimer, tags, null);
    }

    /**
     * Records the integration time for a connector with additional custom tags.
     *
     * @param integrationTimer the Timer.Sample started before the integration began
     * @param tags the notification tags containing bundle, application, and event_type
     * @param additionalTags additional tags to include in the metric
     */
    public void recordIntegrationTime(Timer.Sample integrationTimer, NotificationTags tags, Map<String, String> additionalTags) {
        Timer.Builder timerBuilder = Timer.builder(INTEGRATION_TIME_TIMER_NAME)
            .tag(TAG_CONNECTOR, connectorConfig.getConnectorName())
            .tag(TAG_BUNDLE, sanitizeTagValue(tags.bundle()))
            .tag(TAG_APPLICATION, sanitizeTagValue(tags.application()))
            .tag(TAG_EVENT_TYPE, sanitizeTagValue(tags.eventType()));

        if (additionalTags != null) {
            additionalTags.forEach((key, value) -> timerBuilder.tag(key, sanitizeTagValue(value)));
        }

        integrationTimer.stop(timerBuilder.register(meterRegistry));
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
}
