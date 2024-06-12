package com.redhat.cloud.notifications.connector.google.chat;

import com.redhat.cloud.notifications.connector.ConnectorRoutesTest;
import com.redhat.cloud.notifications.connector.Constants;
import com.redhat.cloud.notifications.connector.ExchangeProperty;
import com.redhat.cloud.notifications.connector.TestLifecycleManager;
import com.redhat.cloud.notifications.connector.payload.PayloadDetails;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.language.ConstantExpression;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder.ENGINE_TO_CONNECTOR;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class GoogleChatConnectorRoutesTest extends ConnectorRoutesTest {

    @Override
    protected String getMockEndpointPattern() {
        return "https://foo.bar";
    }

    @Override
    protected String getMockEndpointUri() {
        return "mock:https:foo.bar";
    }

    @Override
    protected JsonObject buildIncomingPayload(String targetUrl) {
        JsonObject payload = new JsonObject();
        payload.put("orgId", DEFAULT_ORG_ID);
        payload.put("webhookUrl", targetUrl);
        payload.put("message", "This is a test!");
        return payload;
    }

    @Override
    protected Predicate checkOutgoingPayload(JsonObject incomingPayload) {
        return exchange -> {
            String outgoingPayload = exchange.getIn().getBody(String.class);
            return outgoingPayload.equals(incomingPayload.getString("message"));
        };
    }

    /**
     * Tests that when the incoming Kafka message contains the payload's ID in
     * a header, then the payload itself gets fetched from the engine. The
     * reason for overriding the test is that Google Chat's {@link GoogleChatCloudEventDataExtractor}
     * class just takes the "message" field of the incoming Cloud Event and
     * replaces the exchange's body with that, so we needed a particular
     * assertion for this connector's test.
     *
     * @throws Exception if any unexpected error occurs.
     */
    @Override
    @Test
    protected void testPayloadFetchedFromEngine() throws Exception {
        mockKafkaSourceEndpoint(); // This is the entry point of the connector.
        final UUID eventId = UUID.randomUUID();

        // Simulate that the engine is returning the payload.
        final String payloadContents = "{\"message\":\"Red Hat Entreprise Linux\"}";
        final PayloadDetails payloadDetails = new PayloadDetails(payloadContents);
        final String serializedPayload = this.objectMapper.writeValueAsString(payloadDetails);

        // Simulate that the engine's HTTP endpoint returns our payload.
        AdviceWith.adviceWith(this.context(), ENGINE_TO_CONNECTOR, a -> a.weaveByToUri(
                    String.format(
                        "http://%s", this.connectorConfig.getNotificationsEngineHostname())
                ).replace()
                .setBody(new ConstantExpression(serializedPayload))
        );

        // Add a mocked "to" statement after the "seda" one so that we don't
        // have to mock the last "to" statement. That would make the rest of
        // the tests fail.
        AdviceWith.adviceWith(this.context(), ENGINE_TO_CONNECTOR, a -> a.weaveAddLast().to("mock:seda:test-engine-to-connector"));

        // Get the last "to" statement.
        final MockEndpoint mockedLastToStatement = this.getMockEndpoint("mock:seda:test-engine-to-connector");
        mockedLastToStatement.expectedMessageCount(1);
        mockedLastToStatement.setResultWaitTime(TimeUnit.SECONDS.toMillis(15));

        // Send the message to the route under test.
        this.sendMessageToKafkaSource(new JsonObject(), Map.of(Constants.X_RH_NOTIFICATIONS_CONNECTOR_PAYLOAD_HEADER, eventId));

        // Assert that we got the exchange with the full payload.
        mockedLastToStatement.assertIsSatisfied();

        final Exchange receivedExchange = mockedLastToStatement.getReceivedExchanges().getFirst();

        // Assert that the exchange contains the "event ID" property.
        Assertions.assertEquals(eventId.toString(), receivedExchange.getProperty(ExchangeProperty.DATABASE_PAYLOAD_EVENT_ID, String.class));

        // Assert that the "data" key of the Json Object contains the payload
        // we fetched from the engine.
        final JsonObject payloadContentsJson = new JsonObject(serializedPayload);
        final JsonObject contents = new JsonObject(payloadContentsJson.getString("contents"));

        Assertions.assertEquals(contents.getString("message"), receivedExchange.getMessage().getBody(String.class));
    }
}
