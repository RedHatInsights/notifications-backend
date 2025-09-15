package com.redhat.cloud.notifications.connector.google.chat;

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
 * Google Chat connector implementation using the new Quarkus-based architecture.
 * This replaces the Camel-based Google Chat routing functionality.
 */
@ApplicationScoped
public class GoogleChatConnector extends ConnectorProcessor {

    private static final String GOOGLE_CHAT_RESPONSE_TIME_METRIC = "google_chat.response.time";

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

                // Transform CloudEvent to Google Chat message format
                JsonObject chatMessage = transformToGoogleChatMessage(context);
                ConnectorResult result = sendToGoogleChat(context, chatMessage);

                timer.stop(meterRegistry.timer(GOOGLE_CHAT_RESPONSE_TIME_METRIC));

                String accountId = context.getAdditionalProperty("ACCOUNT_ID", String.class);
                Log.infof("Delivered event %s (orgId %s account %s) to Google Chat %s",
                        context.getId(),
                        context.getOrgId(),
                        accountId,
                        context.getTargetUrl());

                return result;

            } catch (Exception e) {
                timer.stop(meterRegistry.timer(GOOGLE_CHAT_RESPONSE_TIME_METRIC));
                Log.errorf(e, "Failed to deliver event %s to Google Chat", context.getId());
                throw new RuntimeException("Failed to process Google Chat event", e);
            }
        });
    }

    private JsonObject transformToGoogleChatMessage(ExceptionProcessor.ProcessingContext context) {
        JsonObject originalEvent = context.getOriginalCloudEvent();
        JsonObject chatMessage = new JsonObject();

        // Create Google Chat card format message
        String text = createGoogleChatText(originalEvent);
        chatMessage.put("text", text);

        // Create a card with sections for better formatting
        JsonObject card = new JsonObject();
        JsonArray sections = new JsonArray();

        JsonObject section = new JsonObject();
        JsonObject header = new JsonObject();

        JsonObject eventData = originalEvent.getJsonObject("data");
        if (eventData != null) {
            String appName = eventData.getString("application", "Red Hat Console");
            header.put("title", appName);
            header.put("subtitle", "Notification");
            header.put("imageUrl", "https://console.redhat.com/favicon.ico");
            section.put("header", header);

            // Add widgets for event details
            JsonArray widgets = new JsonArray();

            JsonObject textWidget = new JsonObject();
            JsonObject textParagraph = new JsonObject();
            textParagraph.put("text", eventData.getString("context", "Notification from Red Hat Console"));
            textWidget.put("textParagraph", textParagraph);
            widgets.add(textWidget);

            // Add metadata as key-value pairs
            String bundleName = eventData.getString("bundle");
            String eventType = eventData.getString("event_type");

            if (bundleName != null || eventType != null) {
                JsonObject keyValueWidget = new JsonObject();
                JsonArray keyValuePairs = new JsonArray();

                if (bundleName != null) {
                    JsonObject bundleKv = new JsonObject();
                    bundleKv.put("topLabel", "Bundle");
                    bundleKv.put("content", bundleName);
                    keyValuePairs.add(bundleKv);
                }

                if (eventType != null) {
                    JsonObject eventKv = new JsonObject();
                    eventKv.put("topLabel", "Event Type");
                    eventKv.put("content", eventType);
                    keyValuePairs.add(eventKv);
                }

                keyValueWidget.put("keyValue", keyValuePairs.getJsonObject(0));
                widgets.add(keyValueWidget);
            }

            section.put("widgets", widgets);
        }

        sections.add(section);
        card.put("sections", sections);

        JsonArray cards = new JsonArray();
        cards.add(card);
        chatMessage.put("cards", cards);

        return chatMessage;
    }

    private String createGoogleChatText(JsonObject event) {
        JsonObject eventData = event.getJsonObject("data");
        if (eventData != null) {
            String appName = eventData.getString("application", "Unknown Application");
            String summary = eventData.getString("context", "Notification from Red Hat Console");
            return String.format("%s: %s", appName, summary);
        }
        return "Notification from Red Hat Console";
    }

    private ConnectorResult sendToGoogleChat(ExceptionProcessor.ProcessingContext context, JsonObject chatMessage) {
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
        var response = httpClientService.sendPost(targetUrl, chatMessage, headers, trustAll)
                .await().indefinitely();

        if (response.isSuccessful()) {
            return new ConnectorResult(
                    true,
                    String.format("Google Chat message %s sent successfully", context.getId()),
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
