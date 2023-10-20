package com.redhat.cloud.notifications.connector.drawer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.connector.ConnectorRoutesTest;
import com.redhat.cloud.notifications.connector.TestLifecycleManager;
import com.redhat.cloud.notifications.connector.drawer.config.DrawerConnectorConfig;
import com.redhat.cloud.notifications.connector.drawer.constant.ExchangeProperty;
import com.redhat.cloud.notifications.connector.drawer.model.DrawerEntryPayload;
import com.redhat.cloud.notifications.connector.drawer.model.DrawerNotificationToConnector;
import com.redhat.cloud.notifications.connector.drawer.model.DrawerUser;
import com.redhat.cloud.notifications.connector.drawer.model.RecipientSettings;
import com.redhat.cloud.notifications.connector.drawer.recipients.RecipientsResolverService;
import com.redhat.cloud.notifications.connector.drawer.recipients.pojo.RecipientsQuery;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.CONNECTOR_TO_ENGINE;
import static com.redhat.cloud.notifications.connector.ConnectorToEngineRouteBuilder.SUCCESS;
import static com.redhat.cloud.notifications.connector.EngineToConnectorRouteBuilder.ENGINE_TO_CONNECTOR;
import static com.redhat.cloud.notifications.connector.drawer.DrawerRouteBuilder.CONNECTOR_TO_DRAWER;
import static org.apache.camel.builder.AdviceWith.adviceWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class DrawerConnectorRoutesTest extends ConnectorRoutesTest {

    @Inject
    DrawerConnectorConfig drawerConnectorConfig;

    @InjectMock
    @RestClient
    RecipientsResolverService recipientsResolverService;

    @Override
    protected String getMockEndpointPattern() {
        return null;
    }

    @Override
    protected String getMockEndpointUri() {
        return null;
    }

    @Override
    protected JsonObject buildIncomingPayload(String targetUrl) {
        return null;
    }

    @Disabled(value = "Not applicable on drawer use case")
    protected void testFailedNotificationError500() {
    }

    @Disabled(value = "Not applicable on drawer use case")
    protected void testFailedNotificationError404() {
    }

    @Disabled(value = "Not applicable on drawer use case")
    protected void testRedeliveredNotification() { }

    @Disabled(value = "Not applicable on drawer use case")
    protected void testSuccessfulNotification() { }

    final String RECIPIENTS_RESOLVER_EXCEPTION_MESSAGE = "Error invoking Recipients-resolver service";

    static final DrawerNotificationToConnector notification = buildTestDrawerNotificationToConnector();

    protected JsonObject buildIncomingPayload() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonObject jsobj = new JsonObject(objectMapper.writeValueAsString(notification));
        jsobj.put("org_id", notification.orgId());
        return jsobj;
    }

    private static DrawerNotificationToConnector buildTestDrawerNotificationToConnector() {
        String orgId = "123456";
        DrawerEntryPayload drawerEntryPayload = new DrawerEntryPayload();
        drawerEntryPayload.setDescription("DataToSend");
        drawerEntryPayload.setId(UUID.fromString("3ccfb747-610d-42e9-97de-05d43d07319d"));
        drawerEntryPayload.setSource("My app");
        drawerEntryPayload.setTitle("the title");

        RecipientSettings recipientSettings = new RecipientSettings();
        return new DrawerNotificationToConnector(orgId, drawerEntryPayload, Set.of(recipientSettings), List.of("user-1", "user-2"));
    }


    private RecipientsQuery getRecipientsQuery() {
        RecipientsQuery recipientsQuery = new RecipientsQuery();
        recipientsQuery.setSubscribers(Set.copyOf(notification.unsubscribers()));
        recipientsQuery.setOrgId(notification.orgId());
        recipientsQuery.setRecipientSettings(Set.copyOf(notification.recipientSettings()));
        recipientsQuery.setOptIn(false);
        return recipientsQuery;
    }

    @Override
    protected Predicate checkOutgoingPayload(JsonObject incomingPayload) {
        return exchange -> {
            String outgoingPayload = exchange.getIn().getBody(String.class);
            return outgoingPayload.equals(incomingPayload.getString("message"));
        };
    }

    @Test
    void testSuccessFulNotificationWithoutRecipient() throws Exception {
        RecipientsQuery recipientsQuery = getRecipientsQuery();

        when(recipientsResolverService.getRecipients(recipientsQuery)).thenReturn(new ArrayList<>());

        JsonObject jsonObject = testSuccessfulDrawerNotification(0);
        JsonObject data = new JsonObject(jsonObject.getString("data"));
        JsonArray recipientsList = data.getJsonObject("details").getJsonArray(ExchangeProperty.RESOLVED_RECIPIENT_LIST);
        assertEquals(0, recipientsList.size());
    }

    @Test
    void testSuccessFulNotificationWithTwoRecipients() throws Exception {

        RecipientsQuery recipientsQuery = getRecipientsQuery();

        DrawerUser user1 = new DrawerUser();
        user1.setId(UUID.randomUUID().toString());
        user1.setUsername("username-1");
        DrawerUser user2 = new DrawerUser();
        user2.setId(UUID.randomUUID().toString());
        user2.setUsername("username-2");
        when(recipientsResolverService.getRecipients(recipientsQuery)).thenReturn(List.of(user1, user2));

        JsonObject jsonObject = testSuccessfulDrawerNotification(2);
        JsonObject data = new JsonObject(jsonObject.getString("data"));
        JsonArray recipientsList = data.getJsonObject("details").getJsonArray(ExchangeProperty.RESOLVED_RECIPIENT_LIST);
        assertEquals(2, recipientsList.size());
    }

    @Test
    void testFailureNotification() throws Exception {
        given(recipientsResolverService.getRecipients(any())).willAnswer(invocation -> { throw new Exception(RECIPIENTS_RESOLVER_EXCEPTION_MESSAGE); });

        JsonObject jsonObject = testFailedNotification();
        JsonObject data = new JsonObject(jsonObject.getString("data"));
        JsonArray recipientsList = data.getJsonObject("details").getJsonArray(ExchangeProperty.RESOLVED_RECIPIENT_LIST);
        assertNull(recipientsList);
    }

    @Override
    protected JsonObject testFailedNotification() throws Exception {

        mockKafkaSourceEndpoint(); // This is the entry point of the connector.
        MockEndpoint kafkaSinkMockEndpoint = mockKafkaSinkEndpoint(); // This is where the return message to the engine is sent.

        JsonObject incomingPayload = buildIncomingPayload();

        String cloudEventId = sendMessageToKafkaSource(incomingPayload);

        JsonObject outcomingPayload = assertKafkaSinkIsSatisfied(cloudEventId, kafkaSinkMockEndpoint, false, null, "java.lang.Exception: " + RECIPIENTS_RESOLVER_EXCEPTION_MESSAGE);

        checkRouteMetrics(ENGINE_TO_CONNECTOR, 1, 1, 1);
        if (isConnectorRouteFailureHandled()) {
            checkRouteMetrics(connectorConfig.getConnectorName(), 1, 1, 1);
        } else {
            checkRouteMetrics(connectorConfig.getConnectorName(), 0, 0, 1);
        }
        checkRouteMetrics(SUCCESS, 0, 0, 0);
        checkRouteMetrics(CONNECTOR_TO_ENGINE, 0, 1, 1);
        micrometerAssertionHelper.assertCounterIncrement(connectorConfig.getRedeliveryCounterName(), 0);
        return outcomingPayload;
    }

    private JsonObject testSuccessfulDrawerNotification(int expectedMessagesOnDrawerTopic) throws Exception {

        mockKafkaSourceEndpoint(); // This is the entry point of the connector.
        MockEndpoint kafkaSinkMockEndpointDrawer = mockKafkaSinkEndpointDrawer(expectedMessagesOnDrawerTopic); // This is where the return message to the engine is sent.
        MockEndpoint kafkaSinkMockEndpoint = mockKafkaSinkEndpoint(); // This is where the return message to the engine is sent.

        JsonObject incomingPayload = buildIncomingPayload();

        String cloudEventId = sendMessageToKafkaSource(incomingPayload);

        JsonObject jsonObject = assertKafkaSinkIsSatisfied(cloudEventId, kafkaSinkMockEndpoint, true, null, "Event " + cloudEventId + " sent successfully");
        assertKafkaSinkDrawerIsSatisfied(kafkaSinkMockEndpointDrawer);

        checkRouteMetrics(ENGINE_TO_CONNECTOR, 0, 1, 1);
        checkRouteMetrics(connectorConfig.getConnectorName(), 0, 1, 1);
        checkRouteMetrics(SUCCESS, 0, 1, 1);
        checkRouteMetrics(CONNECTOR_TO_ENGINE, 0, 1, 1);
        micrometerAssertionHelper.assertCounterIncrement(connectorConfig.getRedeliveryCounterName(), 0);
        return jsonObject;
    }

    protected MockEndpoint mockKafkaSinkEndpointDrawer(int expectedMessagesOnDrawerTopic) throws Exception {
        adviceWith(CONNECTOR_TO_DRAWER, context(), new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                mockEndpointsAndSkip("kafka:" + drawerConnectorConfig.getOutgoingDrawerTopic());
            }
        });

        MockEndpoint kafkaEndpoint = getMockEndpoint("mock:kafka:" + drawerConnectorConfig.getOutgoingDrawerTopic());
        kafkaEndpoint.expectedMessageCount(expectedMessagesOnDrawerTopic);
        return kafkaEndpoint;
    }

    protected static void assertKafkaSinkDrawerIsSatisfied(MockEndpoint kafkaSinkMockDrawerEndpoint) throws InterruptedException {

        kafkaSinkMockDrawerEndpoint.assertIsSatisfied();

        for (Exchange exchange : kafkaSinkMockDrawerEndpoint.getReceivedExchanges()) {
            JsonObject payload = new JsonObject(exchange.getIn().getBody(String.class));

            assertEquals("com.redhat.console.notifications.drawer", payload.getString("type"));
            assertEquals("1.0.2", payload.getString("specversion"));
            assertNotNull(payload.getString("id"));
            assertEquals("urn:redhat:source:notifications:drawer", payload.getString("source"));
            assertNotNull(payload.getString("time"));

            JsonObject data = payload.getJsonObject("data");

            assertEquals(1, data.getJsonArray("organizations").size());
            assertEquals(1, data.getJsonArray("users").size());

            JsonObject secondPayloadLevel = data.getJsonObject("payload");
            assertNotNull(secondPayloadLevel.getString("id"));
            assertNotEquals(payload.getString("id"), secondPayloadLevel.getString("id"));
            assertEquals(false, secondPayloadLevel.getBoolean("read"));

            assertEquals(notification.drawerEntryPayload().getDescription(), secondPayloadLevel.getString("description"));
            assertEquals(notification.drawerEntryPayload().getSource(), secondPayloadLevel.getString("source"));
            assertEquals(notification.drawerEntryPayload().getTitle(), secondPayloadLevel.getString("title"));
        }
    }
}
