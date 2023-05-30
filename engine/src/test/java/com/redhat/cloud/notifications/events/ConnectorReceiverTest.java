package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.repositories.NotificationHistoryRepository;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.NotificationStatus;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.providers.connectors.InMemoryConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.enterprise.inject.Any;
import javax.inject.Inject;

import java.util.HashMap;
import java.util.Map;

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

    @BeforeEach
    void beforeEach() {
        micrometerAssertionHelper.saveCounterValuesBeforeTest(
                MESSAGES_PROCESSED_COUNTER_NAME,
                MESSAGES_ERROR_COUNTER_NAME
        );
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

    private void testPayload(boolean isSuccessful, long expectedDuration, String expectedOutcome, NotificationStatus expectedNotificationStatus) {
        String expectedHistoryId = "e3c90a94-751b-4ce1-b345-b85d825795a4";
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
                "id", expectedHistoryId,
                "content-type", "application/json",
                "data", Json.encode(dataMap)
        ));
        inMemoryConnector.source(FROMCAMEL_CHANNEL).send(payload);

        micrometerAssertionHelper.awaitAndAssertCounterIncrement(MESSAGES_PROCESSED_COUNTER_NAME, 1);
        micrometerAssertionHelper.assertCounterIncrement(MESSAGES_ERROR_COUNTER_NAME, 0);

        ArgumentCaptor<NotificationHistory> nhUpdate = ArgumentCaptor.forClass(NotificationHistory.class);
        verify(notificationHistoryRepository, times(1)).updateHistoryItem(nhUpdate.capture());

        if (!isSuccessful) {
            verify(notificationHistoryRepository, times(1)).getEndpointForHistoryId(nhUpdate.getValue().getId().toString());
        }

        verifyNoMoreInteractions(notificationHistoryRepository);

        ArgumentCaptor<Map<String, Object>> decodedPayload = ArgumentCaptor.forClass(Map.class);
        verify(camelHistoryFillerHelper).updateHistoryItem(decodedPayload.capture());

        assertEquals(expectedNotificationStatus, nhUpdate.getValue().getStatus());

        assertEquals(expectedHistoryId, decodedPayload.getValue().get("historyId"));
        assertEquals(expectedDuration, ((Number) decodedPayload.getValue().get("duration")).longValue());
        assertEquals(expectedOutcome, decodedPayload.getValue().get("outcome"));
        Map<String, Object> details = (Map<String, Object>) decodedPayload.getValue().get("details");
        assertEquals(expectedDetailsType, details.get("type"));
        assertEquals(expectedDetailsTarget, details.get("target"));
    }
}
