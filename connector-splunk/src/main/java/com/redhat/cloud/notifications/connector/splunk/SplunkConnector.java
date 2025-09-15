package com.redhat.cloud.notifications.connector.splunk;

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
 * Splunk connector implementation using the new Quarkus-based architecture.
 * This replaces the Camel-based SplunkRouteBuilder.
 */
@ApplicationScoped
public class SplunkConnector extends ConnectorProcessor {

    private static final String SPLUNK_RESPONSE_TIME_METRIC = "splunk.response.time";

    @Inject
    HttpConnectorConfig connectorConfig;

    @Inject
    EventsSplitter eventsSplitter;


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

                // Split events for Splunk HEC format
                String splunkPayload = eventsSplitter.splitEvents(context.getOriginalCloudEvent());
                ConnectorResult result = sendToSplunk(context, splunkPayload);

                timer.stop(meterRegistry.timer(SPLUNK_RESPONSE_TIME_METRIC));

                String accountId = context.getAdditionalProperty("ACCOUNT_ID", String.class);
                Log.infof("Delivered event %s (orgId %s account %s) to %s",
                        context.getId(),
                        context.getOrgId(),
                        accountId,
                        context.getTargetUrl());

                return result;

            } catch (Exception e) {
                timer.stop(meterRegistry.timer(SPLUNK_RESPONSE_TIME_METRIC));
                Log.errorf(e, "Failed to deliver event %s to Splunk", context.getId());
                throw new RuntimeException("Failed to process Splunk event", e);
            }
        });
    }

    private ConnectorResult sendToSplunk(ExceptionProcessor.ProcessingContext context, String payload) {
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

        JsonObject payloadJson;
        try {
            payloadJson = new JsonObject(payload);
        } catch (Exception e) {
            Log.errorf("Invalid JSON payload for Splunk: %s", payload);
            return new ConnectorResult(
                    false,
                    "Invalid JSON payload: " + e.getMessage(),
                    context.getId(),
                    context.getOrgId(),
                    context.getOriginalCloudEvent()
            );
        }

        // Send HTTP request synchronously
        var response = httpClientService.sendPost(targetUrl, payloadJson, headers, trustAll)
                .await().indefinitely();

        if (response.isSuccessful()) {
            return new ConnectorResult(
                    true,
                    String.format("Event %s sent successfully", context.getId()),
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
