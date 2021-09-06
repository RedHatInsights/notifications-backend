package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.CounterAssertionHelper;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.ApplicationResources;
import com.redhat.cloud.notifications.db.EventResources;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.connectors.InMemoryConnector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.enterprise.inject.Any;
import javax.inject.Inject;
import javax.persistence.NoResultException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.redhat.cloud.notifications.TestHelpers.serializeAction;
import static com.redhat.cloud.notifications.events.EventConsumer.INGRESS_CHANNEL;
import static com.redhat.cloud.notifications.events.EventConsumer.PROCESSING_ERROR_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.EventConsumer.REJECTED_COUNTER_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EventConsumerTest {

    private static final String TENANT_ID = "test-tenant";
    private static final String BUNDLE = "my-bundle";
    private static final String APP = "Policies";
    private static final String EVENT_TYPE = "Any";

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    @InjectMock
    EndpointProcessor endpointProcessor;

    @InjectMock
    ApplicationResources appResources;

    @InjectMock
    EventResources eventResources;

    @Inject
    CounterAssertionHelper counterAssertionHelper;

    @BeforeEach
    void init() {
        counterAssertionHelper.saveCounterValuesBeforeTest(REJECTED_COUNTER_NAME, PROCESSING_ERROR_COUNTER_NAME);
    }

    @AfterEach
    void clear() {
        counterAssertionHelper.clear();
    }

    @Test
    void testValidMessagePayload() throws IOException {
        EventType eventType = mockGetEventTypeAndCreateEvent();
        Action action = buildValidAction();
        String payload = serializeAction(action);
        inMemoryConnector.source(INGRESS_CHANNEL).send(payload);
        counterAssertionHelper.assertIncrement(REJECTED_COUNTER_NAME, 0);
        counterAssertionHelper.assertIncrement(PROCESSING_ERROR_COUNTER_NAME, 0);
        verifyProcessing(eventType, payload, action);
    }

    @Test
    void testInvalidMessagePayload() {
        inMemoryConnector.source(INGRESS_CHANNEL).send("I am not a valid payload!");
        counterAssertionHelper.assertIncrement(REJECTED_COUNTER_NAME, 1);
        counterAssertionHelper.assertIncrement(PROCESSING_ERROR_COUNTER_NAME, 0);
        verify(endpointProcessor, never()).process(any(Event.class));
    }

    @Test
    void testUnknownEventType() throws IOException {
        mockGetUnknownEventType();
        Action action = buildValidAction();
        String payload = serializeAction(action);
        inMemoryConnector.source(INGRESS_CHANNEL).send(payload);
        counterAssertionHelper.assertIncrement(REJECTED_COUNTER_NAME, 1);
        counterAssertionHelper.assertIncrement(PROCESSING_ERROR_COUNTER_NAME, 0);
        verify(endpointProcessor, never()).process(any(Event.class));
    }

    @Test
    void testProcessingError() throws IOException {
        EventType eventType = mockGetEventTypeAndCreateEvent();
        mockProcessingFailure();
        Action action = buildValidAction();
        String payload = serializeAction(action);
        inMemoryConnector.source(INGRESS_CHANNEL).send(payload);
        counterAssertionHelper.assertIncrement(REJECTED_COUNTER_NAME, 0);
        counterAssertionHelper.assertIncrement(PROCESSING_ERROR_COUNTER_NAME, 1);
        verifyProcessing(eventType, payload, action);
    }

    private EventType mockGetEventTypeAndCreateEvent() {
        EventType eventType = new EventType();
        when(appResources.getEventType(eq(BUNDLE), eq(APP), eq(EVENT_TYPE))).thenReturn(
                Uni.createFrom().item(eventType)
        );
        when(eventResources.create(any(Event.class))).thenAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            return Uni.createFrom().item(event);
        });
        return eventType;
    }

    private void mockGetUnknownEventType() {
        when(appResources.getEventType(eq(BUNDLE), eq(APP), eq(EVENT_TYPE))).thenReturn(
                Uni.createFrom().failure(() -> new NoResultException("I am a forced exception!"))
        );
    }

    private void mockProcessingFailure() {
        when(endpointProcessor.process(any(Event.class))).thenReturn(
                Uni.createFrom().failure(() -> new RuntimeException("I am a forced exception!"))
        );
    }

    private void verifyProcessing(EventType eventType, String payload, Action action) {
        ArgumentCaptor<Event> argumentCaptor = ArgumentCaptor.forClass(Event.class);
        verify(endpointProcessor, times(1)).process(argumentCaptor.capture());
        assertEquals(TENANT_ID, argumentCaptor.getValue().getAccountId());
        assertEquals(eventType, argumentCaptor.getValue().getEventType());
        assertEquals(payload, argumentCaptor.getValue().getPayload());
        assertEquals(action, argumentCaptor.getValue().getAction());
    }

    private static Action buildValidAction() {
        Action action = new Action();
        action.setBundle(BUNDLE);
        action.setApplication(APP);
        action.setEventType(EVENT_TYPE);
        action.setTimestamp(LocalDateTime.now());
        action.setAccountId(TENANT_ID);
        action.setEvents(
                List.of(
                        com.redhat.cloud.notifications.ingress.Event
                                .newBuilder()
                                .setMetadataBuilder(Metadata.newBuilder())
                                .setPayload(Map.of("k", "v", "k2", "v2", "k3", "v"))
                                .build()
                )
        );

        action.setContext(new HashMap());
        return action;
    }
}
