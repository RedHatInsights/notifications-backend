package com.redhat.cloud.notifications.connector.v2.http;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.redhat.cloud.notifications.connector.v2.BaseConnectorIntegrationTest;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.redhat.cloud.notifications.MockServerLifecycleManager.getClient;

/**
 * Base class for connector integration tests using the new Quarkus-based architecture.
 * Replaces the old Camel-based ConnectorRoutesTest.
 */
public abstract class BaseHttpConnectorIntegrationTest extends BaseConnectorIntegrationTest {

    // Abstract methods for connector-specific implementation
    protected abstract JsonObject buildIncomingPayload(String targetUrl);

    protected boolean useHttps() {
        return false;
    }

    @Override
    protected String getConnectorSpecificTargetUrl() {
        return useHttps() ? getMockServerUrl().replace("http:", "https:") : getMockServerUrl();
    }

    @Test
    protected void testSuccessfulNotification() {
        // Mock successful HTTP response
        mockHttpResponse(getRemoteServerPath(), 200, "OK");

        String targetUrl = getConnectorSpecificTargetUrl() + getRemoteServerPath();
        JsonObject incomingPayload = buildIncomingPayload(targetUrl);

        // Send message via InMemory messaging
        String cloudEventId = sendCloudEventMessage(incomingPayload);

        // Assert successful response
        assertSuccessfulOutgoingMessage(cloudEventId, targetUrl);

        // Assert metrics
        assertMetricsIncrement(1, 1, 0);

        // Check what was actually sent to the original endpoint
        List<LoggedRequest> originalRequests = getClient().findAll(
            postRequestedFor(urlEqualTo(getRemoteServerPath()))
        );

        // Hook for subclasses
        afterSuccessfulNotification(originalRequests);
    }

    @Test
    protected void testFailedNotificationError500() {
        testFailedNotificationWithError(500, "My custom internal error", "Received: 'Server Error, status code 500'");
    }

    @Test
    protected void testFailedNotificationError404() {
        testFailedNotificationWithError(404, "Page not found", "Not Found");
    }

    protected void testFailedNotificationWithError(int statusCode, String responseBody, String expectedErrorType) {
        // Mock HTTP error response
        mockHttpError(getRemoteServerPath(), statusCode, responseBody);

        String targetUrl = getConnectorSpecificTargetUrl() + getRemoteServerPath();
        JsonObject incomingPayload = buildIncomingPayload(targetUrl);

        // Send message via InMemory messaging
        String cloudEventId = sendCloudEventMessage(incomingPayload);

        // Assert failed response
        assertFailedOutgoingMessage(cloudEventId, expectedErrorType, String.valueOf(statusCode));

        // Assert metrics
        assertMetricsIncrement(1, 0, 1);
    }

    @Test
    protected void testNetworkFailureNotification() {
        // Mock network failure
        mockNetworkFailure(getRemoteServerPath());

        String targetUrl = getConnectorSpecificTargetUrl() + getRemoteServerPath();
        JsonObject incomingPayload = buildIncomingPayload(targetUrl);

        // Send message via InMemory messaging
        String cloudEventId = sendCloudEventMessage(incomingPayload);

        // Assert failed response
        assertFailedOutgoingMessage(cloudEventId, "Connection reset");

        // Assert metrics
        assertMetricsIncrement(1, 0, 1);
    }

    // Hook methods for subclasses to override
    protected void afterSuccessfulNotification(List<LoggedRequest> loggedRequests) {
        // Override in subclasses if needed
    }
}

