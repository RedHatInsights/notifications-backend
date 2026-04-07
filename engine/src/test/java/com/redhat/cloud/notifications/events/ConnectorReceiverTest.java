package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.repositories.DrawerNotificationRepository;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.db.repositories.NotificationHistoryRepository;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.NotificationStatus;
import com.redhat.cloud.notifications.processors.drawer.DrawerProcessor;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.events.ConnectorReceiver.FROMCAMEL_CHANNEL;
import static com.redhat.cloud.notifications.events.ConnectorReceiver.MESSAGES_ERROR_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.ConnectorReceiver.MESSAGES_PROCESSED_COUNTER_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class ConnectorReceiverTest {

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    @InjectMock
    NotificationHistoryRepository notificationHistoryRepository;

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

    @InjectSpy
    CamelHistoryFillerHelper camelHistoryFillerHelper;

    @InjectSpy
    EndpointRepository endpointRepository;

    @InjectSpy
    DrawerProcessor drawerProcessor;

    @InjectMock
    DrawerNotificationRepository drawerNotificationRepository;

    final String expectedHistoryId = UUID.randomUUID().toString();

    @BeforeEach
    void beforeEach() {
        micrometerAssertionHelper.saveCounterValuesBeforeTest(
                MESSAGES_PROCESSED_COUNTER_NAME,
                MESSAGES_ERROR_COUNTER_NAME
        );
        final Endpoint endpoint = new Endpoint();
        endpoint.setId(UUID.fromString(expectedHistoryId));
        Mockito.when(notificationHistoryRepository.getEndpointForHistoryId(Mockito.eq(expectedHistoryId))).thenReturn(endpoint);
    }

    @AfterEach
    void afterEach() {
        micrometerAssertionHelper.clearSavedValues();
    }

    @Test
    void testInvalidPayload() {
        inMemoryConnector.source(FROMCAMEL_CHANNEL).send("I am not valid!");

        micrometerAssertionHelper.awaitAndAssertCounterIncrement(MESSAGES_PROCESSED_COUNTER_NAME, 1);
        micrometerAssertionHelper.assertCounterIncrement(MESSAGES_ERROR_COUNTER_NAME, 1);

        verifyNoInteractions(notificationHistoryRepository);
    }

    @Test
    void testValidFailurePayload() {
        String expectedOutcome = "com.jayway.jsonpath.PathNotFoundException: Missing property in path $['bla']";
        testPayload(false, 67549274, expectedOutcome, NotificationStatus.FAILED_EXTERNAL);
    }

    @Test
    void testValidSuccessPayload() {
        testPayload(true, 67549274, null, NotificationStatus.SUCCESS);
    }

    @Test
    void testPayloadWithDurationFitsInteger1() {
        testPayload(true, 15, null, NotificationStatus.SUCCESS);
    }

    @Test
    void testPayloadWithDurationFitsInteger2() {
        testPayload(true, 2147483600, null, NotificationStatus.SUCCESS);
    }

    @Test
    void testPayloadWithDurationFitsLong1() {
        testPayload(true, 2415706709L, null, NotificationStatus.SUCCESS);
    }

    @Test
    void testPayloadWithDurationFitsLong2() {
        testPayload(true, 2147483600000L, null, NotificationStatus.SUCCESS);
    }

    @Test
    void testValidPayloadWithDeletedEndpoint() {
        testPayload(UUID.randomUUID().toString(), true, 67549274, null, NotificationStatus.SUCCESS);
    }

    private void testPayload(boolean isSuccessful, long expectedDuration, String expectedOutcome, NotificationStatus expectedNotificationStatus) {
        testPayload(expectedHistoryId, isSuccessful, expectedDuration, expectedOutcome, expectedNotificationStatus);
    }

    private void testPayload(String historyId, boolean isSuccessful, long expectedDuration, String expectedOutcome, NotificationStatus expectedNotificationStatus) {

        String expectedDetailsType = "com.redhat.console.notification.toCamel.tower";
        String expectedDetailsTarget = "1.2.3.4";

        HashMap<String, Object> dataMap = new HashMap<>(Map.of(
                "duration", expectedDuration,
                "finishTime", 1639476503209L,
                "details", Map.of(
                        "type", expectedDetailsType,
                        "target", expectedDetailsTarget
                ),
                "successful", isSuccessful
        ));

        dataMap.put("outcome", expectedOutcome);

        String payload = Json.encode(Map.of(
                "specversion", "1.0",
                "source", "demo-log",
                "type", "com.redhat.cloud.notifications.history",
                "time", "2021-12-14T10:08:23.217Z",
                "id", historyId,
                "content-type", "application/json",
                "data", Json.encode(dataMap)
        ));
        inMemoryConnector.source(FROMCAMEL_CHANNEL).send(payload);

        micrometerAssertionHelper.awaitAndAssertCounterIncrement(MESSAGES_PROCESSED_COUNTER_NAME, 1);
        micrometerAssertionHelper.assertCounterIncrement(MESSAGES_ERROR_COUNTER_NAME, 0);

        ArgumentCaptor<NotificationHistory> nhUpdate = ArgumentCaptor.forClass(NotificationHistory.class);
        verify(notificationHistoryRepository, times(1)).updateHistoryItem(nhUpdate.capture());
        verify(notificationHistoryRepository, times(1)).getEndpointForHistoryId(nhUpdate.getValue().getId().toString());

        verifyNoMoreInteractions(notificationHistoryRepository);

        ArgumentCaptor<Map<String, Object>> decodedPayload = ArgumentCaptor.forClass(Map.class);
        verify(camelHistoryFillerHelper).updateHistoryItem(decodedPayload.capture());

        assertEquals(expectedNotificationStatus, nhUpdate.getValue().getStatus());

        assertEquals(historyId, decodedPayload.getValue().get("historyId"));
        assertEquals(expectedDuration, ((Number) decodedPayload.getValue().get("duration")).longValue());
        assertEquals(expectedOutcome, decodedPayload.getValue().get("outcome"));
        Map<String, Object> details = (Map<String, Object>) decodedPayload.getValue().get("details");
        assertEquals(expectedDetailsType, details.get("type"));
        assertEquals(expectedDetailsTarget, details.get("target"));

        if (!expectedHistoryId.equals(historyId)) {
            verifyNoInteractions(endpointRepository);
        } else if (isSuccessful) {
            verify(endpointRepository, times(1)).resetEndpointServerErrors(UUID.fromString(expectedHistoryId));
        }
    }

    @Test
    void testDrawerCallbackProcessed() {
        String orgId = "org-with-drawer";
        String historyId = UUID.randomUUID().toString();

        Endpoint endpoint = new Endpoint();
        endpoint.setId(UUID.fromString(historyId));
        endpoint.setOrgId(orgId);

        Event event = new Event();
        event.setId(UUID.randomUUID());
        event.setOrgId(orgId);
        event.setCreated(LocalDateTime.now());

        Mockito.when(notificationHistoryRepository.getEndpointForHistoryId(eq(historyId)))
            .thenReturn(endpoint);
        Mockito.when(notificationHistoryRepository.getEventIdFromHistoryId(eq(UUID.fromString(historyId))))
            .thenReturn(event);

        HashMap<String, Object> dataMap = new HashMap<>(Map.of(
            "duration", 1000L,
            "finishTime", 1639476503209L,
            "details", Map.of(
                "type", "com.redhat.console.notification.toCamel.drawer",
                "resolved_recipient_list", List.of("user1", "user2")
            ),
            "successful", true
        ));

        String payload = Json.encode(Map.of(
            "specversion", "1.0",
            "source", "drawer-connector",
            "type", "com.redhat.cloud.notifications.history",
            "time", "2021-12-14T10:08:23.217Z",
            "id", historyId,
            "content-type", "application/json",
            "data", Json.encode(dataMap)
        ));

        inMemoryConnector.source(FROMCAMEL_CHANNEL).send(payload);

        micrometerAssertionHelper.awaitAndAssertCounterIncrement(MESSAGES_PROCESSED_COUNTER_NAME, 1);
        micrometerAssertionHelper.assertCounterIncrement(MESSAGES_ERROR_COUNTER_NAME, 0);

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<UUID> historyIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(drawerProcessor, times(1)).manageConnectorDrawerReturnsIfNeeded(
            payloadCaptor.capture(),
            historyIdCaptor.capture()
        );

        assertEquals(historyId, historyIdCaptor.getValue().toString());
        Map<String, Object> capturedDetails = (Map<String, Object>) payloadCaptor.getValue().get("details");
        assertEquals("com.redhat.console.notification.toCamel.drawer", capturedDetails.get("type"));
    }

    @Test
    void testNonDrawerCallbackHandled() {
        String historyId = UUID.randomUUID().toString();

        Endpoint endpoint = new Endpoint();
        endpoint.setId(UUID.fromString(historyId));

        Mockito.when(notificationHistoryRepository.getEndpointForHistoryId(eq(historyId)))
            .thenReturn(endpoint);

        HashMap<String, Object> dataMap = new HashMap<>(Map.of(
            "duration", 1000L,
            "finishTime", 1639476503209L,
            "details", Map.of(
                "type", "com.redhat.console.notification.toCamel.webhook",
                "target", "https://example.com/webhook"
            ),
            "successful", true
        ));

        String payload = Json.encode(Map.of(
            "specversion", "1.0",
            "source", "webhook-connector",
            "type", "com.redhat.cloud.notifications.history",
            "time", "2021-12-14T10:08:23.217Z",
            "id", historyId,
            "content-type", "application/json",
            "data", Json.encode(dataMap)
        ));

        inMemoryConnector.source(FROMCAMEL_CHANNEL).send(payload);

        micrometerAssertionHelper.awaitAndAssertCounterIncrement(MESSAGES_PROCESSED_COUNTER_NAME, 1);

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(drawerProcessor, times(1)).manageConnectorDrawerReturnsIfNeeded(
            payloadCaptor.capture(),
            any()
        );

        Map<String, Object> capturedDetails = (Map<String, Object>) payloadCaptor.getValue().get("details");
        assertEquals("com.redhat.console.notification.toCamel.webhook", capturedDetails.get("type"));
    }
}
