package com.redhat.cloud.notifications.connector.pagerduty;

import com.redhat.cloud.notifications.connector.ConnectorRoutesTest;
import com.redhat.cloud.notifications.connector.TestLifecycleManager;
import com.redhat.cloud.notifications.connector.authentication.AuthenticationType;
import com.redhat.cloud.notifications.connector.authentication.secrets.SecretsLoader;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.CONNECTOR_TO_ENGINE;
import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.SUCCESS;
import static com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder.ENGINE_TO_CONNECTOR;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.AUTHENTICATION_TYPE;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationExchangeProperty.SECRET_ID;
import static com.redhat.cloud.notifications.connector.authentication.AuthenticationType.SECRET_TOKEN;
import static com.redhat.cloud.notifications.connector.pagerduty.ExchangeProperty.ACCOUNT_ID;
import static com.redhat.cloud.notifications.connector.pagerduty.PagerDutyTestUtils.createCloudEventData;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class PagerDutyConnectorRoutesTest extends ConnectorRoutesTest {

    @InjectMock
    SecretsLoader secretsLoader;

    @Override
    protected String getMockEndpointPattern() {
        return "https://foo.bar*";
    }

    @Override
    protected String getMockEndpointUri() {
        return "mock:https:foo.bar";
    }

    @Override
    protected JsonObject buildIncomingPayload(String targetUrl) {
        return createCloudEventData(targetUrl);
    }

    @Override
    protected Predicate checkOutgoingPayload(JsonObject incomingPayload) {

        JsonObject expectedPayload = incomingPayload.copy();
        expectedPayload.remove(PagerDutyCloudEventDataExtractor.NOTIF_METADATA);

        return exchange -> {
            String outgoingPayload = exchange.getIn().getBody(String.class);

            assertEquals(DEFAULT_ACCOUNT_ID, exchange.getProperty(ACCOUNT_ID, String.class));
            assertEquals(expectedPayload.encode(), outgoingPayload);
            assertEquals(123L, exchange.getProperty(SECRET_ID, Long.class));
            assertEquals(SECRET_TOKEN, exchange.getProperty(AUTHENTICATION_TYPE, AuthenticationType.class));

            // In case of assertion failure, this return value won't be used.
            return true;
        };
    }

    @Test
    void testMissingPayloadParameter() throws Exception {
        JsonObject expectedPayload = buildIncomingPayload(getMockServerUrl());
        expectedPayload.remove("payload");
        testMissingParameters(expectedPayload, "The 'payload' field is required");
    }

    @Test
    void testMissingPayloadSummaryParameter() throws Exception {
        JsonObject expectedPayload = buildIncomingPayload(getMockServerUrl());
        JsonObject payload = (JsonObject) expectedPayload.remove("payload");
        payload.remove("summary");
        expectedPayload.put("payload", payload);

        testMissingParameters(expectedPayload, "The alert summary field is required");
    }

    @Test
    void testMissingPayloadSeverityParameter() throws Exception {
        JsonObject expectedPayload = buildIncomingPayload(getMockServerUrl());
        JsonObject payload = (JsonObject) expectedPayload.remove("payload");
        payload.remove("severity");
        expectedPayload.put("payload", payload);

        testMissingParameters(expectedPayload, "The alert severity field is required");
    }

    @Test
    void testMissingPayloadSourceParameter() throws Exception {
        JsonObject expectedPayload = buildIncomingPayload(getMockServerUrl());
        JsonObject payload = (JsonObject) expectedPayload.remove("payload");
        payload.remove("source");
        expectedPayload.put("payload", payload);

        testMissingParameters(expectedPayload, "The alert source field is required");

    }

    @Test
    void testMissingEventActionParameter() throws Exception {
        JsonObject expectedPayload = buildIncomingPayload(getMockServerUrl());
        expectedPayload.remove("event_action");
        testMissingParameters(expectedPayload, "The 'event_action' field is required");
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
