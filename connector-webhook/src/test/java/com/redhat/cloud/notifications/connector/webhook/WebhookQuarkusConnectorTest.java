package com.redhat.cloud.notifications.connector.webhook;

import com.redhat.cloud.notifications.connector.QuarkusConnectorBase;
import com.redhat.cloud.notifications.connector.authentication.secrets.SecretsLoader;
import com.redhat.cloud.notifications.connector.http.HttpConnectorConfig;
import com.redhat.cloud.notifications.connector.webhook.clients.WebhookClient;
import com.redhat.cloud.notifications.connector.webhook.dto.WebhookRequest;
import com.redhat.cloud.notifications.connector.webhook.dto.WebhookResponse;
import com.redhat.cloud.notifications.connector.webhook.processors.AuthenticationProcessor;
import com.redhat.cloud.notifications.connector.webhook.processors.WebhookProcessor;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.connector.authentication.AuthenticationType.BEARER;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationType.SECRET_TOKEN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test class for WebhookQuarkusConnector.
 * Replaces the Camel-based WebhookConnectorRoutesTest.
 */
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class WebhookQuarkusConnectorTest {

    @Inject
    WebhookQuarkusConnector webhookConnector;

    @Inject
    HttpConnectorConfig connectorConfig;

    @Inject
    WebhookProcessor webhookProcessor;

    @Inject
    @RestClient
    @InjectMock
    WebhookClient webhookClient;

    @InjectMock
    SecretsLoader secretsLoader;

    @InjectMock
    AuthenticationProcessor authenticationProcessor;

    @Test
    void testWebhookResponseStructure() {
        // Test the WebhookResponse DTO structure
        WebhookResponse response = new WebhookResponse(true, 200, null, "OK");

        assertTrue(response.isSuccess());
        assertEquals(200, response.getStatusCode());
        assertNull(response.getErrorMessage());
        assertEquals("OK", response.getResponseBody());
    }

    @Test
    void testWebhookRequestStructure() {
        // Test the WebhookRequest DTO structure
        WebhookRequest request = new WebhookRequest();
        request.setTargetUrl("https://example.com/webhook");
        JsonObject payload = new JsonObject();
        payload.put("message", "Test webhook");
        request.setPayload(payload);
        request.setContentType("application/json");
        request.setTrustAll(false);

        assertEquals("https://example.com/webhook", request.getTargetUrl());
        assertEquals(payload, request.getPayload());
        assertEquals("application/json", request.getContentType());
        assertFalse(request.isTrustAll());
    }

    @Test
    void testWebhookResponseFailure() {
        // Test the WebhookResponse DTO for failure cases
        WebhookResponse response = new WebhookResponse(false, 500, "Internal Server Error", null);

        assertFalse(response.isSuccess());
        assertEquals(500, response.getStatusCode());
        assertEquals("Internal Server Error", response.getErrorMessage());
        assertNull(response.getResponseBody());
    }

    @Test
    void testWebhookClientMock() {
        // Test that the WebhookClient mock is properly configured
        WebhookResponse mockResponse = new WebhookResponse(true, 200, null, "OK");
        when(webhookClient.sendWebhook(any(WebhookRequest.class))).thenReturn(mockResponse);

        WebhookRequest request = new WebhookRequest();
        request.setTargetUrl("https://example.com/webhook");
        JsonObject payload = new JsonObject();
        payload.put("message", "Test message");
        request.setPayload(payload);

        WebhookResponse response = webhookClient.sendWebhook(request);

        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals(200, response.getStatusCode());
        verify(webhookClient).sendWebhook(any(WebhookRequest.class));
    }

    @Test
    void testWebhookConnectorProcessing() {
        // Test the basic processing logic of the webhook connector
        JsonObject payload = new JsonObject();
        payload.put("target_url", "https://example.com/webhook");
        payload.put("message", "Test webhook notification");

        // Create a mock CloudEventData
        QuarkusConnectorBase.CloudEventData cloudEventData = new QuarkusConnectorBase.CloudEventData(
            "test-org-id", "test-history-id", "webhook", payload);

        // Mock the WebhookClient behavior
        WebhookResponse mockResponse = new WebhookResponse(true, 200, null, "OK");
        when(webhookClient.sendWebhook(any(WebhookRequest.class))).thenReturn(mockResponse);
        when(authenticationProcessor.processAuthentication(any(JsonObject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Process the notification
        QuarkusConnectorBase.ProcessingResult result = webhookConnector.processConnectorSpecificLogic(cloudEventData, payload);

        // Verify the result
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNull(result.getError());
        verify(webhookClient, times(1)).sendWebhook(any(WebhookRequest.class));
        verify(authenticationProcessor, times(1)).processAuthentication(any(JsonObject.class));
    }

    @Test
    void testWebhookConnectorErrorHandling() {
        // Test error handling by passing null target URL
        JsonObject payload = new JsonObject();
        payload.put("target_url", ""); // Empty target URL should trigger error

        // Create a mock CloudEventData
        QuarkusConnectorBase.CloudEventData cloudEventData = new QuarkusConnectorBase.CloudEventData(
            "test-org-id", "test-history-id", "webhook", payload);

        // Process the notification
        QuarkusConnectorBase.ProcessingResult result = webhookConnector.processConnectorSpecificLogic(cloudEventData, payload);

        // Verify the result shows failure due to missing target URL
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
        assertTrue(result.getError().getMessage().contains("Target URL is required"));
        verify(webhookClient, never()).sendWebhook(any(WebhookRequest.class));
    }

    @Test
    void testWebhookProcessorIntegration() {
        // Test the WebhookProcessor directly
        JsonObject testPayload = new JsonObject()
            .put("org_id", "test-org-id")
            .put("target_url", "https://example.com/webhook")
            .put("message", "test message");

        // When
        JsonObject processedPayload = webhookProcessor.processWebhookPayload(testPayload);

        // Then
        assertNotNull(processedPayload);
        assertFalse(processedPayload.containsKey("org_id"));
        assertTrue(processedPayload.containsKey("target_url")); // target_url is kept
        assertTrue(processedPayload.containsKey("webhook_timestamp"));
        assertTrue(processedPayload.containsKey("webhook_version"));
        assertEquals("test message", processedPayload.getString("message"));
    }

    @Test
    void testWebhookTargetUrlValidation() {
        // Test valid URLs
        assertTrue(webhookProcessor.isValidTargetUrl("https://example.com/webhook"));
        assertTrue(webhookProcessor.isValidTargetUrl("http://localhost:8080/webhook"));

        // Test invalid URLs
        assertFalse(webhookProcessor.isValidTargetUrl(null));
        assertFalse(webhookProcessor.isValidTargetUrl(""));
        assertFalse(webhookProcessor.isValidTargetUrl("invalid-url"));
        assertFalse(webhookProcessor.isValidTargetUrl("ftp://example.com"));
    }

    @Test
    void testWebhookWithAuthentication() {
        // Test webhook processing with authentication
        JsonObject payload = new JsonObject();
        payload.put("target_url", "https://example.com/webhook");
        payload.put("message", "Test webhook with auth");
        JsonObject authentication = new JsonObject();
        authentication.put("type", SECRET_TOKEN.name());
        authentication.put("secretId", 123L);
        payload.put("authentication", authentication);

        // Create a mock CloudEventData
        QuarkusConnectorBase.CloudEventData cloudEventData = new QuarkusConnectorBase.CloudEventData(
            "test-org-id", "test-history-id", "webhook", payload);

        // Mock the WebhookClient behavior
        WebhookResponse mockResponse = new WebhookResponse(true, 200, null, "OK");
        when(webhookClient.sendWebhook(any(WebhookRequest.class))).thenReturn(mockResponse);
        when(authenticationProcessor.processAuthentication(any(JsonObject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Process the notification
        QuarkusConnectorBase.ProcessingResult result = webhookConnector.processConnectorSpecificLogic(cloudEventData, payload);

        // Verify the result
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNull(result.getError());
        verify(authenticationProcessor, times(1)).processAuthentication(any(JsonObject.class));
    }

    @Test
    void testWebhookWithBearerToken() {
        // Test webhook processing with Bearer token authentication
        JsonObject payload = new JsonObject();
        payload.put("target_url", "https://example.com/webhook");
        payload.put("message", "Test webhook with Bearer token");
        JsonObject authentication = new JsonObject();
        authentication.put("type", BEARER.name());
        authentication.put("secretId", 456L);
        payload.put("authentication", authentication);

        // Create a mock CloudEventData
        QuarkusConnectorBase.CloudEventData cloudEventData = new QuarkusConnectorBase.CloudEventData(
            "test-org-id", "test-history-id", "webhook", payload);

        // Mock the WebhookClient behavior
        WebhookResponse mockResponse = new WebhookResponse(true, 200, null, "OK");
        when(webhookClient.sendWebhook(any(WebhookRequest.class))).thenReturn(mockResponse);
        when(authenticationProcessor.processAuthentication(any(JsonObject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Process the notification
        QuarkusConnectorBase.ProcessingResult result = webhookConnector.processConnectorSpecificLogic(cloudEventData, payload);

        // Verify the result
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNull(result.getError());
        verify(authenticationProcessor, times(1)).processAuthentication(any(JsonObject.class));
    }

    @Test
    void testWebhookWithTrustAllSsl() {
        // Test webhook processing with trust all SSL
        JsonObject payload = new JsonObject();
        payload.put("target_url", "https://example.com/webhook");
        payload.put("message", "Test webhook with trust all SSL");
        payload.put("trust_all", true);

        // Create a mock CloudEventData
        QuarkusConnectorBase.CloudEventData cloudEventData = new QuarkusConnectorBase.CloudEventData(
            "test-org-id", "test-history-id", "webhook", payload);

        // Mock the WebhookClient behavior
        WebhookResponse mockResponse = new WebhookResponse(true, 200, null, "OK");
        when(webhookClient.sendWebhook(any(WebhookRequest.class))).thenReturn(mockResponse);
        when(authenticationProcessor.processAuthentication(any(JsonObject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Process the notification
        QuarkusConnectorBase.ProcessingResult result = webhookConnector.processConnectorSpecificLogic(cloudEventData, payload);

        // Verify the result
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNull(result.getError());

        // Verify that the WebhookRequest was created with trustAll = true
        verify(webhookClient, times(1)).sendWebhook(argThat(request ->
            request.isTrustAll()
        ));
    }

    @Test
    void testWebhookDeliveryFailure() {
        // Test webhook delivery failure
        JsonObject payload = new JsonObject();
        payload.put("target_url", "https://example.com/webhook");
        payload.put("message", "Test webhook failure");

        // Create a mock CloudEventData
        QuarkusConnectorBase.CloudEventData cloudEventData = new QuarkusConnectorBase.CloudEventData(
            "test-org-id", "test-history-id", "webhook", payload);

        // Mock the WebhookClient behavior to return failure
        WebhookResponse mockResponse = new WebhookResponse(false, 500, "Internal Server Error", null);
        when(webhookClient.sendWebhook(any(WebhookRequest.class))).thenReturn(mockResponse);
        when(authenticationProcessor.processAuthentication(any(JsonObject.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Process the notification
        QuarkusConnectorBase.ProcessingResult result = webhookConnector.processConnectorSpecificLogic(cloudEventData, payload);

        // Verify the result shows failure
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertNotNull(result.getError());
        assertTrue(result.getError().getMessage().contains("Webhook delivery failed"));
        verify(webhookClient, times(1)).sendWebhook(any(WebhookRequest.class));
    }
}
