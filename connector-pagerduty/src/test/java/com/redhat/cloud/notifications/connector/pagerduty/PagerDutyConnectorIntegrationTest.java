package com.redhat.cloud.notifications.connector.pagerduty;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationLoader;
import com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationResult;
import com.redhat.cloud.notifications.connector.authentication.v2.AuthenticationType;
import com.redhat.cloud.notifications.connector.authentication.v2.sources.SourcesSecretResponse;
import com.redhat.cloud.notifications.connector.pagerduty.config.PagerDutyConnectorConfig;
import com.redhat.cloud.notifications.connector.v2.http.BaseHttpConnectorIntegrationTest;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyMessageHandlerTest.AUTHENTICATION;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTransformer.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(PagerDutyTestLifecycleManager.class)
class PagerDutyConnectorIntegrationTest extends BaseHttpConnectorIntegrationTest {

    private static final String ROUTING_KEY_VALUE = "test-routing-key";
    private static final String REMOTE_PATH = "/v2/enqueue";

    @InjectMock
    AuthenticationLoader authenticationLoader;

    @InjectSpy
    PagerDutyConnectorConfig config;

    @Override
    protected boolean useHttps() {
        return true;
    }

    @Override
    protected String getRemoteServerPath() {
        return REMOTE_PATH;
    }

    @Override
    protected JsonObject buildIncomingPayload(String targetUrl) {
        when(config.getPagerDutyUrl()).thenReturn(targetUrl);
        when(config.isDynamicPagerdutySeverityEnabled(anyString())).thenReturn(true);

        JsonObject authentication = new JsonObject();
        authentication.put("type", AuthenticationType.SECRET_TOKEN.name());
        authentication.put("secretId", 123L);

        JsonObject innerPayload = new JsonObject();
        innerPayload.put(ACCOUNT_ID, DEFAULT_ACCOUNT_ID);
        innerPayload.put(APPLICATION, "default-application");
        innerPayload.put(BUNDLE, "default-bundle");
        innerPayload.put(CONTEXT, JsonObject.of(
                DISPLAY_NAME, "console",
                "inventory_id", "8a4a4f75-5319-4255-9eb5-1ee5a92efd7f"
        ));
        innerPayload.put(EVENT_TYPE, "default-event-type");
        innerPayload.put(EVENTS, JsonArray.of(
                JsonObject.of("event-1-key", "event-1-value")
        ));
        innerPayload.put(ORG_ID, DEFAULT_ORG_ID);
        innerPayload.put(TIMESTAMP, LocalDateTime.of(2024, 8, 12, 17, 26, 19).toString());
        innerPayload.put(APPLICATION_URL, "https://console.redhat.com/insights/default-application");
        innerPayload.put(INVENTORY_URL, "https://console.redhat.com/insights/inventory/8a4a4f75-5319-4255-9eb5-1ee5a92efd7f");
        innerPayload.put(SEVERITY, "IMPORTANT");

        JsonObject payload = new JsonObject();
        payload.put(AUTHENTICATION, authentication);
        payload.put(PAYLOAD, innerPayload);
        payload.put(ORG_ID, DEFAULT_ORG_ID);

        SourcesSecretResponse secretResponse = new SourcesSecretResponse();
        secretResponse.password = ROUTING_KEY_VALUE;
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

        verify(authenticationLoader).fetchAuthenticationData(eq(DEFAULT_ORG_ID), any(JsonObject.class));

        assertNull(request.getHeader("Authorization"));

        JsonObject sentPayload = new JsonObject(body);
        assertEquals(ROUTING_KEY_VALUE, sentPayload.getString(ROUTING_KEY));
        assertEquals("trigger", sentPayload.getString(EVENT_ACTION));
        assertEquals("default-application", sentPayload.getString(CLIENT));
        assertNotNull(sentPayload.getJsonObject(PAYLOAD));
        assertEquals("default-event-type", sentPayload.getJsonObject(PAYLOAD).getString(SUMMARY));
        assertEquals("error", sentPayload.getJsonObject(PAYLOAD).getString(SEVERITY));
    }
}
