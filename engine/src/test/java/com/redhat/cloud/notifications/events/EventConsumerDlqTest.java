package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.EngineConfig;
import io.quarkus.logging.Log;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static com.redhat.cloud.notifications.events.ReplayEventConsumer.REPLAYED_MESSAGE_COUNTER_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration test that validates the full Dead Letter Queue mechanism:
 * 1. Message fails processing in ingress consumer
 * 2. Message is sent to DLQ topic
 * 3. DLQ consumer (ReplayEventConsumer) picks it up and reprocesses it
 */
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
@TestProfile(DlqTestProfile.class)
public class EventConsumerDlqTest {

    private static final String INGRESS_TOPIC = "platform.notifications.ingress";

    @InjectSpy
    EngineConfig config;

    @InjectSpy
    ReplayEventConsumer replayEventConsumer;

    @InjectSpy
    EventConsumer eventConsumer;

    @Inject
    MicrometerAssertionHelper micrometerAssertionHelper;

    private AtomicInteger processCallCount;

    @BeforeEach
    void setup() {
        // save metric value
        micrometerAssertionHelper.saveCounterValuesBeforeTest(REPLAYED_MESSAGE_COUNTER_NAME);

        // Disable async processing to ensure exception propagates properly
        when(config.isAsyncEventProcessing()).thenReturn(false);

        // Track how many times endpointProcessor.process is called
        processCallCount = new AtomicInteger(0);

        // Mock endpoint processor to throw exception on first call, succeed on second
        doAnswer(invocation -> {
            int count = processCallCount.incrementAndGet();
            Log.debugf("EventConsumer.process() called - attempt #%d", count);
            if (count == 1) {
                // First call (from ingress consumer) - throw exception to trigger DLQ
                Log.debugf("Throwing exception to trigger DLQ");
                throw new RuntimeException("Simulated processing failure for DLQ test");
            }
            // Second call (from DLQ consumer) - succeed
            Log.debugf("Processing succeeded (from DLQ)");
            throw new RuntimeException("Simulated processing failure for DLQ test");
            //return null;
        }).when(eventConsumer).process(any());
    }

    @Test
    void testKafkaDlqMechanism() throws Exception {

        sendKafkaMessage();

        // Wait for the full DLQ workflow:
        // 1. Ingress consumer processes message and fails
        // 2. Kafka sends message to DLQ topic (notifications-dlq)
        // 3. DLQ consumer picks it up and calls consumeDeadLetterQueue
        // 4. Message is reprocessed successfully
        micrometerAssertionHelper.awaitAndAssertCounterIncrement(REPLAYED_MESSAGE_COUNTER_NAME, 1);
        verify(replayEventConsumer, times(1)).consumeDeadLetterQueue(any());
        verify(eventConsumer, times(2)).process(any());
    }

    private static void sendKafkaMessage() throws InterruptedException, ExecutionException {
        // Configure Kafka producer to send directly to the ingress topic
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            // Create record with message ID header
            ProducerRecord<String, String> record = new ProducerRecord<>(INGRESS_TOPIC, "I love Kafka");

            Log.debugf("Sending message to Kafka topic: %s", INGRESS_TOPIC);

            // Send to Kafka and wait for acknowledgment
            producer.send(record).get();

            Log.debugf("Message sent successfully");
        }
    }
}
