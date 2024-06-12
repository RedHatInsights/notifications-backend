package com.redhat.cloud.notifications.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.MockServerLifecycleManager;
import com.redhat.cloud.notifications.connector.payload.PayloadDetails;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.model.HttpResponse;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.redhat.cloud.notifications.MockServerLifecycleManager.getClient;
import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.CONNECTOR_TO_ENGINE;
import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.SUCCESS;
import static com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder.ENGINE_TO_CONNECTOR;
import static com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder.KAFKA_REINJECTION;
import static com.redhat.cloud.notifications.connector.IncomingCloudEventFilter.X_RH_NOTIFICATIONS_CONNECTOR_HEADER;
import static com.redhat.cloud.notifications.connector.IncomingCloudEventProcessor.CLOUD_EVENT_DATA;
import static com.redhat.cloud.notifications.connector.IncomingCloudEventProcessor.CLOUD_EVENT_ID;
import static com.redhat.cloud.notifications.connector.IncomingCloudEventProcessor.CLOUD_EVENT_TYPE;
import static com.redhat.cloud.notifications.connector.OutgoingCloudEventBuilder.CE_SPEC_VERSION;
import static com.redhat.cloud.notifications.connector.OutgoingCloudEventBuilder.CE_TYPE;
import static org.apache.camel.builder.AdviceWith.adviceWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.VerificationTimes.atLeast;

public abstract class ConnectorRoutesTest extends CamelQuarkusTestSupport {

    private static final String KAFKA_SOURCE_MOCK = "direct:kafka-source-mock";

    @Inject
    protected ConnectorConfig connectorConfig;

    @Inject
    protected MicrometerAssertionHelper micrometerAssertionHelper;

    @Inject
    protected ObjectMapper objectMapper;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    protected abstract String getMockEndpointPattern();

    protected abstract String getMockEndpointUri();

    protected abstract JsonObject buildIncomingPayload(String targetUrl);

    protected abstract Predicate checkOutgoingPayload(JsonObject incomingPayload);

    protected boolean useHttps() {
        return false;
    }

    protected String getMockServerUrl() {
        String mockServerUrl = MockServerLifecycleManager.getMockServerUrl();
        return useHttps() ? mockServerUrl.replace("http:", "https:") : mockServerUrl;
    }

    protected String getRemoteServerPath() {
        return "";
    }

    @BeforeEach
    void beforeEach() {
        getClient().reset();
        saveRoutesMetrics(
                ENGINE_TO_CONNECTOR,
                connectorConfig.getConnectorName(),
                SUCCESS,
                CONNECTOR_TO_ENGINE
        );
        micrometerAssertionHelper.saveCounterValuesBeforeTest(connectorConfig.getRedeliveryCounterName());
    }

    @AfterEach
    void afterEach() {
        getClient().reset();
        micrometerAssertionHelper.clearSavedValues();
    }

    @Test
    protected void testSuccessfulNotification() throws Exception {

        mockKafkaSourceEndpoint(); // This is the entry point of the connector.
        MockEndpoint remoteServerMockEndpoint = mockRemoteServerEndpoint(); // This is where the notification is sent.
        MockEndpoint kafkaSinkMockEndpoint = mockKafkaSinkEndpoint(); // This is where the return message to the engine is sent.

        String targetUrl = "https://foo.bar";

        JsonObject incomingPayload = buildIncomingPayload(targetUrl);
        remoteServerMockEndpoint.expectedMessagesMatches(checkOutgoingPayload(incomingPayload));
        remoteServerMockEndpoint.expectedMessageCount(1);

        String cloudEventId = sendMessageToKafkaSource(incomingPayload);

        remoteServerMockEndpoint.assertIsSatisfied();
        assertKafkaSinkIsSatisfied(cloudEventId, kafkaSinkMockEndpoint, true, targetUrl + getRemoteServerPath(), "Event " + cloudEventId + " sent successfully");

        afterKafkaSinkSuccess();

        checkRouteMetrics(ENGINE_TO_CONNECTOR, 0, 1, 1);
        checkRouteMetrics(connectorConfig.getConnectorName(), 0, 1, 1);
        checkRouteMetrics(SUCCESS, 0, 1, 1);
        checkRouteMetrics(CONNECTOR_TO_ENGINE, 0, 1, 1);
        micrometerAssertionHelper.assertCounterIncrement(connectorConfig.getRedeliveryCounterName(), 0);
    }

    @Test
    protected void testFailedNotificationError500() throws Exception {
        mockRemoteServerError(500, "My custom internal error");

        // We assume that the connector extends from the "HTTP Common" module,
        // so in that case the "500" errors should trigger a redelivery. If
        // the connector you are testing does not extend that module, you will
        // have to override this test and set the expected redeliveries to
        // zero.
        testFailedNotification(this.connectorConfig.getRedeliveryMaxAttempts());
    }

    @Test
    protected void testFailedNotificationError404() throws Exception {
        mockRemoteServerError(404, "Page not found");

        // We expect the connector to not retry the notification since the
        // mocked request returns a 404.
        testFailedNotification(0);
    }


    protected JsonObject testFailedNotification(final int maxRedeliveriesCount) throws Exception {

        mockKafkaSourceEndpoint(); // This is the entry point of the connector.
        MockEndpoint kafkaSinkMockEndpoint = mockKafkaSinkEndpoint(); // This is where the return message to the engine is sent.

        JsonObject incomingPayload = buildIncomingPayload(getMockServerUrl());

        String cloudEventId = sendMessageToKafkaSource(incomingPayload);

        JsonObject outcomingPayload = assertKafkaSinkIsSatisfied(cloudEventId, kafkaSinkMockEndpoint, false, getMockServerUrl() + getRemoteServerPath(), "HTTP operation failed", "Error POSTing to Slack API");

        checkRouteMetrics(ENGINE_TO_CONNECTOR, 1, 1, 1);
        checkRouteMetrics(connectorConfig.getConnectorName(), 1, 1, 1);
        checkRouteMetrics(SUCCESS, 0, 0, 0);
        checkRouteMetrics(CONNECTOR_TO_ENGINE, 0, 1, 1);
        micrometerAssertionHelper.assertCounterIncrement(connectorConfig.getRedeliveryCounterName(), maxRedeliveriesCount);
        return outcomingPayload;
    }

    @Test
    protected void testRedeliveredNotification() throws Exception {

        mockKafkaSourceEndpoint(); // This is the entry point of the connector.
        mockRemoteServerNetworkFailure();
        MockEndpoint kafkaSinkMockEndpoint = mockKafkaSinkEndpoint(); // This is where the return message to the engine is sent.

        JsonObject incomingPayload = buildIncomingPayload(getMockServerUrl());

        String cloudEventId = sendMessageToKafkaSource(incomingPayload);

        assertKafkaSinkIsSatisfied(cloudEventId, kafkaSinkMockEndpoint, false, getMockServerUrl() + getRemoteServerPath(), "unexpected end of stream", "localhost:" + MockServerLifecycleManager.getClient().getPort() + " failed to respond");
        getClient().verify(request().withMethod("POST").withPath(getRemoteServerPath()), atLeast(3));

        checkRouteMetrics(ENGINE_TO_CONNECTOR, 1, 1, 1);
        checkRouteMetrics(connectorConfig.getConnectorName(), 1, 1, 1);
        checkRouteMetrics(SUCCESS, 0, 0, 0);
        checkRouteMetrics(CONNECTOR_TO_ENGINE, 0, 1, 1);
        micrometerAssertionHelper.assertCounterIncrement(connectorConfig.getRedeliveryCounterName(), this.connectorConfig.getRedeliveryMaxAttempts());
    }

    /**
     * Tests that when a notification fails to be delivered, the original Cloud
     * Event is reinjected to the incoming Kafka queue.
     * @throws Exception if the incoming source Kafka endpoint or the
     * reinjection route's endpoint cannot be adviced.
     */
    @Test
    protected void testSendReinjectionRoute() throws Exception {
        mockKafkaSourceEndpoint(); // This is the entry point of the connector.
        mockRemoteServerNetworkFailure();

        JsonObject incomingPayload = buildIncomingPayload(getMockServerUrl());

        // Mock the "to" Kafka endpoint in the reinjection route.
        AdviceWith.adviceWith(this.context(), KAFKA_REINJECTION, a -> a.mockEndpointsAndSkip(
            String.format("kafka:%s", this.connectorConfig.getIncomingKafkaTopic())
        ));

        // Make sure we wait for the exchange more than the delay time
        // specified in the KafkaReinjectionProcessor, as otherwise the test
        // will fail.
        final MockEndpoint incomingMockedKafkaEndpoint = this.getMockEndpoint(String.format("mock:kafka:%s", this.connectorConfig.getIncomingKafkaTopic()));
        incomingMockedKafkaEndpoint.expectedMessageCount(1);
        incomingMockedKafkaEndpoint.setResultWaitTime(TimeUnit.SECONDS.toMillis(15));

        // Send the message to the route under test.
        this.sendMessageToKafkaSource(incomingPayload, Map.of(KafkaHeader.REINJECTION_COUNT, 0));

        // Assert that we received the exchange in the incoming queue again,
        // which signals that the reinjection was successful.
        incomingMockedKafkaEndpoint.assertIsSatisfied();

        // Assert that before reinjecting the original Cloud Event we did retry
        // sending the notification.
        getClient().verify(request().withMethod("POST").withPath(getRemoteServerPath()), atLeast(3));
    }

    /**
     * Tests that when the incoming Kafka message contains the payload's ID in
     * a header, then the payload itself gets fetched from the engine.
     *
     * @throws Exception if any unexpected error occurs.
     */
    @Test
    protected void testPayloadFetchedFromEngine() throws Exception {
        mockKafkaSourceEndpoint(); // This is the entry point of the connector.
        final UUID eventId = UUID.randomUUID();

        // Simulate that the engine is returning the payload.
        final String payloadContents = "{\"flavor\":\"Red Hat Entreprise Linux\"}";
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
        final String messageBody = receivedExchange.getMessage().getBody(String.class);
        final JsonObject messageBodyJson = new JsonObject(messageBody);
        final JsonObject data = messageBodyJson.getJsonObject("data");

        Assertions.assertEquals(payloadContents, data.encode());
    }

    /**
     * Tests that when the exchange contains an event ID in the exchange's
     * property, then the final exchange that gets sent to Kafka has the
     * corresponding header with that event ID for the engine.
     * @throws Exception if any unexpected error occurs.
     */
    @Test
    protected void testSendEventIdToEngine() throws Exception {
        // Mock the last endpoint on the "connector to engine" route.
        final MockEndpoint kafkaSinkMockEndpoint = mockKafkaSinkEndpoint(); // This is the entry point of the connector.
        kafkaSinkMockEndpoint.expectedMessageCount(1);
        kafkaSinkMockEndpoint.setResultWaitTime(TimeUnit.SECONDS.toMillis(15));

        // Simulate that the exchange contains an "event ID" that was set in
        // the "engine to connector" route. Set the required properties so that
        // the "Cloud Event builder" does not complain.
        final String eventId = UUID.randomUUID().toString();

        final Exchange exchange = this.createExchangeWithBody("");
        exchange.setProperty(ExchangeProperty.START_TIME, System.currentTimeMillis());
        exchange.setProperty(ExchangeProperty.DATABASE_PAYLOAD_EVENT_ID, eventId);

        // Send the exchange.
        this.template.send(String.format("direct:%s", SUCCESS), exchange);

        kafkaSinkMockEndpoint.assertIsSatisfied();

        // Assert that the header with the event ID for the engine was set.
        final Exchange receivedExchange = kafkaSinkMockEndpoint.getReceivedExchanges().getFirst();
        Assertions.assertEquals(eventId, receivedExchange.getMessage().getHeader(Constants.X_RH_NOTIFICATIONS_CONNECTOR_PAYLOAD_HEADER));
    }

    protected void saveRoutesMetrics(String... routeIds) {
        for (String routeId : routeIds) {
            micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest("CamelExchangesFailuresHandled", "routeId", routeId);
            micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest("CamelExchangesSucceeded", "routeId", routeId);
            micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest("CamelExchangesTotal", "routeId", routeId);
        }
    }

    protected void checkRouteMetrics(String routeId, double expectedFailuresHandledIncrement, double expectedSucceededIncrement, double expectedTotalIncrement) {
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesFailuresHandled",  "routeId", routeId, expectedFailuresHandledIncrement);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesSucceeded", "routeId", routeId, expectedSucceededIncrement);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesTotal", "routeId", routeId, expectedTotalIncrement);
    }

    protected void mockKafkaSourceEndpoint() throws Exception {
        adviceWith(ENGINE_TO_CONNECTOR, context(), new AdviceWithRouteBuilder() {
            @Override
            public void configure() {
                replaceFromWith(KAFKA_SOURCE_MOCK);
            }
        });
    }

    protected MockEndpoint mockRemoteServerEndpoint() throws Exception {
        adviceWith(connectorConfig.getConnectorName(), context(), new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                mockEndpointsAndSkip(getMockEndpointPattern());
            }
        });
        return getMockEndpoint(getMockEndpointUri());
    }

    protected void mockRemoteServerError(int httpReturnCode, String bodyMessage) {
        getClient()
            .withSecure(useHttps())
            .when(request().withMethod("POST").withPath(getRemoteServerPath()))
            .respond(new HttpResponse().withStatusCode(httpReturnCode).withBody(bodyMessage));
    }

    protected void mockRemoteServerNetworkFailure() {
        getClient()
                .withSecure(useHttps())
                .when(request().withMethod("POST").withPath(getRemoteServerPath()))
                .error(error().withDropConnection(true));
    }

    protected MockEndpoint mockKafkaSinkEndpoint() throws Exception {
        adviceWith(CONNECTOR_TO_ENGINE, context(), new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                mockEndpointsAndSkip("kafka:" + connectorConfig.getOutgoingKafkaTopic());
            }
        });

        MockEndpoint kafkaEndpoint = getMockEndpoint("mock:kafka:" + connectorConfig.getOutgoingKafkaTopic());
        kafkaEndpoint.expectedMessageCount(1);

        return kafkaEndpoint;
    }

    /**
     * Sends the message to the mocked incoming Kafka queue.
     * @param incomingPayload the payload to be sent to the incoming queue.
     */
    protected String sendMessageToKafkaSource(final JsonObject incomingPayload) {
        return this.sendMessageToKafkaSource(incomingPayload, Map.of(KafkaHeader.REINJECTION_COUNT, this.connectorConfig.getKafkaMaximumReinjections() + 1));
    }

    /**
     * Sends the message to the mocked incoming Kafka queue.
     * @param incomingPayload the payload to be sent to the incoming queue.
     * @param customHeaders the custom headers to add to the incoming message.
     * @return the created Cloud Event's {@link UUID}.
     */
    protected String sendMessageToKafkaSource(final JsonObject incomingPayload, final Map<String, Object> customHeaders) {
        final String cloudEventId = UUID.randomUUID().toString();

        final JsonObject cloudEvent = new JsonObject();
        cloudEvent.put(CLOUD_EVENT_ID, cloudEventId);
        cloudEvent.put(CLOUD_EVENT_TYPE, "com.redhat.console.notification.toCamel." + connectorConfig.getConnectorName());
        cloudEvent.put(CLOUD_EVENT_DATA, JsonObject.mapFrom(incomingPayload));

        // Make sure that we specify the connector's name so that the
        // IncomingCloudEventFilter accepts the exchange.
        //
        // Also, for the majority of the tests we want to simulate that we
        // already exhausted the reinjection attempts, so that we can test
        // that the metrics are correctly asserted.
        final Map<String, Object> headers = new HashMap<>();
        headers.put(X_RH_NOTIFICATIONS_CONNECTOR_HEADER, this.connectorConfig.getConnectorName());
        headers.putAll(customHeaders);

        this.template.sendBodyAndHeaders(KAFKA_SOURCE_MOCK, cloudEvent.encode(), headers);

        return cloudEventId;
    }

    /**
     * Override this method to run any checks after the Kafka sink successfully received a message.
     */
    protected void afterKafkaSinkSuccess() {
    }

    protected static JsonObject assertKafkaSinkIsSatisfied(String cloudEventId, MockEndpoint kafkaSinkMockEndpoint, boolean expectedSuccessful, String expectedTargetUrl, String... expectedOutcomeStarts) throws InterruptedException {

        // We need a timeout here because SEDA processes the exchange from a different thread and a race condition may happen.
        kafkaSinkMockEndpoint.assertIsSatisfied(2000L);

        Exchange exchange = kafkaSinkMockEndpoint.getReceivedExchanges().get(0);
        JsonObject payload = new JsonObject(exchange.getIn().getBody(String.class));

        assertEquals(CE_TYPE, payload.getString("type"));
        assertEquals(CE_SPEC_VERSION, payload.getString("specversion"));
        assertEquals(cloudEventId, payload.getString("id"));
        assertNotNull(payload.getString("source"));
        assertNotNull(payload.getString("time"));

        JsonObject data = new JsonObject(payload.getString("data"));

        assertEquals(expectedSuccessful, data.getBoolean("successful"));
        assertNotNull(data.getString("duration"));
        assertNotNull(data.getJsonObject("details").getString("type"));
        if (null != expectedTargetUrl) {
            assertEquals(expectedTargetUrl, data.getJsonObject("details").getString("target"));
        }

        String outcome = data.getJsonObject("details").getString("outcome");

        if (Arrays.stream(expectedOutcomeStarts).noneMatch(outcome::startsWith)) {
            fail(String.format("Expected outcome starts: %s - Actual outcome: %s", Arrays.toString(expectedOutcomeStarts), outcome));
        }
        return payload;
    }
}
