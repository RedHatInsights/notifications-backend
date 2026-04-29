package com.redhat.cloud.notifications.connector.pagerduty;

import com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationLoader;
import com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationResult;
import com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationType;
import com.redhat.cloud.notifications.connector.authentication.v2.sources.SourcesSecretResponse;
import com.redhat.cloud.notifications.connector.pagerduty.config.PagerDutyConnectorConfig;
import com.redhat.cloud.notifications.connector.v2.http.models.HandledHttpMessageDetails;
import com.redhat.cloud.notifications.connector.v2.models.HandledMessageDetails;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.*;
import static com.redhat.cloud.notifications.connector.v2.BaseConnectorIntegrationTest.buildIncomingCloudEvent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(PagerDutyTestLifecycleManager.class)
class PagerDutyMessageHandlerTest {

    static final String TEST_URL = "https://events.pagerduty.com/v2/enqueue";
    static final String ROUTING_KEY_VALUE = "my-routing-key";
    public static final String AUTHENTICATION = "authentication";

    @Inject
    PagerDutyMessageHandler handler;

    @InjectMock
    AuthenticationLoader authenticationLoader;

    @InjectMock
    @RestClient
    PagerDutyRestClient pagerDutyRestClient;

    @InjectMock
    PagerDutyConnectorConfig config;

    @BeforeEach
    void init() {
        when(config.getPagerDutyUrl()).thenReturn(TEST_URL);
        when(config.isDynamicPagerdutySeverityEnabled(anyString())).thenReturn(true);
    }

    // --- Authentication tests ---

    @Test
    void testRoutingKeyInjectedInBody() {
        mockAuthentication();
        mockRestClient(200);

        JsonObject payload = createIncomingPayload();
        handler.handle(buildIncomingCloudEvent("test-id", "test-type", payload));

        String sentBody = captureSentBody();
        JsonObject sent = new JsonObject(sentBody);
        assertEquals(ROUTING_KEY_VALUE, sent.getString(ROUTING_KEY));
    }

    @Test
    void testBearerAuthIsRejected() {
        SourcesSecretResponse secretResponse = new SourcesSecretResponse();
        secretResponse.password = "my-token";
        when(authenticationLoader.fetchAuthenticationData(anyString(), any(JsonObject.class)))
            .thenReturn(Optional.of(new AuthenticationResult(secretResponse, AuthenticationType.BEARER)));

        mockRestClient(200);

        JsonObject payload = createIncomingPayload();
        assertThrows(IllegalStateException.class, () ->
            handler.handle(buildIncomingCloudEvent("test-id", "test-type", payload))
        );
    }

    @Test
    void testMissingAuthenticationResult() {
        when(authenticationLoader.fetchAuthenticationData(anyString(), any(JsonObject.class)))
            .thenReturn(Optional.empty());

        mockRestClient(200);

        JsonObject payload = createIncomingPayload();
        assertThrows(IllegalStateException.class, () ->
            handler.handle(buildIncomingCloudEvent("test-id", "test-type", payload))
        );
    }

    // --- HTTP interaction tests ---

    @Test
    void testSuccessfulDelivery() {
        mockAuthentication();
        mockRestClient(200);

        JsonObject payload = createIncomingPayload();
        HandledMessageDetails result = handler.handle(
            buildIncomingCloudEvent("test-id", "test-type", payload)
        );

        HandledHttpMessageDetails httpDetails = (HandledHttpMessageDetails) result;
        assertEquals(200, httpDetails.httpStatus);
        assertEquals(TEST_URL, httpDetails.targetUrl);
    }

    @Test
    void testHttpErrorStatus() {
        mockAuthentication();
        mockRestClient(500);

        JsonObject payload = createIncomingPayload();
        HandledMessageDetails result = handler.handle(
            buildIncomingCloudEvent("test-id", "test-type", payload)
        );

        HandledHttpMessageDetails httpDetails = (HandledHttpMessageDetails) result;
        assertEquals(500, httpDetails.httpStatus);
    }

    // --- Helper methods ---

    private void mockAuthentication() {
        SourcesSecretResponse secretResponse = new SourcesSecretResponse();
        secretResponse.password = ROUTING_KEY_VALUE;
        when(authenticationLoader.fetchAuthenticationData(anyString(), any(JsonObject.class)))
            .thenReturn(Optional.of(new AuthenticationResult(secretResponse, AuthenticationType.SECRET_TOKEN)));
    }

    private void mockRestClient(int statusCode) {
        Response mockResponse = mock(Response.class);
        when(mockResponse.getStatus()).thenReturn(statusCode);
        when(pagerDutyRestClient.post(anyString())).thenReturn(mockResponse);
    }

    private String captureSentBody() {
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(pagerDutyRestClient).post(bodyCaptor.capture());
        return bodyCaptor.getValue();
    }

    static JsonObject createIncomingPayload() {
        JsonObject authentication = new JsonObject();
        authentication.put("type", AuthenticationType.SECRET_TOKEN.name());
        authentication.put("secretId", 123L);

        JsonObject cloudEventData = new JsonObject();
        cloudEventData.put(AUTHENTICATION, authentication);
        cloudEventData.put(PAYLOAD, PagerDutyTransformerTest.createIncomingPayload());
        cloudEventData.put(ORG_ID, DEFAULT_ORG_ID);

        return cloudEventData;
    }
}
