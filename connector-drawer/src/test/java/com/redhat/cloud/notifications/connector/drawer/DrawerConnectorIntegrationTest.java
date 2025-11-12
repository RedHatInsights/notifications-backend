package com.redhat.cloud.notifications.connector.drawer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.MockServerLifecycleManager;
import com.redhat.cloud.notifications.connector.drawer.constant.Constants;
import com.redhat.cloud.notifications.connector.drawer.models.DrawerEntryPayload;
import com.redhat.cloud.notifications.connector.drawer.models.DrawerNotificationToConnector;
import com.redhat.cloud.notifications.connector.drawer.models.DrawerUser;
import com.redhat.cloud.notifications.connector.drawer.models.RecipientSettings;
import com.redhat.cloud.notifications.connector.v2.BaseConnectorIntegrationTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.ce.OutgoingCloudEventMetadata;
import io.smallrye.reactive.messaging.ce.impl.DefaultOutgoingCloudEventMetadata;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class DrawerConnectorIntegrationTest extends BaseConnectorIntegrationTest {

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    protected InMemorySink<JsonObject> inMemoryDrawerSink;

    @PostConstruct
    void postConstruct() {
        inMemoryDrawerSink = inMemoryConnector.sink(DrawerMessageHandler.DRAWER_CHANNEL);

        // Initialize the InMemory sources and sinks for reactive messaging
        incomingMessageSource = inMemoryConnector.source("incomingmessages");
        outgoingMessageSink = inMemoryConnector.sink("outgoingmessages");
    }

    @Override
    protected JsonObject buildIncomingPayload(String targetUrl) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            DrawerNotificationToConnector notification = buildTestDrawerNotificationToConnector();
            JsonObject payload = new JsonObject(objectMapper.writeValueAsString(notification));
            payload.put("org_id", notification.getOrgId());
            return payload;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected String getConnectorSpecificTargetUrl() {
        return getMockServerUrl();
    }

    private static final DrawerNotificationToConnector testNotification = buildTestDrawerNotificationToConnector();

    private static DrawerNotificationToConnector buildTestDrawerNotificationToConnector() {
        String orgId = "123456";
        DrawerEntryPayload drawerEntryPayload = new DrawerEntryPayload();
        drawerEntryPayload.setDescription("DataToSend");
        drawerEntryPayload.setEventId(UUID.fromString("3ccfb747-610d-42e9-97de-05d43d07319d"));
        drawerEntryPayload.setSource("My app");
        drawerEntryPayload.setTitle("the title");
        drawerEntryPayload.setBundle("My Bundle");

        RecipientSettings recipientSettings = new RecipientSettings();
        return new DrawerNotificationToConnector(orgId, drawerEntryPayload, Set.of(recipientSettings),
            Set.of("user-1", "user-2"), new JsonObject(), ActionTemplateHelper.jsonActionToMap(ActionTemplateHelper.actionAsJson));
    }

    private void setupMockHttpRequest(String path, String responseBody, int statusCode) {
        MockServerLifecycleManager.getClient().resetAll();
        MockServerLifecycleManager.getClient().stubFor(
            put(urlEqualTo(path))
                .willReturn(aResponse()
                    .withStatus(statusCode)
                    .withHeader("Content-Type", "application/json")
                    .withBody(responseBody))
        );
    }

    @Test
    void testSuccessfulNotificationWithoutRecipient() {
        // Mock empty recipients response
        setupMockHttpRequest("/internal/recipients-resolver", "[]", 200);

        JsonObject incomingPayload = buildIncomingPayload(getMockServerUrl());

        // Send message via InMemory messaging
        String cloudEventId = sendCloudEventMessage(incomingPayload);

        // Verify no drawer messages sent (0 recipients)
        assertEquals(0, inMemoryDrawerSink.received().size());

        // Assert successful response
        assertSuccessfulOutgoingMessage(cloudEventId, null);

        // Check metrics
        assertMetricsIncrement(1, 1, 0);
    }

    @Test
    void testSuccessfulNotificationWithTwoRecipients() throws Exception {
        // Create test users
        DrawerUser user1 = new DrawerUser();
        user1.setUsername("username-1");
        DrawerUser user2 = new DrawerUser();
        user2.setUsername("username-2");

        // Mock recipients response with 2 users
        ObjectMapper objectMapper = new ObjectMapper();
        String responseBody = objectMapper.writeValueAsString(List.of(user1, user2));

        setupMockHttpRequest("/internal/recipients-resolver", responseBody, 200);

        JsonObject incomingPayload = buildIncomingPayload(getMockServerUrl());

        // Send message via InMemory messaging
        String cloudEventId = sendCloudEventMessage(incomingPayload);

        // Verify drawer message was sent
        await().until(() -> inMemoryDrawerSink.received().size() == 1);
        assertDrawerMessage(inMemoryDrawerSink.received().get(0), 2);

        // Assert successful response
        assertSuccessfulOutgoingMessage(cloudEventId, null);

        // Check metrics
        assertMetricsIncrement(1, 1, 0);
    }

    @Test
    void testFailureNotification() {
        // Mock 500 error response
        setupMockHttpRequest("/internal/recipients-resolver", "", 500);

        JsonObject incomingPayload = buildIncomingPayload(getMockServerUrl());

        // Send message via InMemory messaging
        String cloudEventId = sendCloudEventMessage(incomingPayload);

        JsonObject message = waitForOutgoingMessage(cloudEventId);

        JsonObject details = message.getJsonObject("details");
        JsonArray recipientsList = details.getJsonArray(Constants.RESOLVED_RECIPIENT_LIST);
        assertNull(recipientsList);
        Integer nbRecipients = details.getInteger(CloudEventHistoryBuilder.TOTAL_RECIPIENTS_KEY);
        assertEquals(0, nbRecipients);
        String errorMessage = details.getString("outcome");

        // Assert failed response
        assertTrue(errorMessage.contains("500") || errorMessage.contains("Server Error"));

        // Verify no drawer messages sent due to failure
        assertEquals(0, inMemoryDrawerSink.received().size());

        // Check metrics
        assertMetricsIncrement(1, 0, 1);
    }

    private void assertDrawerMessage(Message<JsonObject> message, int expectedUserCount) {
        OutgoingCloudEventMetadata<JsonObject> cloudEventMetadata = message.getMetadata().get(DefaultOutgoingCloudEventMetadata.class).get();
        assertEquals("com.redhat.console.notifications.drawer", cloudEventMetadata.getType());
        assertEquals("1.0.2", cloudEventMetadata.getSpecVersion());
        assertNotNull(cloudEventMetadata.getId());
        assertEquals("urn:redhat:source:notifications:drawer", cloudEventMetadata.getSource().toString());
        assertNotNull(cloudEventMetadata.getTimeStamp());

        JsonObject data = message.getPayload();
        assertEquals(expectedUserCount, data.getJsonArray("usernames").size());

        JsonObject secondPayloadLevel = data.getJsonObject("payload");
        assertNotNull(secondPayloadLevel.getString("id"));
        assertEquals(false, secondPayloadLevel.getBoolean("read"));
        assertEquals("My Bundle", secondPayloadLevel.getString("bundle"));

        assertEquals(testNotification.getDrawerEntryPayload().getDescription(), secondPayloadLevel.getString("description"));
        assertEquals(testNotification.getDrawerEntryPayload().getSource(), secondPayloadLevel.getString("source"));
        assertEquals(testNotification.getDrawerEntryPayload().getTitle(), secondPayloadLevel.getString("title"));
    }

}
