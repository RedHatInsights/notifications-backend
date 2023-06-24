package com.redhat.cloud.notifications.connector;

import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.MockServerLifecycleManager;
import io.vertx.core.json.JsonObject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.model.HttpResponse;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.UUID;

import static com.redhat.cloud.notifications.MockServerLifecycleManager.getClient;
import static com.redhat.cloud.notifications.MockServerLifecycleManager.getMockServerUrl;
import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.CONNECTOR_TO_ENGINE;
import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.SUCCESS;
import static com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder.ENGINE_TO_CONNECTOR;
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
    protected static final String REMOTE_SERVER_PATH = "/some/path";

    @Inject
    protected ConnectorConfig connectorConfig;

    @Inject
    protected MicrometerAssertionHelper micrometerAssertionHelper;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    protected abstract String getOriginalEndpointPattern();

    protected abstract String getMockEndpointUri();

    protected abstract JsonObject buildNotification(String targetUrl);

    /*
     * This is a workaround for the Slack connector where metrics are incremented in a different way
     * than all other connectors. This weird behavior is caused by the exception thrown by the Slack
     * component from Camel. It may or may not be fixed in our code in the future...
     */
    protected boolean isConnectorRouteFailureHandled() {
        return true;
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
    void testSuccessfulNotification() throws Exception {

        mockKafkaSourceEndpoint(); // This is the entry point of the connector.
        MockEndpoint remoteServerMockEndpoint = mockRemoteServerEndpoint(); // This is where the notification is sent.
        MockEndpoint kafkaSinkMockEndpoint = mockKafkaSinkEndpoint(); // This is where the return message to the engine is sent.

        JsonObject notification = buildNotification(getOriginalEndpointPattern());
        remoteServerMockEndpoint.expectedBodiesReceived(notification.getString("message"));

        String cloudEventId = sendMessageToKafkaSource(notification);

        remoteServerMockEndpoint.assertIsSatisfied();
        assertKafkaSinkIsSatisfied(cloudEventId, notification, kafkaSinkMockEndpoint, true, "Event " + cloudEventId + " sent successfully");

        checkRouteMetrics(ENGINE_TO_CONNECTOR, 0, 1, 1);
        checkRouteMetrics(connectorConfig.getConnectorName(), 0, 1, 1);
        checkRouteMetrics(SUCCESS, 0, 1, 1);
        checkRouteMetrics(CONNECTOR_TO_ENGINE, 0, 1, 1);
        micrometerAssertionHelper.assertCounterIncrement(connectorConfig.getRedeliveryCounterName(), 0);
    }

    @Test
    void testFailedNotification() throws Exception {

        mockKafkaSourceEndpoint(); // This is the entry point of the connector.
        String remoteServerPath = mockRemoteServer500();
        MockEndpoint kafkaSinkMockEndpoint = mockKafkaSinkEndpoint(); // This is where the return message to the engine is sent.

        JsonObject notification = buildNotification(remoteServerPath);

        String cloudEventId = sendMessageToKafkaSource(notification);

        assertKafkaSinkIsSatisfied(cloudEventId, notification, kafkaSinkMockEndpoint, false, "HTTP operation failed", "Error POSTing to Slack API");

        checkRouteMetrics(ENGINE_TO_CONNECTOR, 1, 1, 1);
        if (isConnectorRouteFailureHandled()) {
            checkRouteMetrics(connectorConfig.getConnectorName(), 1, 1, 1);
        } else {
            checkRouteMetrics(connectorConfig.getConnectorName(), 0, 0, 1);
        }
        checkRouteMetrics(SUCCESS, 0, 0, 0);
        checkRouteMetrics(CONNECTOR_TO_ENGINE, 0, 1, 1);
        micrometerAssertionHelper.assertCounterIncrement(connectorConfig.getRedeliveryCounterName(), 0);
    }

    @Test
    void testRedeliveredNotification() throws Exception {

        mockKafkaSourceEndpoint(); // This is the entry point of the connector.
        String remoteServerPath = mockRemoteServerNetworkFailure();
        MockEndpoint kafkaSinkMockEndpoint = mockKafkaSinkEndpoint(); // This is where the return message to the engine is sent.

        JsonObject notification = buildNotification(remoteServerPath);

        String cloudEventId = sendMessageToKafkaSource(notification);

        assertKafkaSinkIsSatisfied(cloudEventId, notification, kafkaSinkMockEndpoint, false, "unexpected end of stream", "localhost:" + MockServerLifecycleManager.getClient().getPort() + " failed to respond");
        getClient().verify(request().withMethod("POST").withPath(REMOTE_SERVER_PATH), atLeast(3));

        checkRouteMetrics(ENGINE_TO_CONNECTOR, 1, 1, 1);
        checkRouteMetrics(connectorConfig.getConnectorName(), 1, 1, 1);
        checkRouteMetrics(SUCCESS, 0, 0, 0);
        checkRouteMetrics(CONNECTOR_TO_ENGINE, 0, 1, 1);
        micrometerAssertionHelper.assertCounterIncrement(connectorConfig.getRedeliveryCounterName(), 2);
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
                mockEndpointsAndSkip(getOriginalEndpointPattern());
            }
        });
        return getMockEndpoint(getMockEndpointUri());
    }

    protected String mockRemoteServer500() {
        getClient()
                .when(request().withMethod("POST").withPath(REMOTE_SERVER_PATH))
                .respond(new HttpResponse().withStatusCode(500).withBody("My custom internal error"));
        return getMockServerUrl() + REMOTE_SERVER_PATH;
    }

    protected String mockRemoteServerNetworkFailure() {
        getClient()
                .when(request().withMethod("POST").withPath(REMOTE_SERVER_PATH))
                .error(error().withDropConnection(true));
        return getMockServerUrl() + REMOTE_SERVER_PATH;
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

    protected String sendMessageToKafkaSource(JsonObject notification) {

        String cloudEventId = UUID.randomUUID().toString();

        JsonObject cloudEvent = new JsonObject();
        cloudEvent.put(CLOUD_EVENT_ID, cloudEventId);
        cloudEvent.put(CLOUD_EVENT_TYPE, "com.redhat.console.notification.toCamel." + connectorConfig.getConnectorName());
        cloudEvent.put(CLOUD_EVENT_DATA, JsonObject.mapFrom(notification));

        template.sendBodyAndHeader(KAFKA_SOURCE_MOCK, cloudEvent.encode(), X_RH_NOTIFICATIONS_CONNECTOR_HEADER, connectorConfig.getConnectorName());

        return cloudEventId;
    }

    protected static void assertKafkaSinkIsSatisfied(String cloudEventId, JsonObject notification, MockEndpoint kafkaSinkMockEndpoint, boolean expectedSuccessful, String... expectedOutcomeStarts) throws InterruptedException {

        kafkaSinkMockEndpoint.assertIsSatisfied();

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
        assertEquals(notification.getString("webhookUrl"), data.getJsonObject("details").getString("target"));

        String outcome = data.getJsonObject("details").getString("outcome");

        if (Arrays.stream(expectedOutcomeStarts).noneMatch(outcome::startsWith)) {
            fail(String.format("Expected outcome starts: %s - Actual outcome: %s", Arrays.toString(expectedOutcomeStarts), outcome));
        }
    }
}
