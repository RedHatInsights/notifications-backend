package com.redhat.cloud.notifications.connector.email;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.connector.ConnectorConfig;
import com.redhat.cloud.notifications.connector.Constants;
import com.redhat.cloud.notifications.connector.ExchangeProperty;
import com.redhat.cloud.notifications.connector.IncomingCloudEventFilter;
import com.redhat.cloud.notifications.connector.payload.PayloadDetails;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder.ENGINE_TO_CONNECTOR;
import static com.redhat.cloud.notifications.connector.IncomingCloudEventProcessor.CLOUD_EVENT_DATA;
import static com.redhat.cloud.notifications.connector.IncomingCloudEventProcessor.CLOUD_EVENT_ID;
import static com.redhat.cloud.notifications.connector.IncomingCloudEventProcessor.CLOUD_EVENT_TYPE;

@QuarkusTest
public class ConnectorRoutesRHCLOUD33142Test extends CamelQuarkusTestSupport {
    @Inject
    ConnectorConfig connectorConfig;

    @Inject
    ObjectMapper objectMapper;

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    /**
     * Disables the route builder to ensure that the Camel Context does not get
     * started before the routes have been advised. More information is
     * available at the <a href="https://people.apache.org/~dkulp/camel/camel-test.html">dkulp's Apache Camel Test documentation page</a>.
     * @return {@code false} in order to stop the Camel Context from booting
     * before the routes have been advised.
     */
    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    /**
     * Tests that when the incoming Kafka message contains the payload's ID in
     * a header, then the payload itself gets fetched from the engine. The
     * reason the test is in this single connector is that we wanted to mock
     * both ends of the "ENGINE_TO_CONNECTOR" route in the "connector-common"
     * module's tests, but by doing that it made the rest of the tests fail
     * because when "advicing" some endpoint we advice it for all of the tests.
     * Also, each connector has its own particularities when it comes to
     * extracting the payload, so this meant that we'd have to implement the
     * same test or a generic one for every single connector to make everything
     * work, when the purpose of this test is to just verify that the common
     * route calls the engine whenever the payload needs to be fetched from
     * there.
     *
     * @throws Exception if any unexpected error occurs.
     */
    @Test
    void testPayloadFetchedFromEngine() throws Exception {
        // Replace the route's "from" statement with a direct endpoint we can
        // send exchanges to.
        AdviceWith.adviceWith(this.context(), ENGINE_TO_CONNECTOR, a -> a.replaceFromWith("direct:kafka-mock"));

        // Also, replace the last "to" statement with a mock so that we can
        // verify that the exchange contains the expected elements.
        AdviceWith.adviceWith(this.context(), ENGINE_TO_CONNECTOR, a -> a.weaveByToUri(String.format("seda://%s", ENGINE_TO_CONNECTOR)).replace().to(String.format("mock:seda:%s", ENGINE_TO_CONNECTOR)));

        // Simulate that the engine is returning the payload.
        final JsonObject payload = new JsonObject();
        payload.put("recipient_settings", new JsonArray());
        payload.put("subscribers", new JsonArray());
        payload.put("unsubscribers", new JsonArray());
        payload.put("email_body", new JsonObject());
        payload.put("email_subject", new JsonObject());
        payload.put("email_sender", new JsonObject());
        payload.put("subscribed_by_default", true);

        final PayloadDetails payloadDetails = new PayloadDetails(payload.encode());
        final String serializedPayload = this.objectMapper.writeValueAsString(payloadDetails);

        // Simulate that the engine's HTTP endpoint returns our payload.
        AdviceWith.adviceWith(this.context(), ENGINE_TO_CONNECTOR, a -> a
            .weaveByToUri(
                String.format("http://%s", this.connectorConfig.getNotificationsEngineHostname())
            ).replace()
            .setBody(new ConstantExpression(serializedPayload))
        );

        // Get the last "to" statement.
        final MockEndpoint mockedLastToStatement = this.getMockEndpoint(String.format("mock:seda:%s", ENGINE_TO_CONNECTOR));
        mockedLastToStatement.expectedMessageCount(1);
        mockedLastToStatement.setResultWaitTime(TimeUnit.SECONDS.toMillis(15));

        // Prepare the exchange and send the message to the route under test.
        final String payloadId = UUID.randomUUID().toString();

        final JsonObject cloudEvent = new JsonObject();
        cloudEvent.put(CLOUD_EVENT_ID, UUID.randomUUID().toString());
        cloudEvent.put(CLOUD_EVENT_TYPE, "com.redhat.console.notification.toCamel." + this.connectorConfig.getConnectorName());
        cloudEvent.put(CLOUD_EVENT_DATA, new JsonObject());

        final Exchange exchange = this.createExchangeWithBody("");
        exchange.getMessage().setHeader(IncomingCloudEventFilter.X_RH_NOTIFICATIONS_CONNECTOR_HEADER, this.connectorConfig.getConnectorName());
        exchange.getMessage().setHeader(Constants.X_RH_NOTIFICATIONS_CONNECTOR_PAYLOAD_ID_HEADER, payloadId);
        exchange.getMessage().setBody(cloudEvent.encode());

        this.template.send("direct:kafka-mock", exchange);

        // Assert that we got the exchange with the full payload.
        mockedLastToStatement.assertIsSatisfied();

        final Exchange receivedExchange = mockedLastToStatement.getReceivedExchanges().getFirst();

        // Assert that the exchange contains the "payload ID" property.
        Assertions.assertEquals(payloadId, receivedExchange.getProperty(ExchangeProperty.PAYLOAD_ID, String.class));

        // Assert that the "data" key of the Json Object contains the payload
        // we fetched from the engine.
        final String messageBody = receivedExchange.getMessage().getBody(String.class);
        final JsonObject messageBodyJson = new JsonObject(messageBody);
        final JsonObject data = messageBodyJson.getJsonObject("data");

        Assertions.assertEquals(payload.encode(), data.encode());
    }
}
