package com.redhat.cloud.notifications.events;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import jakarta.inject.Inject;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class KafkaHeadersExtractorTest {

    @Inject
    KafkaHeadersExtractor kafkaHeadersExtractor;

    @Test
    void testWithNoHeaderKeys() {
        assertTrue(kafkaHeadersExtractor.extract(Message.of("{}")).isEmpty());
    }

    @Test
    void test() {
        String messageId = UUID.randomUUID().toString();
        String sourceEnvironment = "stage";

        Message<String> message = buildMessageWithHeaders(Map.of(
                "rh-message-id", messageId,
                "rh-source-environment", sourceEnvironment,
                "rh-unused-header", "whatever"
        ));

        Map<String, Optional<String>> extractedHeaders = kafkaHeadersExtractor.extract(message,
                "rh-message-id",
                "rh-unknown-header",
                "rh-source-environment"
        );

        assertEquals(messageId, extractedHeaders.get("rh-message-id").get());
        assertTrue(extractedHeaders.get("rh-unknown-header").isEmpty());
        assertEquals(sourceEnvironment, extractedHeaders.get("rh-source-environment").get());
    }

    private static Message<String> buildMessageWithHeaders(Map<String, String> headers) {
        Headers recordHeaders = new RecordHeaders();
        for (Map.Entry<String, String> header : headers.entrySet()) {
            recordHeaders.add(header.getKey(), header.getValue().getBytes(UTF_8));
        }
        OutgoingKafkaRecordMetadata<String> metadata = OutgoingKafkaRecordMetadata.<String>builder()
                .withHeaders(recordHeaders)
                .build();
        return Message.of("{}")
                .addMetadata(metadata);
    }
}
