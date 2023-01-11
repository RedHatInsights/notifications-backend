package com.redhat.cloud.notifications.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.repositories.EventRepository;
import com.redhat.cloud.notifications.db.repositories.EventTypeRepository;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.event.TestEventHelper;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.smallrye.reactive.messaging.providers.connectors.InMemoryConnector;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import javax.enterprise.inject.Any;
import javax.inject.Inject;
import javax.persistence.NoResultException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.TestHelpers.serializeAction;
import static com.redhat.cloud.notifications.events.EventConsumer.CONSUMED_TIMER_NAME;
import static com.redhat.cloud.notifications.events.EventConsumer.DUPLICATE_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.EventConsumer.INGRESS_CHANNEL;
import static com.redhat.cloud.notifications.events.EventConsumer.PROCESSING_ERROR_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.EventConsumer.PROCESSING_EXCEPTION_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.EventConsumer.REJECTED_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.KafkaMessageDeduplicator.MESSAGE_ID_HEADER;
import static com.redhat.cloud.notifications.events.KafkaMessageDeduplicator.MESSAGE_ID_INVALID_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.KafkaMessageDeduplicator.MESSAGE_ID_MISSING_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.KafkaMessageDeduplicator.MESSAGE_ID_VALID_COUNTER_NAME;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private static final String APP = "Policies";
    private static final String EVENT_TYPE = "Any";

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    @InjectMock
    EndpointProcessor endpointProcessor;

    @InjectMock
    EventTypeRepository eventTypeRepository;

    @InjectMock
    EventRepository eventRepository;

    @InjectSpy
    KafkaMessageDeduplicator kafkaMessageDeduplicator;

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

    @Inject
    MeterRegistry registry;

    @Inject
    EventConsumer eventConsumer;


    @BeforeEach
    void beforeEach() {
        micrometerAssertionHelper.saveCounterValuesBeforeTest(
                REJECTED_COUNTER_NAME,
                PROCESSING_ERROR_COUNTER_NAME,
                PROCESSING_EXCEPTION_COUNTER_NAME,
                DUPLICATE_COUNTER_NAME,
                MESSAGE_ID_VALID_COUNTER_NAME,
                MESSAGE_ID_INVALID_COUNTER_NAME,
                MESSAGE_ID_MISSING_COUNTER_NAME
        );
        micrometerAssertionHelper.removeDynamicTimer(CONSUMED_TIMER_NAME);
    }

    @AfterEach
    void clear() {
        micrometerAssertionHelper.clearSavedValues();
        micrometerAssertionHelper.removeDynamicTimer(CONSUMED_TIMER_NAME);
    }

    @Test
    void testValidPayloadWithMessageId() {
        EventType eventType = mockGetEventTypeAndCreateEvent();
        Action action = buildValidAction(true);
        String payload = serializeAction(action);
        UUID messageId = UUID.randomUUID();
        Message<String> message = buildMessageWithId(messageId.toString().getBytes(UTF_8), payload);
        inMemoryConnector.source(INGRESS_CHANNEL).send(message);

        micrometerAssertionHelper.awaitAndAssertTimerIncrement(CONSUMED_TIMER_NAME, 1);
        assertEquals(1L, registry.timer(CONSUMED_TIMER_NAME, "bundle", action.getBundle(), "application", action.getApplication()).count());
        micrometerAssertionHelper.assertCounterIncrement(MESSAGE_ID_VALID_COUNTER_NAME, 1);
        assertNoCounterIncrement(
                REJECTED_COUNTER_NAME,
                PROCESSING_ERROR_COUNTER_NAME,
                PROCESSING_EXCEPTION_COUNTER_NAME,
                DUPLICATE_COUNTER_NAME,
                MESSAGE_ID_INVALID_COUNTER_NAME,
                MESSAGE_ID_MISSING_COUNTER_NAME
        );
        verifyExactlyOneProcessing(eventType, payload, action, true);
        verify(kafkaMessageDeduplicator, times(1)).registerMessageId(messageId);
    }

    @Test
    void testValidPayloadWithoutMessageId() {
        EventType eventType = mockGetEventTypeAndCreateEvent();
        Action action = buildValidAction(false);
        String payload = serializeAction(action);
        inMemoryConnector.source(INGRESS_CHANNEL).send(payload);

        micrometerAssertionHelper.awaitAndAssertTimerIncrement(CONSUMED_TIMER_NAME, 1);
        assertEquals(1L, registry.timer(CONSUMED_TIMER_NAME, "bundle", action.getBundle(), "application", action.getApplication()).count());
        micrometerAssertionHelper.assertCounterIncrement(MESSAGE_ID_MISSING_COUNTER_NAME, 1);
        assertNoCounterIncrement(
                REJECTED_COUNTER_NAME,
                PROCESSING_ERROR_COUNTER_NAME,
                PROCESSING_EXCEPTION_COUNTER_NAME,
                DUPLICATE_COUNTER_NAME,
                MESSAGE_ID_VALID_COUNTER_NAME,
                MESSAGE_ID_INVALID_COUNTER_NAME
        );
        verifyExactlyOneProcessing(eventType, payload, action, false);
        verify(kafkaMessageDeduplicator, times(1)).registerMessageId(null);
    }

    @Test
    void testInvalidPayloadWithMessageId() {
        Message<String> message = buildMessageWithId(UUID.randomUUID().toString().getBytes(UTF_8), "I am not a valid payload!");
        inMemoryConnector.source(INGRESS_CHANNEL).send(message);

        micrometerAssertionHelper.awaitAndAssertTimerIncrement(CONSUMED_TIMER_NAME, 1);
        assertEquals(1L, registry.timer(CONSUMED_TIMER_NAME, "bundle", "", "application", "").count());
        micrometerAssertionHelper.assertCounterIncrement(REJECTED_COUNTER_NAME, 1);
        micrometerAssertionHelper.assertCounterIncrement(PROCESSING_EXCEPTION_COUNTER_NAME, 1);
        assertNoCounterIncrement(
                PROCESSING_ERROR_COUNTER_NAME,
                DUPLICATE_COUNTER_NAME,
                MESSAGE_ID_VALID_COUNTER_NAME,
                MESSAGE_ID_INVALID_COUNTER_NAME,
                MESSAGE_ID_MISSING_COUNTER_NAME
        );
        verify(endpointProcessor, never()).process(any(Event.class));
        verify(kafkaMessageDeduplicator, never()).registerMessageId(any(UUID.class));
    }

    @Test
    void testUnknownEventTypeWithoutMessageId() {
        mockGetUnknownEventType();
        Action action = buildValidAction(false);
        String payload = serializeAction(action);
        inMemoryConnector.source(INGRESS_CHANNEL).send(payload);

        micrometerAssertionHelper.awaitAndAssertTimerIncrement(CONSUMED_TIMER_NAME, 1);
        assertEquals(1L, registry.timer(CONSUMED_TIMER_NAME, "bundle", action.getBundle(), "application", action.getApplication()).count());
        micrometerAssertionHelper.assertCounterIncrement(MESSAGE_ID_MISSING_COUNTER_NAME, 1);
        micrometerAssertionHelper.assertCounterIncrement(REJECTED_COUNTER_NAME, 1);
        micrometerAssertionHelper.assertCounterIncrement(PROCESSING_EXCEPTION_COUNTER_NAME, 1);
        assertNoCounterIncrement(
                PROCESSING_ERROR_COUNTER_NAME,
                DUPLICATE_COUNTER_NAME,
                MESSAGE_ID_VALID_COUNTER_NAME,
                MESSAGE_ID_INVALID_COUNTER_NAME
        );
        verify(endpointProcessor, never()).process(any(Event.class));
        verify(kafkaMessageDeduplicator, times(1)).registerMessageId(null);
    }

    @Test
    void testProcessingErrorWithoutMessageId() {
        EventType eventType = mockGetEventTypeAndCreateEvent();
        mockProcessingFailure();
        Action action = buildValidAction(false);
        String payload = serializeAction(action);
        inMemoryConnector.source(INGRESS_CHANNEL).send(payload);

        micrometerAssertionHelper.awaitAndAssertTimerIncrement(CONSUMED_TIMER_NAME, 1);
        assertEquals(1L, registry.timer(CONSUMED_TIMER_NAME, "bundle", action.getBundle(), "application", action.getApplication()).count());
        micrometerAssertionHelper.assertCounterIncrement(MESSAGE_ID_MISSING_COUNTER_NAME, 1);
        micrometerAssertionHelper.assertCounterIncrement(PROCESSING_ERROR_COUNTER_NAME, 1);
        assertNoCounterIncrement(
                REJECTED_COUNTER_NAME,
                DUPLICATE_COUNTER_NAME,
                MESSAGE_ID_VALID_COUNTER_NAME,
                MESSAGE_ID_INVALID_COUNTER_NAME
        );
        verifyExactlyOneProcessing(eventType, payload, action, false);
        verify(kafkaMessageDeduplicator, times(1)).registerMessageId(null);
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
        assertEquals(2L, registry.timer(CONSUMED_TIMER_NAME, "bundle", action.getBundle(), "application", action.getApplication()).count());
        micrometerAssertionHelper.assertCounterIncrement(MESSAGE_ID_VALID_COUNTER_NAME, 2);
        micrometerAssertionHelper.assertCounterIncrement(DUPLICATE_COUNTER_NAME, 1);
        assertNoCounterIncrement(
                REJECTED_COUNTER_NAME,
                PROCESSING_ERROR_COUNTER_NAME,
                PROCESSING_EXCEPTION_COUNTER_NAME,
                MESSAGE_ID_INVALID_COUNTER_NAME,
                MESSAGE_ID_MISSING_COUNTER_NAME
        );
        verifyExactlyOneProcessing(eventType, payload, action, false);
        verify(kafkaMessageDeduplicator, times(1)).registerMessageId(messageId);
    }

    @Test
    void testNullMessageId() {
        EventType eventType = mockGetEventTypeAndCreateEvent();
        Action action = buildValidAction(false);
        String payload = serializeAction(action);
        Message<String> message = buildMessageWithId(null, payload);
        inMemoryConnector.source(INGRESS_CHANNEL).send(message);

        micrometerAssertionHelper.awaitAndAssertTimerIncrement(CONSUMED_TIMER_NAME, 1);
        assertEquals(1L, registry.timer(CONSUMED_TIMER_NAME, "bundle", action.getBundle(), "application", action.getApplication()).count());
        micrometerAssertionHelper.assertCounterIncrement(MESSAGE_ID_INVALID_COUNTER_NAME, 1);
        assertNoCounterIncrement(
                REJECTED_COUNTER_NAME,
                PROCESSING_ERROR_COUNTER_NAME,
                PROCESSING_EXCEPTION_COUNTER_NAME,
                DUPLICATE_COUNTER_NAME,
                MESSAGE_ID_VALID_COUNTER_NAME,
                MESSAGE_ID_MISSING_COUNTER_NAME
        );
        verifyExactlyOneProcessing(eventType, payload, action, false);
        verify(kafkaMessageDeduplicator, times(1)).registerMessageId(null);
    }

    @Test
    void testInvalidMessageId() {
        EventType eventType = mockGetEventTypeAndCreateEvent();
        Action action = buildValidAction(false);
        String payload = serializeAction(action);
        Message<String> message = buildMessageWithId("I am not a valid UUID!".getBytes(UTF_8), payload);
        inMemoryConnector.source(INGRESS_CHANNEL).send(message);

        micrometerAssertionHelper.awaitAndAssertTimerIncrement(CONSUMED_TIMER_NAME, 1);
        assertEquals(1L, registry.timer(CONSUMED_TIMER_NAME, "bundle", action.getBundle(), "application", action.getApplication()).count());
        micrometerAssertionHelper.assertCounterIncrement(MESSAGE_ID_INVALID_COUNTER_NAME, 1);
        assertNoCounterIncrement(
                REJECTED_COUNTER_NAME,
                PROCESSING_ERROR_COUNTER_NAME,
                PROCESSING_EXCEPTION_COUNTER_NAME,
                DUPLICATE_COUNTER_NAME,
                MESSAGE_ID_VALID_COUNTER_NAME,
                MESSAGE_ID_MISSING_COUNTER_NAME
        );
        verifyExactlyOneProcessing(eventType, payload, action, false);
        verify(kafkaMessageDeduplicator, times(1)).registerMessageId(null);
    }

    /**
     * Tests that when a test event action is received on the processor, the {@link EndpointProcessor#processTestEvent(Event, UUID)}
     * function is called.
     * @throws JsonProcessingException if the test action cannot be serialized as a JSON string.
     */
    @Test
    void testEventGetsProcessedTest() throws JsonProcessingException {
        // Create a test action for the event processor.
        final UUID endpointUuid = UUID.randomUUID();
        final String orgId = UUID.randomUUID().toString();

        final Action testAction = TestEventHelper.createTestAction(endpointUuid, orgId);

        // Serialize the object as a JSON string.
        final var mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        final String json = mapper.writeValueAsString(testAction);
        final Message<String> message = Message.of(json);

        // Create a mocked event type in order to avoid null pointer exceptions when running the processor.
        final var bundle = new Bundle();
        bundle.setId(UUID.randomUUID());

        final var application = new Application();
        application.setId(UUID.randomUUID());
        application.setDisplayName("test application");
        application.setBundle(bundle);

        final var testEventType = new EventType();
        testEventType.setApplication(application);
        testEventType.setDisplayName("test event type");

        Mockito.when(this.eventTypeRepository.getEventType(Mockito.anyString(), Mockito.anyString(), Mockito.anyString())).thenReturn(testEventType);

        // Call the function under test.
        this.eventConsumer.process(message);

        Mockito.verify(this.endpointProcessor, Mockito.times(1)).processTestEvent(Mockito.any(), Mockito.any());
    }

    /**
     * Tests that when a regular, non-test, action is received on the processor, the
     * {@link EndpointProcessor#process(Event)} function is called.
     * @throws JsonProcessingException if the test action cannot be serialized as a JSON string.
     */
    @Test
    void normalEventGetsProcessed() throws JsonProcessingException {
        // Create a mocked event type in order to avoid null pointer exceptions when running the processor.
        final var bundle = new Bundle();
        bundle.setId(UUID.randomUUID());

        final var application = new Application();
        application.setId(UUID.randomUUID());
        application.setDisplayName("test application");
        application.setBundle(bundle);

        final var testEventType = new EventType();
        testEventType.setApplication(application);
        testEventType.setDisplayName("test event type");

        Mockito.when(this.eventTypeRepository.getEventType(Mockito.anyString(), Mockito.anyString(), Mockito.anyString())).thenReturn(testEventType);

        // Create an action that should not be identified as a test action.
        final UUID endpointUuid = UUID.randomUUID();
        final String orgId = UUID.randomUUID().toString();

        final Action testAction = TestEventHelper.createTestAction(endpointUuid, orgId);

        // Remove the test action flag that is used to identify a test action.
        final var properties = testAction.getContext().getAdditionalProperties();
        properties.remove(TestEventHelper.TEST_ACTION_CONTEXT_TEST_EVENT);

        // Serialize the object as a JSON string.
        final var mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        final String noTestPropertyAction = mapper.writeValueAsString(testAction);

        // Remove the context from the action and serialize it again to create a new message.
        testAction.setContext(null);
        final String noContextAction = mapper.writeValueAsString(testAction);

        // Create the messages to test the beavior.
        final var normalMessagesList = new ArrayList<Message<String>>();
        normalMessagesList.add(Message.of(noTestPropertyAction));
        normalMessagesList.add(Message.of(noContextAction));

        for (final var message : normalMessagesList) {
            // Call the function under test.
            this.eventConsumer.process(message);
        }

        // The regular "process" function should be called twice, since the two actions we have prepared should be
        // considered as regular actions, and not test actions.
        Mockito.verify(this.endpointProcessor, Mockito.times(2)).process(Mockito.any());
        // None of the actions should be identified as test actions, so the "processTestEvent" function should not
        // be called.
        Mockito.verify(this.endpointProcessor, Mockito.times(0)).processTestEvent(Mockito.any(), Mockito.any());
    }

    private EventType mockGetEventTypeAndCreateEvent() {
        Bundle bundle = new Bundle();
        bundle.setDisplayName("Bundle");

        Application app = new Application();
        app.setDisplayName("Application");
        app.setBundle(bundle);

        EventType eventType = new EventType();
        eventType.setDisplayName("Event type");
        eventType.setApplication(app);
        when(eventTypeRepository.getEventType(eq(BUNDLE), eq(APP), eq(EVENT_TYPE))).thenReturn(eventType);
        when(eventRepository.create(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return eventType;
    }

    private void mockGetUnknownEventType() {
        when(eventTypeRepository.getEventType(eq(BUNDLE), eq(APP), eq(EVENT_TYPE))).thenThrow(
                new NoResultException("I am a forced exception!")
        );
    }

    private void mockProcessingFailure() {
        doThrow(new RuntimeException("I am a forced exception!"))
                .when(endpointProcessor).process(any(Event.class));
    }

    private void verifyExactlyOneProcessing(EventType eventType, String payload, Action action, boolean withAccountId) {
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
        assertEquals(action, argumentCaptor.getValue().getAction());
    }

    private void assertNoCounterIncrement(String... counterNames) {
        for (String counterName : counterNames) {
            micrometerAssertionHelper.assertCounterIncrement(counterName, 0);
        }
    }

    private static Action buildValidAction(boolean withAccountId) {
        Action action = new Action();
        action.setVersion("v1.0.0");
        action.setBundle(BUNDLE);
        action.setApplication(APP);
        action.setEventType(EVENT_TYPE);
        action.setTimestamp(LocalDateTime.now());
        if (withAccountId) {
            action.setAccountId(DEFAULT_ACCOUNT_ID);
        }
        action.setOrgId(DEFAULT_ORG_ID);
        action.setRecipients(List.of());
        action.setEvents(
                List.of(
                        new com.redhat.cloud.notifications.ingress.Event.EventBuilder()
                                .withMetadata(new Metadata.MetadataBuilder().build())
                                .withPayload(new Payload.PayloadBuilder()
                                        .withAdditionalProperty("k", "v")
                                        .withAdditionalProperty("k2", "v2")
                                        .withAdditionalProperty("k3", "v")
                                        .build()
                                )
                                .build()
                )
        );

        action.setContext(new Context.ContextBuilder().build());
        return action;
    }

    private static Message<String> buildMessageWithId(byte[] messageId, String payload) {
        OutgoingKafkaRecordMetadata metadata = OutgoingKafkaRecordMetadata.builder()
                .withHeaders(new RecordHeaders().add(MESSAGE_ID_HEADER, messageId))
                .build();
        return Message.of(payload).addMetadata(metadata);
    }
}
