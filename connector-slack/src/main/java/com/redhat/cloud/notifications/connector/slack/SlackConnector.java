package com.redhat.cloud.notifications.connector.slack;

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

import java.util.HashMap;
import java.util.Map;

/**
 * Slack connector implementation using the new Quarkus-based architecture.
 * This replaces the Camel-based Slack routing functionality.
 */
@ApplicationScoped
public class SlackConnector extends ConnectorProcessor {

    private static final String SLACK_RESPONSE_TIME_METRIC = "slack.response.time";

    @Inject
    HttpConnectorConfig connectorConfig;


    @Inject
    HttpClientService httpClientService;

    @Inject
    MeterRegistry meterRegistry;

    @Override
    protected Uni<ConnectorResult> processCloudEvent(ExceptionProcessor.ProcessingContext context) {
        return Uni.createFrom().item(() -> {
            Timer.Sample timer = Timer.start(meterRegistry);

            try {
                // Simple inline authentication processing
                String secretPassword = context.getAdditionalProperty("SECRET_PASSWORD", String.class);
                if (secretPassword != null) {
                    String headerValue = "Bearer " + secretPassword;
                    context.setAdditionalProperty("AUTHORIZATION_HEADER", headerValue);
                }

                // Transform CloudEvent to Slack message format
                JsonObject slackMessage = transformToSlackMessage(context);
                ConnectorResult result = sendToSlack(context, slackMessage);

                timer.stop(meterRegistry.timer(SLACK_RESPONSE_TIME_METRIC));

                String accountId = context.getAdditionalProperty("ACCOUNT_ID", String.class);
                String channel = context.getAdditionalProperty("CHANNEL", String.class);
                Log.infof("Delivered event %s (orgId %s account %s) to Slack channel %s",
                        context.getId(),
                        context.getOrgId(),
                        accountId,
                        channel);

                return result;

            } catch (Exception e) {
                timer.stop(meterRegistry.timer(SLACK_RESPONSE_TIME_METRIC));
                Log.errorf(e, "Failed to deliver event %s to Slack", context.getId());
                throw new RuntimeException("Failed to process Slack event", e);
            }
        });
    }

    private JsonObject transformToSlackMessage(ExceptionProcessor.ProcessingContext context) {
        JsonObject originalEvent = context.getOriginalCloudEvent();
        JsonObject slackMessage = new JsonObject();

        // Extract channel from context
        String channel = context.getAdditionalProperty("CHANNEL", String.class);
        if (channel != null) {
            slackMessage.put("channel", channel);
        }

        // Create message text from the event
        String text = createSlackText(originalEvent);
        slackMessage.put("text", text);

        // Add attachments or blocks for richer formatting if needed
        JsonObject attachment = new JsonObject();
        attachment.put("color", getSeverityColor(originalEvent));

        // Add event details as fields
        JsonObject eventData = originalEvent.getJsonObject("data");
        if (eventData != null) {
            String appName = eventData.getString("application");
            String bundleName = eventData.getString("bundle");
            String eventType = eventData.getString("event_type");

            if (appName != null || bundleName != null || eventType != null) {
                JsonObject fields = new JsonObject();
                if (appName != null) {
                    fields.put("Application", appName);
                }
                if (bundleName != null) {
                    fields.put("Bundle", bundleName);
                }
                if (eventType != null) {
                    fields.put("Event Type", eventType);
                }
                attachment.put("fields", fields);
            }
        }

        slackMessage.put("attachments", new JsonObject().put("0", attachment));

        return slackMessage;
    }

    private String createSlackText(JsonObject event) {
        JsonObject eventData = event.getJsonObject("data");
        if (eventData != null) {
            String appName = eventData.getString("application", "Unknown Application");
            String summary = eventData.getString("context", "Notification from Red Hat Console");
            return String.format("*%s*: %s", appName, summary);
        }
        return "Notification from Red Hat Console";
    }

    private String getSeverityColor(JsonObject event) {
        // Default color mapping based on event severity or type
        JsonObject eventData = event.getJsonObject("data");
        if (eventData != null) {
            String severity = eventData.getString("severity", "info");
            return switch (severity.toLowerCase()) {
                case "critical", "error" -> "danger";
                case "warning" -> "warning";
                case "info" -> "good";
                default -> "#36a64f";
            };
        }
        return "#36a64f";
    }

    private ConnectorResult sendToSlack(ExceptionProcessor.ProcessingContext context, JsonObject slackMessage) {
        String targetUrl = context.getTargetUrl();
        boolean trustAll = context.getAdditionalProperty("TRUST_ALL", Boolean.class) == Boolean.TRUE;

        // Prepare headers
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        // Add authentication header (webhook URL for Slack typically doesn't need auth headers)
        String authHeader = context.getAdditionalProperty("AUTHORIZATION_HEADER", String.class);
        if (authHeader != null) {
            headers.put("Authorization", authHeader);
        }

        // Send HTTP request synchronously
        var response = httpClientService.sendPost(targetUrl, slackMessage, headers, trustAll)
                .await().indefinitely();

        if (response.isSuccessful()) {
            return new ConnectorResult(
                    true,
                    String.format("Slack message %s sent successfully", context.getId()),
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
