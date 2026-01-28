package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.EventPayloadTestHelper;
import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.EngineConfig;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.events.deduplication.EventDeduplicator;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.RecipientsAuthorizationCriterion;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Set;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.TestHelpers.serializeAction;
import static com.redhat.cloud.notifications.events.EventConsumer.CONSUMED_TIMER_NAME;
import static com.redhat.cloud.notifications.events.EventConsumer.DUPLICATE_EVENT_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.EventConsumer.INGRESS_CHANNEL;
import static com.redhat.cloud.notifications.events.EventConsumer.PROCESSING_BLACKLISTED_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.EventConsumer.PROCESSING_ERROR_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.EventConsumer.PROCESSING_EXCEPTION_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.EventConsumer.REJECTED_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.EventConsumer.TAG_KEY_APPLICATION;
import static com.redhat.cloud.notifications.events.EventConsumer.TAG_KEY_BUNDLE;
import static com.redhat.cloud.notifications.events.EventConsumer.TAG_KEY_EVENT_TYPE;
import static com.redhat.cloud.notifications.events.EventConsumer.TAG_KEY_EVENT_TYPE_FQN;
import static com.redhat.cloud.notifications.events.KafkaMessageDeduplicator.MESSAGE_ID_HEADER;
import static com.redhat.cloud.notifications.events.KafkaMessageDeduplicator.MESSAGE_ID_INVALID_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.KafkaMessageDeduplicator.MESSAGE_ID_MISSING_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.KafkaMessageDeduplicator.MESSAGE_ID_VALID_COUNTER_NAME;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class EventConsumerTest {

    private static final String BUNDLE = "my-bundle";
    private static final String APP = "my-app";
    private static final String EVENT_TYPE = "my-event-type";

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    @InjectMock
    EndpointProcessor endpointProcessor;

    @InjectSpy
    EventDeduplicator eventDeduplicator;

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

    @Inject
    MeterRegistry registry;

    @InjectSpy
    EngineConfig config;

    @Inject
    ResourceHelpers resourceHelpers;

    @BeforeEach
    void beforeEach() {
        micrometerAssertionHelper.saveCounterValuesBeforeTest(
                REJECTED_COUNTER_NAME,
                PROCESSING_ERROR_COUNTER_NAME,
                PROCESSING_EXCEPTION_COUNTER_NAME,
                DUPLICATE_EVENT_COUNTER_NAME,
                MESSAGE_ID_VALID_COUNTER_NAME,
                MESSAGE_ID_INVALID_COUNTER_NAME,
                MESSAGE_ID_MISSING_COUNTER_NAME,
                PROCESSING_BLACKLISTED_COUNTER_NAME
        );
        micrometerAssertionHelper.removeDynamicTimer(CONSUMED_TIMER_NAME);

        when(config.isBlacklistedEventType(any())).thenReturn(false);
    }

    @AfterEach
    void clear() {
        micrometerAssertionHelper.clearSavedValues();
        micrometerAssertionHelper.removeDynamicTimer(CONSUMED_TIMER_NAME);
        resourceHelpers.deleteBundle(BUNDLE);
    }

    @Test
    void testValidPayloadWithMessageId() {
        EventType eventType = mockGetEventTypeAndCreateEvent(true);
        Action action = buildValidAction(true);
        RecipientsAuthorizationCriterion authorizationCriterion = EventPayloadTestHelper.buildRecipientsAuthorizationCriterion();
        action.setRecipientsAuthorizationCriterion(authorizationCriterion);
        action.setSeverity(Severity.LOW.name());
        String payload = serializeAction(action);
        UUID messageId = UUID.randomUUID();
        Message<String> message = buildMessageWithId(messageId.toString().getBytes(UTF_8), payload);
        inMemoryConnector.source(INGRESS_CHANNEL).send(message);

        micrometerAssertionHelper.awaitAndAssertTimerIncrement(CONSUMED_TIMER_NAME, 1);
        assertEquals(1L, getTimerCount(action.getBundle(), action.getApplication(), action.getEventType()));
        micrometerAssertionHelper.assertCounterIncrement(MESSAGE_ID_VALID_COUNTER_NAME, 1);
        assertNoCounterIncrement(
                REJECTED_COUNTER_NAME,
                PROCESSING_ERROR_COUNTER_NAME,
                PROCESSING_EXCEPTION_COUNTER_NAME,
                MESSAGE_ID_INVALID_COUNTER_NAME,
                MESSAGE_ID_MISSING_COUNTER_NAME,
                PROCESSING_BLACKLISTED_COUNTER_NAME
        );

        final Event processedEvent = verifyExactlyOneProcessing(eventType, payload, action, true);
        verifySeverity(action, false);
        verify(eventDeduplicator, times(1)).isNew(any(Event.class));
        assertEquals(messageId, processedEvent.getExternalId());
        assertNotEquals(messageId, processedEvent.getId());
    }

    @Test
    void testValidPayloadWithBlacklistedEventType() {
        EventType eventType = mockGetEventTypeAndCreateEvent(false);
        Action action = buildValidAction(true);
        RecipientsAuthorizationCriterion authorizationCriterion = EventPayloadTestHelper.buildRecipientsAuthorizationCriterion();
        action.setRecipientsAuthorizationCriterion(authorizationCriterion);
        String payload = serializeAction(action);
        UUID messageId = UUID.randomUUID();
        Message<String> message = buildMessageWithId(messageId.toString().getBytes(UTF_8), payload);
        when(config.isBlacklistedEventType(eq(eventType.getId()))).thenReturn(true);

        inMemoryConnector.source(INGRESS_CHANNEL).send(message);

        micrometerAssertionHelper.awaitAndAssertTimerIncrement(CONSUMED_TIMER_NAME, 1);
        assertEquals(1L, getTimerCount(action.getBundle(), action.getApplication(), action.getEventType()));
        micrometerAssertionHelper.assertCounterIncrement(MESSAGE_ID_VALID_COUNTER_NAME, 1);

        assertEquals(1L,
                registry.counter(PROCESSING_BLACKLISTED_COUNTER_NAME,
                        TAG_KEY_BUNDLE, action.getBundle(),
                        TAG_KEY_APPLICATION, action.getApplication(),
                        TAG_KEY_EVENT_TYPE, action.getEventType(),
                        TAG_KEY_EVENT_TYPE_FQN, ""
                ).count());

        assertNoCounterIncrement(
                REJECTED_COUNTER_NAME,
                PROCESSING_ERROR_COUNTER_NAME,
                PROCESSING_EXCEPTION_COUNTER_NAME,
                MESSAGE_ID_INVALID_COUNTER_NAME,
                MESSAGE_ID_MISSING_COUNTER_NAME
        );

        verify(endpointProcessor, never()).process(any(Event.class));
        verify(eventDeduplicator, never()).isNew(any(Event.class));
    }

    @Test
    void testValidPayloadWithoutMessageId() {
        EventType eventType = mockGetEventTypeAndCreateEvent();
        Action action = buildValidAction(false);
        String payload = serializeAction(action);
        inMemoryConnector.source(INGRESS_CHANNEL).send(payload);

        micrometerAssertionHelper.awaitAndAssertTimerIncrement(CONSUMED_TIMER_NAME, 1);
        assertEquals(1L, getTimerCount(action.getBundle(), action.getApplication(), action.getEventType()));
        micrometerAssertionHelper.assertCounterIncrement(MESSAGE_ID_MISSING_COUNTER_NAME, 1);
        assertNoCounterIncrement(
                REJECTED_COUNTER_NAME,
                PROCESSING_ERROR_COUNTER_NAME,
                PROCESSING_EXCEPTION_COUNTER_NAME,
                MESSAGE_ID_VALID_COUNTER_NAME,
                MESSAGE_ID_INVALID_COUNTER_NAME,
                PROCESSING_BLACKLISTED_COUNTER_NAME
        );
        verifyExactlyOneProcessing(eventType, payload, action, false);
        verify(eventDeduplicator, times(1)).isNew(any(Event.class));
    }

    @Test
    void testValidPayloadWithIgnoredSeverityForApplication() {
        boolean severityIgnored = true;

        mockGetEventTypeAndCreateEvent(false);
        Action action = buildValidAction(false);
        action.setSeverity(null);
        String payload = serializeAction(action);
        UUID messageId = UUID.randomUUID();
        Message<String> message = buildMessageWithId(messageId.toString().getBytes(UTF_8), payload);
        inMemoryConnector.source(INGRESS_CHANNEL).send(message);

        micrometerAssertionHelper.awaitAndAssertTimerIncrement(CONSUMED_TIMER_NAME, 1);
        assertEquals(1L, getTimerCount(action.getBundle(), action.getApplication(), action.getEventType()));
        micrometerAssertionHelper.assertCounterIncrement(MESSAGE_ID_VALID_COUNTER_NAME, 1);
        assertNoCounterIncrement(
                REJECTED_COUNTER_NAME,
                PROCESSING_ERROR_COUNTER_NAME,
                PROCESSING_EXCEPTION_COUNTER_NAME,
                MESSAGE_ID_INVALID_COUNTER_NAME,
                MESSAGE_ID_MISSING_COUNTER_NAME,
                PROCESSING_BLACKLISTED_COUNTER_NAME
        );
        verifySeverity(action, severityIgnored);
    }

    @Test
    void testInvalidPayloadWithMessageId() {
        Message<String> message = buildMessageWithId(UUID.randomUUID().toString().getBytes(UTF_8), "I am not a valid payload!");
        inMemoryConnector.source(INGRESS_CHANNEL).send(message);

        micrometerAssertionHelper.awaitAndAssertTimerIncrement(CONSUMED_TIMER_NAME, 1);
        assertEquals(1L, getTimerCount("", "", ""));
        micrometerAssertionHelper.assertCounterIncrement(REJECTED_COUNTER_NAME, 1);
        micrometerAssertionHelper.assertCounterIncrement(PROCESSING_EXCEPTION_COUNTER_NAME, 1);
        assertNoCounterIncrement(
                PROCESSING_ERROR_COUNTER_NAME,
                MESSAGE_ID_VALID_COUNTER_NAME,
                MESSAGE_ID_INVALID_COUNTER_NAME,
                MESSAGE_ID_MISSING_COUNTER_NAME,
                PROCESSING_BLACKLISTED_COUNTER_NAME
        );
        verify(endpointProcessor, never()).process(any(Event.class));
        verify(eventDeduplicator, never()).isNew(any(Event.class));
    }

    @Test
    void testUnknownEventTypeWithoutMessageId() {
        resourceHelpers.deleteBundle(BUNDLE);
        Action action = buildValidAction(false);
        String payload = serializeAction(action);
        inMemoryConnector.source(INGRESS_CHANNEL).send(payload);

        micrometerAssertionHelper.awaitAndAssertTimerIncrement(CONSUMED_TIMER_NAME, 1);
        assertEquals(1L, getTimerCount(action.getBundle(), action.getApplication(), action.getEventType()));
        micrometerAssertionHelper.assertCounterIncrement(MESSAGE_ID_MISSING_COUNTER_NAME, 1);
        micrometerAssertionHelper.assertCounterIncrement(REJECTED_COUNTER_NAME, 1);
        micrometerAssertionHelper.assertCounterIncrement(PROCESSING_EXCEPTION_COUNTER_NAME, 1);
        assertNoCounterIncrement(
                PROCESSING_ERROR_COUNTER_NAME,
                MESSAGE_ID_VALID_COUNTER_NAME,
                MESSAGE_ID_INVALID_COUNTER_NAME,
                PROCESSING_BLACKLISTED_COUNTER_NAME
        );
        verify(endpointProcessor, never()).process(any(Event.class));
        verify(eventDeduplicator, never()).isNew(any(Event.class));
    }

    @Test
    void testProcessingErrorWithoutMessageId() {
        EventType eventType = mockGetEventTypeAndCreateEvent();
        mockProcessingFailure();
        Action action = buildValidAction(false);
        String payload = serializeAction(action);
        inMemoryConnector.source(INGRESS_CHANNEL).send(payload);

        micrometerAssertionHelper.awaitAndAssertTimerIncrement(CONSUMED_TIMER_NAME, 1);
        assertEquals(1L, getTimerCount(action.getBundle(), action.getApplication(), action.getEventType()));
        micrometerAssertionHelper.assertCounterIncrement(MESSAGE_ID_MISSING_COUNTER_NAME, 1);
        micrometerAssertionHelper.assertCounterIncrement(PROCESSING_ERROR_COUNTER_NAME, 1);
        assertNoCounterIncrement(
                REJECTED_COUNTER_NAME,
                MESSAGE_ID_VALID_COUNTER_NAME,
                MESSAGE_ID_INVALID_COUNTER_NAME,
                PROCESSING_BLACKLISTED_COUNTER_NAME
        );
        verifyExactlyOneProcessing(eventType, payload, action, false);
        verify(eventDeduplicator, times(1)).isNew(any(Event.class));
    }

    @Test
    void testDuplicatePayload() {
        EventType eventType = mockGetEventTypeAndCreateEvent();
        Action action = buildValidAction(false);
        String payload = serializeAction(action);
        UUID messageId = UUID.randomUUID();
        Message<String> message = buildMessageWithId(messageId.toString().getBytes(UTF_8), payload);
        inMemoryConnector.source(INGRESS_CHANNEL).send(message);
        inMemoryConnector.source(INGRESS_CHANNEL).send(message);

        micrometerAssertionHelper.awaitAndAssertTimerIncrement(CONSUMED_TIMER_NAME, 2);
        assertEquals(2L, getTimerCount(action.getBundle(), action.getApplication(), action.getEventType()));
        micrometerAssertionHelper.assertCounterIncrement(MESSAGE_ID_VALID_COUNTER_NAME, 2);
        // TODO DUPLICATE_EVENT_COUNTER_NAME will be increased to 1 when kafkaMessageDeduplicator is removed.
        micrometerAssertionHelper.assertCounterIncrement(DUPLICATE_EVENT_COUNTER_NAME, 0);
        assertNoCounterIncrement(
                REJECTED_COUNTER_NAME,
                PROCESSING_ERROR_COUNTER_NAME,
                PROCESSING_EXCEPTION_COUNTER_NAME,
                MESSAGE_ID_INVALID_COUNTER_NAME,
                MESSAGE_ID_MISSING_COUNTER_NAME,
                PROCESSING_BLACKLISTED_COUNTER_NAME
        );
        verifyExactlyOneProcessing(eventType, payload, action, false);
        verify(eventDeduplicator, times(2)).isNew(any(Event.class));

    }

    @Test
    void testNullMessageId() {
        EventType eventType = mockGetEventTypeAndCreateEvent();
        Action action = buildValidAction(false);
        String payload = serializeAction(action);
        Message<String> message = buildMessageWithId(null, payload);
        inMemoryConnector.source(INGRESS_CHANNEL).send(message);

        micrometerAssertionHelper.awaitAndAssertTimerIncrement(CONSUMED_TIMER_NAME, 1);
        assertEquals(1L, getTimerCount(action.getBundle(), action.getApplication(), action.getEventType()));
        micrometerAssertionHelper.assertCounterIncrement(MESSAGE_ID_MISSING_COUNTER_NAME, 1);
        assertNoCounterIncrement(
                REJECTED_COUNTER_NAME,
                PROCESSING_ERROR_COUNTER_NAME,
                PROCESSING_EXCEPTION_COUNTER_NAME,
                MESSAGE_ID_VALID_COUNTER_NAME,
                MESSAGE_ID_INVALID_COUNTER_NAME,
                PROCESSING_BLACKLISTED_COUNTER_NAME
        );
        verifyExactlyOneProcessing(eventType, payload, action, false);
        verify(eventDeduplicator, times(1)).isNew(any(Event.class));
    }

    @Test
    void testInvalidMessageId() {
        EventType eventType = mockGetEventTypeAndCreateEvent();
        Action action = buildValidAction(false);
        String payload = serializeAction(action);
        Message<String> message = buildMessageWithId("I am not a valid UUID!".getBytes(UTF_8), payload);
        inMemoryConnector.source(INGRESS_CHANNEL).send(message);

        micrometerAssertionHelper.awaitAndAssertTimerIncrement(CONSUMED_TIMER_NAME, 1);
        assertEquals(1L, getTimerCount(action.getBundle(), action.getApplication(), action.getEventType()));
        micrometerAssertionHelper.assertCounterIncrement(MESSAGE_ID_INVALID_COUNTER_NAME, 1);
        assertNoCounterIncrement(
                REJECTED_COUNTER_NAME,
                PROCESSING_ERROR_COUNTER_NAME,
                PROCESSING_EXCEPTION_COUNTER_NAME,
                MESSAGE_ID_VALID_COUNTER_NAME,
                MESSAGE_ID_MISSING_COUNTER_NAME,
                PROCESSING_BLACKLISTED_COUNTER_NAME
        );
        verifyExactlyOneProcessing(eventType, payload, action, false);
        verify(eventDeduplicator, times(1)).isNew(any(Event.class));
    }

    private EventType mockGetEventTypeAndCreateEvent() {
        return mockGetEventTypeAndCreateEvent(true);
    }

    private EventType mockGetEventTypeAndCreateEvent(final boolean shouldHaveSeverity) {
        Bundle bundle = resourceHelpers.findOrCreateBundle(BUNDLE);
        Application app = resourceHelpers.findOrCreateApplication(bundle.getName(), APP);
        EventType eventType = resourceHelpers.findOrCreateEventType(app.getId(), EVENT_TYPE);

        if (shouldHaveSeverity) {
            eventType.setDefaultSeverity(Severity.MODERATE);
            eventType.setAvailableSeverities(Set.of(Severity.values()));
        } else {
            eventType.setDefaultSeverity(null);
        }
        resourceHelpers.updateEventType(eventType);
        return eventType;
    }

    private void mockProcessingFailure() {
        doThrow(new RuntimeException("I am a forced exception!"))
                .when(endpointProcessor).process(any(Event.class));
    }

    private Event verifyExactlyOneProcessing(EventType eventType, String payload, Action action, boolean withAccountId) {
        ArgumentCaptor<Event> argumentCaptor = ArgumentCaptor.forClass(Event.class);
        verify(endpointProcessor, times(1)).process(argumentCaptor.capture());
        if (withAccountId) {
            assertEquals(DEFAULT_ACCOUNT_ID, argumentCaptor.getValue().getAccountId());
        } else {
            assertNull(argumentCaptor.getValue().getAccountId());
        }
        assertEquals(DEFAULT_ORG_ID, argumentCaptor.getValue().getOrgId());
        assertEquals(eventType, argumentCaptor.getValue().getEventType());
        assertEquals(payload, argumentCaptor.getValue().getPayload());
        assertEquals(action, argumentCaptor.getValue().getEventWrapper().getEvent());
        return argumentCaptor.getValue();
    }

    private void verifySeverity(Action action, boolean severityIgnored) {
        ArgumentCaptor<Event> argumentCaptor = ArgumentCaptor.forClass(Event.class);
        verify(endpointProcessor, times(1)).process(argumentCaptor.capture());
        if (severityIgnored) {
            assertNull(argumentCaptor.getValue().getSeverity());
            assertNull(((Action) argumentCaptor.getValue().getEventWrapper().getEvent()).getSeverity());
        } else {
            assertEquals(action.getSeverity(), argumentCaptor.getValue().getSeverity().name());
            assertEquals(action.getSeverity(), ((Action) argumentCaptor.getValue().getEventWrapper().getEvent()).getSeverity());
        }
    }

    private void assertNoCounterIncrement(String... counterNames) {
        for (String counterName : counterNames) {
            micrometerAssertionHelper.assertCounterIncrement(counterName, 0);
        }
    }

    private static Action buildValidAction(boolean withAccountId) {

        Action action = EventPayloadTestHelper.buildValidAction(DEFAULT_ORG_ID, BUNDLE, APP, EVENT_TYPE);

        if (withAccountId) {
            action.setAccountId(DEFAULT_ACCOUNT_ID);
        }
        return action;
    }

    private static Message<String> buildMessageWithId(byte[] messageId, String payload) {
        OutgoingKafkaRecordMetadata metadata = OutgoingKafkaRecordMetadata.builder()
                .withHeaders(new RecordHeaders().add(MESSAGE_ID_HEADER, messageId))
                .build();
        return Message.of(payload).addMetadata(metadata);
    }

    private long getTimerCount(final String bundle, final String application, final String eventType) {
        return registry.timer(CONSUMED_TIMER_NAME,
                TAG_KEY_BUNDLE, bundle,
                TAG_KEY_APPLICATION, application,
                TAG_KEY_EVENT_TYPE, eventType,
                TAG_KEY_EVENT_TYPE_FQN, ""
        ).count();
    }
}
