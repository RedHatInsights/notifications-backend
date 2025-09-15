package com.redhat.cloud.notifications.connector.pagerduty;

import com.redhat.cloud.notifications.connector.ConnectorProcessor;
import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import com.redhat.cloud.notifications.connector.http.HttpClientService;
import com.redhat.cloud.notifications.connector.http.HttpConnectorConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/**
 * PagerDuty connector implementation using the new Quarkus-based architecture.
 * This replaces the Camel-based PagerDuty routing functionality.
 */
@ApplicationScoped
public class PagerDutyConnector extends ConnectorProcessor {

    private static final String PAGERDUTY_RESPONSE_TIME_METRIC = "pagerduty.response.time";
    private static final String PAGERDUTY_API_URL = "https://events.pagerduty.com/v2/enqueue";

    @Inject
    HttpConnectorConfig connectorConfig;


    @Inject
    HttpClientService httpClientService;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    PagerDutyTransformer transformer;

    @Override
    protected Uni<ConnectorResult> processCloudEvent(ExceptionProcessor.ProcessingContext context) {
        return Uni.createFrom().item(() -> {
            Timer.Sample timer = Timer.start(meterRegistry);

            try {
                // Simple inline authentication processing
                String routingKey = context.getAdditionalProperty("ROUTING_KEY", String.class);
                if (routingKey != null) {
                    context.setAdditionalProperty("ROUTING_KEY", routingKey);
                }

                // Transform CloudEvent to PagerDuty event format
                JsonObject pagerDutyEvent = transformToPagerDutyEvent(context);
                ConnectorResult result = sendToPagerDuty(context, pagerDutyEvent);

                timer.stop(meterRegistry.timer(PAGERDUTY_RESPONSE_TIME_METRIC));

                String accountId = context.getAdditionalProperty("ACCOUNT_ID", String.class);
                Log.infof("Delivered event %s (orgId %s account %s) to PagerDuty",
                        context.getId(),
                        context.getOrgId(),
                        accountId);

                return result;

            } catch (Exception e) {
                timer.stop(meterRegistry.timer(PAGERDUTY_RESPONSE_TIME_METRIC));
                Log.errorf(e, "Failed to deliver event %s to PagerDuty", context.getId());
                throw new RuntimeException("Failed to process PagerDuty event", e);
            }
        });
    }

    private JsonObject transformToPagerDutyEvent(ExceptionProcessor.ProcessingContext context) {
        JsonObject originalEvent = context.getOriginalCloudEvent();
        JsonObject pagerDutyEvent = new JsonObject();

        // Get routing key from authentication
        String routingKey = context.getAdditionalProperty("ROUTING_KEY", String.class);
        if (routingKey != null) {
            pagerDutyEvent.put("routing_key", routingKey);
        }

        // Set event action (trigger, acknowledge, or resolve)
        String eventActionStr = context.getAdditionalProperty("EVENT_ACTION", String.class);
        PagerDutyEventAction eventAction = PagerDutyEventAction.TRIGGER; // default
        if (eventActionStr != null) {
            try {
                eventAction = PagerDutyEventAction.valueOf(eventActionStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                Log.warnf("Invalid PagerDuty event action '%s', using TRIGGER", eventActionStr);
            }
        }
        pagerDutyEvent.put("event_action", eventAction.name().toLowerCase());

        // Create dedup key from event ID and org ID
        String dedupKey = String.format("%s-%s", context.getId(), context.getOrgId());
        pagerDutyEvent.put("dedup_key", dedupKey);

        // Create payload
        JsonObject payload = new JsonObject();

        JsonObject eventData = originalEvent.getJsonObject("data");
        if (eventData != null) {
            String appName = eventData.getString(PagerDutyTransformer.APPLICATION, "Red Hat Console");
            String bundleName = eventData.getString(PagerDutyTransformer.BUNDLE, "Unknown Bundle");
            String eventType = eventData.getString(PagerDutyTransformer.EVENT_TYPE, "Unknown Event");
            String summary = eventData.getString(PagerDutyTransformer.CONTEXT, "Notification from Red Hat Console");

            payload.put("summary", String.format("%s - %s: %s", appName, bundleName, summary));
            payload.put("source", appName);
            payload.put("severity", mapSeverityToPagerDuty(eventData.getString(PagerDutyTransformer.SEVERITY, "info")));
            payload.put("timestamp", PagerDutyTransformer.PD_DATE_TIME_FORMATTER.format(Instant.now().atZone(ZoneOffset.UTC)));

            // Add custom details
            JsonObject customDetails = new JsonObject();
            customDetails.put("event_id", context.getId());
            customDetails.put("org_id", context.getOrgId());
            customDetails.put("account_id", context.getAdditionalProperty("ACCOUNT_ID", String.class));
            customDetails.put("bundle", bundleName);
            customDetails.put("application", appName);
            customDetails.put("event_type", eventType);

            // Add source names if available
            JsonObject source = eventData.getJsonObject(PagerDutyTransformer.SOURCE);
            JsonObject sourceNames = PagerDutyTransformer.getSourceNames(source);
            if (sourceNames != null) {
                customDetails.put("source_names", sourceNames);
            }

            // Add client links if available
            JsonObject clientLinks = PagerDutyTransformer.getClientLinks(eventData);
            if (clientLinks != null && !clientLinks.isEmpty()) {
                customDetails.put("client_links", clientLinks);
            }

            payload.put("custom_details", customDetails);

        } else {
            // Fallback payload
            payload.put("summary", "Red Hat Console Notification");
            payload.put("source", "Red Hat Console");
            payload.put("severity", "info");
            payload.put("timestamp", PagerDutyTransformer.PD_DATE_TIME_FORMATTER.format(Instant.now().atZone(ZoneOffset.UTC)));

            JsonObject customDetails = new JsonObject();
            customDetails.put("event_id", context.getId());
            customDetails.put("org_id", context.getOrgId());
            payload.put("custom_details", customDetails);
        }

        pagerDutyEvent.put("payload", payload);

        return pagerDutyEvent;
    }

    private String mapSeverityToPagerDuty(String severity) {
        // Map event severity to PagerDuty severity levels
        return switch (severity.toLowerCase()) {
            case "critical" -> "critical";
            case "error" -> "error";
            case "warning" -> "warning";
            case "info" -> "info";
            default -> "info";
        };
    }

    private ConnectorResult sendToPagerDuty(ExceptionProcessor.ProcessingContext context, JsonObject pagerDutyEvent) {
        // PagerDuty Events API v2 endpoint
        String targetUrl = PAGERDUTY_API_URL;
        boolean trustAll = context.getAdditionalProperty("TRUST_ALL", Boolean.class) == Boolean.TRUE;

        // Prepare headers
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        // Send HTTP request synchronously
        var response = httpClientService.sendPost(targetUrl, pagerDutyEvent, headers, trustAll)
                .await().indefinitely();

        if (response.isSuccessful()) {
            return new ConnectorResult(
                    true,
                    String.format("PagerDuty event %s sent successfully", context.getId()),
                    context.getId(),
                    context.getOrgId(),
                    context.getOriginalCloudEvent()
            );
        } else {
            return new ConnectorResult(
                    false,
                    String.format("HTTP %d: %s", response.getStatusCode(), response.getBody()),
                    context.getId(),
                    context.getOrgId(),
                    context.getOriginalCloudEvent()
            );
        }
    }
}
