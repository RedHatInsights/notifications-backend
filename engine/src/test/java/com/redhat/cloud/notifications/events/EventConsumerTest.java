package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.FeatureFlipper;
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

import javax.enterprise.inject.Any;
import javax.inject.Inject;
import javax.persistence.NoResultException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ACCOUNT_ID;
import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.TestHelpers.serializeAction;
import static com.redhat.cloud.notifications.events.EventConsumer.CONSUMED_TIMER_NAME;
import static com.redhat.cloud.notifications.events.EventConsumer.DUPLICATE_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.EventConsumer.INGRESS_CHANNEL;
import static com.redhat.cloud.notifications.events.EventConsumer.MISSING_ORG_ID;
import static com.redhat.cloud.notifications.events.EventConsumer.PROCESSING_ERROR_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.EventConsumer.PROCESSING_EXCEPTION_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.EventConsumer.REJECTED_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.KafkaMessageDeduplicator.MESSAGE_ID_HEADER;
import static com.redhat.cloud.notifications.events.KafkaMessageDeduplicator.MESSAGE_ID_INVALID_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.KafkaMessageDeduplicator.MESSAGE_ID_MISSING_COUNTER_NAME;
import static com.redhat.cloud.notifications.events.KafkaMessageDeduplicator.MESSAGE_ID_VALID_COUNTER_NAME;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    FeatureFlipper featureFlipper;

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
        micrometerAssertionHelper.saveCounterValueWithTagsBeforeTest(MISSING_ORG_ID, "application", "bundle");
        micrometerAssertionHelper.removeDynamicTimer(CONSUMED_TIMER_NAME);
    }

    @AfterEach
    void clear() {
        micrometerAssertionHelper.clearSavedValues();
        micrometerAssertionHelper.removeDynamicTimer(CONSUMED_TIMER_NAME);
    }

    @Test
    void testValidPayloadWithMessageId_AccountId() {
        featureFlipper.setUseOrgId(false);
        testValidPayloadWithMessageId();
    }

    @Test
    void testValidPayloadWithMessageId_OrgId() {
        featureFlipper.setUseOrgId(true);
        testValidPayloadWithMessageId();
        featureFlipper.setUseOrgId(false);
    }

    void testValidPayloadWithMessageId() {
        EventType eventType = mockGetEventTypeAndCreateEvent();
        Action action = buildValidAction();
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
                MESSAGE_ID_MISSING_COUNTER_NAME,
                MISSING_ORG_ID
        );
        verifyExactlyOneProcessing(eventType, payload, action);
        verify(kafkaMessageDeduplicator, times(1)).registerMessageId(messageId);
    }

    @Test
    void testValidPayloadWithoutOrgId_AccountId() {
        featureFlipper.setUseOrgId(false);
        testValidPayloadWithoutOrgId();
    }

    @Test
    void testValidPayloadWithoutOrgId_OrgId() {
        featureFlipper.setUseOrgId(true);
        testValidPayloadWithoutOrgId();
        featureFlipper.setUseOrgId(false);
    }

    void testValidPayloadWithoutOrgId() {
        featureFlipper.setUseOrgId(true);
        featureFlipper.setTranslateAccountIdToOrgId(true);

        EventType eventType = mockGetEventTypeAndCreateEvent();
        Action action = buildValidAction();
        action.setOrgId(null);
        MockServerConfig.mockBopOrgIdTranslation();
        String payload = serializeAction(action);
        UUID messageId = UUID.randomUUID();
        Message<String> message = buildMessageWithId(messageId.toString().getBytes(UTF_8), payload);
        inMemoryConnector.source(INGRESS_CHANNEL).send(message);

        micrometerAssertionHelper.awaitAndAssertTimerIncrement(CONSUMED_TIMER_NAME, 1);
        assertEquals(1L, registry.timer(CONSUMED_TIMER_NAME, "bundle", action.getBundle(), "application", action.getApplication()).count());
        micrometerAssertionHelper.assertCounterIncrement(MESSAGE_ID_VALID_COUNTER_NAME, 1);
        micrometerAssertionHelper.assertCounterIncrement(MISSING_ORG_ID, 1, "application", APP, "bundle", BUNDLE);
        assertNoCounterIncrement(
                REJECTED_COUNTER_NAME,
                PROCESSING_ERROR_COUNTER_NAME,
                PROCESSING_EXCEPTION_COUNTER_NAME,
                DUPLICATE_COUNTER_NAME,
                MESSAGE_ID_INVALID_COUNTER_NAME,
                MESSAGE_ID_MISSING_COUNTER_NAME
        );
        // The action org ID is set by OrgIdTranslator after the Kafka message is received.
        action.setOrgId(DEFAULT_ORG_ID);
        verifyExactlyOneProcessing(eventType, payload, action);
        verify(kafkaMessageDeduplicator, times(1)).registerMessageId(messageId);

        featureFlipper.setUseOrgId(false);
        featureFlipper.setTranslateAccountIdToOrgId(false);
    }

    @Test
    void testValidPayloadWithoutMessageId_AccountId() {
        featureFlipper.setUseOrgId(false);
        testValidPayloadWithoutMessageId();
    }

    @Test
    void testValidPayloadWithoutMessageId_OrgId() {
        featureFlipper.setUseOrgId(true);
        testValidPayloadWithoutMessageId();
        featureFlipper.setUseOrgId(false);
    }

    void testValidPayloadWithoutMessageId() {
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
                PROCESSING_EXCEPTION_COUNTER_NAME,
                DUPLICATE_COUNTER_NAME,
                MESSAGE_ID_VALID_COUNTER_NAME,
                MESSAGE_ID_INVALID_COUNTER_NAME,
                MISSING_ORG_ID
        );
        verifyExactlyOneProcessing(eventType, payload, action);
        verify(kafkaMessageDeduplicator, times(1)).registerMessageId(null);
    }

    @Test
    void testInvalidPayloadWithMessageId_AccountId() {
        featureFlipper.setUseOrgId(false);
        testInvalidPayloadWithMessageId();
    }

    @Test
    void testInvalidPayloadWithMessageId_OrgId() {
        featureFlipper.setUseOrgId(true);
        testInvalidPayloadWithMessageId();
        featureFlipper.setUseOrgId(false);
    }

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
                MESSAGE_ID_MISSING_COUNTER_NAME,
                MISSING_ORG_ID
        );
        verify(endpointProcessor, never()).process(any(Event.class));
        verify(kafkaMessageDeduplicator, never()).registerMessageId(any(UUID.class));
    }

    @Test
    void testUnknownEventTypeWithoutMessageId_AccountId() {
        featureFlipper.setUseOrgId(false);
        testUnknownEventTypeWithoutMessageId();
    }

    @Test
    void testUnknownEventTypeWithoutMessageId_OrgId() {
        featureFlipper.setUseOrgId(true);
        testUnknownEventTypeWithoutMessageId();
        featureFlipper.setUseOrgId(false);
    }

    void testUnknownEventTypeWithoutMessageId() {
        mockGetUnknownEventType();
        Action action = buildValidAction();
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
                MESSAGE_ID_INVALID_COUNTER_NAME,
                MISSING_ORG_ID
        );
        verify(endpointProcessor, never()).process(any(Event.class));
        verify(kafkaMessageDeduplicator, times(1)).registerMessageId(null);
    }

    @Test
    void testProcessingErrorWithoutMessageId_AccountId() {
        featureFlipper.setUseOrgId(false);
        testProcessingErrorWithoutMessageId();
    }

    @Test
    void testProcessingErrorWithoutMessageId_OrgId() {
        featureFlipper.setUseOrgId(true);
        testProcessingErrorWithoutMessageId();
        featureFlipper.setUseOrgId(false);
    }

    void testProcessingErrorWithoutMessageId() {
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
                MESSAGE_ID_INVALID_COUNTER_NAME,
                MISSING_ORG_ID
        );
        verifyExactlyOneProcessing(eventType, payload, action);
        verify(kafkaMessageDeduplicator, times(1)).registerMessageId(null);
    }

    @Test
    void testDuplicatePayload_AccountId() {
        featureFlipper.setUseOrgId(false);
        testDuplicatePayload();
    }

    @Test
    void testDuplicatePayload_OrgId() {
        featureFlipper.setUseOrgId(true);
        testDuplicatePayload();
        featureFlipper.setUseOrgId(false);
    }

    void testDuplicatePayload() {
        EventType eventType = mockGetEventTypeAndCreateEvent();
        Action action = buildValidAction();
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
                MESSAGE_ID_MISSING_COUNTER_NAME,
                MISSING_ORG_ID
        );
        verifyExactlyOneProcessing(eventType, payload, action);
        verify(kafkaMessageDeduplicator, times(1)).registerMessageId(messageId);
    }

    @Test
    void testNullMessageId_AccountId() {
        featureFlipper.setUseOrgId(false);
        testNullMessageId();
    }

    @Test
    void testNullMessageId_OrgId() {
        featureFlipper.setUseOrgId(true);
        testNullMessageId();
        featureFlipper.setUseOrgId(false);
    }

    void testNullMessageId() {
        EventType eventType = mockGetEventTypeAndCreateEvent();
        Action action = buildValidAction();
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
                MESSAGE_ID_MISSING_COUNTER_NAME,
                MISSING_ORG_ID
        );
        verifyExactlyOneProcessing(eventType, payload, action);
        verify(kafkaMessageDeduplicator, times(1)).registerMessageId(null);
    }

    @Test
    void testInvalidMessageId_AccountId() {
        featureFlipper.setUseOrgId(false);
        testInvalidMessageId();
    }

    @Test
    void testInvalidMessageId_OrgId() {
        featureFlipper.setUseOrgId(true);
        testInvalidMessageId();
        featureFlipper.setUseOrgId(false);
    }

    void testInvalidMessageId() {
        EventType eventType = mockGetEventTypeAndCreateEvent();
        Action action = buildValidAction();
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
                MESSAGE_ID_MISSING_COUNTER_NAME,
                MISSING_ORG_ID
        );
        verifyExactlyOneProcessing(eventType, payload, action);
        verify(kafkaMessageDeduplicator, times(1)).registerMessageId(null);
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

    private void verifyExactlyOneProcessing(EventType eventType, String payload, Action action) {
        ArgumentCaptor<Event> argumentCaptor = ArgumentCaptor.forClass(Event.class);
        verify(endpointProcessor, times(1)).process(argumentCaptor.capture());
        assertEquals(DEFAULT_ACCOUNT_ID, argumentCaptor.getValue().getAccountId());
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

    private static Action buildValidAction() {
        Action action = new Action();
        action.setVersion("v1.0.0");
        action.setBundle(BUNDLE);
        action.setApplication(APP);
        action.setEventType(EVENT_TYPE);
        action.setTimestamp(LocalDateTime.now());
        action.setAccountId(DEFAULT_ACCOUNT_ID);
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
