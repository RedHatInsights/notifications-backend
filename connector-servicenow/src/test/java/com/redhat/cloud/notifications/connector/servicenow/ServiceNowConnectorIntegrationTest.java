package com.redhat.cloud.notifications.connector.servicenow;

import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationLoader;
import com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationResult;
import com.redhat.cloud.notifications.connector.authentication.v2.sources.SourcesSecretResponse;
import com.redhat.cloud.notifications.connector.v2.TestLifecycleManager;
import com.redhat.cloud.notifications.connector.v2.http.BaseHttpConnectorIntegrationTest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.redhat.cloud.notifications.MockServerLifecycleManager.getClient;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationType.SECRET_TOKEN;
import static com.redhat.cloud.notifications.connector.servicenow.ServiceNowMessageHandler.USERNAME;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class ServiceNowConnectorIntegrationTest extends BaseHttpConnectorIntegrationTest {

    @InjectMock
    AuthenticationLoader authenticationLoader;

    boolean addBasicAuth = false;
    JsonObject expectedPayloadBody;

    @BeforeEach
    void resetTestState() {
        addBasicAuth = false;
        expectedPayloadBody = null;
    }

    @Override
    protected boolean useHttps() {
        return true;
    }

    @Override
    protected String getRemoteServerPath() {
        return "/test-servicenow";
    }

    @Override
    protected JsonObject buildIncomingPayload(String targetUrl) {
        JsonObject metadata = new JsonObject();
        metadata.put("url", targetUrl);

        if (addBasicAuth) {
            JsonObject authentication = new JsonObject();
            authentication.put("type", SECRET_TOKEN.name());
            authentication.put("secretId", 123L);
            metadata.put("authentication", authentication);
        }

        JsonObject payload = new JsonObject();
        payload.put("notif-metadata", metadata);
        payload.put("org_id", DEFAULT_ORG_ID);
        payload.put("account_id", DEFAULT_ACCOUNT_ID);
        payload.put("random_data", RandomStringUtils.secure().nextAlphanumeric(50));

        // Expected body sent to ServiceNow is payload minus notif-metadata
        expectedPayloadBody = payload.copy();
        expectedPayloadBody.remove("notif-metadata");

        return payload;
    }

    @Test
    void testSuccessfulNotificationWithBasicAuth() {
        SourcesSecretResponse secretResponse = new SourcesSecretResponse();
        secretResponse.password = "passw0rd_servicenow";
        when(authenticationLoader.fetchAuthenticationData(anyString(), any(JsonObject.class)))
            .thenReturn(Optional.of(new AuthenticationResult(secretResponse, SECRET_TOKEN)));
        try {
            addBasicAuth = true;
            testSuccessfulNotification();
        } finally {
            addBasicAuth = false;
        }
    }

    @Test
    void testSourcesClientFailure() {
        when(authenticationLoader.fetchAuthenticationData(anyString(), any(JsonObject.class)))
            .thenThrow(new IllegalStateException("Sources API error"));
        try {
            addBasicAuth = true;

            String targetUrl = getConnectorSpecificTargetUrl() + getRemoteServerPath();
            JsonObject incomingPayload = buildIncomingPayload(targetUrl);

            String cloudEventId = sendCloudEventMessage(incomingPayload);

            assertFailedOutgoingMessage(cloudEventId, "Error fetching authentication data");
        } finally {
            addBasicAuth = false;
        }
    }

    @Test
    void testUnsupportedAuthenticationType() {
        AuthenticationResult mockResult = mock(AuthenticationResult.class);
        when(authenticationLoader.fetchAuthenticationData(anyString(), any())).thenReturn(Optional.of(mockResult));

        try {
            addBasicAuth = true;

            String targetUrl = getConnectorSpecificTargetUrl() + getRemoteServerPath();
            JsonObject incomingPayload = buildIncomingPayload(targetUrl);

            String cloudEventId = sendCloudEventMessage(incomingPayload);

            assertFailedOutgoingMessage(cloudEventId, "Unsupported authentication type");
        } finally {
            addBasicAuth = false;
        }
    }

    @Test
    void testAuthenticationConfiguredButNoCredentialsReturned() {
        when(authenticationLoader.fetchAuthenticationData(anyString(), any(JsonObject.class)))
            .thenReturn(Optional.empty());

        mockHttpResponse(getRemoteServerPath(), 200, "OK");

        try {
            addBasicAuth = true;

            String targetUrl = getConnectorSpecificTargetUrl() + getRemoteServerPath();
            JsonObject incomingPayload = buildIncomingPayload(targetUrl);

            String cloudEventId = sendCloudEventMessage(incomingPayload);

            assertSuccessfulOutgoingMessage(cloudEventId, targetUrl, 200);

            List<LoggedRequest> loggedRequests = getClient().findAll(
                postRequestedFor(urlEqualTo(getRemoteServerPath()))
            );
            assertEquals(1, loggedRequests.size());
            assertFalse(loggedRequests.get(0).containsHeader("Authorization"));
        } finally {
            addBasicAuth = false;
        }
    }

    @Test
    void testSuccessfulNotificationWithoutAuth() {
        mockHttpResponse(getRemoteServerPath(), 200, "OK");

        String targetUrl = getConnectorSpecificTargetUrl() + getRemoteServerPath();
        JsonObject incomingPayload = buildIncomingPayload(targetUrl);

        String cloudEventId = sendCloudEventMessage(incomingPayload);

        assertSuccessfulOutgoingMessage(cloudEventId, targetUrl, 200);

        List<LoggedRequest> loggedRequests = getClient().findAll(
            postRequestedFor(urlEqualTo(getRemoteServerPath()))
        );
        assertEquals(1, loggedRequests.size());
        assertFalse(loggedRequests.get(0).containsHeader("Authorization"));
    }

    @Test
    void testInvalidUrlFormat() {
        JsonObject incomingPayload = buildIncomingPayload("not-a-valid-url");
        String cloudEventId = sendCloudEventMessage(incomingPayload);
        assertFailedOutgoingMessage(cloudEventId, "Illegal URL scheme");
    }

    @Test
    void testEmptyUrl() {
        JsonObject incomingPayload = buildIncomingPayload("");
        String cloudEventId = sendCloudEventMessage(incomingPayload);
        assertFailedOutgoingMessage(cloudEventId, "Missing or empty 'url'");
    }

    @Test
    void testNullUrl() {
        JsonObject metadata = new JsonObject();
        // url is intentionally not set

        JsonObject payload = new JsonObject();
        payload.put("notif-metadata", metadata);
        payload.put("org_id", DEFAULT_ORG_ID);
        payload.put("account_id", DEFAULT_ACCOUNT_ID);

        String cloudEventId = sendCloudEventMessage(payload);
        assertFailedOutgoingMessage(cloudEventId, "Missing or empty 'url'");
    }

    @Test
    void testFtpSchemeRejected() {
        JsonObject incomingPayload = buildIncomingPayload("ftp://example.com/file");
        String cloudEventId = sendCloudEventMessage(incomingPayload);
        assertFailedOutgoingMessage(cloudEventId, "Illegal URL scheme");
    }

    @Test
    void testMissingMetadata() {
        JsonObject payload = new JsonObject();
        payload.put("org_id", DEFAULT_ORG_ID);
        payload.put("account_id", DEFAULT_ACCOUNT_ID);
        // notif-metadata is intentionally not set

        String cloudEventId = sendCloudEventMessage(payload);
        assertFailedOutgoingMessage(cloudEventId, "Validation failed");
    }

    @Override
    protected void afterSuccessfulNotification(List<LoggedRequest> loggedRequests) {
        for (LoggedRequest loggedRequest : loggedRequests) {
            assertEquals(expectedPayloadBody, new JsonObject(loggedRequest.getBodyAsString()));

            HttpHeaders headers = loggedRequest.getHeaders();
            assertTrue(headers.getHeader("Content-Type").firstValue().startsWith("application/json"));
            if (addBasicAuth) {
                String expectedAuth = "Basic " + Base64.getEncoder().encodeToString(
                    (USERNAME + ":passw0rd_servicenow").getBytes(UTF_8));
                assertEquals(expectedAuth, headers.getHeader("Authorization").firstValue());
            }
        }
    }
}
