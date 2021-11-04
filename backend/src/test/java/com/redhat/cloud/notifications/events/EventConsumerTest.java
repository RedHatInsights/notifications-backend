package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.ApplicationResources;
import com.redhat.cloud.notifications.db.EventResources;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.connectors.InMemoryConnector;
import io.smallrye.reactive.messaging.kafka.OutgoingKafkaRecordMetadata;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.reactive.messaging.Message;
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
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestHelpers.serializeAction;
import static com.redhat.cloud.notifications.events.EventConsumer.CONSUMED_TIMER_NAME;
import static com.redhat.cloud.notifications.events.EventConsumer.DUPLICATE_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.EventConsumer.INGRESS_CHANNEL;
import static com.redhat.cloud.notifications.events.EventConsumer.PROCESSING_ERROR_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.EventConsumer.REJECTED_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.KafkaMessageDeduplicator.MESSAGE_ID_HEADER;
import static com.redhat.cloud.notifications.events.KafkaMessageDeduplicator.MESSAGE_ID_INVALID_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.KafkaMessageDeduplicator.MESSAGE_ID_MISSING_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.KafkaMessageDeduplicator.MESSAGE_ID_VALID_COUNTER_NAME;
import static java.nio.charset.StandardCharsets.UTF_8;
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

    @InjectSpy
    KafkaMessageDeduplicator kafkaMessageDeduplicator;

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

    @Inject
    MeterRegistry registry;

    @BeforeEach
    void beforeEach() {
        micrometerAssertionHelper.saveCounterValuesBeforeTest(
                REJECTED_COUNTER_NAME,
                PROCESSING_ERROR_COUNTER_NAME,
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
    void testValidPayloadWithMessageId() throws IOException {
        EventType eventType = mockGetEventTypeAndCreateEvent();
        Action action = buildValidAction();
        String payload = serializeAction(action);
        UUID messageId = UUID.randomUUID();
        Message message = buildMessageWithId(messageId.toString().getBytes(UTF_8), payload);
        inMemoryConnector.source(INGRESS_CHANNEL).send(message);

        micrometerAssertionHelper.awaitAndAssertTimerIncrement(CONSUMED_TIMER_NAME, 1);
        assertEquals(1L, registry.timer(CONSUMED_TIMER_NAME, "bundle", action.getBundle(), "application", action.getApplication()).count());
        micrometerAssertionHelper.assertCounterIncrement(MESSAGE_ID_VALID_COUNTER_NAME, 1);
        assertNoCounterIncrement(
                REJECTED_COUNTER_NAME,
                PROCESSING_ERROR_COUNTER_NAME,
                DUPLICATE_COUNTER_NAME,
                MESSAGE_ID_INVALID_COUNTER_NAME,
                MESSAGE_ID_MISSING_COUNTER_NAME
        );
        verifyExactlyOneProcessing(eventType, payload, action);
        verify(kafkaMessageDeduplicator, times(1)).registerMessageId(messageId);
    }

    @Test
    void testValidPayloadWithoutMessageId() throws IOException {
        EventType eventType = mockGetEventTypeAndCreateEvent();
        Action action = buildValidAction();
        String payload = serializeAction(action);
        inMemoryConnector.source(INGRESS_CHANNEL).send(payload);

        micrometerAssertionHelper.awaitAndAssertTimerIncrement(CONSUMED_TIMER_NAME, 1);
        assertEquals(1L, registry.timer(CONSUMED_TIMER_NAME, "bundle", action.getBundle(), "application", action.getApplication()).count());
        micrometerAssertionHelper.assertCounterIncrement(MESSAGE_ID_MISSING_COUNTER_NAME, 1);
        assertNoCounterIncrement(
                REJECTED_COUNTER_NAME,
                PROCESSING_ERROR_COUNTER_NAME,
                DUPLICATE_COUNTER_NAME,
                MESSAGE_ID_VALID_COUNTER_NAME,
                MESSAGE_ID_INVALID_COUNTER_NAME
        );
        verifyExactlyOneProcessing(eventType, payload, action);
        verify(kafkaMessageDeduplicator, times(1)).registerMessageId(null);
    }

    @Test
    void testInvalidPayloadWithMessageId() {
        Message message = buildMessageWithId(UUID.randomUUID().toString().getBytes(UTF_8), "I am not a valid payload!");
        inMemoryConnector.source(INGRESS_CHANNEL).send(message);

        micrometerAssertionHelper.awaitAndAssertTimerIncrement(CONSUMED_TIMER_NAME, 1);
        assertEquals(1L, registry.timer(CONSUMED_TIMER_NAME, "bundle", "", "application", "").count());
        micrometerAssertionHelper.assertCounterIncrement(REJECTED_COUNTER_NAME, 1);
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
    void testUnknownEventTypeWithoutMessageId() throws IOException {
        mockGetUnknownEventType();
        Action action = buildValidAction();
        String payload = serializeAction(action);
        inMemoryConnector.source(INGRESS_CHANNEL).send(payload);

        micrometerAssertionHelper.awaitAndAssertTimerIncrement(CONSUMED_TIMER_NAME, 1);
        assertEquals(1L, registry.timer(CONSUMED_TIMER_NAME, "bundle", action.getBundle(), "application", action.getApplication()).count());
        micrometerAssertionHelper.assertCounterIncrement(MESSAGE_ID_MISSING_COUNTER_NAME, 1);
        micrometerAssertionHelper.assertCounterIncrement(REJECTED_COUNTER_NAME, 1);
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
    void testProcessingErrorWithoutMessageId() throws IOException {
        EventType eventType = mockGetEventTypeAndCreateEvent();
        mockProcessingFailure();
        Action action = buildValidAction();
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
        verifyExactlyOneProcessing(eventType, payload, action);
        verify(kafkaMessageDeduplicator, times(1)).registerMessageId(null);
    }

    @Test
    void testDuplicatePayload() throws IOException {
        EventType eventType = mockGetEventTypeAndCreateEvent();
        Action action = buildValidAction();
        String payload = serializeAction(action);
        UUID messageId = UUID.randomUUID();
        Message message = buildMessageWithId(messageId.toString().getBytes(UTF_8), payload);
        inMemoryConnector.source(INGRESS_CHANNEL).send(message);
        inMemoryConnector.source(INGRESS_CHANNEL).send(message);

        micrometerAssertionHelper.awaitAndAssertTimerIncrement(CONSUMED_TIMER_NAME, 2);
        assertEquals(2L, registry.timer(CONSUMED_TIMER_NAME, "bundle", action.getBundle(), "application", action.getApplication()).count());
        micrometerAssertionHelper.assertCounterIncrement(MESSAGE_ID_VALID_COUNTER_NAME, 2);
        micrometerAssertionHelper.assertCounterIncrement(DUPLICATE_COUNTER_NAME, 1);
        assertNoCounterIncrement(
                REJECTED_COUNTER_NAME,
                PROCESSING_ERROR_COUNTER_NAME,
                MESSAGE_ID_INVALID_COUNTER_NAME,
                MESSAGE_ID_MISSING_COUNTER_NAME
        );
        verifyExactlyOneProcessing(eventType, payload, action);
        verify(kafkaMessageDeduplicator, times(1)).registerMessageId(messageId);
    }

    @Test
    void testNullMessageId() throws IOException {
        EventType eventType = mockGetEventTypeAndCreateEvent();
        Action action = buildValidAction();
        String payload = serializeAction(action);
        Message message = buildMessageWithId(null, payload);
        inMemoryConnector.source(INGRESS_CHANNEL).send(message);

        micrometerAssertionHelper.awaitAndAssertTimerIncrement(CONSUMED_TIMER_NAME, 1);
        assertEquals(1L, registry.timer(CONSUMED_TIMER_NAME, "bundle", action.getBundle(), "application", action.getApplication()).count());
        micrometerAssertionHelper.assertCounterIncrement(MESSAGE_ID_INVALID_COUNTER_NAME, 1);
        assertNoCounterIncrement(
                REJECTED_COUNTER_NAME,
                PROCESSING_ERROR_COUNTER_NAME,
                DUPLICATE_COUNTER_NAME,
                MESSAGE_ID_VALID_COUNTER_NAME,
                MESSAGE_ID_MISSING_COUNTER_NAME
        );
        verifyExactlyOneProcessing(eventType, payload, action);
        verify(kafkaMessageDeduplicator, times(1)).registerMessageId(null);
    }

    @Test
    void testInvalidMessageId() throws IOException {
        EventType eventType = mockGetEventTypeAndCreateEvent();
        Action action = buildValidAction();
        String payload = serializeAction(action);
        Message message = buildMessageWithId("I am not a valid UUID!".getBytes(UTF_8), payload);
        inMemoryConnector.source(INGRESS_CHANNEL).send(message);

        micrometerAssertionHelper.awaitAndAssertTimerIncrement(CONSUMED_TIMER_NAME, 1);
        assertEquals(1L, registry.timer(CONSUMED_TIMER_NAME, "bundle", action.getBundle(), "application", action.getApplication()).count());
        micrometerAssertionHelper.assertCounterIncrement(MESSAGE_ID_INVALID_COUNTER_NAME, 1);
        assertNoCounterIncrement(
                REJECTED_COUNTER_NAME,
                PROCESSING_ERROR_COUNTER_NAME,
                DUPLICATE_COUNTER_NAME,
                MESSAGE_ID_VALID_COUNTER_NAME,
                MESSAGE_ID_MISSING_COUNTER_NAME
        );
        verifyExactlyOneProcessing(eventType, payload, action);
        verify(kafkaMessageDeduplicator, times(1)).registerMessageId(null);
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

    private void verifyExactlyOneProcessing(EventType eventType, String payload, Action action) {
        ArgumentCaptor<Event> argumentCaptor = ArgumentCaptor.forClass(Event.class);
        verify(endpointProcessor, times(1)).process(argumentCaptor.capture());
        assertEquals(DEFAULT_ACCOUNT_ID, argumentCaptor.getValue().getAccountId());
        assertEquals(eventType, argumentCaptor.getValue().getEventType());
        assertEquals(payload, argumentCaptor.getValue().getPayload());
        assertEquals(action, argumentCaptor.getValue().getAction());
    }

    private void assertNoCounterIncrement(String... counterNames) {
        for (String counterName : counterNames) {
            micrometerAssertionHelper.assertCounterIncrement(counterName, 0);
        }
    }

    private static Action buildValidAction() {
        Action action = new Action();
        action.setVersion("v1.0.0");
        action.setBundle(BUNDLE);
        action.setApplication(APP);
        action.setEventType(EVENT_TYPE);
        action.setTimestamp(LocalDateTime.now());
        action.setAccountId(DEFAULT_ACCOUNT_ID);
        action.setRecipients(List.of());
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

    private static Message buildMessageWithId(byte[] messageId, String payload) {
        OutgoingKafkaRecordMetadata metadata = OutgoingKafkaRecordMetadata.builder()
                .withHeaders(new RecordHeaders().add(MESSAGE_ID_HEADER, messageId))
                .build();
        return Message.of(payload).addMetadata(metadata);
    }
}
