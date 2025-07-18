package com.redhat.cloud.notifications.connector.webhook;

import com.redhat.cloud.notifications.connector.ConnectorRoutesTest;
import com.redhat.cloud.notifications.connector.TestLifecycleManager;
import com.redhat.cloud.notifications.connector.authentication.AuthenticationType;
import com.redhat.cloud.notifications.connector.authentication.secrets.SecretsLoader;
import com.redhat.cloud.notifications.connector.http.HttpConnectorConfig;
import com.redhat.cloud.notifications.connector.http.HttpErrorType;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.CONNECTOR_TO_ENGINE;
import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.SUCCESS;
import static com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder.ENGINE_TO_CONNECTOR;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.AUTHENTICATION_TYPE;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.SECRET_ID;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationType.BEARER;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationType.SECRET_TOKEN;
import static com.redhat.cloud.notifications.connector.http.HttpErrorType.HTTP_3XX;
import static com.redhat.cloud.notifications.connector.http.HttpErrorType.HTTP_4XX;
import static com.redhat.cloud.notifications.connector.http.HttpErrorType.HTTP_5XX;
import static com.redhat.cloud.notifications.connector.webhook.AuthenticationProcessor.X_INSIGHT_TOKEN_HEADER;
import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class WebhookConnectorRoutesTest extends ConnectorRoutesTest {

    @InjectMock
    SecretsLoader secretsLoader;

    @Inject
    HttpConnectorConfig connectorConfig;

    @Override
    protected String getMockEndpointPattern() {
        return "https://foo.bar";
    }

    @Override
    protected String getMockEndpointUri() {
        return "mock:https:foo.bar";
    }

    boolean addInsightsToken = false;
    boolean addBearerToken = false;

    @Override
    protected JsonObject buildIncomingPayload(String targetUrl) {
        JsonObject endpointProperties = new JsonObject();
        endpointProperties.put("url", targetUrl);
        endpointProperties.put("method", "POST");

        JsonObject eventPayload = new JsonObject();
        eventPayload.put("orgId", DEFAULT_ORG_ID);
        eventPayload.put("message", "This is a test!");

        JsonObject fullPayload = new JsonObject();
        fullPayload.put("endpoint_properties", endpointProperties);
        fullPayload.put("payload", eventPayload);

        JsonObject authentication = new JsonObject();
        if (addInsightsToken) {
            authentication.put("type", SECRET_TOKEN.name());
            authentication.put("secretId", 123L);
        }
        if (addBearerToken) {
            authentication.put("type", BEARER.name());
            authentication.put("secretId", 456L);
        }
        if (!authentication.isEmpty()) {
            fullPayload.put("authentication", authentication);
        }

        return fullPayload;
    }

    @Override
    protected Predicate checkOutgoingPayload(JsonObject incomingPayload) {
        return exchange -> {
            String outgoingPayload = exchange.getIn().getBody(String.class);
            String outgoingContentType = exchange.getIn().getHeader(Exchange.CONTENT_TYPE, String.class);
            boolean checkInsightsToken;
            String outgoingInsightToken = exchange.getIn().getHeader(X_INSIGHT_TOKEN_HEADER, String.class);
            if (addInsightsToken) {
                checkInsightsToken = exchange.getProperty(AUTHENTICATION_TYPE, AuthenticationType.class) == SECRET_TOKEN &&
                    exchange.getProperty(SECRET_ID, Long.class).equals(123L);
            } else {
                checkInsightsToken = outgoingInsightToken == null;
            }
            boolean checkBearerToken;
            String outgoingAuthorization = exchange.getIn().getHeader(AUTHORIZATION, String.class);
            if (addBearerToken) {
                checkBearerToken = exchange.getProperty(AUTHENTICATION_TYPE, AuthenticationType.class) == BEARER &&
                    exchange.getProperty(SECRET_ID, Long.class).equals(456L);
            } else {
                checkBearerToken = outgoingAuthorization == null || !outgoingAuthorization.startsWith("Bearer ");
            }

            return outgoingPayload.equals(incomingPayload.getString("payload"))
                && outgoingContentType.equals("application/json; charset=utf-8")
                && checkInsightsToken
                && checkBearerToken;
        };
    }

    @Test
    protected void testFailedNotificationError500() throws Exception {
        // We expect the connector to attempt redeliveries for the error.
        testFailedNotificationAndReturnedFlagsToEngine(500, "My custom internal error", HTTP_5XX, this.connectorConfig.getRedeliveryMaxAttempts());
    }

    @Test
    protected void testFailedNotificationError404() throws Exception {
        testFailedNotificationAndReturnedFlagsToEngine(404, "Page not found", HTTP_4XX, 0);
    }

    @Test
    protected void testFailedNotificationError301() throws Exception {
        testFailedNotificationAndReturnedFlagsToEngine(301, "Moved Permanently", HTTP_3XX, 0);
    }

    private void testFailedNotificationAndReturnedFlagsToEngine(int httpStatusCode, String returnedBodyMessage, HttpErrorType httpErrorType, final int expectedRedeliveriesCount) throws Exception {
        mockRemoteServerError(httpStatusCode, returnedBodyMessage);
        JsonObject returnToEngine = super.testFailedNotification(expectedRedeliveriesCount);
        JsonObject data = new JsonObject(returnToEngine.getString("data"));
        JsonObject error = data.getJsonObject("error");
        assertEquals(httpErrorType.name(), error.getString("error_type"));
        assertEquals(expectedRedeliveriesCount + 1, error.getInteger("delivery_attempts"));
        assertEquals(httpStatusCode, error.getInteger("http_status_code"));
    }

    @Test
    void testSuccessfulNotificationWithInsightHeader() throws Exception {
        try {
            addInsightsToken = true;
            testSuccessfulNotification();
        } finally {
            addInsightsToken = false;
        }
    }

    @Test
    void testSuccessfulNotificationWithBearerToken() throws Exception {
        try {
            addBearerToken = true;
            testSuccessfulNotification();
        } finally {
            addBearerToken = false;
        }
    }

    @Test
    void testMissingEndpointPropertiesParameter() throws Exception {
        JsonObject incomingPayload = buildIncomingPayload(getMockServerUrl());
        incomingPayload.remove("endpoint_properties");
        testMissingParameters(incomingPayload, "The 'endpoint_properties' field is required");
    }

    @Test
    void testMissingUrlParameter() throws Exception {
        JsonObject incomingPayload = buildIncomingPayload(getMockServerUrl());
        incomingPayload.getJsonObject("endpoint_properties").remove("url");
        testMissingParameters(incomingPayload, "The endpoint url is required");
    }

    @Test
    void testMissingPayloadParameter() throws Exception {
        JsonObject incomingPayload = buildIncomingPayload(getMockServerUrl());
        incomingPayload.remove("payload");
        testMissingParameters(incomingPayload, "The 'payload' field is required");
    }

    void testMissingParameters(JsonObject incomingPayload, String expectedErrorMessage) throws Exception {

        mockKafkaSourceEndpoint(); // This is the entry point of the connector.
        MockEndpoint kafkaSinkMockEndpoint = mockKafkaSinkEndpoint(); // This is where the return message to the engine is sent.

        String cloudEventId = sendMessageToKafkaSource(incomingPayload);

        assertKafkaSinkIsSatisfied(cloudEventId, kafkaSinkMockEndpoint, false, null, expectedErrorMessage);

        checkRouteMetrics(ENGINE_TO_CONNECTOR, 1, 1, 1);
        checkRouteMetrics(connectorConfig.getConnectorName(), 0, 0, 0);

        checkRouteMetrics(SUCCESS, 0, 0, 0);
        checkRouteMetrics(CONNECTOR_TO_ENGINE, 0, 1, 1);
        micrometerAssertionHelper.assertCounterIncrement(connectorConfig.getRedeliveryCounterName(), 0);
    }

    @Override
    protected void afterKafkaSinkSuccess() {
        verify(secretsLoader, times(1)).process(any(Exchange.class));
    }
}
