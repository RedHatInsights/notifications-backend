package com.redhat.cloud.notifications.connector.webhook;

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
 * Webhook connector implementation using the new Quarkus-based architecture.
 * This replaces the Camel-based webhook routing functionality.
 */
@ApplicationScoped
public class WebhookConnector extends ConnectorProcessor {

    private static final String WEBHOOK_RESPONSE_TIME_METRIC = "webhook.response.time";

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

                // Extract payload for webhook delivery
                JsonObject payload = context.getOriginalCloudEvent();
                ConnectorResult result = sendWebhook(context, payload);

                timer.stop(meterRegistry.timer(WEBHOOK_RESPONSE_TIME_METRIC));

                String accountId = context.getAdditionalProperty("ACCOUNT_ID", String.class);
                Log.infof("Delivered event %s (orgId %s account %s) to %s",
                        context.getId(),
                        context.getOrgId(),
                        accountId,
                        context.getTargetUrl());

                return result;

            } catch (Exception e) {
                timer.stop(meterRegistry.timer(WEBHOOK_RESPONSE_TIME_METRIC));
                Log.errorf(e, "Failed to deliver event %s to webhook", context.getId());
                throw new RuntimeException("Failed to process webhook event", e);
            }
        });
    }

    private ConnectorResult sendWebhook(ExceptionProcessor.ProcessingContext context, JsonObject payload) {
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

        // Add custom headers if available
        String customHeaders = context.getAdditionalProperty("CUSTOM_HEADERS", String.class);
        if (customHeaders != null) {
            try {
                JsonObject headerJson = new JsonObject(customHeaders);
                for (String key : headerJson.fieldNames()) {
                    headers.put(key, headerJson.getString(key));
                }
            } catch (Exception e) {
                Log.warnf("Failed to parse custom headers: %s", customHeaders);
            }
        }

        // Send HTTP request synchronously
        var response = httpClientService.sendPost(targetUrl, payload, headers, trustAll)
                .await().indefinitely();

        if (response.isSuccessful()) {
            return new ConnectorResult(
                    true,
                    String.format("Webhook event %s sent successfully", context.getId()),
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
