package com.redhat.cloud.notifications.connector.webhook;

import com.redhat.cloud.notifications.connector.QuarkusConnectorBase;
import com.redhat.cloud.notifications.connector.authentication.secrets.SecretsLoader;
import com.redhat.cloud.notifications.connector.http.HttpConnectorConfig;
import com.redhat.cloud.notifications.connector.webhook.clients.WebhookClient;
import com.redhat.cloud.notifications.connector.webhook.dto.WebhookRequest;
import com.redhat.cloud.notifications.connector.webhook.dto.WebhookResponse;
import com.redhat.cloud.notifications.connector.webhook.processors.AuthenticationProcessor;
import com.redhat.cloud.notifications.connector.webhook.processors.WebhookProcessor;
import io.quarkus.logging.Log;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Quarkus-based webhook connector that replaces the Camel-based WebhookRouteBuilder.
 * This connector processes webhook notifications using native Quarkus components.
 */
@ApplicationScoped
public class WebhookQuarkusConnector extends QuarkusConnectorBase {

    @Inject
    HttpConnectorConfig connectorConfig;

    @Inject
    SecretsLoader secretsLoader;

    @Inject
    AuthenticationProcessor authenticationProcessor;

    @Inject
    WebhookProcessor webhookProcessor;

    @Inject
    @RestClient
    WebhookClient webhookClient;

    @Override
    protected ProcessingResult processConnectorSpecificLogic(CloudEventData cloudEventData, JsonObject payload) {
        try {
            Log.debugf("Processing webhook notification for orgId=%s, historyId=%s",
                cloudEventData.getOrgId(), cloudEventData.getHistoryId());

            // Extract webhook data from payload
            String targetUrl = payload.getString("target_url");
            String connectorType = cloudEventData.getConnector();

            if (targetUrl == null || targetUrl.isEmpty()) {
                Log.warnf("No target URL provided for webhook notification orgId=%s, historyId=%s",
                    cloudEventData.getOrgId(), cloudEventData.getHistoryId());
                return ProcessingResult.failure(new IllegalArgumentException("Target URL is required"));
            }

            // Load secrets and prepare authentication
            // Note: In a real implementation, you would need to adapt the SecretsLoader
            // to work with JsonObject instead of Exchange, or create a wrapper
            JsonObject processedPayload = payload; // Simplified for now
            processedPayload = authenticationProcessor.processAuthentication(processedPayload);

            // Build webhook request
            WebhookRequest webhookRequest = buildWebhookRequest(processedPayload, targetUrl);

            // Send webhook
            WebhookResponse webhookResponse = webhookClient.sendWebhook(webhookRequest);

            // Process response
            if (webhookResponse.isSuccess()) {
                Log.infof("Webhook sent successfully for orgId=%s, historyId=%s, targetUrl=%s",
                    cloudEventData.getOrgId(), cloudEventData.getHistoryId(), targetUrl);

                return ProcessingResult.success(new JsonObject()
                    .put("success", true)
                    .put("statusCode", webhookResponse.getStatusCode())
                    .put("targetUrl", targetUrl)
                    .put("connectorType", connectorType));
            } else {
                Log.errorf("Failed to send webhook for orgId=%s, historyId=%s, targetUrl=%s: %s",
                    cloudEventData.getOrgId(), cloudEventData.getHistoryId(), targetUrl,
                    webhookResponse.getErrorMessage());

                return ProcessingResult.failure(new RuntimeException(
                    "Webhook delivery failed: " + webhookResponse.getErrorMessage()));
            }

        } catch (Exception e) {
            Log.errorf(e, "Error processing webhook notification for orgId=%s, historyId=%s",
                cloudEventData.getOrgId(), cloudEventData.getHistoryId());
            return ProcessingResult.failure(e);
        }
    }

    /**
     * Build webhook request from payload and target URL.
     */
    private WebhookRequest buildWebhookRequest(JsonObject payload, String targetUrl) {
        return WebhookRequest.builder()
            .targetUrl(targetUrl)
            .payload(payload)
            .contentType("application/json; charset=utf-8")
            .trustAll(payload.getBoolean("trust_all", false))
            .build();
    }
}

