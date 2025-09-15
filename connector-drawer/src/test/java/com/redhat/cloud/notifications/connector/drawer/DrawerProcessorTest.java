package com.redhat.cloud.notifications.connector.drawer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.connector.ConnectorProcessor.ConnectorResult;
import com.redhat.cloud.notifications.connector.ExceptionProcessor;
import com.redhat.cloud.notifications.connector.drawer.config.DrawerConnectorConfig;
import com.redhat.cloud.notifications.connector.drawer.model.DrawerEntryPayload;
import com.redhat.cloud.notifications.connector.drawer.model.DrawerNotificationToConnector;
import com.redhat.cloud.notifications.connector.drawer.model.DrawerUser;
import com.redhat.cloud.notifications.connector.drawer.model.RecipientSettings;
import com.redhat.cloud.notifications.connector.drawer.recipients.pojo.RecipientsQuery;
import com.redhat.cloud.notifications.connector.drawer.recipients.recipientsresolver.RecipientsResolverService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class DrawerProcessorTest {

    @Inject
    DrawerProcessor drawerProcessor;

    @InjectMock
    @RestClient
    RecipientsResolverService recipientsResolverService;

    @InjectMock
    DrawerConnectorConfig drawerConnectorConfig;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    private InMemorySink<String> drawerSink;

    @BeforeEach
    void setUp() {
        drawerSink = inMemoryConnector.sink(DrawerProcessor.DRAWER_CHANNEL);
        drawerSink.clear();
    }

    @AfterEach
    void tearDown() {
        drawerSink.clear();
    }

    @Test
    void testProcessCloudEvent_Success() throws Exception {
        // Prepare test data
        String orgId = "test-org-123";
        String eventId = UUID.randomUUID().toString();

        DrawerEntryPayload payload = new DrawerEntryPayload();
        payload.setEventId(UUID.fromString(eventId));
        payload.setTitle("Test Notification");
        payload.setDescription("This is a test notification");
        payload.setSource("policies");
        payload.setBundle("rhel");
        payload.setCreated(LocalDateTime.now());

        DrawerNotificationToConnector drawerNotification = new DrawerNotificationToConnector(
                orgId,
                payload,
                Set.of(new RecipientSettings()),
                Set.of(),
                new JsonObject(),
                ActionTemplateHelper.jsonActionToMap(ActionTemplateHelper.actionAsJson)
        );

        JsonObject cloudEvent = JsonObject.mapFrom(drawerNotification);

        ExceptionProcessor.ProcessingContext context = mock(ExceptionProcessor.ProcessingContext.class);
        when(context.getOriginalCloudEvent()).thenReturn(cloudEvent);
        when(context.getId()).thenReturn(eventId);
        when(context.getOrgId()).thenReturn(orgId);

        // Mock recipients resolution
        DrawerUser user1 = new DrawerUser();
        user1.setUsername("user1");
        DrawerUser user2 = new DrawerUser();
        user2.setUsername("user2");
        Set<DrawerUser> mockUsers = Set.of(user1, user2);

        when(recipientsResolverService.getRecipients(any(RecipientsQuery.class))).thenReturn(mockUsers);

        // Execute
        Uni<ConnectorResult> result = drawerProcessor.processCloudEvent(context);
        ConnectorResult connectorResult = result.await().indefinitely();

        // Verify result
        assertTrue(connectorResult.isSuccessful());
        assertEquals(eventId, connectorResult.getId());
        assertEquals(orgId, connectorResult.getOrgId());
        assertTrue(connectorResult.getOutcome().contains("2 recipients"));

        // Verify message was sent to drawer channel
        List<String> messages = drawerSink.received().stream()
                .map(message -> message.getPayload())
                .toList();

        assertEquals(1, messages.size());

        JsonObject sentMessage = new JsonObject(messages.get(0));
        assertTrue(sentMessage.containsKey("usernames"));
        assertTrue(sentMessage.containsKey("payload"));

        // Verify usernames
        List<String> usernames = sentMessage.getJsonArray("usernames").getList();
        assertEquals(2, usernames.size());
        assertTrue(usernames.contains("user1"));
        assertTrue(usernames.contains("user2"));

        // Verify payload
        JsonObject sentPayload = sentMessage.getJsonObject("payload");
        assertEquals("Test Notification", sentPayload.getString("title"));
        assertEquals("This is a test notification", sentPayload.getString("description"));
        assertEquals("policies", sentPayload.getString("source"));
        assertEquals("rhel", sentPayload.getString("bundle"));
    }

    @Test
    void testProcessCloudEvent_NoRecipients() throws Exception {
        // Prepare test data
        String orgId = "test-org-123";
        String eventId = UUID.randomUUID().toString();

        DrawerEntryPayload payload = new DrawerEntryPayload();
        payload.setEventId(UUID.fromString(eventId));
        payload.setTitle("Test Notification");
        payload.setDescription("This is a test notification");

        DrawerNotificationToConnector drawerNotification = new DrawerNotificationToConnector(
                orgId,
                payload,
                Set.of(new RecipientSettings()),
                Set.of(),
                new JsonObject(),
                ActionTemplateHelper.jsonActionToMap(ActionTemplateHelper.actionAsJson)
        );

        JsonObject cloudEvent = JsonObject.mapFrom(drawerNotification);

        ExceptionProcessor.ProcessingContext context = mock(ExceptionProcessor.ProcessingContext.class);
        when(context.getOriginalCloudEvent()).thenReturn(cloudEvent);
        when(context.getId()).thenReturn(eventId);
        when(context.getOrgId()).thenReturn(orgId);

        // Mock empty recipients resolution
        when(recipientsResolverService.getRecipients(any(RecipientsQuery.class))).thenReturn(Set.of());

        // Execute
        Uni<ConnectorResult> result = drawerProcessor.processCloudEvent(context);
        ConnectorResult connectorResult = result.await().indefinitely();

        // Verify result
        assertTrue(connectorResult.isSuccessful());
        assertTrue(connectorResult.getOutcome().contains("0 recipients"));

        // Verify message was still sent to drawer channel but with empty usernames
        List<String> messages = drawerSink.received().stream()
                .map(message -> message.getPayload())
                .toList();

        assertEquals(1, messages.size());

        JsonObject sentMessage = new JsonObject(messages.get(0));
        List<String> usernames = sentMessage.getJsonArray("usernames").getList();
        assertEquals(0, usernames.size());
    }

    @Test
    void testProcessCloudEvent_CreatedTimestampSet() throws Exception {
        // Prepare test data with no created timestamp
        String orgId = "test-org-123";
        String eventId = UUID.randomUUID().toString();

        DrawerEntryPayload payload = new DrawerEntryPayload();
        payload.setEventId(UUID.fromString(eventId));
        payload.setTitle("Test Notification");
        payload.setDescription("This is a test notification");
        // No created timestamp set

        DrawerNotificationToConnector drawerNotification = new DrawerNotificationToConnector(
                orgId,
                payload,
                Set.of(new RecipientSettings()),
                Set.of(),
                new JsonObject(),
                ActionTemplateHelper.jsonActionToMap(ActionTemplateHelper.actionAsJson)
        );

        JsonObject cloudEvent = JsonObject.mapFrom(drawerNotification);

        ExceptionProcessor.ProcessingContext context = mock(ExceptionProcessor.ProcessingContext.class);
        when(context.getOriginalCloudEvent()).thenReturn(cloudEvent);
        when(context.getId()).thenReturn(eventId);
        when(context.getOrgId()).thenReturn(orgId);

        // Mock recipients resolution
        DrawerUser user1 = new DrawerUser();
        user1.setUsername("user1");
        when(recipientsResolverService.getRecipients(any(RecipientsQuery.class))).thenReturn(Set.of(user1));

        LocalDateTime beforeExecution = LocalDateTime.now();

        // Execute
        Uni<ConnectorResult> result = drawerProcessor.processCloudEvent(context);
        ConnectorResult connectorResult = result.await().indefinitely();

        LocalDateTime afterExecution = LocalDateTime.now();

        // Verify result
        assertTrue(connectorResult.isSuccessful());

        // Verify created timestamp was set
        List<String> messages = drawerSink.received().stream()
                .map(message -> message.getPayload())
                .toList();

        assertEquals(1, messages.size());

        JsonObject sentMessage = new JsonObject(messages.get(0));
        JsonObject sentPayload = sentMessage.getJsonObject("payload");

        assertNotNull(sentPayload.getString("created"));
        // Verify the timestamp is reasonable (between before and after execution)
        LocalDateTime createdTime = LocalDateTime.parse(sentPayload.getString("created"));
        assertTrue(createdTime.isAfter(beforeExecution.minusSeconds(1)));
        assertTrue(createdTime.isBefore(afterExecution.plusSeconds(1)));
    }

    @Test
    void testProcessCloudEvent_RecipientsResolverFailure() throws Exception {
        // Prepare test data
        String orgId = "test-org-123";
        String eventId = UUID.randomUUID().toString();

        DrawerEntryPayload payload = new DrawerEntryPayload();
        payload.setEventId(UUID.fromString(eventId));
        payload.setTitle("Test Notification");

        DrawerNotificationToConnector drawerNotification = new DrawerNotificationToConnector(
                orgId,
                payload,
                Set.of(new RecipientSettings()),
                Set.of(),
                new JsonObject(),
                ActionTemplateHelper.jsonActionToMap(ActionTemplateHelper.actionAsJson)
        );

        JsonObject cloudEvent = JsonObject.mapFrom(drawerNotification);

        ExceptionProcessor.ProcessingContext context = mock(ExceptionProcessor.ProcessingContext.class);
        when(context.getOriginalCloudEvent()).thenReturn(cloudEvent);
        when(context.getId()).thenReturn(eventId);
        when(context.getOrgId()).thenReturn(orgId);

        // Mock recipients resolver failure
        when(recipientsResolverService.getRecipients(any(RecipientsQuery.class)))
                .thenThrow(new RuntimeException("Recipients resolver failed"));

        // Execute and verify exception
        Uni<ConnectorResult> result = drawerProcessor.processCloudEvent(context);

        assertThrows(RuntimeException.class, () -> {
            result.await().indefinitely();
        });

        // Verify no message was sent to drawer channel
        List<String> messages = drawerSink.received().stream()
                .map(message -> message.getPayload())
                .toList();

        assertEquals(0, messages.size());
    }

    @Test
    void testProcessCloudEvent_InvalidJsonPayload() throws Exception {
        // Prepare invalid data
        String orgId = "test-org-123";
        String eventId = UUID.randomUUID().toString();

        // Invalid JSON that can't be parsed as DrawerNotificationToConnector
        JsonObject invalidCloudEvent = new JsonObject().put("invalid", "data");

        ExceptionProcessor.ProcessingContext context = mock(ExceptionProcessor.ProcessingContext.class);
        when(context.getOriginalCloudEvent()).thenReturn(invalidCloudEvent);
        when(context.getId()).thenReturn(eventId);
        when(context.getOrgId()).thenReturn(orgId);

        // Execute and verify exception
        Uni<ConnectorResult> result = drawerProcessor.processCloudEvent(context);

        assertThrows(RuntimeException.class, () -> {
            result.await().indefinitely();
        });

        // Verify no message was sent to drawer channel
        List<String> messages = drawerSink.received().stream()
                .map(message -> message.getPayload())
                .toList();

        assertEquals(0, messages.size());
    }

    @Test
    void testProcessCloudEvent_VerifyRecipientsQueryMapping() throws Exception {
        // Prepare test data with specific recipient settings
        String orgId = "test-org-456";
        String eventId = UUID.randomUUID().toString();

        RecipientSettings recipientSettings = new RecipientSettings(true, false, null, Set.of("admin1", "admin2"));
        Set<String> unsubscribers = Set.of("unsubscribed-user");
        JsonObject authCriteria = new JsonObject().put("permission", "read");

        DrawerEntryPayload payload = new DrawerEntryPayload();
        payload.setEventId(UUID.fromString(eventId));
        payload.setTitle("Admin Notification");

        DrawerNotificationToConnector drawerNotification = new DrawerNotificationToConnector(
                orgId,
                payload,
                Set.of(recipientSettings),
                unsubscribers,
                authCriteria,
                ActionTemplateHelper.jsonActionToMap(ActionTemplateHelper.actionAsJson)
        );

        JsonObject cloudEvent = JsonObject.mapFrom(drawerNotification);

        ExceptionProcessor.ProcessingContext context = mock(ExceptionProcessor.ProcessingContext.class);
        when(context.getOriginalCloudEvent()).thenReturn(cloudEvent);
        when(context.getId()).thenReturn(eventId);
        when(context.getOrgId()).thenReturn(orgId);

        // Mock recipients resolution
        DrawerUser admin = new DrawerUser();
        admin.setUsername("admin1");
        when(recipientsResolverService.getRecipients(any(RecipientsQuery.class))).thenReturn(Set.of(admin));

        // Execute
        Uni<ConnectorResult> result = drawerProcessor.processCloudEvent(context);
        result.await().indefinitely();

        // Verify recipients query was called with correct parameters
        verify(recipientsResolverService).getRecipients(argThat(query -> {
            return orgId.equals(query.orgId) &&
                   query.recipientSettings.equals(Set.of(recipientSettings)) &&
                   query.unsubscribers.equals(unsubscribers) &&
                   query.authorizationCriteria.equals(authCriteria);
        }));
    }
}
