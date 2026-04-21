package com.redhat.cloud.notifications.connector.splunk;

import com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationLoader;
import com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationResult;
import com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationType;
import com.redhat.cloud.notifications.connector.authentication.v2.sources.SourcesSecretResponse;
import com.redhat.cloud.notifications.connector.v2.TestLifecycleManager;
import com.redhat.cloud.notifications.connector.v2.http.models.HandledHttpMessageDetails;
import com.redhat.cloud.notifications.connector.v2.models.HandledMessageDetails;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.connector.splunk.SplunkMessageHandler.SERVICES_COLLECTOR_EVENT;
import static com.redhat.cloud.notifications.connector.v2.BaseConnectorIntegrationTest.buildIncomingCloudEvent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class SplunkMessageHandlerTest {

    @Inject
    SplunkMessageHandler handler;

    @InjectMock
    AuthenticationLoader authenticationLoader;

    @InjectMock
    @RestClient
    SplunkRestClient splunkRestClient;

    @Test
    void testSuccessfulDeliveryWithSingleEvent() {
        mockAuthentication();

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(200);
        when(splunkRestClient.post(eq("Splunk my-token"), anyString(), anyString())).thenReturn(mockResponse);

        JsonObject payload = buildPayload("https://splunk.example.com",
            JsonArray.of(JsonObject.of("key", "value")));
        HandledMessageDetails result = handler.handle(
            buildIncomingCloudEvent("test-id", "test-type", payload)
        );

        HandledHttpMessageDetails httpDetails = (HandledHttpMessageDetails) result;
        assertEquals(200, httpDetails.httpStatus);
        assertTrue(httpDetails.targetUrl.endsWith(SERVICES_COLLECTOR_EVENT));
    }

    @Test
    void testSuccessfulDeliveryWithMultipleEvents() {
        mockAuthentication();

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(200);
        when(splunkRestClient.post(eq("Splunk my-token"), anyString(), anyString())).thenReturn(mockResponse);

        JsonObject payload = buildPayload("https://splunk.example.com",
            JsonArray.of(
                JsonObject.of("event-1-key", "event-1-value"),
                JsonObject.of("event-2-key", "event-2-value")
            ));
        HandledMessageDetails result = handler.handle(
            buildIncomingCloudEvent("test-id", "test-type", payload)
        );

        HandledHttpMessageDetails httpDetails = (HandledHttpMessageDetails) result;
        assertEquals(200, httpDetails.httpStatus);

        // Verify the payload was split and concatenated
        verify(splunkRestClient).post(eq("Splunk my-token"),
            eq("https://splunk.example.com" + SERVICES_COLLECTOR_EVENT),
            anyString());
    }

    @Test
    void testUrlFixingAddsServicesCollectorEvent() {
        mockAuthentication();

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(200);
        when(splunkRestClient.post(anyString(), anyString(), anyString())).thenReturn(mockResponse);

        JsonObject payload = buildPayload("https://splunk.example.com", JsonArray.of(JsonObject.of("k", "v")));
        handler.handle(buildIncomingCloudEvent("test-id", "test-type", payload));

        verify(splunkRestClient).post(anyString(),
            eq("https://splunk.example.com" + SERVICES_COLLECTOR_EVENT),
            anyString());
    }

    @Test
    void testUrlFixingWithTrailingSlash() {
        mockAuthentication();

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(200);
        when(splunkRestClient.post(anyString(), anyString(), anyString())).thenReturn(mockResponse);

        JsonObject payload = buildPayload("https://splunk.example.com/", JsonArray.of(JsonObject.of("k", "v")));
        handler.handle(buildIncomingCloudEvent("test-id", "test-type", payload));

        verify(splunkRestClient).post(anyString(),
            eq("https://splunk.example.com" + SERVICES_COLLECTOR_EVENT),
            anyString());
    }

    @Test
    void testUrlFixingWithRawPath() {
        mockAuthentication();

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(200);
        when(splunkRestClient.post(anyString(), anyString(), anyString())).thenReturn(mockResponse);

        JsonObject payload = buildPayload("https://splunk.example.com/services/collector/raw",
            JsonArray.of(JsonObject.of("k", "v")));
        handler.handle(buildIncomingCloudEvent("test-id", "test-type", payload));

        verify(splunkRestClient).post(anyString(),
            eq("https://splunk.example.com" + SERVICES_COLLECTOR_EVENT),
            anyString());
    }

    @Test
    void testUrlWithServicesCollectorOnly() {
        mockAuthentication();

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(200);
        when(splunkRestClient.post(anyString(), anyString(), anyString())).thenReturn(mockResponse);

        JsonObject payload = buildPayload("https://splunk.example.com/services/collector",
            JsonArray.of(JsonObject.of("k", "v")));
        handler.handle(buildIncomingCloudEvent("test-id", "test-type", payload));

        verify(splunkRestClient).post(anyString(),
            eq("https://splunk.example.com" + SERVICES_COLLECTOR_EVENT),
            anyString());
    }

    @Test
    void testUrlAlreadyCorrect() {
        mockAuthentication();

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(200);
        when(splunkRestClient.post(anyString(), anyString(), anyString())).thenReturn(mockResponse);

        JsonObject payload = buildPayload("https://splunk.example.com/services/collector/event",
            JsonArray.of(JsonObject.of("k", "v")));
        handler.handle(buildIncomingCloudEvent("test-id", "test-type", payload));

        verify(splunkRestClient).post(anyString(),
            eq("https://splunk.example.com" + SERVICES_COLLECTOR_EVENT),
            anyString());
    }

    @Test
    void testNullMetadataIsRejected() {
        JsonObject payload = new JsonObject();
        payload.put("org_id", DEFAULT_ORG_ID);
        payload.put("account_id", DEFAULT_ACCOUNT_ID);
        payload.putNull("notif-metadata");

        assertThrows(ConstraintViolationException.class, () ->
            handler.handle(buildIncomingCloudEvent("test-id", "test-type", payload))
        );
    }

    @Test
    void testBearerAuthIsRejected() {
        SourcesSecretResponse secretResponse = new SourcesSecretResponse();
        secretResponse.password = "my-token";
        when(authenticationLoader.fetchAuthenticationData(anyString(), any(JsonObject.class)))
            .thenReturn(Optional.of(new AuthenticationResult(secretResponse, AuthenticationType.BEARER)));

        JsonObject payload = buildPayload("https://splunk.example.com", JsonArray.of(JsonObject.of("k", "v")));

        assertThrows(IllegalStateException.class, () ->
            handler.handle(buildIncomingCloudEvent("test-id", "test-type", payload))
        );
    }

    @Test
    void testHttpErrorStatus() {
        mockAuthentication();

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(500);
        when(splunkRestClient.post(anyString(), anyString(), anyString())).thenReturn(mockResponse);

        JsonObject payload = buildPayload("https://splunk.example.com",
            JsonArray.of(JsonObject.of("k", "v")));
        HandledMessageDetails result = handler.handle(
            buildIncomingCloudEvent("test-id", "test-type", payload)
        );

        HandledHttpMessageDetails httpDetails = (HandledHttpMessageDetails) result;
        assertEquals(500, httpDetails.httpStatus);
    }

    @Test
    void testMissingEventsFieldIsRejected() {
        // Build payload without events key — @NotNull on events should trigger validation failure
        JsonObject authentication = new JsonObject();
        authentication.put("type", AuthenticationType.SECRET_TOKEN.name());
        authentication.put("secretId", 123L);

        JsonObject metadata = new JsonObject();
        metadata.put("url", "https://splunk.example.com");
        metadata.put("authentication", authentication);

        JsonObject payload = new JsonObject();
        payload.put("notif-metadata", metadata);
        payload.put("org_id", DEFAULT_ORG_ID);
        payload.put("account_id", DEFAULT_ACCOUNT_ID);

        assertThrows(ConstraintViolationException.class, () ->
            handler.handle(buildIncomingCloudEvent("test-id", "test-type", payload))
        );
    }

    // --- H3: URL protocol validation tests ---

    @Test
    void testHttpProtocolIsRejected() {
        mockAuthentication();
        JsonObject payload = buildPayload("http://splunk.example.com", JsonArray.of(JsonObject.of("k", "v")));

        assertThrows(IllegalArgumentException.class, () ->
            handler.handle(buildIncomingCloudEvent("test-id", "test-type", payload))
        );
    }

    @Test
    void testFtpProtocolIsRejected() {
        mockAuthentication();
        JsonObject payload = buildPayload("ftp://splunk.example.com", JsonArray.of(JsonObject.of("k", "v")));

        assertThrows(IllegalArgumentException.class, () ->
            handler.handle(buildIncomingCloudEvent("test-id", "test-type", payload))
        );
    }

    @Test
    void testMalformedUrlIsRejected() {
        mockAuthentication();
        JsonObject payload = buildPayload("not-a-valid-url", JsonArray.of(JsonObject.of("k", "v")));

        assertThrows(IllegalArgumentException.class, () ->
            handler.handle(buildIncomingCloudEvent("test-id", "test-type", payload))
        );
    }

    @Test
    void testNullUrlIsRejected() {
        mockAuthentication();

        JsonObject authentication = new JsonObject();
        authentication.put("type", AuthenticationType.SECRET_TOKEN.name());
        authentication.put("secretId", 123L);

        JsonObject metadata = new JsonObject();
        metadata.putNull("url");
        metadata.put("authentication", authentication);

        JsonObject payload = new JsonObject();
        payload.put("notif-metadata", metadata);
        payload.put("org_id", DEFAULT_ORG_ID);
        payload.put("account_id", DEFAULT_ACCOUNT_ID);
        payload.put("events", JsonArray.of(JsonObject.of("k", "v")));

        assertThrows(IllegalArgumentException.class, () ->
            handler.handle(buildIncomingCloudEvent("test-id", "test-type", payload))
        );
    }

    @Test
    void testEmptyUrlIsRejected() {
        mockAuthentication();

        JsonObject authentication = new JsonObject();
        authentication.put("type", AuthenticationType.SECRET_TOKEN.name());
        authentication.put("secretId", 123L);

        JsonObject metadata = new JsonObject();
        metadata.put("url", "");
        metadata.put("authentication", authentication);

        JsonObject payload = new JsonObject();
        payload.put("notif-metadata", metadata);
        payload.put("org_id", DEFAULT_ORG_ID);
        payload.put("account_id", DEFAULT_ACCOUNT_ID);
        payload.put("events", JsonArray.of(JsonObject.of("k", "v")));

        assertThrows(IllegalArgumentException.class, () ->
            handler.handle(buildIncomingCloudEvent("test-id", "test-type", payload))
        );
    }

    @Test
    void testHttpsWithPortIsAccepted() {
        mockAuthentication();

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(200);
        when(splunkRestClient.post(anyString(), anyString(), anyString())).thenReturn(mockResponse);

        JsonObject payload = buildPayload("https://splunk.example.com:8088", JsonArray.of(JsonObject.of("k", "v")));
        HandledMessageDetails result = handler.handle(
            buildIncomingCloudEvent("test-id", "test-type", payload)
        );

        HandledHttpMessageDetails httpDetails = (HandledHttpMessageDetails) result;
        assertEquals(200, httpDetails.httpStatus);
    }

    @Test
    void testHttpsWithIpAddressIsAccepted() {
        mockAuthentication();

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(200);
        when(splunkRestClient.post(anyString(), anyString(), anyString())).thenReturn(mockResponse);

        JsonObject payload = buildPayload("https://123.123.123.123:8088", JsonArray.of(JsonObject.of("k", "v")));
        HandledMessageDetails result = handler.handle(
            buildIncomingCloudEvent("test-id", "test-type", payload)
        );

        HandledHttpMessageDetails httpDetails = (HandledHttpMessageDetails) result;
        assertEquals(200, httpDetails.httpStatus);
    }

    @Test
    void testHttpsWithLocalhostIsAccepted() {
        mockAuthentication();

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(200);
        when(splunkRestClient.post(anyString(), anyString(), anyString())).thenReturn(mockResponse);

        JsonObject payload = buildPayload("https://localhost:8088", JsonArray.of(JsonObject.of("k", "v")));
        HandledMessageDetails result = handler.handle(
            buildIncomingCloudEvent("test-id", "test-type", payload)
        );

        HandledHttpMessageDetails httpDetails = (HandledHttpMessageDetails) result;
        assertEquals(200, httpDetails.httpStatus);
    }

    @Test
    void testHttpsWithLocalhostAndPathIsAccepted() {
        mockAuthentication();

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(200);
        when(splunkRestClient.post(anyString(), anyString(), anyString())).thenReturn(mockResponse);

        JsonObject payload = buildPayload("https://localhost/services/collector/event",
            JsonArray.of(JsonObject.of("k", "v")));
        handler.handle(buildIncomingCloudEvent("test-id", "test-type", payload));

        verify(splunkRestClient).post(anyString(),
            eq("https://localhost" + SERVICES_COLLECTOR_EVENT),
            anyString());
    }

    // --- M7: Missing edge case tests ---

    @Test
    void testEmptyEventsArray() {
        mockAuthentication();

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(200);
        when(splunkRestClient.post(anyString(), anyString(), anyString())).thenReturn(mockResponse);

        JsonObject payload = buildPayload("https://splunk.example.com", new JsonArray());
        handler.handle(buildIncomingCloudEvent("test-id", "test-type", payload));

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(splunkRestClient).post(anyString(), anyString(), bodyCaptor.capture());

        // Empty events should still be wrapped in Splunk HEC format
        String sentBody = bodyCaptor.getValue();
        assertTrue(sentBody.contains("\"source\":\"eventing\""));
        assertTrue(sentBody.contains("\"sourcetype\":\"Insights event\""));
        assertTrue(sentBody.contains("\"event\":"));
    }

    @Test
    void testAuthenticationFetchFailure() {
        when(authenticationLoader.fetchAuthenticationData(anyString(), any(JsonObject.class)))
            .thenThrow(new WebApplicationException("Sources API down", 500));

        JsonObject payload = buildPayload("https://splunk.example.com", JsonArray.of(JsonObject.of("k", "v")));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
            handler.handle(buildIncomingCloudEvent("test-id", "test-type", payload))
        );
        assertTrue(ex.getMessage().contains("Error fetching authentication data"));
    }

    @Test
    void testMissingAuthenticationResult() {
        when(authenticationLoader.fetchAuthenticationData(anyString(), any(JsonObject.class)))
            .thenReturn(Optional.empty());

        JsonObject payload = buildPayload("https://splunk.example.com", JsonArray.of(JsonObject.of("k", "v")));

        assertThrows(IllegalStateException.class, () ->
            handler.handle(buildIncomingCloudEvent("test-id", "test-type", payload))
        );
    }

    @Test
    void testMultipleEventsPayloadContent() {
        mockAuthentication();

        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(200);
        when(splunkRestClient.post(anyString(), anyString(), anyString())).thenReturn(mockResponse);

        JsonObject payload = buildPayload("https://splunk.example.com",
            JsonArray.of(
                JsonObject.of("event-1-key", "event-1-value"),
                JsonObject.of("event-2-key", "event-2-value")
            ));
        handler.handle(buildIncomingCloudEvent("test-id", "test-type", payload));

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(splunkRestClient).post(eq("Splunk my-token"),
            eq("https://splunk.example.com" + SERVICES_COLLECTOR_EVENT),
            bodyCaptor.capture());

        String capturedBody = bodyCaptor.getValue();
        assertTrue(capturedBody.contains("\"source\":\"eventing\""));
        assertTrue(capturedBody.contains("\"sourcetype\":\"Insights event\""));
        assertTrue(capturedBody.contains("\"event-1-key\":\"event-1-value\""));
        assertTrue(capturedBody.contains("\"event-2-key\":\"event-2-value\""));
    }

    // --- Coverage gap tests ---

    @Test
    void testNullCloudEventDataIsRejected() {
        assertThrows(IllegalStateException.class, () ->
            handler.handle(buildIncomingCloudEvent("test-id", "test-type", null))
        );
    }

    @Test
    void testBlankSecretTokenIsRejected() {
        SourcesSecretResponse secretResponse = new SourcesSecretResponse();
        secretResponse.password = "   ";
        when(authenticationLoader.fetchAuthenticationData(anyString(), any(JsonObject.class)))
            .thenReturn(Optional.of(new AuthenticationResult(secretResponse, AuthenticationType.SECRET_TOKEN)));

        JsonObject payload = buildPayload("https://splunk.example.com", JsonArray.of(JsonObject.of("k", "v")));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            handler.handle(buildIncomingCloudEvent("test-id", "test-type", payload))
        );
        assertTrue(ex.getMessage().contains("Missing Splunk secret token"));
    }

    @Test
    void testNullSecretTokenIsRejected() {
        SourcesSecretResponse secretResponse = new SourcesSecretResponse();
        secretResponse.password = null;
        when(authenticationLoader.fetchAuthenticationData(anyString(), any(JsonObject.class)))
            .thenReturn(Optional.of(new AuthenticationResult(secretResponse, AuthenticationType.SECRET_TOKEN)));

        JsonObject payload = buildPayload("https://splunk.example.com", JsonArray.of(JsonObject.of("k", "v")));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
            handler.handle(buildIncomingCloudEvent("test-id", "test-type", payload))
        );
        assertTrue(ex.getMessage().contains("Missing Splunk secret token"));
    }

    @Test
    void testInvalidUriSyntaxIsRejected() {
        JsonObject authentication = new JsonObject();
        authentication.put("type", AuthenticationType.SECRET_TOKEN.name());
        authentication.put("secretId", 123L);

        JsonObject metadata = new JsonObject();
        metadata.put("url", "https://invalid host with spaces");
        metadata.put("authentication", authentication);

        JsonObject payload = new JsonObject();
        payload.put("notif-metadata", metadata);
        payload.put("org_id", DEFAULT_ORG_ID);
        payload.put("account_id", DEFAULT_ACCOUNT_ID);
        payload.put("events", JsonArray.of(JsonObject.of("k", "v")));

        assertThrows(IllegalArgumentException.class, () ->
            handler.handle(buildIncomingCloudEvent("test-id", "test-type", payload))
        );
    }

    @Test
    void testBuildSplunkPayloadWithNullEvents() {
        SplunkNotification notification = new SplunkNotification();
        notification.setOrgId(DEFAULT_ORG_ID);
        notification.accountId = DEFAULT_ACCOUNT_ID;
        notification.events = null;

        String result = SplunkMessageHandler.buildSplunkPayload(notification);
        assertTrue(result.contains("\"source\":\"eventing\""));
        assertTrue(result.contains("\"org_id\":\"" + DEFAULT_ORG_ID + "\""));
    }

    private void mockAuthentication() {
        SourcesSecretResponse secretResponse = new SourcesSecretResponse();
        secretResponse.password = "my-token";
        when(authenticationLoader.fetchAuthenticationData(anyString(), any(JsonObject.class)))
            .thenReturn(Optional.of(new AuthenticationResult(secretResponse, AuthenticationType.SECRET_TOKEN)));
    }

    private static JsonObject buildPayload(String targetUrl, JsonArray events) {
        JsonObject authentication = new JsonObject();
        authentication.put("type", AuthenticationType.SECRET_TOKEN.name());
        authentication.put("secretId", 123L);

        JsonObject metadata = new JsonObject();
        metadata.put("url", targetUrl);
        metadata.put("authentication", authentication);

        JsonObject payload = new JsonObject();
        payload.put("notif-metadata", metadata);
        payload.put("org_id", DEFAULT_ORG_ID);
        payload.put("account_id", DEFAULT_ACCOUNT_ID);
        payload.put("events", events);

        return payload;
    }
}
