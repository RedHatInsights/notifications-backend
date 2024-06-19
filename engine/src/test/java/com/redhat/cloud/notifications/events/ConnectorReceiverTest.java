package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.repositories.EndpointRepository;
import com.redhat.cloud.notifications.db.repositories.NotificationHistoryRepository;
import com.redhat.cloud.notifications.db.repositories.PayloadDetailsRepository;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.NotificationStatus;
import com.redhat.cloud.notifications.processors.payload.PayloadDetails;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.kafka.api.IncomingKafkaRecordMetadata;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.redhat.cloud.notifications.events.ConnectorReceiver.FROMCAMEL_CHANNEL;
import static com.redhat.cloud.notifications.events.ConnectorReceiver.MESSAGES_ERROR_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.ConnectorReceiver.MESSAGES_PROCESSED_COUNTER_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @InjectMock
    PayloadDetailsRepository payloadDetailsRepository;

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
        testPayload(UUID.randomUUID().toString(), true, 67549274, null, NotificationStatus.SUCCESS, new HashMap<>());
    }

    /**
     * Tests that when the receiver receives a message with a header indicating
     * the payload ID, then the payload is deleted from the database.
     */
    @Test
    void testDeletePayloadFromDatabase() {
        // Prepare the header which signals that the payload must be deleted
        // from the database.
        final UUID eventId = UUID.randomUUID();
        final Map<String, String> databasePayloadHeader = Map.of(PayloadDetails.X_RH_NOTIFICATIONS_CONNECTOR_PAYLOAD_HEADER, eventId.toString());

        // Send the message.
        testPayload(UUID.randomUUID().toString(), true, 67549274, null, NotificationStatus.SUCCESS, databasePayloadHeader);

        // Verify that the payload is deleted.
        Mockito.verify(this.payloadDetailsRepository, Mockito.times(1)).deleteById(eventId);
    }

    /**
     * Tests that for a regular incoming message without any payload ID header
     * no payload is attempted to be deleted.
     */
    @Test
    void testDoNotDeletePayloadFromDatabase() {
        // Send the message.
        testPayload(UUID.randomUUID().toString(), true, 67549274, null, NotificationStatus.SUCCESS, new HashMap<>());

        // Verify that the payload is not deleted.
        Mockito.verify(this.payloadDetailsRepository, Mockito.times(0)).deleteById(Mockito.any());
    }

    private void testPayload(boolean isSuccessful, long expectedDuration, String expectedOutcome, NotificationStatus expectedNotificationStatus) {
        testPayload(expectedHistoryId, isSuccessful, expectedDuration, expectedOutcome, expectedNotificationStatus, new HashMap<>());
    }

    private void testPayload(String historyId, boolean isSuccessful, long expectedDuration, String expectedOutcome, NotificationStatus expectedNotificationStatus, final Map<String, String> customHeaders) {

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

        // Create a Spy for the message because when building the message, we
        // need to use the "OutgoingKafkaRecordMetadata" class, but the
        // receiver end expects an "IncomingKafkaRecordMetadata", so we need
        // to mock that somehow.
        final Message<String> message = Mockito.spy(Message.class);
        Mockito.when(message.getPayload()).thenReturn(payload);

        // If any custom headers are specified add them to the message.
        if (!customHeaders.isEmpty()) {
            final Headers headers = new RecordHeaders();
            customHeaders.forEach((key, value) -> headers.add(key, value.getBytes(StandardCharsets.UTF_8)));

            final IncomingKafkaRecordMetadata incomingKafkaRecordMetadata = Mockito.mock(IncomingKafkaRecordMetadata.class);
            Mockito.when(incomingKafkaRecordMetadata.getHeaders()).thenReturn(headers);

            Mockito.when(message.getMetadata(IncomingKafkaRecordMetadata.class)).thenReturn(Optional.of(incomingKafkaRecordMetadata));

            message.addMetadata(message);
        }

        inMemoryConnector.source(FROMCAMEL_CHANNEL).send(message);

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
}
