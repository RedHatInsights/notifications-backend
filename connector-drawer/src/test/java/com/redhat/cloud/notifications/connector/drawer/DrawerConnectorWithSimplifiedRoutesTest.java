package com.redhat.cloud.notifications.connector.drawer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.MockServerLifecycleManager;
import com.redhat.cloud.notifications.connector.drawer.config.DrawerConnectorConfig;
import com.redhat.cloud.notifications.connector.drawer.constant.ExchangeProperty;
import com.redhat.cloud.notifications.connector.drawer.model.DrawerEntryPayload;
import com.redhat.cloud.notifications.connector.drawer.model.DrawerNotificationToConnector;
import com.redhat.cloud.notifications.connector.drawer.model.DrawerUser;
import com.redhat.cloud.notifications.connector.drawer.model.RecipientSettings;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.ce.OutgoingCloudEventMetadata;
import io.smallrye.reactive.messaging.ce.impl.DefaultOutgoingCloudEventMetadata;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.MediaType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.connector.ConnectorRoutesTest.KAFKA_SOURCE_MOCK;
import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.CONNECTOR_TO_ENGINE;
import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.SUCCESS;
import static com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder.ENGINE_TO_CONNECTOR;
import static com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder.KAFKA_REINJECTION;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.OUTCOME;
import static com.redhat.cloud.notifications.connector.IncomingCloudEventFilter.X_RH_NOTIFICATIONS_CONNECTOR_HEADER;
import static com.redhat.cloud.notifications.connector.IncomingCloudEventProcessor.CLOUD_EVENT_DATA;
import static com.redhat.cloud.notifications.connector.IncomingCloudEventProcessor.CLOUD_EVENT_ID;
import static com.redhat.cloud.notifications.connector.IncomingCloudEventProcessor.CLOUD_EVENT_TYPE;
import static com.redhat.cloud.notifications.connector.OutgoingCloudEventBuilder.CE_SPEC_VERSION;
import static com.redhat.cloud.notifications.connector.OutgoingCloudEventBuilder.CE_TYPE;
import static org.apache.camel.builder.AdviceWith.adviceWith;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockserver.model.HttpResponse.response;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class DrawerConnectorWithSimplifiedRoutesTest extends CamelQuarkusTestSupport {

    @InjectSpy
    DrawerConnectorConfig drawerConnectorConfig;

    static MockEndpoint kafkaConnectorToEngine;
    static MockEndpoint kafkaEngineToConnector;
    static MockEndpoint kafkaReinjection;

    @Inject
    protected MicrometerAssertionHelper micrometerAssertionHelper;

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    protected InMemorySink<JsonObject> inMemoryToCamelSink;

    @PostConstruct
    void postConstruct() {
        inMemoryToCamelSink = inMemoryConnector.sink(DrawerProcessor.DRAWER_CHANNEL);
    }

    @BeforeEach
    void beforeEach() {
        saveRoutesMetrics(
            ENGINE_TO_CONNECTOR,
            drawerConnectorConfig.getConnectorName(),
            SUCCESS,
            CONNECTOR_TO_ENGINE
        );
        micrometerAssertionHelper.saveCounterValuesBeforeTest(drawerConnectorConfig.getRedeliveryCounterName());
    }

    void initCamelRoutes() throws Exception {

        adviceWith(CONNECTOR_TO_ENGINE, context(), new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                mockEndpointsAndSkip("kafka:" + drawerConnectorConfig.getOutgoingKafkaTopic());
            }
        });

        adviceWith(ENGINE_TO_CONNECTOR, context(), new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                replaceFromWith(KAFKA_SOURCE_MOCK);
            }
        });

        kafkaConnectorToEngine = getMockEndpoint("mock:kafka:" + drawerConnectorConfig.getOutgoingKafkaTopic());
        kafkaEngineToConnector = getMockEndpoint("mock:" + KAFKA_SOURCE_MOCK);
        kafkaReinjection = getMockEndpoint("mock:kafka:" + KAFKA_REINJECTION);
    }

    static final DrawerNotificationToConnector notification = buildTestDrawerNotificationToConnector();

    private String buildIncomingPayloadAndSendIt() throws JsonProcessingException {
        final ObjectMapper objectMapper = new ObjectMapper();
        final JsonObject payload = new JsonObject(objectMapper.writeValueAsString(notification));
        payload.put("org_id", notification.orgId());

        final String cloudEventId = UUID.randomUUID().toString();

        final JsonObject cloudEvent = new JsonObject();
        cloudEvent.put(CLOUD_EVENT_ID, cloudEventId);
        cloudEvent.put(CLOUD_EVENT_TYPE, "com.redhat.console.notification.toCamel." + drawerConnectorConfig.getConnectorName());
        cloudEvent.put(CLOUD_EVENT_DATA, JsonObject.mapFrom(payload));

        final Map<String, Object> headers = new HashMap<>();
        headers.put(X_RH_NOTIFICATIONS_CONNECTOR_HEADER, drawerConnectorConfig.getConnectorName());
        template.sendBodyAndHeaders(KAFKA_SOURCE_MOCK, cloudEvent.encode(), headers);

        return cloudEventId;
    }

    private static DrawerNotificationToConnector buildTestDrawerNotificationToConnector() {
        String orgId = "123456";
        DrawerEntryPayload drawerEntryPayload = new DrawerEntryPayload();
        drawerEntryPayload.setDescription("DataToSend");
        drawerEntryPayload.setEventId(UUID.fromString("3ccfb747-610d-42e9-97de-05d43d07319d"));
        drawerEntryPayload.setSource("My app");
        drawerEntryPayload.setTitle("the title");
        drawerEntryPayload.setBundle("My Bundle");

        RecipientSettings recipientSettings = new RecipientSettings();
        return new DrawerNotificationToConnector(orgId, drawerEntryPayload, Set.of(recipientSettings), List.of("user-1", "user-2"), new JsonObject(), ActionTemplateHelper.jsonActionToMap(ActionTemplateHelper.actionAsJson));
    }

    private HttpRequest getMockHttpRequest(String path, ExpectationResponseCallback expectationResponseCallback) {
        MockServerLifecycleManager.getClient().reset();
        HttpRequest postReq = new HttpRequest()
            .withPath(path)
            .withMethod("PUT");
        MockServerLifecycleManager.getClient()
            .withSecure(false)
            .when(postReq)
            .respond(expectationResponseCallback);
        return postReq;
    }

    @Test
    void testSuccessFulNotificationWithoutRecipient() throws Exception {
        ExpectationResponseCallback verifyEmptyRequest = req -> response().withBody("[]").withContentType(MediaType.APPLICATION_JSON).withStatusCode(200);

        getMockHttpRequest("/internal/recipients-resolver", verifyEmptyRequest);

        JsonObject jsonObject = testSuccessfulDrawerNotification(0, 0);
        JsonObject data = new JsonObject(jsonObject.getString("data"));
        Integer nbRecipients = data.getJsonObject("details").getInteger(CloudEventHistoryBuilder.TOTAL_RECIPIENTS_KEY);
        assertEquals(0, nbRecipients);
    }

    @Test
    void testSuccessFulNotificationWithTwoRecipients() throws Exception {

        DrawerUser user1 = new DrawerUser();
        user1.setUsername("username-1");
        DrawerUser user2 = new DrawerUser();
        user2.setUsername("username-2");

        ExpectationResponseCallback verifyEmptyRequest = req -> {
            ObjectMapper objectMapper = new ObjectMapper();
            String responseBody = objectMapper.writeValueAsString(List.of(user1, user2));
            return response().withBody(responseBody).withContentType(MediaType.APPLICATION_JSON).withStatusCode(200);
        };

        getMockHttpRequest("/internal/recipients-resolver", verifyEmptyRequest);

        JsonObject jsonObject = testSuccessfulDrawerNotification(1, 2);
        JsonObject data = new JsonObject(jsonObject.getString("data"));
        Integer nbRecipients = data.getJsonObject("details").getInteger(CloudEventHistoryBuilder.TOTAL_RECIPIENTS_KEY);
        assertEquals(2, nbRecipients);
    }

    @Test
    void testFailureNotification() throws Exception {
        ExpectationResponseCallback verifyEmptyRequest = req -> response().withStatusCode(500);

        getMockHttpRequest("/internal/recipients-resolver", verifyEmptyRequest);

        // We expect the connector to retry the notifications since the mocked
        // request returns a 500.
        JsonObject jsonObject = testFailedNotification();
        JsonObject data = new JsonObject(jsonObject.getString("data"));
        JsonArray recipientsList = data.getJsonObject("details").getJsonArray(ExchangeProperty.RESOLVED_RECIPIENT_LIST);
        assertNull(recipientsList);
        Integer nbRecipients = data.getJsonObject("details").getInteger(CloudEventHistoryBuilder.TOTAL_RECIPIENTS_KEY);
        assertEquals(0, nbRecipients);
        String errorMessage = data.getJsonObject("details").getString(OUTCOME);
        assertTrue(errorMessage.contains("Internal Server Error, status code 500"));
    }

    protected JsonObject testFailedNotification() throws Exception {

        initCamelRoutes(); // This is the entry point of the connector.

        kafkaConnectorToEngine.expectedMessageCount(1);

        String cloudEventId = buildIncomingPayloadAndSendIt();

        JsonObject outcomingPayload = assertKafkaSinkIsSatisfied(cloudEventId, kafkaConnectorToEngine, false);

        checkRouteMetrics(ENGINE_TO_CONNECTOR, 1, 1, 1);
        checkRouteMetrics(drawerConnectorConfig.getConnectorName(), 1, 1, 1);
        checkRouteMetrics(SUCCESS, 0, 0, 0);
        checkRouteMetrics(CONNECTOR_TO_ENGINE, 0, 1, 1);
        return outcomingPayload;
    }

    private JsonObject testSuccessfulDrawerNotification(int expectedMessagesOnDrawerTopic, int expectedNumberOfUsers) throws Exception {

        initCamelRoutes(); // This is the entry point of the connector.

        kafkaConnectorToEngine.expectedMessageCount(1);

        String cloudEventId = buildIncomingPayloadAndSendIt();

        JsonObject jsonObject = assertKafkaSinkIsSatisfied(cloudEventId, kafkaConnectorToEngine, true);
        assertKafkaSinkDrawerIsSatisfied(expectedMessagesOnDrawerTopic, expectedNumberOfUsers);

        checkRouteMetrics(ENGINE_TO_CONNECTOR, 0, 1, 1);
        checkRouteMetrics(drawerConnectorConfig.getConnectorName(), 0, 1, 1);
        checkRouteMetrics(SUCCESS, 0, 1, 1);
        checkRouteMetrics(CONNECTOR_TO_ENGINE, 0, 1, 1);
        micrometerAssertionHelper.assertCounterIncrement(drawerConnectorConfig.getRedeliveryCounterName(), 0);
        return jsonObject;
    }

    protected void assertKafkaSinkDrawerIsSatisfied(int expectedMessagesOnDrawerTopic, int expectedNumberOfUsers) throws InterruptedException {

        // We need a timeout here because SEDA processes the exchange from a different thread and a race condition may happen.
        await().until(() -> inMemoryToCamelSink.received().size() == expectedMessagesOnDrawerTopic);

        //inMemoryToCamelSink.
        for (Message<JsonObject> message : inMemoryToCamelSink.received()) {
            OutgoingCloudEventMetadata<JsonObject> cloudEventMetadata  = message.getMetadata().get(DefaultOutgoingCloudEventMetadata.class).get();
            assertEquals("com.redhat.console.notifications.drawer", cloudEventMetadata.getType());
            assertEquals("1.0.2", cloudEventMetadata.getSpecVersion());
            assertNotNull(cloudEventMetadata.getId());
            assertEquals("urn:redhat:source:notifications:drawer", cloudEventMetadata.getSource().toString());
            assertNotNull(cloudEventMetadata.getTimeStamp());

            JsonObject data = message.getPayload();

            assertEquals(expectedNumberOfUsers, data.getJsonArray("usernames").size());

            JsonObject secondPayloadLevel = data.getJsonObject("payload");
            assertNotNull(secondPayloadLevel.getString("id"));
            assertEquals(false, secondPayloadLevel.getBoolean("read"));
            assertEquals("My Bundle", secondPayloadLevel.getString("bundle"));

            assertEquals(notification.drawerEntryPayload().getDescription(), secondPayloadLevel.getString("description"));
            assertEquals(notification.drawerEntryPayload().getSource(), secondPayloadLevel.getString("source"));
            assertEquals(notification.drawerEntryPayload().getTitle(), secondPayloadLevel.getString("title"));
        }
    }

    protected static JsonObject assertKafkaSinkIsSatisfied(String cloudEventId, MockEndpoint kafkaSinkMockEndpoint, boolean expectedSuccessful) throws InterruptedException {

        // We need a timeout here because SEDA processes the exchange from a different thread and a race condition may happen.
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

        return payload;
    }

    protected void checkRouteMetrics(String routeId, double expectedFailuresHandledIncrement, double expectedSucceededIncrement, double expectedTotalIncrement) {
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesFailuresHandled",  "routeId", routeId, expectedFailuresHandledIncrement);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesSucceeded", "routeId", routeId, expectedSucceededIncrement);
        micrometerAssertionHelper.assertCounterValueFilteredByTagsIncrement("CamelExchangesTotal", "routeId", routeId, expectedTotalIncrement);
    }

    protected void saveRoutesMetrics(String... routeIds) {
        for (String routeId : routeIds) {
            micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest("CamelExchangesFailuresHandled", "routeId", routeId);
            micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest("CamelExchangesSucceeded", "routeId", routeId);
            micrometerAssertionHelper.saveCounterValueFilteredByTagsBeforeTest("CamelExchangesTotal", "routeId", routeId);
        }
    }
}
