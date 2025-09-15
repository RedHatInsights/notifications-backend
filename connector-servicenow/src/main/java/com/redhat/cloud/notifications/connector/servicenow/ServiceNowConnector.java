package com.redhat.cloud.notifications.connector.servicenow;

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
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * ServiceNow connector implementation using the new Quarkus-based architecture.
 * This replaces the Camel-based ServiceNow routing functionality.
 */
@ApplicationScoped
public class ServiceNowConnector extends ConnectorProcessor {

    private static final String SERVICENOW_RESPONSE_TIME_METRIC = "servicenow.response.time";
    private static final DateTimeFormatter SERVICENOW_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

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

                // Transform CloudEvent to ServiceNow incident format
                JsonObject serviceNowPayload = transformToServiceNowIncident(context);
                ConnectorResult result = sendToServiceNow(context, serviceNowPayload);

                timer.stop(meterRegistry.timer(SERVICENOW_RESPONSE_TIME_METRIC));

                String accountId = context.getAdditionalProperty("ACCOUNT_ID", String.class);
                Log.infof("Delivered event %s (orgId %s account %s) to ServiceNow %s",
                        context.getId(),
                        context.getOrgId(),
                        accountId,
                        context.getTargetUrl());

                return result;

            } catch (Exception e) {
                timer.stop(meterRegistry.timer(SERVICENOW_RESPONSE_TIME_METRIC));
                Log.errorf(e, "Failed to deliver event %s to ServiceNow", context.getId());
                throw new RuntimeException("Failed to process ServiceNow event", e);
            }
        });
    }

    private JsonObject transformToServiceNowIncident(ExceptionProcessor.ProcessingContext context) {
        JsonObject originalEvent = context.getOriginalCloudEvent();
        JsonObject incident = new JsonObject();

        JsonObject eventData = originalEvent.getJsonObject("data");
        if (eventData != null) {
            String appName = eventData.getString("application", "Red Hat Console");
            String bundleName = eventData.getString("bundle", "Unknown Bundle");
            String eventType = eventData.getString("event_type", "Unknown Event");
            String summary = eventData.getString("context", "Notification from Red Hat Console");

            // ServiceNow incident fields
            incident.put("short_description", String.format("%s - %s: %s", appName, bundleName, eventType));
            incident.put("description", summary);
            incident.put("urgency", mapSeverityToUrgency(eventData.getString("severity", "info")));
            incident.put("impact", "3"); // Default to low impact
            incident.put("category", "Software");
            incident.put("subcategory", "Application");
            incident.put("caller_id", "Red Hat Console");
            incident.put("opened_at", SERVICENOW_DATE_FORMAT.format(Instant.now()));

            // Add custom fields for tracking
            incident.put("u_event_id", context.getId());
            incident.put("u_org_id", context.getOrgId());
            incident.put("u_account_id", context.getAdditionalProperty("ACCOUNT_ID", String.class));
            incident.put("u_source_application", appName);
            incident.put("u_source_bundle", bundleName);
            incident.put("u_event_type", eventType);

            // Add assignment group if configured
            String assignmentGroup = context.getAdditionalProperty("ASSIGNMENT_GROUP", String.class);
            if (assignmentGroup != null) {
                incident.put("assignment_group", assignmentGroup);
            }

        } else {
            // Fallback incident data
            incident.put("short_description", "Red Hat Console Notification");
            incident.put("description", "Notification from Red Hat Console");
            incident.put("urgency", "3");
            incident.put("impact", "3");
            incident.put("category", "Software");
            incident.put("subcategory", "Application");
            incident.put("caller_id", "Red Hat Console");
            incident.put("opened_at", SERVICENOW_DATE_FORMAT.format(Instant.now()));
            incident.put("u_event_id", context.getId());
            incident.put("u_org_id", context.getOrgId());
        }

        return incident;
    }

    private String mapSeverityToUrgency(String severity) {
        // Map event severity to ServiceNow urgency levels
        return switch (severity.toLowerCase()) {
            case "critical" -> "1"; // High
            case "error" -> "2";    // Medium
            case "warning" -> "3";  // Low
            case "info" -> "3";     // Low
            default -> "3";         // Low
        };
    }

    private ConnectorResult sendToServiceNow(ExceptionProcessor.ProcessingContext context, JsonObject incident) {
        String targetUrl = context.getTargetUrl();
        boolean trustAll = context.getAdditionalProperty("TRUST_ALL", Boolean.class) == Boolean.TRUE;

        // Ensure the URL points to the incident API
        if (!targetUrl.contains("/api/now/table/incident")) {
            if (targetUrl.endsWith("/")) {
                targetUrl = targetUrl + "api/now/table/incident";
            } else {
                targetUrl = targetUrl + "/api/now/table/incident";
            }
        }

        // Prepare headers
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");

        // Add authentication header (required for ServiceNow)
        String authHeader = context.getAdditionalProperty("AUTHORIZATION_HEADER", String.class);
        if (authHeader != null) {
            headers.put("Authorization", authHeader);
        } else {
            return new ConnectorResult(
                    false,
                    "ServiceNow integration requires authentication",
                    context.getId(),
                    context.getOrgId(),
                    context.getOriginalCloudEvent()
            );
        }

        // Send HTTP request synchronously
        var response = httpClientService.sendPost(targetUrl, incident, headers, trustAll)
                .await().indefinitely();

        if (response.isSuccessful()) {
            return new ConnectorResult(
                    true,
                    String.format("ServiceNow incident %s created successfully", context.getId()),
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
