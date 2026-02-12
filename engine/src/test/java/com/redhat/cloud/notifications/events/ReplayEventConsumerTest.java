package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.EventPayloadTestHelper;
import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.EngineConfig;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.TestHelpers.serializeAction;
import static com.redhat.cloud.notifications.events.ReplayEventConsumer.INGRESS_REPLAY_CHANNEL;
import static com.redhat.cloud.notifications.events.ReplayEventConsumer.REPLAYED_MESSAGE_COUNTER_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class ReplayEventConsumerTest {

    private static final String BUNDLE = "my-bundle";
    private static final String APP = "my-app";
    private static final String EVENT_TYPE = "my-event-type";

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

    @InjectSpy
    EngineConfig config;

    @InjectSpy
    ReplayEventConsumer replayEventConsumer;

    @InjectMock
    EventConsumer eventConsumer;

    @BeforeEach
    void beforeEach() {
        micrometerAssertionHelper.saveCounterValuesBeforeTest(
            REPLAYED_MESSAGE_COUNTER_NAME
        );

        // Enable replay processing
        when(config.isSkipMessageProcessing()).thenReturn(false);

        // Set replay time window to allow test messages through
        replayEventConsumer.replayStartTime = Instant.parse("2020-01-01T00:00:00Z");
        replayEventConsumer.replayEndTime = Instant.parse("2025-01-01T00:00:00Z");
    }

    @AfterEach
    void clear() {
        micrometerAssertionHelper.clearSavedValues();
    }

    @Test
    void testKafkaTimestampOutOfTimeWindow() throws InterruptedException {
        Message<String> message = buildMessageWithTimestamp(Instant.now());
        inMemoryConnector.source(INGRESS_REPLAY_CHANNEL).send(message);
        Thread.sleep(200);

        verify(replayEventConsumer, times(1)).consume(any());
        verifyNoInteractions(eventConsumer);
        micrometerAssertionHelper.assertCounterIncrement(REPLAYED_MESSAGE_COUNTER_NAME, 0);
    }

    @Test
    void testKafkaTimestampInTimeWindow() {
        Message<String> message = buildMessageWithTimestamp(Instant.parse("2022-01-01T00:00:00Z"));
        inMemoryConnector.source(INGRESS_REPLAY_CHANNEL).send(message);

        micrometerAssertionHelper.awaitAndAssertCounterIncrement(REPLAYED_MESSAGE_COUNTER_NAME, 1);
        verify(replayEventConsumer, times(1)).consume(any());
        verify(eventConsumer, times(1)).process(any());
    }

    @Test
    void testKafkaTimestampInTimeWindowButSkipMessageEnabled() throws InterruptedException  {
        when(config.isSkipMessageProcessing()).thenReturn(true);
        Message<String> message = buildMessageWithTimestamp(Instant.parse("2022-01-01T00:00:00Z"));
        inMemoryConnector.source(INGRESS_REPLAY_CHANNEL).send(message);
        Thread.sleep(200);

        verify(replayEventConsumer, times(1)).consume(any());
        verifyNoInteractions(eventConsumer);
        micrometerAssertionHelper.assertCounterIncrement(REPLAYED_MESSAGE_COUNTER_NAME, 0);
    }

    private static Message<String> buildMessageWithTimestamp(Instant timestamp) {
        String payload = serializeAction(EventPayloadTestHelper.buildValidAction(DEFAULT_ORG_ID, BUNDLE, APP, EVENT_TYPE));
        OutgoingKafkaRecordMetadata metadata = OutgoingKafkaRecordMetadata.builder()
                .withTimestamp(timestamp)
                .build();
        return Message.of(payload).addMetadata(metadata);
    }
}
