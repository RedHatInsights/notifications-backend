package com.redhat.cloud.notifications.connector.teams;

import com.redhat.cloud.notifications.connector.ConnectorProcessor;
import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import com.redhat.cloud.notifications.connector.http.HttpClientService;
import com.redhat.cloud.notifications.connector.http.HttpConnectorConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.Map;

/**
 * Microsoft Teams connector implementation using the new Quarkus-based architecture.
 * This replaces the Camel-based Teams routing functionality.
 */
@ApplicationScoped
public class TeamsConnector extends ConnectorProcessor {

    private static final String TEAMS_RESPONSE_TIME_METRIC = "teams.response.time";

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

                // Transform CloudEvent to Teams message format
                JsonObject teamsMessage = transformToTeamsMessage(context);
                ConnectorResult result = sendToTeams(context, teamsMessage);

                timer.stop(meterRegistry.timer(TEAMS_RESPONSE_TIME_METRIC));

                String accountId = context.getAdditionalProperty("ACCOUNT_ID", String.class);
                Log.infof("Delivered event %s (orgId %s account %s) to Microsoft Teams %s",
                        context.getId(),
                        context.getOrgId(),
                        accountId,
                        context.getTargetUrl());

                return result;

            } catch (Exception e) {
                timer.stop(meterRegistry.timer(TEAMS_RESPONSE_TIME_METRIC));
                Log.errorf(e, "Failed to deliver event %s to Microsoft Teams", context.getId());
                throw new RuntimeException("Failed to process Teams event", e);
            }
        });
    }

    private JsonObject transformToTeamsMessage(ExceptionProcessor.ProcessingContext context) {
        JsonObject originalEvent = context.getOriginalCloudEvent();
        JsonObject teamsMessage = new JsonObject();

        // Use MessageCard format for Teams webhook
        teamsMessage.put("@type", "MessageCard");
        teamsMessage.put("@context", "https://schema.org/extensions");

        JsonObject eventData = originalEvent.getJsonObject("data");
        if (eventData != null) {
            String appName = eventData.getString("application", "Red Hat Console");
            String summary = eventData.getString("context", "Notification from Red Hat Console");

            teamsMessage.put("summary", appName + " Notification");
            teamsMessage.put("title", appName);
            teamsMessage.put("text", summary);
            teamsMessage.put("themeColor", getSeverityColor(eventData));

            // Add sections for additional information
            JsonArray sections = new JsonArray();
            JsonObject section = new JsonObject();

            JsonArray facts = new JsonArray();

            String bundleName = eventData.getString("bundle");
            String eventType = eventData.getString("event_type");
            String orgId = context.getOrgId();

            if (bundleName != null) {
                JsonObject bundleFact = new JsonObject();
                bundleFact.put("name", "Bundle");
                bundleFact.put("value", bundleName);
                facts.add(bundleFact);
            }

            if (eventType != null) {
                JsonObject eventFact = new JsonObject();
                eventFact.put("name", "Event Type");
                eventFact.put("value", eventType);
                facts.add(eventFact);
            }

            if (orgId != null) {
                JsonObject orgFact = new JsonObject();
                orgFact.put("name", "Organization");
                orgFact.put("value", orgId);
                facts.add(orgFact);
            }

            if (!facts.isEmpty()) {
                section.put("facts", facts);
                sections.add(section);
                teamsMessage.put("sections", sections);
            }
        } else {
            teamsMessage.put("summary", "Red Hat Console Notification");
            teamsMessage.put("title", "Red Hat Console");
            teamsMessage.put("text", "Notification from Red Hat Console");
            teamsMessage.put("themeColor", "0078D4");
        }

        return teamsMessage;
    }

    private String getSeverityColor(JsonObject eventData) {
        // Teams color mapping based on event severity
        String severity = eventData.getString("severity", "info");
        return switch (severity.toLowerCase()) {
            case "critical", "error" -> "FF0000";  // Red
            case "warning" -> "FFA500";           // Orange
            case "info" -> "0078D4";              // Blue
            default -> "0078D4";                  // Blue
        };
    }

    private ConnectorResult sendToTeams(ExceptionProcessor.ProcessingContext context, JsonObject teamsMessage) {
        String targetUrl = context.getTargetUrl();
        boolean trustAll = context.getAdditionalProperty("TRUST_ALL", Boolean.class) == Boolean.TRUE;

        // Prepare headers
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        // Add authentication header if available
        String authHeader = context.getAdditionalProperty("AUTHORIZATION_HEADER", String.class);
        if (authHeader != null) {
            headers.put("Authorization", authHeader);
        }

        // Send HTTP request synchronously
        var response = httpClientService.sendPost(targetUrl, teamsMessage, headers, trustAll)
                .await().indefinitely();

        if (response.isSuccessful()) {
            return new ConnectorResult(
                    true,
                    String.format("Teams message %s sent successfully", context.getId()),
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
