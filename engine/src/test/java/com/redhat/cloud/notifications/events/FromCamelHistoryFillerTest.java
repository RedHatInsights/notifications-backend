package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.repositories.NotificationHistoryRepository;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.smallrye.reactive.messaging.providers.connectors.InMemoryConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.enterprise.inject.Any;
import javax.inject.Inject;

import java.util.Map;

import static com.redhat.cloud.notifications.events.FromCamelHistoryFiller.FROMCAMEL_CHANNEL;
import static com.redhat.cloud.notifications.events.FromCamelHistoryFiller.MESSAGES_ERROR_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.FromCamelHistoryFiller.MESSAGES_PROCESSED_COUNTER_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class FromCamelHistoryFillerTest {

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    @InjectMock
    NotificationHistoryRepository notificationHistoryRepository;

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

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
    void name() {
        FromCamelHistoryFiller historyFiller = new FromCamelHistoryFiller();
        historyFiller.decodeItem("Event 11331111-abcd-1337-1337-9f543bf0c3b7 sent successfully");
    }

    @Test
    void testInvalidPayload() {
        inMemoryConnector.source(FROMCAMEL_CHANNEL).send("I am not valid!");

        micrometerAssertionHelper.awaitAndAssertCounterIncrement(MESSAGES_PROCESSED_COUNTER_NAME, 1);
        micrometerAssertionHelper.assertCounterIncrement(MESSAGES_ERROR_COUNTER_NAME, 1);

        verifyNoInteractions(notificationHistoryRepository);
    }

    @Test
    void testValidPayload() {
        String expectedHistoryId = "e3c90a94-751b-4ce1-b345-b85d825795a4";
        int expectedDuration = 67549274;
        String expectedOutcome = "com.jayway.jsonpath.PathNotFoundException: Missing property in path $['bla']";
        String expectedDetailsType = "com.redhat.console.notification.toCamel.tower";
        String expectedDetailsTarget = "1.2.3.4";

        String payload = Json.encode(Map.of(
                "specversion", "1.0",
                "source", "demo-log",
                "type", "com.redhat.cloud.notifications.history",
                "time", "2021-12-14T10:08:23.217Z",
                "id", expectedHistoryId,
                "content-type", "application/json",
                "data", Json.encode(Map.of(
                        "duration", expectedDuration,
                        "finishTime", 1639476503209L,
                        "details", Map.of(
                                "type", expectedDetailsType,
                                "target", expectedDetailsTarget
                        ),
                        "outcome", expectedOutcome,
                        "successful", false
                ))
        ));
        inMemoryConnector.source(FROMCAMEL_CHANNEL).send(payload);

        micrometerAssertionHelper.awaitAndAssertCounterIncrement(MESSAGES_PROCESSED_COUNTER_NAME, 1);
        micrometerAssertionHelper.assertCounterIncrement(MESSAGES_ERROR_COUNTER_NAME, 0);

        ArgumentCaptor<Map<String, Object>> decodedPayload = ArgumentCaptor.forClass(Map.class);
        verify(notificationHistoryRepository, times(1)).updateHistoryItem(decodedPayload.capture());
        verify(notificationHistoryRepository, times(1)).getEndpointForHistoryId((String) decodedPayload.getValue().get("historyId"));
        verifyNoMoreInteractions(notificationHistoryRepository);

        assertEquals(expectedHistoryId, decodedPayload.getValue().get("historyId"));
        assertEquals(expectedDuration, decodedPayload.getValue().get("duration"));
        assertEquals(expectedOutcome, decodedPayload.getValue().get("outcome"));
        Map<String, Object> details = (Map<String, Object>) decodedPayload.getValue().get("details");
        assertEquals(expectedDetailsType, details.get("type"));
        assertEquals(expectedDetailsTarget, details.get("target"));
    }
}
