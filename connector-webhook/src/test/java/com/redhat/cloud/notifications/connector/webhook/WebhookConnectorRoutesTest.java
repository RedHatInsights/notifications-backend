package com.redhat.cloud.notifications.connector.webhook;

import com.redhat.cloud.notifications.connector.ConnectorRoutesTest;
import com.redhat.cloud.notifications.connector.TestLifecycleManager;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.CONNECTOR_TO_ENGINE;
import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.SUCCESS;
import static com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder.ENGINE_TO_CONNECTOR;
import static com.redhat.cloud.notifications.connector.webhook.ExchangeProperty.INSIGHT_TOKEN_HEADER;
import static com.redhat.cloud.notifications.connector.webhook.WebhookCloudEventDataExtractor.BASIC_AUTHENTICATION;
import static com.redhat.cloud.notifications.connector.webhook.WebhookCloudEventDataExtractor.ENDPOINT_PROPERTIES;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.http.HttpHeaders.AUTHORIZATION;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class WebhookConnectorRoutesTest extends ConnectorRoutesTest {

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

    boolean addBasicAuth = false;

    @Override
    protected JsonObject buildIncomingPayload(String targetUrl) {
        JsonObject endpointProperties = new JsonObject();
        endpointProperties.put("url", targetUrl);
        endpointProperties.put("method", "POST");

        if (addInsightsToken) {
            endpointProperties.put("secret_token", "mySuperSecretInsightToken");
        }
        if (addBearerToken) {
            endpointProperties.put("bearer_token", "mySuperSecretBearerToken");
        }
        if (addBasicAuth) {
            JsonObject basicAuthProperties = new JsonObject();
            basicAuthProperties.put("username", RandomStringUtils.randomAlphanumeric(10));
            basicAuthProperties.put("password", RandomStringUtils.randomAlphanumeric(10));
            endpointProperties.put(BASIC_AUTHENTICATION, basicAuthProperties);
        }
        JsonObject eventPayload = new JsonObject();
        eventPayload.put("orgId", DEFAULT_ORG_ID);
        eventPayload.put("message", "This is a test!");

        JsonObject fullPayload = new JsonObject();
        fullPayload.put("endpoint_properties", endpointProperties);
        fullPayload.put("payload", eventPayload);

        return fullPayload;
    }

    @Override
    protected Predicate checkOutgoingPayload(JsonObject incomingPayload) {
        return exchange -> {
            String outgoingPayload = exchange.getIn().getBody(String.class);
            String outgoingContentType = exchange.getIn().getHeader(Exchange.CONTENT_TYPE, String.class);
            Boolean checkInsightsToken;
            String outgoingInsightToken = exchange.getIn().getHeader(INSIGHT_TOKEN_HEADER, String.class);
            if (addInsightsToken) {
                checkInsightsToken = outgoingInsightToken.equals(incomingPayload.getJsonObject(ENDPOINT_PROPERTIES).getString("secret_token"));
            } else {
                checkInsightsToken = outgoingInsightToken == null;
            }
            Boolean checkBearerToken;
            String outgoingAuthorization = exchange.getIn().getHeader(AUTHORIZATION, String.class);
            if (addBearerToken) {
                checkBearerToken = outgoingAuthorization.equals("Bearer " + incomingPayload.getJsonObject(ENDPOINT_PROPERTIES).getString("bearer_token"));
            } else {
                checkBearerToken = outgoingAuthorization == null || !outgoingAuthorization.startsWith("Bearer ");
            }

            Boolean checkBasicAuth;
            if (addBasicAuth) {
                String username = incomingPayload.getJsonObject(ENDPOINT_PROPERTIES).getJsonObject(BASIC_AUTHENTICATION).getString("username");
                String password = incomingPayload.getJsonObject(ENDPOINT_PROPERTIES).getJsonObject(BASIC_AUTHENTICATION).getString("password");
                String decodedAuthentication = new String(Base64.getDecoder().decode(outgoingAuthorization.replace("Basic ", "").getBytes(UTF_8)), UTF_8);
                checkBasicAuth = decodedAuthentication.equals(username + ":" + password);
            } else {
                checkBasicAuth = outgoingAuthorization == null || !outgoingAuthorization.startsWith("Basic ");
            }

            return outgoingPayload.equals(incomingPayload.getString("payload"))
                && outgoingContentType.equals("application/json")
                && checkInsightsToken
                && checkBearerToken
                && checkBasicAuth;
        };
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
    void testSuccessfulNotificationWithBasicAuth() throws Exception {
        try {
            addBasicAuth = true;
            testSuccessfulNotification();
        } finally {
            addBasicAuth = false;
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
}
