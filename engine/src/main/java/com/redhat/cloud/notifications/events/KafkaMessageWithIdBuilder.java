package com.redhat.cloud.notifications.events;

import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.util.UUID;

import static com.redhat.cloud.notifications.events.KafkaMessageDeduplicator.MESSAGE_ID_HEADER;
import static java.nio.charset.StandardCharsets.UTF_8;

public class KafkaMessageWithIdBuilder {

    public static Message build(String payload) {
        byte[] messageId = UUID.randomUUID().toString().getBytes(UTF_8);
        OutgoingKafkaRecordMetadata metadata = OutgoingKafkaRecordMetadata.builder()
                .withHeaders(new RecordHeaders().add(MESSAGE_ID_HEADER, messageId))
                .build();
        return Message.of(payload).addMetadata(metadata);
    }
}
