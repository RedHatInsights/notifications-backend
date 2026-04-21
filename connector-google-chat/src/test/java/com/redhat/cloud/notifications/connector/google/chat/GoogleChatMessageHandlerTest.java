package com.redhat.cloud.notifications.connector.google.chat;

import com.redhat.cloud.notifications.connector.google.chat.config.GoogleChatConnectorConfig;
import com.redhat.cloud.notifications.connector.v2.http.models.HandledHttpMessageDetails;
import com.redhat.cloud.notifications.connector.v2.models.HandledMessageDetails;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static com.redhat.cloud.notifications.connector.v2.BaseConnectorIntegrationTest.buildIncomingCloudEvent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class GoogleChatMessageHandlerTest {

    @Inject
    GoogleChatMessageHandler handler;

    @InjectMock
    TemplateService templateService;

    @InjectMock
    GoogleChatConnectorConfig connectorConfig;

    @InjectMock
    @RestClient
    GoogleChatRestClient webhookRestClient;

    @Test
    void testMissingEventDataKeys() {
        // eventData with none of the optional keys (bundle, application, event_type)
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("events", java.util.List.of());

        when(connectorConfig.isUseBetaTemplatesEnabled(any(), any(), any(), any())).thenReturn(false);
        when(templateService.renderTemplate(
            argThat(td -> td.bundle() == null && td.application() == null && td.eventType() == null),
            anyMap()
        )).thenReturn("{}");

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(200);
        when(webhookRestClient.post(anyString(), anyString())).thenReturn(mockResponse);

        JsonObject payload = buildPayload("http://localhost/webhook", eventData);
        HandledMessageDetails result = handler.handle(
            buildIncomingCloudEvent("test-id", "test-type", payload)
        );

        HandledHttpMessageDetails httpDetails = (HandledHttpMessageDetails) result;
        assertEquals(200, httpDetails.httpStatus);
    }

    @Test
    void testNullEventDataValues() {
        // eventData where keys exist but values are explicitly null
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("bundle", null);
        eventData.put("application", null);
        eventData.put("event_type", null);

        when(connectorConfig.isUseBetaTemplatesEnabled(any(), any(), any(), any())).thenReturn(false);
        when(templateService.renderTemplate(
            argThat(td -> td.bundle() == null && td.application() == null && td.eventType() == null),
            anyMap()
        )).thenReturn("{}");

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(200);
        when(webhookRestClient.post(anyString(), anyString())).thenReturn(mockResponse);

        JsonObject payload = buildPayload("http://localhost/webhook", eventData);
        HandledMessageDetails result = handler.handle(
            buildIncomingCloudEvent("test-id", "test-type", payload)
        );

        HandledHttpMessageDetails httpDetails = (HandledHttpMessageDetails) result;
        assertEquals(200, httpDetails.httpStatus);
    }

    @Test
    void testPresentEventDataKeys() {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("bundle", "rhel");
        eventData.put("application", "advisor");
        eventData.put("event_type", "new-recommendation");

        when(connectorConfig.isUseBetaTemplatesEnabled(any(), any(), any(), any())).thenReturn(false);
        when(templateService.renderTemplate(
            argThat(td -> "rhel".equals(td.bundle())
                && "advisor".equals(td.application())
                && "new-recommendation".equals(td.eventType())),
            anyMap()
        )).thenReturn("{\"text\": \"hello\"}");

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(200);
        when(webhookRestClient.post(anyString(), anyString())).thenReturn(mockResponse);

        JsonObject payload = buildPayload("http://localhost/webhook", eventData);
        HandledMessageDetails result = handler.handle(
            buildIncomingCloudEvent("test-id", "test-type", payload)
        );

        HandledHttpMessageDetails httpDetails = (HandledHttpMessageDetails) result;
        assertEquals(200, httpDetails.httpStatus);
        assertEquals("http://localhost/webhook", httpDetails.targetUrl);
    }

    @Test
    void testPartialEventDataKeys() {
        // Only bundle is present, application and event_type are missing
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("bundle", "rhel");

        when(connectorConfig.isUseBetaTemplatesEnabled(any(), any(), any(), any())).thenReturn(false);
        when(templateService.renderTemplate(
            argThat(td -> "rhel".equals(td.bundle()) && td.application() == null && td.eventType() == null),
            anyMap()
        )).thenReturn("{}");

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(200);
        when(webhookRestClient.post(anyString(), anyString())).thenReturn(mockResponse);

        JsonObject payload = buildPayload("http://localhost/webhook", eventData);
        HandledMessageDetails result = handler.handle(
            buildIncomingCloudEvent("test-id", "test-type", payload)
        );

        HandledHttpMessageDetails httpDetails = (HandledHttpMessageDetails) result;
        assertEquals(200, httpDetails.httpStatus);
    }

    @Test
    void testNullWebhookUrl() {
        JsonObject payload = new JsonObject();
        payload.put("org_id", "12345");
        payload.putNull("webhookUrl");
        payload.put("eventData", new HashMap<>());

        assertThrows(ConstraintViolationException.class, () ->
            handler.handle(buildIncomingCloudEvent("test-id", "test-type", payload))
        );
    }

    @Test
    void testBlankWebhookUrl() {
        JsonObject payload = buildPayload("", new HashMap<>());

        assertThrows(ConstraintViolationException.class, () ->
            handler.handle(buildIncomingCloudEvent("test-id", "test-type", payload))
        );
    }

    @Test
    void testNullEventData() {
        JsonObject payload = new JsonObject();
        payload.put("org_id", "12345");
        payload.put("webhookUrl", "http://localhost/webhook");
        payload.putNull("eventData");

        assertThrows(ConstraintViolationException.class, () ->
            handler.handle(buildIncomingCloudEvent("test-id", "test-type", payload))
        );
    }

    @Test
    void testHttpErrorStatus() {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("bundle", "rhel");

        when(connectorConfig.isUseBetaTemplatesEnabled(any(), any(), any(), any())).thenReturn(false);
        when(templateService.renderTemplate(any(), anyMap())).thenReturn("{}");

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(500);
        when(webhookRestClient.post(anyString(), anyString())).thenReturn(mockResponse);

        JsonObject payload = buildPayload("http://localhost/webhook", eventData);
        HandledMessageDetails result = handler.handle(
            buildIncomingCloudEvent("test-id", "test-type", payload)
        );

        HandledHttpMessageDetails httpDetails = (HandledHttpMessageDetails) result;
        assertEquals(500, httpDetails.httpStatus);
        assertEquals("http://localhost/webhook", httpDetails.targetUrl);
    }

    @Test
    void testBetaTemplateEnabled() {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("bundle", "rhel");
        eventData.put("application", "advisor");
        eventData.put("event_type", "new-recommendation");

        when(connectorConfig.isUseBetaTemplatesEnabled(eq("12345"), eq("rhel"), eq("advisor"), eq("new-recommendation"))).thenReturn(true);
        when(templateService.renderTemplate(
            argThat(td -> "rhel".equals(td.bundle())
                && "advisor".equals(td.application())
                && "new-recommendation".equals(td.eventType())
                && td.isBetaVersion()),
            anyMap()
        )).thenReturn("{\"text\": \"beta\"}");

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(200);
        when(webhookRestClient.post(anyString(), anyString())).thenReturn(mockResponse);

        JsonObject payload = buildPayload("http://localhost/webhook", eventData);
        HandledMessageDetails result = handler.handle(
            buildIncomingCloudEvent("test-id", "test-type", payload)
        );

        HandledHttpMessageDetails httpDetails = (HandledHttpMessageDetails) result;
        assertEquals(200, httpDetails.httpStatus);
        verify(connectorConfig).isUseBetaTemplatesEnabled(eq("12345"), eq("rhel"), eq("advisor"), eq("new-recommendation"));
    }

    private static JsonObject buildPayload(String webhookUrl, Map<String, Object> eventData) {
        JsonObject payload = new JsonObject();
        payload.put("org_id", "12345");
        payload.put("webhookUrl", webhookUrl);
        payload.put("eventData", eventData);
        return payload;
    }
}
