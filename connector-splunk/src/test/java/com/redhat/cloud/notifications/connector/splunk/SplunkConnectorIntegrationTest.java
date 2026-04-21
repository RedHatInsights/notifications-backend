package com.redhat.cloud.notifications.connector.splunk;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationLoader;
import com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationResult;
import com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationType;
import com.redhat.cloud.notifications.connector.authentication.v2.sources.SourcesSecretResponse;
import com.redhat.cloud.notifications.connector.v2.TestLifecycleManager;
import com.redhat.cloud.notifications.connector.v2.http.BaseHttpConnectorIntegrationTest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.Optional;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.connector.splunk.SplunkMessageHandler.SERVICES_COLLECTOR_EVENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class SplunkConnectorIntegrationTest extends BaseHttpConnectorIntegrationTest {

    @InjectMock
    AuthenticationLoader authenticationLoader;

    @Override
    protected boolean useHttps() {
        return true;
    }

    @Override
    protected String getRemoteServerPath() {
        return SERVICES_COLLECTOR_EVENT;
    }

    @Override
    protected JsonObject buildIncomingPayload(String targetUrl) {
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
        payload.put("events", JsonArray.of(
            JsonObject.of("event-1-key", "event-1-value"),
            JsonObject.of("event-2-key", "event-2-value"),
            JsonObject.of("event-3-key", "event-3-value")
        ));

        // Mock the authentication loader to return a secret
        SourcesSecretResponse secretResponse = new SourcesSecretResponse();
        secretResponse.password = "my-splunk-token";
        when(authenticationLoader.fetchAuthenticationData(anyString(), any(JsonObject.class)))
            .thenReturn(Optional.of(new AuthenticationResult(secretResponse, AuthenticationType.SECRET_TOKEN)));

        return payload;
    }

    @Override
    protected void afterSuccessfulNotification(List<LoggedRequest> loggedRequests) {
        assertEquals(1, loggedRequests.size());
        LoggedRequest request = loggedRequests.get(0);
        String body = request.getBodyAsString();
        assertNotNull(body);

        // Verify authentication loader was called with correct org ID
        verify(authenticationLoader).fetchAuthenticationData(eq(DEFAULT_ORG_ID), any(JsonObject.class));

        // Verify authorization header
        assertEquals("Splunk my-splunk-token", request.getHeader("Authorization"));

        // Verify the payload contains split events in HEC format
        JsonObject event1 = buildSplitEvent("event-1-key", "event-1-value");
        JsonObject event2 = buildSplitEvent("event-2-key", "event-2-value");
        JsonObject event3 = buildSplitEvent("event-3-key", "event-3-value");
        assertEquals(event1.encode() + event2.encode() + event3.encode(), body);
    }

    private JsonObject buildSplitEvent(String key, String value) {
        return JsonObject.of(
            "source", "eventing",
            "sourcetype", "Insights event",
            "event", JsonObject.of(
                "org_id", DEFAULT_ORG_ID,
                "account_id", DEFAULT_ACCOUNT_ID,
                "events", JsonArray.of(
                    JsonObject.of(key, value)
                )
            )
        );
    }
}
